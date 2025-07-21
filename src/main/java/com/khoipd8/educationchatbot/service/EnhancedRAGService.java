package com.khoipd8.educationchatbot.service;

import com.khoipd8.educationchatbot.entity.University;
import com.khoipd8.educationchatbot.entity.Program;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
@Slf4j
public class EnhancedRAGService {
    
    // SEMANTIC SEARCH - Hiểu ý nghĩa câu hỏi
    private final Map<String, List<String>> semanticKeywords = new HashMap<>();
    
    // QUERY PREPROCESSING - Chuẩn hóa câu hỏi
    private final Map<String, String> queryNormalization = new HashMap<>();
    
    // CONTEXT ENRICHMENT - Bổ sung thông tin liên quan
    private final Map<String, List<String>> relatedTopics = new HashMap<>();
    
    public EnhancedRAGService() {
        initializeSemanticMaps();
    }
    
    /**
     * 🧠 SMART QUERY UNDERSTANDING
     */
    public QueryContext analyzeQuery(String userQuery) {
        String normalizedQuery = normalizeQuery(userQuery);
        QueryIntent intent = detectIntent(normalizedQuery);
        List<String> entities = extractEntities(normalizedQuery);
        List<String> keywords = extractEnhancedKeywords(normalizedQuery);
        
        log.info("🧠 Query Analysis - Intent: {}, Entities: {}, Keywords: {}", 
                intent, entities, keywords);
        
        return new QueryContext(normalizedQuery, intent, entities, keywords);
    }
    
    /**
     * 📚 ENHANCED CHUNK CREATION - Tạo chunks thông minh hơn
     */
    public List<DocumentChunk> createEnhancedChunks(University university) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        // 1. University overview chunk với metadata
        chunks.add(createUniversityOverviewChunk(university));
        
        // 2. Programs chunks theo từng category
        chunks.addAll(createProgramChunksByCategory(university));
        
        // 3. Admission requirements chunk
        chunks.add(createAdmissionRequirementsChunk(university));
        
        // 4. Statistical summary chunk
        chunks.add(createStatisticalSummaryChunk(university));
        
