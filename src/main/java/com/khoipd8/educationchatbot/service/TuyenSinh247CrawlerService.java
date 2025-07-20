package com.khoipd8.educationchatbot.service;

import com.khoipd8.educationchatbot.dto.UniversityInfo;
import com.khoipd8.educationchatbot.entity.Program;
import com.khoipd8.educationchatbot.entity.University;
import com.khoipd8.educationchatbot.repository.UniversityRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TuyenSinh247CrawlerService {
    
    private static final String BASE_URL = "https://diemthi.tuyensinh247.com";
    private static final int TIMEOUT_MS = 15000;
    private static final int DELAY_MS = 1000;
    
    @Autowired
    private UniversityRepository universityRepository;
    
    // 1. Discover universities (unchanged)
    public List<UniversityInfo> discoverUniversities() throws IOException {
        log.info("Discovering universities from main page...");
        
        String mainPageUrl = BASE_URL + "/diem-chuan.html";
        Document doc = Jsoup.connect(mainPageUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(TIMEOUT_MS)
                .get();
        
        List<UniversityInfo> universities = new ArrayList<>();
        Elements universityLinks = doc.select("a[href*='/diem-chuan/'][href*='.html']");
        
        for (Element link : universityLinks) {
            String href = link.attr("href");
            String text = link.text().trim();
            
            if (href.contains("/diem-chuan/") && !href.equals("/diem-chuan.html") && !text.isEmpty()) {
                String code = extractUniversityCode(href);
                if (code != null) {
                    UniversityInfo info = new UniversityInfo();
                    info.setName(cleanUniversityName(text));
                    info.setCode(code);
                    info.setUrl(href.startsWith("http") ? href : BASE_URL + href);
                    universities.add(info);
                }
            }
        }
        
        Map<String, UniversityInfo> uniqueUniversities = universities.stream()
                .collect(Collectors.toMap(
                    UniversityInfo::getCode,
                    Function.identity(),
                    (existing, replacement) -> existing));
        
        List<UniversityInfo> result = new ArrayList<>(uniqueUniversities.values());
        log.info("Discovered {} unique universities", result.size());
        
        return result;
    }
    
    // 2. Fixed crawl university details
    public University crawlUniversityDetails(UniversityInfo info) throws IOException {
        log.info("Crawling university: {} ({})", info.getName(), info.getCode());
        
        Document doc = Jsoup.connect(info.getUrl())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(TIMEOUT_MS)
                .get();
        
        University university = new University();
        university.setName(info.getName());
        university.setCode(info.getCode());
        university.setFullName(extractFullName(doc, info.getName()));
        
        // Fixed location extraction
        university.setLocation(extractLocationFixed(doc, info.getName()));
        university.setType("Công lập");
        university.setDescription(extractDescription(doc));
        
        // Fixed program crawling
        List<Program> programs = crawlProgramsFixed(doc, university);
        university.setPrograms(programs);
        
        log.info("Crawled {} programs for {} with scores", programs.size(), university.getName());
        logScoreStats(programs);
        
        return university;
    }
    
    // 3. Fixed program extraction - Parse HTML table correctly
    private List<Program> crawlProgramsFixed(Document doc, University university) {
        Map<String, Program> programMap = new HashMap<>();
        
        // Strategy: Look for tables with specific structure
        Elements tables = doc.select("table");
        
        log.debug("Found {} tables on page", tables.size());
        
        for (int i = 0; i < tables.size(); i++) {
            Element table = tables.get(i);
            
            // Check if this table contains benchmark data
            if (isScoreTable(table)) {
                log.debug("Processing score table #{}", i + 1);
                
                // Determine context (year, method) from surrounding elements
                String context = getTableContext(table);
                Integer year = extractYearFromContext(context, table);
                String method = extractMethodFromContext(context, table);
                
                log.debug("Table context: year={}, method={}, context='{}'", year, method, context);
                
                // Process table rows
                processScoreTable(table, university, programMap, year, method);
            }
        }
        
        List<Program> programs = new ArrayList<>(programMap.values());
        log.info("Extracted {} unique programs", programs.size());
        
        return programs;
    }
    
    // 4. Check if table contains score data
    private boolean isScoreTable(Element table) {
        // Check table headers
        Elements headers = table.select("th, thead td");
        String headerText = headers.text().toLowerCase();
        
        // Check for typical score table headers
        if (headerText.contains("tên ngành") && 
            (headerText.contains("điểm chuẩn") || headerText.contains("điểm"))) {
            return true;
        }
        
        // Check first few rows for score patterns
        Elements rows = table.select("tbody tr, tr");
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            Elements cells = rows.get(i).select("td");
            if (cells.size() >= 3) {
                String secondCol = cells.get(1).text().trim();
                String thirdCol = cells.get(2).text().trim();
                
                // Check for subject combination pattern (A00, A01, etc.)
                if (secondCol.matches(".*[ABCD]\\d{2}.*") && isScoreValue(thirdCol)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // 5. Process score table
    private void processScoreTable(Element table, University university, 
                                 Map<String, Program> programMap, 
                                 Integer year, String method) {
        
        Elements rows = table.select("tbody tr");
        if (rows.isEmpty()) {
            rows = table.select("tr");
        }
        
        int processedRows = 0;
        int scoreRows = 0;
        
        for (Element row : rows) {
            Elements cells = row.select("td");
            
            if (cells.size() >= 3) {
                String programName = cells.get(0).text().trim();
                String combination = cells.get(1).text().trim();
                String scoreText = cells.get(2).text().trim();
                String note = cells.size() > 3 ? cells.get(3).text().trim() : "";
                
                // Skip header rows
                if (isHeaderRow(programName)) {
                    continue;
                }
                
                // Parse score
                Double score = parseScoreFixed(scoreText);
                
                if (score != null && !programName.isEmpty()) {
                    String programKey = createProgramKey(programName, note, method);
                    
                    Program program = programMap.computeIfAbsent(programKey, k -> {
                        Program p = new Program();
                        p.setUniversity(university);
                        p.setName(programName);
                        p.setSubjectCombination(combination);
                        p.setNote(note);
                        p.setAdmissionMethod(method != null ? method : "Xét tuyển kết hợp");
                        return p;
                    });
                    
                    // Assign score to correct year
                    assignScoreToYear(program, score, year != null ? year : 2024);
                    scoreRows++;
                }
                processedRows++;
            }
        }
        
        log.debug("Processed {} rows, found {} with valid scores", processedRows, scoreRows);
    }
    
    // 6. Enhanced score parsing
    private Double parseScoreFixed(String scoreText) {
        if (scoreText == null || scoreText.trim().isEmpty()) {
            return null;
        }
        
        // Remove all non-numeric characters except dots
        String cleaned = scoreText.replaceAll("[^0-9.]", "");
        
        if (cleaned.isEmpty()) {
            return null;
        }
        
        try {
            double score = Double.parseDouble(cleaned);
            
            // Validate score range (typical university entrance scores)
            if (score >= 0 && score <= 100) {
                return Math.round(score * 100.0) / 100.0; // Round to 2 decimal places
            }
            
        } catch (NumberFormatException e) {
            log.debug("Failed to parse score: '{}'", scoreText);
        }
        
        return null;
    }
    
    // 7. Check if value looks like a score
    private boolean isScoreValue(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String cleaned = text.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) {
            return false;
        }
        
        try {
            double value = Double.parseDouble(cleaned);
            return value >= 10 && value <= 100; // Reasonable score range
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    // 8. Enhanced location extraction
    private String extractLocationFixed(Document doc, String universityName) {
        // Priority 1: Extract from university name
        String nameLocation = extractLocationFromName(universityName);
        if (nameLocation != null) {
            return nameLocation;
        }
        
        // Priority 2: Extract from full text content
        String fullText = doc.text().toLowerCase();
        
        // Check for specific location keywords
        if (fullText.contains("tp.hcm") || fullText.contains("thành phố hồ chí minh") || 
            fullText.contains("hồ chí minh") || fullText.contains("sài gòn")) {
            return "TP.HCM";
        }
        
        if (fullText.contains("hà nội") || fullText.contains("hanoi")) {
            return "Hà Nội";
        }
        
        if (fullText.contains("đà nẵng")) return "Đà Nẵng";
        if (fullText.contains("hải phòng")) return "Hải Phòng";
        if (fullText.contains("cần thơ")) return "Cần Thơ";
        if (fullText.contains("huế")) return "Huế";
        if (fullText.contains("quảng ninh")) return "Quảng Ninh";
        if (fullText.contains("nghệ an")) return "Nghệ An";
        
        return "Chưa xác định";
    }
    
    // 9. Extract location from university name
    private String extractLocationFromName(String name) {
        if (name == null) return null;
        
        String lowerName = name.toLowerCase();
        
        if (lowerName.contains("hcm") || lowerName.contains("tp.hcm") || 
            lowerName.contains("sài gòn") || lowerName.contains("hồ chí minh")) {
            return "TP.HCM";
        }
        
        if (lowerName.contains("hà nội") || lowerName.contains("thủ đô")) {
            return "Hà Nội";
        }
        
        if (lowerName.contains("đà nẵng")) return "Đà Nẵng";
        if (lowerName.contains("huế")) return "Huế";
        if (lowerName.contains("cần thơ")) return "Cần Thơ";
        
        return null;
    }
    
    // 10. Enhanced context extraction
    private String getTableContext(Element table) {
        StringBuilder context = new StringBuilder();
        
        // Look at preceding elements (headings, paragraphs)
        Element current = table.previousElementSibling();
        int lookback = 0;
        
        while (current != null && lookback < 5) {
            String text = current.text().trim();
            if (!text.isEmpty()) {
                if (current.tagName().matches("h[1-6]")) {
                    context.insert(0, text + " ");
                    break; // Stop at first heading
                } else {
                    context.insert(0, text + " ");
                }
            }
            current = current.previousElementSibling();
            lookback++;
        }
        
        return context.toString();
    }
    
    // 11. Extract year from context
    private Integer extractYearFromContext(String context, Element table) {
        if (context != null) {
            Pattern yearPattern = Pattern.compile("\\b(202[2-5])\\b");
            Matcher matcher = yearPattern.matcher(context);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        
        // Check table content for year clues
        String tableText = table.text();
        if (tableText.contains("2024")) return 2024;
        if (tableText.contains("2023")) return 2023;
        if (tableText.contains("2022")) return 2022;
        
        return 2024; // Default to 2024
    }
    
    // 12. Extract method from context
    private String extractMethodFromContext(String context, Element table) {
        if (context != null) {
            String lowerContext = context.toLowerCase();
            
            if (lowerContext.contains("xét tuyển kết hợp")) return "Xét tuyển kết hợp";
            if (lowerContext.contains("điểm thi thpt")) return "Điểm thi THPT";
            if (lowerContext.contains("đgnl")) return "Đánh giá năng lực";
            if (lowerContext.contains("học bạ")) return "Xét học bạ";
            if (lowerContext.contains("đánh giá tư duy")) return "Đánh giá tư duy";
        }
        
        return "Xét tuyển kết hợp"; // Default
    }
    
    // Helper methods (unchanged but optimized)
    private void assignScoreToYear(Program program, Double score, Integer year) {
        switch (year) {
            case 2024:
                if (program.getBenchmarkScore2024() == null) {
                    program.setBenchmarkScore2024(score);
                }
                break;
            case 2023:
                if (program.getBenchmarkScore2023() == null) {
                    program.setBenchmarkScore2023(score);
                }
                break;
            case 2022:
                if (program.getBenchmarkScore2022() == null) {
                    program.setBenchmarkScore2022(score);
                }
                break;
            default:
                if (program.getBenchmarkScore2024() == null) {
                    program.setBenchmarkScore2024(score);
                }
                break;
        }
    }
    
    private String createProgramKey(String name, String note, String method) {
        return name + "|" + (note != null ? note : "") + "|" + (method != null ? method : "");
    }
    
    private boolean isHeaderRow(String text) {
        if (text == null) return true;
        
        String lowerText = text.toLowerCase();
        return lowerText.contains("tên ngành") || 
               lowerText.contains("chuyên ngành") ||
               lowerText.contains("ngành") && text.length() < 10 ||
               lowerText.contains("mã ngành") ||
               text.trim().isEmpty();
    }
    
    private void logScoreStats(List<Program> programs) {
        long with2024 = programs.stream().filter(p -> p.getBenchmarkScore2024() != null).count();
        long with2023 = programs.stream().filter(p -> p.getBenchmarkScore2023() != null).count();
        long with2022 = programs.stream().filter(p -> p.getBenchmarkScore2022() != null).count();
        
        log.info("Score coverage - 2024: {}, 2023: {}, 2022: {}", with2024, with2023, with2022);
        
        if (with2024 > 0) {
            Double avgScore = programs.stream()
                    .map(Program::getBenchmarkScore2024)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            log.info("Average 2024 score: {}", Math.round(avgScore * 100.0) / 100.0);
        }
    }
    
    // Existing helper methods (unchanged)
    private String extractUniversityCode(String url) {
        Pattern pattern = Pattern.compile(".*-([A-Z]{3})\\.html");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    private String cleanUniversityName(String name) {
        return name.replaceAll("Điểm chuẩn\\s+", "")
                  .replaceAll("\\s+\\d{4}.*", "")
                  .replaceAll("chính xác", "")
                  .trim();
    }
    
    private String extractFullName(Document doc, String defaultName) {
        Elements headings = doc.select("h1, h2, title");
        for (Element heading : headings) {
            String text = heading.text();
            if (text.contains("Đại học") || text.contains("Trường")) {
                return cleanUniversityName(text);
            }
        }
        return defaultName;
    }
    
    private String extractDescription(Document doc) {
        Elements metaDesc = doc.select("meta[name=description]");
        if (!metaDesc.isEmpty()) {
            return metaDesc.attr("content").trim();
        }
        
        Elements paragraphs = doc.select("p");
        if (!paragraphs.isEmpty()) {
            return paragraphs.first().text().trim();
        }
        
        return null;
    }
    
    // Async methods and update logic (unchanged)
    @Async
    public CompletableFuture<Map<String, Object>> crawlAllUniversities() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Starting full university crawl with fixed parser...");
            
            List<UniversityInfo> universityInfos = discoverUniversities();
            log.info("Discovered {} universities to crawl", universityInfos.size());
            
            AtomicInteger processed = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);
            AtomicInteger updated = new AtomicInteger(0);
            AtomicInteger created = new AtomicInteger(0);
            
            for (UniversityInfo info : universityInfos) {
                try {
                    University university = crawlUniversityDetails(info);
                    
                    Optional<University> existing = universityRepository.findByCode(university.getCode());
                    if (existing.isPresent()) {
                        updateExistingUniversity(existing.get(), university);
                        updated.incrementAndGet();
                    } else {
                        universityRepository.save(university);
                        created.incrementAndGet();
                    }
                    
                    processed.incrementAndGet();
                    
                    if (processed.get() % 10 == 0) {
                        log.info("Progress: {}/{} universities processed", processed.get(), universityInfos.size());
                    }
                    
                    Thread.sleep(DELAY_MS + (long)(Math.random() * 1000));
                    
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.error("Error crawling university {}: {}", info.getName(), e.getMessage());
                    
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            result.put("status", "completed");
            result.put("total_discovered", universityInfos.size());
            result.put("processed", processed.get());
            result.put("created", created.get());
            result.put("updated", updated.get());
            result.put("errors", errors.get());
            
            log.info("Fixed crawl completed. Processed: {}, Created: {}, Updated: {}, Errors: {}", 
                    processed.get(), created.get(), updated.get(), errors.get());
            
        } catch (Exception e) {
            log.error("Fatal error in crawl process", e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }
        
        return CompletableFuture.completedFuture(result);
    }
    
    private void updateExistingUniversity(University existing, University newData) {
        existing.setName(newData.getName());
        existing.setFullName(newData.getFullName());
        existing.setLocation(newData.getLocation());
        
        Map<String, Program> existingProgramsMap = new HashMap<>();
        for (Program p : existing.getPrograms()) {
            String key = createProgramKey(p.getName(), p.getNote(), p.getAdmissionMethod());
            existingProgramsMap.put(key, p);
        }
        
        existing.getPrograms().clear();
        
        for (Program newProgram : newData.getPrograms()) {
            String key = createProgramKey(newProgram.getName(), newProgram.getNote(), newProgram.getAdmissionMethod());
            Program existingProgram = existingProgramsMap.get(key);
            
            if (existingProgram != null) {
                if (newProgram.getBenchmarkScore2024() != null) {
                    existingProgram.setBenchmarkScore2024(newProgram.getBenchmarkScore2024());
                }
                if (newProgram.getBenchmarkScore2023() != null) {
                    existingProgram.setBenchmarkScore2023(newProgram.getBenchmarkScore2023());
                }
                if (newProgram.getBenchmarkScore2022() != null) {
                    existingProgram.setBenchmarkScore2022(newProgram.getBenchmarkScore2022());
                }
                existing.getPrograms().add(existingProgram);
            } else {
                newProgram.setUniversity(existing);
                existing.getPrograms().add(newProgram);
            }
        }
        
        universityRepository.save(existing);
    }
    
    public University crawlSingleUniversity(String code) throws IOException {
        List<UniversityInfo> allUniversities = discoverUniversities();
        
        Optional<UniversityInfo> targetUniversity = allUniversities.stream()
                .filter(u -> u.getCode().equalsIgnoreCase(code))
                .findFirst();
        
        if (targetUniversity.isPresent()) {
            return crawlUniversityDetails(targetUniversity.get());
        } else {
            throw new RuntimeException("University with code " + code + " not found");
        }
    }
}