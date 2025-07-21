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

// YOUR PROJECT IMPORTS
import com.khoipd8.educationchatbot.entity.University;
import com.khoipd8.educationchatbot.entity.Program;
import com.khoipd8.educationchatbot.repository.UniversityRepository;

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
    
    // Inner class
    public static class DocumentChunk {
        private String id, content, type, universityCode, title;
        
        public DocumentChunk(String id, String content, String type, String universityCode, String title) {
            this.id = id;
            this.content = content;
            this.type = type;
            this.universityCode = universityCode;
            this.title = title;
        }
        
        public String getId() { return id; }
        public String getContent() { return content; }
        public String getType() { return type; }
        public String getUniversityCode() { return universityCode; }
        public String getTitle() { return title; }
    }
}