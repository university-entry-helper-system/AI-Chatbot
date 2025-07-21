package com.khoipd8.educationchatbot.service;

// SPRING CORE IMPORTS
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// SPRING HTTP IMPORTS (quan trọng cho RestTemplate)
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// JACKSON JSON IMPORTS
import com.fasterxml.jackson.databind.ObjectMapper;

// LOMBOK
import lombok.extern.slf4j.Slf4j;

// JAVA STANDARD IMPORTS
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// YOUR PROJECT IMPORTS
import com.khoipd8.educationchatbot.entity.University;
import com.khoipd8.educationchatbot.entity.Program;
import com.khoipd8.educationchatbot.repository.UniversityRepository;
import com.khoipd8.educationchatbot.service.EnhancedRAGService.DocumentChunk;
import com.khoipd8.educationchatbot.entity.StudentScore;
import com.khoipd8.educationchatbot.repository.StudentScoreRepository;

// XÓA TOÀN BỘ OpenAI SDK imports (không cần nữa)
// import com.openai.client.OpenAIClient;
// import com.openai.client.okhttp.OpenAIOkHttpClient;
// import com.openai.models.chat.completions.ChatCompletion;
// import com.openai.models.chat.completions.ChatCompletionCreateParams;
// import com.openai.models.chat.completions.ChatCompletionMessageParam;

@Service
@Slf4j
public class ChatGPTRAGService {
    
    @Autowired
    private UniversityRepository universityRepository;
    
    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private EnhancedRAGService enhancedRAGService;
    
    @Autowired
    private StudentScoreRepository studentScoreRepository;
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;
    
    @Value("${openai.api.model:gpt-4o-mini}")
    private String openaiModel;
    
    // CHỈ CẦN RestTemplate - XÓA OpenAI client
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // CACHE và tracking như cũ
    private final Map<String, String> responseCache = new ConcurrentHashMap<>();
    private final Map<String, DocumentChunk> vectorStore = new HashMap<>();
    private boolean isIndexed = false;
    
    // COST TRACKING
    private int totalTokensUsed = 0;
    private double totalCostUSD = 0.0;
    
    // Pricing constants
    private static final double GPT4_INPUT_COST = 0.03 / 1000;
    private static final double GPT4_OUTPUT_COST = 0.06 / 1000;
    private static final double GPT35_INPUT_COST = 0.0015 / 1000;
    private static final double GPT35_OUTPUT_COST = 0.002 / 1000;
    
    // Add SBD-related keywords for validation
    private static final List<String> SBD_KEYWORDS = Arrays.asList(
        // Vietnamese terms
        "số báo danh", "sbd", "báo danh", "mã thí sinh", "mã dự thi", 
        "số thí sinh", "số dự thi", "mã số thí sinh", "số báo", "điểm thi",
        "tra cứu điểm", "xem điểm", "kiểm tra điểm", "tìm điểm",
        
        // English terms
        "exam id", "candidate id", "registration number", "exam number",
        "student id", "test id", "examination id",
        
        // Common patterns
        "có bao nhiêu điểm", "được bao nhiêu điểm", "điểm của", "kết quả thi",
        "tra điểm", "coi điểm", "điểm số", "điểm thi thpt"
    );

    // Subject keyword mapping for SBD subject-specific queries
    private static final Map<String, String> SUBJECT_KEYWORDS = Map.ofEntries(
        Map.entry("toán", "scoreMath"),
        Map.entry("math", "scoreMath"),
        Map.entry("văn", "scoreLiterature"),
        Map.entry("ngữ văn", "scoreLiterature"),
        Map.entry("literature", "scoreLiterature"),
        Map.entry("anh", "scoreEnglish"),
        Map.entry("tiếng anh", "scoreEnglish"),
        Map.entry("english", "scoreEnglish"),
        Map.entry("lý", "scorePhysics"),
        Map.entry("vật lý", "scorePhysics"),
        Map.entry("physics", "scorePhysics"),
        Map.entry("hóa", "scoreChemistry"),
        Map.entry("hóa học", "scoreChemistry"),
        Map.entry("chemistry", "scoreChemistry"),
        Map.entry("sinh", "scoreBiology"),
        Map.entry("sinh học", "scoreBiology"),
        Map.entry("biology", "scoreBiology"),
        Map.entry("sử", "scoreHistory"),
        Map.entry("lịch sử", "scoreHistory"),
        Map.entry("history", "scoreHistory"),
        Map.entry("địa", "scoreGeography"),
        Map.entry("địa lý", "scoreGeography"),
        Map.entry("geography", "scoreGeography"),
        Map.entry("gdcd", "scoreCivicEducation"),
        Map.entry("giáo dục công dân", "scoreCivicEducation"),
        Map.entry("civic education", "scoreCivicEducation")
    );

    // Normalize string: lower case, remove accents, remove extra spaces
    private String normalizeForSBD(String input) {
        if (input == null) return "";
        String lower = input.toLowerCase();
        String noAccent = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noAccent.replaceAll("\\s+", " ").trim();
    }