        return chunks;
    }
    
    /**
     * 🎯 INTELLIGENT SEARCH - Tìm kiếm thông minh
     */
    public List<DocumentChunk> intelligentSearch(QueryContext queryContext, List<DocumentChunk> allChunks, int limit) {
        Map<DocumentChunk, Double> chunkScores = new HashMap<>();
        
        for (DocumentChunk chunk : allChunks) {
            double score = calculateIntelligentScore(queryContext, chunk);
            if (score > 0.1) { // Threshold for relevance
                chunkScores.put(chunk, score);
            }
        }
        
        return chunkScores.entrySet().stream()
                .sorted(Map.Entry.<DocumentChunk, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * 💡 CONTEXT-AWARE RESPONSE GENERATION
     */
    public String generateContextualPrompt(QueryContext queryContext, List<DocumentChunk> chunks) {
        StringBuilder prompt = new StringBuilder();
        
        // 1. Specify the role based on query intent
        prompt.append(getRoleSpecificPrompt(queryContext.getIntent()));
        
        // 2. Add relevant context with structure
        prompt.append("\n\nThông tin có sẵn:\n");
        for (DocumentChunk chunk : chunks) {
            prompt.append(formatChunkForPrompt(chunk, queryContext));
        }
        
        // 3. Add specific instructions based on query type
        prompt.append(getQuerySpecificInstructions(queryContext));
        
        // 4. Add the user question with emphasis
        prompt.append(String.format("\n\nCâu hỏi cần trả lời: \"%s\"", queryContext.getOriginalQuery()));
        
        return prompt.toString();
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    private void initializeSemanticMaps() {
        // Semantic keyword mapping
        semanticKeywords.put("admission_scores", Arrays.asList(
            "điểm chuẩn", "điểm xét tuyển", "điểm đầu vào", "benchmark", "cutoff score", 
            "score", "điểm", "xét tuyển", "tuyển sinh"
        ));
        
        semanticKeywords.put("programs", Arrays.asList(
            "ngành học", "chuyên ngành", "program", "major", "course", "học", "đào tạo",
            "curriculum", "ngành", "chuyên", "training"
        ));
        
        semanticKeywords.put("universities", Arrays.asList(
            "trường đại học", "university", "college", "trường", "đại học", "học viện",
            "viện", "school", "institution"
        ));
        
        semanticKeywords.put("requirements", Arrays.asList(
            "điều kiện", "yêu cầu", "requirement", "criteria", "qualification", 
            "prerequisite", "tuyển", "nhận"
        ));
        
        semanticKeywords.put("fees", Arrays.asList(
            "học phí", "fee", "tuition", "cost", "price", "phí", "tiền", "chi phí"
        ));
        
        // Query normalization
        queryNormalization.put("cntt", "công nghệ thông tin");
        queryNormalization.put("it", "công nghệ thông tin");
        queryNormalization.put("cs", "khoa học máy tính");
        queryNormalization.put("bk", "bách khoa");
        queryNormalization.put("dhbk", "đại học bách khoa");
        queryNormalization.put("ngoại thương", "đại học ngoại thương");
        
        // Related topics
        relatedTopics.put("admission_scores", Arrays.asList("requirements", "programs", "subject_combinations"));
        relatedTopics.put("programs", Arrays.asList("admission_scores", "fees", "universities"));
        relatedTopics.put("fees", Arrays.asList("programs", "universities"));
    }
    
    private String normalizeQuery(String query) {
        String normalized = query.toLowerCase().trim();
        
        // Apply normalization mappings
        for (Map.Entry<String, String> entry : queryNormalization.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        
        // Clean up extra spaces
        normalized = normalized.replaceAll("\\s+", " ");
        
        return normalized;
    }
    
    private QueryIntent detectIntent(String query) {
        if (containsAnyKeyword(query, semanticKeywords.get("admission_scores"))) {
            return QueryIntent.GET_ADMISSION_SCORES;
        } else if (containsAnyKeyword(query, semanticKeywords.get("programs"))) {
            return QueryIntent.GET_PROGRAMS;
        } else if (containsAnyKeyword(query, semanticKeywords.get("fees"))) {
            return QueryIntent.GET_FEES;
        } else if (containsAnyKeyword(query, semanticKeywords.get("requirements"))) {
            return QueryIntent.GET_REQUIREMENTS;
        } else if (containsAnyKeyword(query, semanticKeywords.get("universities"))) {
            return QueryIntent.GET_UNIVERSITY_INFO;
        } else if (query.contains("so sánh") || query.contains("compare")) {
            return QueryIntent.COMPARE;
        } else if (query.contains("tư vấn") || query.contains("gợi ý") || query.contains("suggest")) {
            return QueryIntent.ADVISE;
        }
        
        return QueryIntent.GENERAL_INFO;
    }
    
    private List<String> extractEntities(String query) {
        List<String> entities = new ArrayList<>();
        
        // Extract university names
        Pattern universityPattern = Pattern.compile("(bách khoa|ngoại thương|kinh tế|y khoa|sư phạm|công nghệ|nông nghiệp)", Pattern.CASE_INSENSITIVE);
        Matcher universityMatcher = universityPattern.matcher(query);
        while (universityMatcher.find()) {
            entities.add("university:" + universityMatcher.group(1));
        }
        
        // Extract major names
        Pattern majorPattern = Pattern.compile("(công nghệ thông tin|y khoa|luật|kinh tế|quản trị|tài chính|marketing)", Pattern.CASE_INSENSITIVE);
        Matcher majorMatcher = majorPattern.matcher(query);
        while (majorMatcher.find()) {
            entities.add("major:" + majorMatcher.group(1));
        }
        
        // Extract years
        Pattern yearPattern = Pattern.compile("(202[0-9]|201[0-9])");
        Matcher yearMatcher = yearPattern.matcher(query);
        while (yearMatcher.find()) {
            entities.add("year:" + yearMatcher.group(1));
        }
        
        // Extract scores
        Pattern scorePattern = Pattern.compile("(\\d{1,2}[\\.,]\\d{1,2}|\\d{2,3})\\s*(điểm|point)");
        Matcher scoreMatcher = scorePattern.matcher(query);
        while (scoreMatcher.find()) {
            entities.add("score:" + scoreMatcher.group(1));
        }
        
        return entities;
    }
    
    private List<String> extractEnhancedKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        
        // Add all semantic keywords that match
        for (Map.Entry<String, List<String>> entry : semanticKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (query.toLowerCase().contains(keyword.toLowerCase())) {
                    keywords.add(keyword);
                }
            }
        }
        
        // Add important words (noun phrases, adjectives)
        String[] words = query.split("\\s+");
        for (String word : words) {
            if (word.length() > 3 && !isStopWord(word)) {
                keywords.add(word.toLowerCase());
            }
        }
        
        return keywords.stream().distinct().collect(Collectors.toList());
    }
    
    private DocumentChunk createUniversityOverviewChunk(University university) {
        StringBuilder content = new StringBuilder();
        content.append(String.format("TỔNG QUAN TRƯỜNG: %s (%s)\n", university.getName(), university.getCode()));
        content.append(String.format("Địa điểm: %s\n", university.getLocation() != null ? university.getLocation() : "Chưa xác định"));
        content.append(String.format("Loại hình: %s\n", university.getType() != null ? university.getType() : "Công lập"));
        content.append(String.format("Số ngành đào tạo: %d\n", university.getPrograms().size()));
        
        // Add some statistics
        if (!university.getPrograms().isEmpty()) {
            OptionalDouble avgScore = university.getPrograms().stream()
                    .filter(p -> p.getBenchmarkScore2024() != null)
                    .mapToDouble(p -> parseScore(String.valueOf(p.getBenchmarkScore2024())))
                    .filter(score -> score > 0)
                    .average();
            
            if (avgScore.isPresent()) {
                content.append(String.format("Điểm chuẩn trung bình: %.2f\n", avgScore.getAsDouble()));
            }
        }
        
        return new DocumentChunk(
            "overview_" + university.getCode(),
            content.toString(),
            "university_overview",
            university.getCode(),
            university.getName() + " - Tổng quan"
        );
    }
    
    private List<DocumentChunk> createProgramChunksByCategory(University university) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        // Group programs by category
        Map<String, List<Program>> programsByCategory = university.getPrograms().stream()
                .collect(Collectors.groupingBy(this::categorizeProgramName));
        
        for (Map.Entry<String, List<Program>> entry : programsByCategory.entrySet()) {
            StringBuilder content = new StringBuilder();
            content.append(String.format("NHÓM NGÀNH %s TẠI %s:\n", entry.getKey().toUpperCase(), university.getName()));
            
            for (Program program : entry.getValue()) {
                content.append(String.format("- %s: Điểm 2024: %s, Tổ hợp: %s\n",
                    program.getName(),
                    program.getBenchmarkScore2024() != null ? program.getBenchmarkScore2024() : "Chưa có",
                    program.getSubjectCombination() != null ? program.getSubjectCombination() : "Chưa xác định"
                ));
            }
            
            chunks.add(new DocumentChunk(
                "category_" + university.getCode() + "_" + entry.getKey(),
                content.toString(),
                "program_category",
                university.getCode(),
                university.getName() + " - " + entry.getKey()
            ));
        }
        
        return chunks;
    }
    
    private DocumentChunk createAdmissionRequirementsChunk(University university) {
        StringBuilder content = new StringBuilder();
        content.append(String.format("ĐIỀU KIỆN XÉT TUYỂN - %s:\n", university.getName()));
        
        // Extract unique subject combinations
        Set<String> subjectCombinations = university.getPrograms().stream()
                .map(Program::getSubjectCombination)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        content.append("Các tổ hợp xét tuyển:\n");
        for (String combination : subjectCombinations) {
            content.append(String.format("- %s\n", combination));
        }
        
        // Add score ranges
        DoubleSummaryStatistics scoreStats = university.getPrograms().stream()
                .filter(p -> p.getBenchmarkScore2024() != null)
                .mapToDouble(p -> parseScore(String.valueOf(p.getBenchmarkScore2024())))
                .filter(score -> score > 0)
                .summaryStatistics();
        
        if (scoreStats.getCount() > 0) {
            content.append(String.format("Điểm chuẩn từ: %.2f đến %.2f\n", scoreStats.getMin(), scoreStats.getMax()));
        }
        
        return new DocumentChunk(
            "requirements_" + university.getCode(),
            content.toString(),
            "admission_requirements",
            university.getCode(),
            university.getName() + " - Điều kiện xét tuyển"
        );
    }
    
    private DocumentChunk createStatisticalSummaryChunk(University university) {
        StringBuilder content = new StringBuilder();
        content.append(String.format("THỐNG KÊ TUYỂN SINH - %s:\n", university.getName()));
        
        Map<String, Long> programCount = university.getPrograms().stream()
                .collect(Collectors.groupingBy(this::categorizeProgramName, Collectors.counting()));
        
        content.append("Số lượng ngành theo lĩnh vực:\n");
        for (Map.Entry<String, Long> entry : programCount.entrySet()) {
            content.append(String.format("- %s: %d ngành\n", entry.getKey(), entry.getValue()));
        }
        
        return new DocumentChunk(
            "stats_" + university.getCode(),
            content.toString(),
            "statistical_summary",
            university.getCode(),
            university.getName() + " - Thống kê"
        );
    }
    
    private double calculateIntelligentScore(QueryContext queryContext, DocumentChunk chunk) {
        double score = 0.0;
        
        // 1. Intent-based scoring
        score += calculateIntentScore(queryContext.getIntent(), chunk);
        
        // 2. Entity matching
        score += calculateEntityScore(queryContext.getEntities(), chunk);
        
        // 3. Keyword relevance
        score += calculateKeywordScore(queryContext.getKeywords(), chunk);
        
        // 4. Chunk type relevance
        score += calculateChunkTypeScore(queryContext.getIntent(), chunk.getType());
        
        return score;
    }
    
    private String getRoleSpecificPrompt(QueryIntent intent) {
        switch (intent) {
            case GET_ADMISSION_SCORES:
                return "Bạn là chuyên gia tư vấn tuyển sinh, chuyên về điểm chuẩn và xét tuyển đại học.";
            case GET_PROGRAMS:
                return "Bạn là cố vấn học thuật, am hiểu về các chương trình đào tạo đại học.";
            case GET_FEES:
                return "Bạn là tư vấn viên tài chính giáo dục, hiểu rõ về học phí và chi phí học tập.";
            case COMPARE:
                return "Bạn là chuyên gia phân tích so sánh các lựa chọn giáo dục.";
            case ADVISE:
                return "Bạn là cố vấn hướng nghiệp, giúp học sinh chọn ngành và trường phù hợp.";
            default:
                return "Bạn là trợ lý tư vấn tuyển sinh đại học thông minh và nhiệt tình.";
        }
    }
    
    // Additional helper methods...
    private boolean containsAnyKeyword(String text, List<String> keywords) {
        return keywords.stream().anyMatch(keyword -> text.toLowerCase().contains(keyword.toLowerCase()));
    }
    
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("là", "của", "và", "có", "được", "trong", "với", "để", "về", "từ", "bao", "nhiêu", "gì", "nào", "như", "thế");
        return stopWords.contains(word.toLowerCase());
    }
    
    private String categorizeProgramName(Program program) {
        String name = program.getName().toLowerCase();
        if (name.contains("công nghệ") || name.contains("kỹ thuật") || name.contains("cntt")) {
            return "Công nghệ - Kỹ thuật";
        } else if (name.contains("kinh tế") || name.contains("quản trị") || name.contains("tài chính")) {
            return "Kinh tế - Quản trị";
        } else if (name.contains("y") || name.contains("dược") || name.contains("điều dưỡng")) {
            return "Y - Dược - Sức khỏe";
        } else if (name.contains("sư phạm") || name.contains("giáo dục")) {
            return "Sư phạm - Giáo dục";
        } else if (name.contains("luật") || name.contains("pháp lý")) {
            return "Luật - Chính trị";
        } else {
            return "Khác";
        }
    }
    
    private double parseScore(String scoreStr) {
        try {
            return Double.parseDouble(scoreStr.replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    // Implement other scoring methods...
    private double calculateIntentScore(QueryIntent intent, DocumentChunk chunk) {
        // Implementation details for intent-based scoring
        return 0.5; // Placeholder
    }
    
    private double calculateEntityScore(List<String> entities, DocumentChunk chunk) {
        // Implementation details for entity matching
        return 0.3; // Placeholder
    }
    
    private double calculateKeywordScore(List<String> keywords, DocumentChunk chunk) {
        // Implementation details for keyword relevance
        return 0.2; // Placeholder
    }
    
    private double calculateChunkTypeScore(QueryIntent intent, String chunkType) {
        // Implementation details for chunk type relevance
        return 0.1; // Placeholder
    }
    
    private String formatChunkForPrompt(DocumentChunk chunk, QueryContext queryContext) {
        return String.format("[%s] %s\n", chunk.getType().toUpperCase(), chunk.getContent());
    }
    
    private String getQuerySpecificInstructions(QueryContext queryContext) {
        switch (queryContext.getIntent()) {
            case GET_ADMISSION_SCORES:
                return "\n\nHãy tập trung vào thông tin điểm chuẩn, tổ hợp xét tuyển và các yêu cầu tuyển sinh.";
            case COMPARE:
                return "\n\nHãy so sánh một cách khách quan và cung cấp bảng so sánh rõ ràng.";
            case ADVISE:
                return "\n\nHãy đưa ra lời khuyên cụ thể, thực tế và phù hợp với hoàn cảnh học sinh.";
            default:
                return "\n\nHãy trả lời chính xác, đầy đủ và dễ hiểu.";
        }
    }
    
    // Inner classes
    public static class QueryContext {
        private final String originalQuery;
        private final QueryIntent intent;
        private final List<String> entities;
        private final List<String> keywords;
        
        public QueryContext(String originalQuery, QueryIntent intent, List<String> entities, List<String> keywords) {
            this.originalQuery = originalQuery;
            this.intent = intent;
            this.entities = entities;
            this.keywords = keywords;
        }
        
        // Getters
        public String getOriginalQuery() { return originalQuery; }
        public QueryIntent getIntent() { return intent; }
        public List<String> getEntities() { return entities; }
        public List<String> getKeywords() { return keywords; }
    }
    
    public enum QueryIntent {
        GET_ADMISSION_SCORES,
        GET_PROGRAMS,
        GET_UNIVERSITY_INFO,
        GET_REQUIREMENTS,
        GET_FEES,
        COMPARE,
        ADVISE,
        GENERAL_INFO
    }
    
    // DocumentChunk class (reuse from existing code)
    public static class DocumentChunk {
        private String id, content, type, universityCode, title;
        
        public DocumentChunk(String id, String content, String type, String universityCode, String title) {
            this.id = id;
            this.content = content;
            this.type = type;
            this.universityCode = universityCode;
            this.title = title;
        }
        
        // Getters
        public String getId() { return id; }
        public String getContent() { return content; }
        public String getType() { return type; }
        public String getUniversityCode() { return universityCode; }
        public String getTitle() { return title; }
    }
}