    /**
     * 🤖 MAIN METHOD - Generate response using RestTemplate
     */
    private String generateChatGPTResponseWithHistory(String userQuery, List<DocumentChunk> context, String sessionId) {
        try {
            // Prepare context and prompt
            String compactContext = compactContext(context);
            String promptWithContext = String.format(
                "Dựa vào thông tin sau về tuyển sinh đại học:\n%s\n\nHãy trả lời câu hỏi một cách chính xác và hữu ích.",
                compactContext
            );

            // Setup HTTP headers for OpenAI API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            // Get conversation history
            List<Map<String, String>> messages = chatSessionService.getMessagesForAPI(sessionId, 10);
            
            // Add system message if not present
            if (messages.isEmpty() || !"system".equals(messages.get(0).get("role"))) {
                messages.add(0, Map.of("role", "system", "content", promptWithContext));
            }
            
            // Add current user query
            messages.add(Map.of("role", "user", "content", userQuery));

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openaiModel);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 150);
            requestBody.put("temperature", 0.5);

            // Create HTTP entity
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // Call OpenAI API
            log.info("🚀 Calling OpenAI API with model: {}", openaiModel);
            Map<String, Object> response = restTemplate.postForObject(openaiApiUrl, entity, Map.class);
            
            // Process response
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, String> message = (Map<String, String>) choice.get("message");
                    
                    // Update cost tracking
                    if (response.containsKey("usage")) {
                        updateCostTracking((Map<String, Object>) response.get("usage"));
                    }
                    
                    String content = message.get("content");
                    log.info("✅ Got response from OpenAI: {} chars", content.length());
                    return content.trim();
                }
            }
            
            log.warn("⚠️ No valid response from OpenAI API");
            
        } catch (Exception e) {
            log.error("❌ Error calling OpenAI API: {}", e.getMessage(), e);
            return generateFallbackResponse(userQuery, context);
        }
        
        return "Xin lỗi, tôi không thể trả lời câu hỏi này lúc này.";
    }
    
    /**
     * 💰 MAIN RAG QUERY với Session Support
     */
    public Map<String, Object> queryRAG(String userQuery, String sessionId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // ENHANCED SBD DETECTION
            String normalizedQuery = userQuery.toLowerCase().trim();
            boolean containsSbdKeyword = SBD_KEYWORDS.stream().anyMatch(normalizedQuery::contains);
            if (containsSbdKeyword) {
                log.info("🔍 SBD-related query detected: {}", userQuery);
                // Only accept SBDs that are exactly 8 digits
                Pattern sbdPattern = Pattern.compile("\\b(\\d{8})\\b");
                Matcher matcher = sbdPattern.matcher(userQuery);
                if (!matcher.find()) {
                    response.put("answer", "Số báo danh không hợp lệ hoặc không tồn tại trong hệ thống, vui lòng kiểm tra lại.");
                    response.put("status", "invalid_sbd");
                    response.put("session_id", sessionId);
                    return response;
                }
                String sbd = matcher.group(1).trim();
                log.info("📋 Extracted SBD: {}", sbd);
                if (!isValidSBD(sbd)) {
                    log.warn("❌ Invalid SBD detected: {}", sbd);
                    response.put("answer", "Số báo danh '" + sbd + "' không hợp lệ. SBD phải có 8-10 chữ số và khác '0'. Vui lòng kiểm tra lại.");
                    response.put("status", "invalid_sbd");
                    response.put("session_id", sessionId);
                    response.put("detected_sbd", sbd);
                    return response;
                }
                // Subject-specific logic
                String subjectKey = null;
                String subjectName = null;
                for (String keyword : SUBJECT_KEYWORDS.keySet()) {
                    if (normalizedQuery.contains(normalizeForSBD(keyword))) {
                        subjectKey = SUBJECT_KEYWORDS.get(keyword);
                        subjectName = keyword;
                        break;
                    }
                }
                if (subjectKey != null) {
                    Optional<StudentScore> scoreOpt = studentScoreRepository.findBySbd(sbd);
                    if (scoreOpt.isPresent()) {
                        StudentScore score = scoreOpt.get();
                        Double subjectScore = null;
                        switch (subjectKey) {
                            case "scoreMath": subjectScore = score.getScoreMath(); break;
                            case "scoreLiterature": subjectScore = score.getScoreLiterature(); break;
                            case "scoreEnglish": subjectScore = score.getScoreEnglish(); break;
                            case "scorePhysics": subjectScore = score.getScorePhysics(); break;
                            case "scoreChemistry": subjectScore = score.getScoreChemistry(); break;
                            case "scoreBiology": subjectScore = score.getScoreBiology(); break;
                            case "scoreHistory": subjectScore = score.getScoreHistory(); break;
                            case "scoreGeography": subjectScore = score.getScoreGeography(); break;
                            case "scoreCivicEducation": subjectScore = score.getScoreCivicEducation(); break;
                        }
                        Map<String, Object> resp = new HashMap<>();
                        if (subjectScore != null) {
                            resp.put("answer", String.format("Điểm %s của SBD %s là: %.2f", subjectName, sbd, subjectScore));
                            resp.put("status", "success");
                        } else {
                            resp.put("answer", String.format("Chưa có điểm %s cho SBD %s.", subjectName, sbd));
                            resp.put("status", "not_found");
                        }
                        resp.put("session_id", sessionId);
                        resp.put("sbd", sbd);
                        return resp;
                    } else {
                        // Auto-insert: Call lookup API, poll DB for up to 5 seconds
                        String apiUrl = String.format("http://localhost:8081/api/sbd/lookup-api/%s?region=To%%C3%%A0n%%20qu%%E1%%BB%%91c", sbd);
                        try { restTemplate.getForObject(apiUrl, String.class); } catch (Exception e) {}
                        StudentScore foundScore = null;
                        for (int i = 0; i < 5; i++) {
                            Optional<StudentScore> scoreOpt2 = studentScoreRepository.findBySbd(sbd);
                            if (scoreOpt2.isPresent()) {
                                foundScore = scoreOpt2.get();
                                break;
                            }
                            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                        }
                        if (foundScore != null) {
                            return formatScoreResponse(foundScore, sessionId);
                        } else {
                            Map<String, Object> resp = new HashMap<>();
                            resp.put("answer", String.format("Số báo danh '%s' không tồn tại trên hệ thống, vui lòng thử SBD khác hoặc thử lại sau ít phút.", sbd));
                            resp.put("status", "not_found");
                            resp.put("session_id", sessionId);
                            resp.put("searched_sbd", sbd);
                            return resp;
                        }
                    }
                }
                // If not subject-specific, always check SBD existence and return immediately if not found
                Optional<StudentScore> scoreOpt = studentScoreRepository.findBySbd(sbd);
                if (scoreOpt.isPresent()) {
                    return formatScoreResponse(scoreOpt.get(), sessionId);
                } else {
                    // Auto-insert: Call lookup API, poll DB for up to 5 seconds
                    String apiUrl = String.format("http://localhost:8081/api/sbd/lookup-api/%s?region=To%%C3%%A0n%%20qu%%E1%%BB%%91c", sbd);
                    try { restTemplate.getForObject(apiUrl, String.class); } catch (Exception e) {}
                    StudentScore foundScore = null;
                    for (int i = 0; i < 5; i++) {
                        Optional<StudentScore> scoreOpt2 = studentScoreRepository.findBySbd(sbd);
                        if (scoreOpt2.isPresent()) {
                            foundScore = scoreOpt2.get();
                            break;
                        }
                        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                    if (foundScore != null) {
                        return formatScoreResponse(foundScore, sessionId);
                    } else {
                        Map<String, Object> resp = new HashMap<>();
                        resp.put("answer", String.format("Số báo danh '%s' không tồn tại trên hệ thống, vui lòng thử SBD khác hoặc thử lại sau ít phút.", sbd));
                        resp.put("status", "not_found");
                        resp.put("session_id", sessionId);
                        resp.put("searched_sbd", sbd);
                        return resp;
                    }
                }
            }
            
            // Add user message to session
            chatSessionService.addMessage(sessionId, "user", userQuery);
            
            // 1. CHECK CACHE trước khi gọi API
            String cacheKey = userQuery.toLowerCase().trim();
            if (responseCache.containsKey(cacheKey)) {
                log.info("💾 Cache hit for query: {}", userQuery);
                String cachedAnswer = responseCache.get(cacheKey);
                
                // Add cached response to session
                chatSessionService.addMessage(sessionId, "assistant", cachedAnswer);
                
                response.put("answer", cachedAnswer);
                response.put("source", "cache");
                response.put("cost_saved", true);
                response.put("session_id", sessionId);
                return response;
            }
            
            // 2. Index data nếu chưa có
            if (!isIndexed) {
                indexAllData();
            }
            
            // 3. Search relevant chunks (GIẢM context để tiết kiệm token với GPT-4)
            List<DocumentChunk> relevantChunks = searchRelevantChunks(userQuery, 2); // Chỉ 2 chunks cho GPT-4
            
            if (relevantChunks.isEmpty()) {
                String fallbackAnswer = "Xin lỗi, tôi không tìm thấy thông tin liên quan. Bạn có thể hỏi về điểm chuẩn, ngành học hoặc thông tin trường đại học cụ thể.";
                
                // Add fallback to session
                chatSessionService.addMessage(sessionId, "assistant", fallbackAnswer);
                
                response.put("answer", fallbackAnswer);
                response.put("sources", new ArrayList<>());
                response.put("session_id", sessionId);
                return response;
            }
            
            // 4. Generate response với ChatGPT (with conversation history)
            String answer = generateChatGPTResponseWithHistory(userQuery, relevantChunks, sessionId);
            
            // 5. Add response to session
            chatSessionService.addMessage(sessionId, "assistant", answer);
            
            // 6. Cache response để tránh gọi lại
            responseCache.put(cacheKey, answer);
            
            // 7. Extract sources
            List<Map<String, Object>> sources = relevantChunks.stream()
                    .map(this::extractSource)
                    .collect(Collectors.toList());
            
            response.put("answer", answer);
            response.put("sources", sources);
            response.put("chunks_used", relevantChunks.size());
            response.put("cost_info", getCostInfo());
            response.put("session_id", sessionId);
            
        } catch (Exception e) {
            log.error("Error in ChatGPT RAG query: {}", e.getMessage());
            String errorAnswer = "Đã xảy ra lỗi khi xử lý câu hỏi của bạn. Vui lòng thử lại.";
            
            // Add error to session
            chatSessionService.addMessage(sessionId, "assistant", errorAnswer);
            
            response.put("answer", errorAnswer);
            response.put("error", e.getMessage());
            response.put("session_id", sessionId);
        }
        
        return response;
    }
    
    /**
     * 🤖 Generate response using ChatGPT API với conversation history
     * FIXED VERSION - Sử dụng đúng cách tạo ChatCompletionMessageParam
     */

    private String generateChatGPTResponseLegacy(String userQuery, List<DocumentChunk> context, String sessionId) {
        try {
            String compactContext = compactContext(context);
            String promptWithContext = String.format(
                "Dựa vào thông tin sau về tuyển sinh đại học:\n%s\n\nHãy trả lời câu hỏi một cách chính xác và hữu ích.",
                compactContext
            );
    
            // Use RestTemplate as fallback
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);
    
            List<Map<String, String>> messages = chatSessionService.getMessagesForAPI(sessionId, 10);
            
            // Add system message if not present
            if (messages.isEmpty() || !"system".equals(messages.get(0).get("role"))) {
                messages.add(0, Map.of("role", "system", "content", promptWithContext));
            }
            
            // Add current user query
            messages.add(Map.of("role", "user", "content", userQuery));
    
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openaiModel);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 150);
            requestBody.put("temperature", 0.5);
    
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            Map<String, Object> response = restTemplate.postForObject(openaiApiUrl, entity, Map.class);
            
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, String> message = (Map<String, String>) choice.get("message");
                    
                    // Update cost tracking
                    if (response.containsKey("usage")) {
                        updateCostTracking((Map<String, Object>) response.get("usage"));
                    }
                    
                    return message.get("content").trim();
                }
            }
            
        } catch (Exception e) {
            log.error("Error in legacy ChatGPT method: {}", e.getMessage(), e);
            return generateFallbackResponse(userQuery, context);
        }
        
        return "Xin lỗi, tôi không thể trả lời câu hỏi này lúc này.";
    }

    
    /**
     * 📊 Cost tracking với support cho multiple models
     */
    private void updateCostTracking(Map<String, Object> usage) {
        Integer promptTokens = (Integer) usage.get("prompt_tokens");
        Integer completionTokens = (Integer) usage.get("completion_tokens");
        Integer totalTokens = (Integer) usage.get("total_tokens");
        
        if (promptTokens != null && completionTokens != null) {
            double callCost = 0.0;
            
            // Calculate cost based on model
            if (openaiModel.startsWith("gpt-4")) {
                callCost = (promptTokens * GPT4_INPUT_COST) + (completionTokens * GPT4_OUTPUT_COST);
            } else {
                callCost = (promptTokens * GPT35_INPUT_COST) + (completionTokens * GPT35_OUTPUT_COST);
            }
            
            totalCostUSD += callCost;
            totalTokensUsed += totalTokens;
            
            log.info("📊 {} | Cost: ${:.4f} | Total: ${:.4f} | Tokens: {}", 
                    openaiModel, callCost, totalCostUSD, totalTokens);
            
            // WARNING với threshold khác cho GPT-4
            double warningThreshold = openaiModel.startsWith("gpt-4") ? 6.0 : 8.0; // GPT-4 warning sớm hơn
            if (totalCostUSD > warningThreshold) {
                log.warn("⚠️ WARNING: Đã sử dụng ${:.2f}/10$ ({}%) với model {}", 
                        totalCostUSD, (int)(totalCostUSD/10*100), openaiModel);
            }
        }
    }
    
    /**
     * 📋 Get cost information
     */
    public Map<String, Object> getCostInfo() {
        Map<String, Object> costInfo = new HashMap<>();
        costInfo.put("total_cost_usd", Math.round(totalCostUSD * 10000.0) / 10000.0);
        costInfo.put("total_tokens", totalTokensUsed);
        costInfo.put("remaining_budget", Math.max(0, 10.0 - totalCostUSD));
        costInfo.put("usage_percentage", Math.round(totalCostUSD / 10.0 * 100 * 100.0) / 100.0);
        costInfo.put("estimated_queries_remaining", estimateRemainingQueries());
        costInfo.put("cache_hits", responseCache.size());
        return costInfo;
    }
    
    private int estimateRemainingQueries() {
        if (totalCostUSD == 0) {
            // Estimate based on model
            return openaiModel.startsWith("gpt-4") ? 100 : 500; // GPT-4 much more expensive
        }
        
        double avgCostPerQuery = totalCostUSD / Math.max(1, responseCache.size() + 1);
        double remainingBudget = 10.0 - totalCostUSD;
        return (int) (remainingBudget / avgCostPerQuery);
    }
    
    /**
     * 🗜️ Compact context để giảm tokens
     */
    private String compactContext(List<DocumentChunk> chunks) {
        StringBuilder compact = new StringBuilder();
        
        for (DocumentChunk chunk : chunks) {
            // Chỉ lấy thông tin quan trọng nhất
            String content = chunk.getContent();
            
            // Lọc chỉ lấy thông tin điểm chuẩn và tên ngành
            String[] lines = content.split("\\n");
            for (String line : lines) {
                if (line.contains("điểm chuẩn") || line.contains("Tổ hợp") || 
                    line.contains("Trường:") || line.contains("Ngành:") ||
                    line.matches(".*\\d{2,3}[\\.,]\\d+.*")) { // Lines with scores
                    compact.append(line.trim()).append("\n");
                }
            }
        }
        
        // Giới hạn độ dài context (max 800 chars để tiết kiệm token)
        String result = compact.toString();
        if (result.length() > 800) {
            result = result.substring(0, 800) + "...";
        }
        
        return result;
    }
    
    /**
     * 📝 Build ULTRA COMPACT prompt cho GPT-4 (tiết kiệm token tối đa)
     */
    private String buildCompactPrompt(String userQuery, String context) {
        // Extremely short prompt for GPT-4 cost optimization
        return String.format("Data:\n%s\n\nQ: %s\nA:", 
            context.length() > 500 ? context.substring(0, 500) + "..." : context, 
            userQuery);
    }
    
    /**
     * 🆘 Fallback response khi API fail
     */
    private String generateFallbackResponse(String userQuery, List<DocumentChunk> context) {
        // Simple pattern matching fallback
        String lowerQuery = userQuery.toLowerCase();
        
        if (lowerQuery.contains("điểm chuẩn")) {
            return extractScoreInfo(context);
        }
        
        if (lowerQuery.contains("ngành") || lowerQuery.contains("chuyên ngành")) {
            return extractMajorInfo(context);
        }
        
        if (lowerQuery.contains("trường")) {
            return extractUniversityInfo(context);
        }
        
        return "Dựa vào dữ liệu có sẵn, tôi tìm thấy một số thông tin liên quan. Bạn có thể hỏi cụ thể hơn về điểm chuẩn, ngành học hoặc thông tin trường đại học.";
    }
    
    private String extractScoreInfo(List<DocumentChunk> context) {
        StringBuilder info = new StringBuilder("Thông tin điểm chuẩn:\n");
        
        for (DocumentChunk chunk : context) {
            String[] lines = chunk.getContent().split("\\n");
            for (String line : lines) {
                if (line.matches(".*\\d{2,3}[\\.,]\\d+.*") && 
                    (line.contains("2024") || line.contains("2023"))) {
                    info.append("- ").append(line.trim()).append("\n");
                }
            }
        }
        
        return info.toString();
    }
    
    private String extractMajorInfo(List<DocumentChunk> context) {
        StringBuilder info = new StringBuilder("Thông tin ngành học:\n");
        
        for (DocumentChunk chunk : context) {
            if (chunk.getType().equals("programs")) {
                String[] lines = chunk.getContent().split("\\n");
                for (String line : lines) {
                    if (line.trim().startsWith("-") && line.length() < 100) {
                        info.append(line.trim()).append("\n");
                    }
                }
            }
        }
        
        return info.toString();
    }
    
    private String extractUniversityInfo(List<DocumentChunk> context) {
        StringBuilder info = new StringBuilder("Thông tin trường đại học:\n");
        
        for (DocumentChunk chunk : context) {
            if (chunk.getType().equals("university_info")) {
                String[] lines = chunk.getContent().split("\\n");
                for (int i = 0; i < Math.min(3, lines.length); i++) {
                    info.append("- ").append(lines[i].trim()).append("\n");
                }
            }
        }
        
        return info.toString();
    }
    
    /**
     * 🔄 Clear cache để reset cost tracking
     */
    public Map<String, Object> clearCache() {
        int cachedResponses = responseCache.size();
        responseCache.clear();
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "cache_cleared");
        result.put("cleared_responses", cachedResponses);
        result.put("cost_info", getCostInfo());
        
        return result;
    }
    
    // ===== REUSE EXISTING METHODS =====
    
    public void indexAllData() {
        if (isIndexed) return;
        
        log.info("🔄 Indexing data for ChatGPT RAG...");
        
        List<University> universities = universityRepository.findAll();
        
        for (University university : universities) {
            List<DocumentChunk> chunks = createUniversityChunks(university);
            for (DocumentChunk chunk : chunks) {
                vectorStore.put(chunk.getId(), chunk);
            }
        }
        
        isIndexed = true;
        log.info("✅ Indexed {} universities for ChatGPT RAG", universities.size());
    }
    
    public List<DocumentChunk> searchRelevantChunks(String query, int limit) {
        return vectorStore.values().stream()
                .filter(chunk -> isRelevant(chunk, query))
                .sorted((a, b) -> Double.compare(
                    calculateRelevanceScore(b, query), 
                    calculateRelevanceScore(a, query)))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    // Copy các helper methods từ RAGService gốc...
    private List<DocumentChunk> createUniversityChunks(University university) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        // University info chunk
        String universityInfo = String.format(
            "Trường: %s (%s), Địa điểm: %s, Loại: %s",
            university.getName(), 
            university.getCode(),
            university.getLocation() != null ? university.getLocation() : "Chưa xác định",
            university.getType() != null ? university.getType() : "Công lập"
        );
        
        chunks.add(new DocumentChunk(
            "uni_" + university.getCode(),
            universityInfo,
            "university_info",
            university.getCode(),
            university.getName()
        ));
        
        // Programs chunk (compact)
        if (!university.getPrograms().isEmpty()) {
            StringBuilder programInfo = new StringBuilder();
            programInfo.append("Ngành học tại ").append(university.getName()).append(":\n");
            
            for (Program program : university.getPrograms().stream().limit(10).collect(Collectors.toList())) {
                programInfo.append(String.format(
                    "- %s: Điểm 2024: %s, Tổ hợp: %s\n",
                    program.getName(),
                    program.getBenchmarkScore2024() != null ? program.getBenchmarkScore2024() : "Chưa có",
                    program.getSubjectCombination() != null ? program.getSubjectCombination() : "Chưa xác định"
                ));
            }
            
            chunks.add(new DocumentChunk(
                "prog_" + university.getCode(),
                programInfo.toString(),
                "programs",
                university.getCode(),
                university.getName() + " - Ngành học"
            ));
        }
        
        return chunks;
    }
    
    private boolean isRelevant(DocumentChunk chunk, String query) {
        String lowerQuery = query.toLowerCase();
        String lowerContent = chunk.getContent().toLowerCase();
        return Arrays.stream(lowerQuery.split("\\s+"))
                .filter(word -> word.length() > 2)
                .anyMatch(lowerContent::contains);
    }
    
    private double calculateRelevanceScore(DocumentChunk chunk, String query) {
        String lowerQuery = query.toLowerCase();
        String lowerContent = chunk.getContent().toLowerCase();
        
        return Arrays.stream(lowerQuery.split("\\s+"))
                .filter(word -> word.length() > 2)
                .mapToDouble(word -> {
                    int count = (lowerContent.split(word, -1).length - 1);
                    return count * word.length();
                })
                .sum();
    }
    
    private Map<String, Object> extractSource(DocumentChunk chunk) {
        return Map.of(
            "id", chunk.getId(),
            "type", chunk.getType(),
            "university_code", chunk.getUniversityCode(),
            "title", chunk.getTitle(),
            "preview", chunk.getContent().substring(0, Math.min(100, chunk.getContent().length())) + "..."
        );
    }
    
    /**
     * 🧠 ENHANCED RAG QUERY với AI Intelligence
     */
    public Map<String, Object> queryRAGEnhanced(String userQuery, String sessionId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Add user message to session
            chatSessionService.addMessage(sessionId, "user", userQuery);
            
            // 1. SMART QUERY ANALYSIS
            EnhancedRAGService.QueryContext queryContext = enhancedRAGService.analyzeQuery(userQuery);
            log.info("🧠 Query Context: Intent={}, Entities={}", 
                    queryContext.getIntent(), queryContext.getEntities());
            
            // 2. CHECK CACHE với normalized query
            String cacheKey = queryContext.getOriginalQuery().toLowerCase().trim();
            if (responseCache.containsKey(cacheKey)) {
                log.info("💾 Cache hit for enhanced query: {}", userQuery);
                String cachedAnswer = responseCache.get(cacheKey);
                chatSessionService.addMessage(sessionId, "assistant", cachedAnswer);
                
                response.put("answer", cachedAnswer);
                response.put("source", "cache");
                response.put("cost_saved", true);
                response.put("query_context", Map.of(
                    "intent", queryContext.getIntent().toString(),
                    "entities", queryContext.getEntities(),
                    "keywords", queryContext.getKeywords()
                ));
                return response;
            }
            
            // 3. Index enhanced data if needed
            if (!isIndexed) {
                indexEnhancedData();
            }
            
            // 4. INTELLIGENT SEARCH
            List<DocumentChunk> relevantChunks = enhancedRAGService.intelligentSearch(
                queryContext, new ArrayList<>(vectorStore.values()), 3);
            
            if (relevantChunks.isEmpty()) {
                String fallbackAnswer = generateSmartFallback(queryContext);
                chatSessionService.addMessage(sessionId, "assistant", fallbackAnswer);
                
                response.put("answer", fallbackAnswer);
                response.put("sources", new ArrayList<>());
                response.put("query_context", Map.of(
                    "intent", queryContext.getIntent().toString(),
                    "suggestion", "Thử hỏi về: điểm chuẩn, ngành học, hoặc thông tin trường đại học cụ thể"
                ));
                return response;
            }
            
            // 5. CONTEXT-AWARE RESPONSE GENERATION
            String contextualPrompt = enhancedRAGService.generateContextualPrompt(queryContext, relevantChunks);
            String answer = generateChatGPTResponseWithEnhancedPrompt(contextualPrompt, sessionId);
            
            // 6. Add to session and cache
            chatSessionService.addMessage(sessionId, "assistant", answer);
            responseCache.put(cacheKey, answer);
            
            // 7. Enhanced response with metadata
            response.put("answer", answer);
            response.put("sources", relevantChunks.stream().map(this::extractEnhancedSource).collect(Collectors.toList()));
            response.put("chunks_used", relevantChunks.size());
            response.put("cost_info", getCostInfo());
            response.put("query_context", Map.of(
                "intent", queryContext.getIntent().toString(),
                "entities", queryContext.getEntities(),
                "keywords", queryContext.getKeywords(),
                "confidence", calculateResponseConfidence(queryContext, relevantChunks)
            ));
            response.put("session_id", sessionId);
            
        } catch (Exception e) {
            log.error("Error in enhanced ChatGPT RAG query: {}", e.getMessage());
            String errorAnswer = "Đã xảy ra lỗi khi xử lý câu hỏi của bạn. Vui lòng thử lại với câu hỏi cụ thể hơn.";
            chatSessionService.addMessage(sessionId, "assistant", errorAnswer);
            
            response.put("answer", errorAnswer);
            response.put("error", e.getMessage());
            response.put("session_id", sessionId);
        }
        
        return response;
    }

    /**
     * 📚 ENHANCED DATA INDEXING
     */
    private void indexEnhancedData() {
        if (isIndexed) return;
        
        log.info("🔄 Enhanced indexing with AI intelligence...");
        List<University> universities = universityRepository.findAll();
        if (universities.isEmpty()) {
            log.warn("⚠️ No universities found to index");
            return;
        }
        for (University university : universities) {
            // Use enhanced chunking strategy
            List<EnhancedRAGService.DocumentChunk> enhancedChunks = enhancedRAGService.createEnhancedChunks(university);
            for (EnhancedRAGService.DocumentChunk chunk : enhancedChunks) {
                vectorStore.put(chunk.getId(), chunk);
            }
        }
        
        isIndexed = true;
        log.info("✅ Enhanced indexed {} universities with {} total chunks", 
                universities.size(), vectorStore.size());
    }

    /**
     * 🎯 SMART FALLBACK GENERATION
     */
    private String generateSmartFallback(EnhancedRAGService.QueryContext queryContext) {
        switch (queryContext.getIntent()) {
            case GET_ADMISSION_SCORES:
                return "Tôi hiểu bạn quan tâm về điểm chuẩn. Bạn có thể hỏi cụ thể: " +
                    "'Điểm chuẩn ngành Công nghệ thông tin 2024?' hoặc " +
                    "'Điểm chuẩn trường Bách Khoa Hà Nội?'";
                    
            case GET_PROGRAMS:
                return "Về thông tin ngành học, bạn có thể hỏi: " +
                    "'Trường nào có ngành Y khoa?' hoặc " +
                    "'Các ngành của trường Kinh tế Quốc dân?'";
                    
            case COMPARE:
                return "Để so sánh, bạn có thể hỏi: " +
                    "'So sánh điểm chuẩn ngành CNTT các trường?' hoặc " +
                    "'Trường nào tốt hơn cho ngành Kinh tế?'";
                    
            default:
                return "Tôi có thể giúp bạn tìm hiểu về điểm chuẩn, ngành học, thông tin trường đại học. " +
                    "Hãy hỏi cụ thể hơn để tôi hỗ trợ tốt nhất!";
        }
    }

    /**
     * 📊 CALCULATE RESPONSE CONFIDENCE
     */
    private double calculateResponseConfidence(EnhancedRAGService.QueryContext queryContext, List<DocumentChunk> chunks) {
        double confidence = 0.5; // Base confidence
        
        // Increase confidence based on number of relevant chunks
        confidence += Math.min(chunks.size() * 0.1, 0.3);
        
        // Increase confidence if entities are found
        confidence += queryContext.getEntities().size() * 0.05;
        
        // Increase confidence for specific intents
        if (queryContext.getIntent() != EnhancedRAGService.QueryIntent.GENERAL_INFO) {
            confidence += 0.2;
        }
        
        return Math.min(confidence, 1.0);
    }

    /**
     * 🔍 ENHANCED SOURCE EXTRACTION
     */
    private Map<String, Object> extractEnhancedSource(DocumentChunk chunk) {
        Map<String, Object> source = extractSource(chunk); // Call existing method
        
        // Add intelligence metadata
        source.put("relevance_reason", getRelevanceReason(chunk));
        source.put("chunk_category", categorizeChunk(chunk));
        source.put("data_freshness", "2024"); // Add data freshness indicator
        
        return source;
    }

    private String getRelevanceReason(DocumentChunk chunk) {
        switch (chunk.getType()) {
            case "admission_requirements":
                return "Chứa thông tin về điều kiện xét tuyển";
            case "program_category":
                return "Chứa thông tin về nhóm ngành học";
            case "statistical_summary":
                return "Chứa thống kê tổng quan";
            default:
                return "Chứa thông tin liên quan";
        }
    }

    private String categorizeChunk(DocumentChunk chunk) {
        if (chunk.getContent().contains("điểm chuẩn")) return "admission_scores";
        if (chunk.getContent().contains("ngành học")) return "programs";
        if (chunk.getContent().contains("thống kê")) return "statistics";
        return "general";
    }

    // Add this stub method to fix the linter error
    private String generateChatGPTResponseWithEnhancedPrompt(String prompt, String sessionId) {
        // TODO: Implement actual OpenAI call with enhanced prompt
        return "[Stub] Enhanced response for prompt: " + prompt;
    }

    // ===== ENHANCED SBD DETECTION HELPERS =====
    private List<String> extractSBDNumbers(String query) {
        List<String> sbds = new ArrayList<>();
        // Pattern 1: Explicit SBD mention - "SBD 12345678", "số báo danh 12345678"
        Pattern explicitPattern = Pattern.compile("(?:sbd|số báo danh|exam id|candidate id)\\s*[:=]?\\s*(\\d{6,12})", Pattern.CASE_INSENSITIVE);
        Matcher explicitMatcher = explicitPattern.matcher(query);
        while (explicitMatcher.find()) {
            sbds.add(explicitMatcher.group(1));
        }
        // Pattern 2: Standalone numbers (8-10 digits) when SBD keyword is present
        if (sbds.isEmpty()) {
            Pattern standalonePattern = Pattern.compile("\\b(\\d{8,10})\\b");
            Matcher standaloneMatcher = standalonePattern.matcher(query);
            while (standaloneMatcher.find()) {
                String number = standaloneMatcher.group(1);
                if (!number.equals("0") && !number.matches("^0+$") && number.length() >= 8) {
                    sbds.add(number);
                }
            }
        }
        // Pattern 3: Flexible format - "tìm điểm 12345678", "12345678 có bao nhiêu điểm"
        if (sbds.isEmpty()) {
            Pattern flexiblePattern = Pattern.compile("\\b(\\d{8,12})\\b");
            Matcher flexibleMatcher = flexiblePattern.matcher(query);
            while (flexibleMatcher.find()) {
                String number = flexibleMatcher.group(1);
                if (couldBeSBD(number)) {
                    sbds.add(number);
                }
            }
        }
        return sbds;
    }

    private boolean isValidSBD(String sbd) {
        if (sbd == null || sbd.trim().isEmpty()) return false;
        sbd = sbd.trim();
        if (sbd.length() < 6 || sbd.length() > 12) return false;
        if (!sbd.matches("\\d+")) return false;
        if (sbd.equals("0") || sbd.matches("^0+$")) return false;
        if (sbd.matches("(.)\\1{7,}")) return false;
        return true;
    }

    private boolean couldBeSBD(String number) {
        if (number.length() < 8 || number.length() > 10) return false;
        if (number.equals("0") || number.matches("^0+$")) return false;
        if (number.matches("^20\\d{2}.*") && number.length() == 4) return false;
        if (number.matches("^[1-9]\\d{2,3}$")) return false;
        if (number.matches("^(01|02|03|04|05|06|07|08|09|10|11|12).*")) return true;
        return number.length() >= 8;
    }

    private Map<String, Object> processSBDLookup(String sbd, String sessionId) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Poll DB for up to 5 seconds
            StudentScore foundScore = null;
            for (int i = 0; i < 5; i++) {
                Optional<StudentScore> scoreOpt = studentScoreRepository.findBySbd(sbd);
                if (scoreOpt.isPresent()) {
                    foundScore = scoreOpt.get();
                    break;
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (foundScore != null) {
                return formatScoreResponse(foundScore, sessionId);
            } else {
                // Call lookup API, then poll DB for another 5 seconds
                String apiUrl = String.format("http://localhost:8081/api/sbd/lookup-api/%s?region=To%%C3%%A0n%%20qu%%E1%%BB%%91c", sbd);
                try { restTemplate.getForObject(apiUrl, String.class); } catch (Exception e) {}
                for (int i = 0; i < 5; i++) {
                    Optional<StudentScore> scoreOpt2 = studentScoreRepository.findBySbd(sbd);
                    if (scoreOpt2.isPresent()) {
                        foundScore = scoreOpt2.get();
                        break;
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
                if (foundScore != null) {
                    return formatScoreResponse(foundScore, sessionId);
                } else {
                    response.put("answer", String.format("Số báo danh '%s' không tồn tại trên hệ thống, vui lòng thử SBD khác hoặc thử lại sau ít phút.", sbd));
                    response.put("status", "not_found");
                    response.put("session_id", sessionId);
                    response.put("searched_sbd", sbd);
                    return response;
                }
            }
        } catch (Exception e) {
            response.put("answer", "Đã xảy ra lỗi khi tra cứu số báo danh. Vui lòng thử lại sau.");
            response.put("status", "error");
            response.put("session_id", sessionId);
            response.put("error", e.getMessage());
            return response;
        }
    }

    private StudentScore pollForScore(String sbd, int timeoutSeconds) {
        for (int i = 0; i < timeoutSeconds; i++) {
            Optional<StudentScore> scoreOpt = studentScoreRepository.findBySbd(sbd);
            if (scoreOpt.isPresent()) {
                return scoreOpt.get();
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return null;
    }

    private Map<String, Object> formatScoreResponse(StudentScore score, String sessionId) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> scores = new LinkedHashMap<>();
        if (score.getScoreMath() != null) scores.put("Toán", score.getScoreMath());
        if (score.getScoreLiterature() != null) scores.put("Văn", score.getScoreLiterature());
        if (score.getScoreEnglish() != null) scores.put("Anh", score.getScoreEnglish());
        if (score.getScorePhysics() != null) scores.put("Lý", score.getScorePhysics());
        if (score.getScoreChemistry() != null) scores.put("Hóa", score.getScoreChemistry());
        if (score.getScoreBiology() != null) scores.put("Sinh", score.getScoreBiology());
        if (score.getScoreHistory() != null) scores.put("Sử", score.getScoreHistory());
        if (score.getScoreGeography() != null) scores.put("Địa", score.getScoreGeography());
        if (score.getScoreCivicEducation() != null) scores.put("GDCD", score.getScoreCivicEducation());
        StringBuilder answer = new StringBuilder();
        answer.append("📋 Điểm thi của SBD ").append(score.getSbd()).append(":\n");
        if (scores.isEmpty()) {
            answer.append("Chưa có điểm thi được cập nhật.");
        } else {
            scores.forEach((subject, scoreValue) -> answer.append("• ").append(subject).append(": ").append(scoreValue).append("\n"));
        }
        response.put("answer", answer.toString().trim());
        response.put("status", "success");
        response.put("session_id", sessionId);
        response.put("sbd", score.getSbd());
        response.put("scores", scores);
        response.put("score_count", scores.size());
        return response;
    }
}