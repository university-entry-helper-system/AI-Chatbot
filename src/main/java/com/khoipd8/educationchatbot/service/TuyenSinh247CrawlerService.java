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
    
    // 1. Discover universities from main page
    public List<UniversityInfo> discoverUniversities() throws IOException {
        log.info("Discovering universities from main page...");
        
        String mainPageUrl = BASE_URL + "/diem-chuan.html";
        Document doc = Jsoup.connect(mainPageUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(TIMEOUT_MS)
                .get();
        
        List<UniversityInfo> universities = new ArrayList<>();
        
        // Find all links to university benchmark pages
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
        
        // Remove duplicates based on code
        Map<String, UniversityInfo> uniqueUniversities = universities.stream()
                .collect(Collectors.toMap(
                    UniversityInfo::getCode,
                    Function.identity(),
                    (existing, replacement) -> existing));
        
        List<UniversityInfo> result = new ArrayList<>(uniqueUniversities.values());
        log.info("Discovered {} unique universities", result.size());
        
        return result;
    }
    
    // 2. Crawl detailed data for a specific university
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
        university.setLocation(extractLocation(doc));
        university.setType("Công lập"); // Default, can be improved
        university.setDescription(extractDescription(doc));
        
        // Crawl programs from tables
        List<Program> programs = crawlPrograms(doc, university);
        university.setPrograms(programs);
        
        log.info("Crawled {} programs for {}", programs.size(), university.getName());
        
        return university;
    }
    
    // 3. Extract programs from HTML tables
    private List<Program> crawlPrograms(Document doc, University university) {
        List<Program> programs = new ArrayList<>();
        
        // Find all tables containing benchmark data
        Elements tables = doc.select("table");
        
        for (Element table : tables) {
            Elements rows = table.select("tbody tr");
            if (rows.isEmpty()) {
                rows = table.select("tr");
            }
            
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() >= 3) {
                    Program program = extractProgramFromRow(cells, university);
                    if (program != null) {
                        programs.add(program);
                    }
                }
            }
        }
        
        return programs;
    }
    
    // 4. Extract program data from table row
    private Program extractProgramFromRow(Elements cells, University university) {
        try {
            Program program = new Program();
            program.setUniversity(university);
            
            // Column 0: Program name
            String programName = cells.get(0).text().trim();
            if (programName.isEmpty() || 
                programName.toLowerCase().contains("tên ngành") ||
                programName.toLowerCase().contains("ngành") && programName.length() < 5) {
                return null; // Skip header rows
            }
            program.setName(programName);
            
            // Column 1: Subject combination
            if (cells.size() > 1) {
                String subjectCombination = cells.get(1).text().trim();
                program.setSubjectCombination(subjectCombination);
            }
            
            // Column 2: Benchmark score
            if (cells.size() > 2) {
                String scoreText = cells.get(2).text().trim();
                Double score = parseScore(scoreText);
                program.setBenchmarkScore2024(score);
            }
            
            // Column 3: Notes (admission method, special programs)
            if (cells.size() > 3) {
                String note = cells.get(3).text().trim();
                program.setNote(note);
                program.setAdmissionMethod(extractAdmissionMethod(note));
            }
            
            // Set default admission method if not found
            if (program.getAdmissionMethod() == null || program.getAdmissionMethod().isEmpty()) {
                program.setAdmissionMethod("Xét tuyển kết hợp");
            }
            
            return program;
            
        } catch (Exception e) {
            log.warn("Error extracting program from row: {}", e.getMessage());
            return null;
        }
    }
    
    // 5. Async method to crawl all universities
    @Async
    public CompletableFuture<Map<String, Object>> crawlAllUniversities() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Starting full university crawl...");
            
            // Discover universities
            List<UniversityInfo> universityInfos = discoverUniversities();
            log.info("Discovered {} universities to crawl", universityInfos.size());
            
            AtomicInteger processed = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);
            AtomicInteger updated = new AtomicInteger(0);
            AtomicInteger created = new AtomicInteger(0);
            
            // Crawl each university
            for (UniversityInfo info : universityInfos) {
                try {
                    University university = crawlUniversityDetails(info);
                    
                    // Check if university already exists
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
                    
                    // Rate limiting
                    Thread.sleep(DELAY_MS + (long)(Math.random() * 1000));
                    
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.error("Error crawling university {}: {}", info.getName(), e.getMessage());
                    
                    // Continue with next university
                    try {
                        Thread.sleep(2000); // Longer delay on error
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
            
            log.info("Crawl completed. Processed: {}, Created: {}, Updated: {}, Errors: {}", 
                    processed.get(), created.get(), updated.get(), errors.get());
            
        } catch (Exception e) {
            log.error("Fatal error in crawl process", e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }
        
        return CompletableFuture.completedFuture(result);
    }
    
    // Helper methods
    private String extractUniversityCode(String url) {
        // Extract code from URL like "/diem-chuan/dai-hoc-bach-khoa-hcm-QSB.html"
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
        // Try to extract full name from page title or main heading
        Elements headings = doc.select("h1, h2, title");
        for (Element heading : headings) {
            String text = heading.text();
            if (text.contains("Đại học") || text.contains("Trường")) {
                return cleanUniversityName(text);
            }
        }
        return defaultName;
    }
    
    private String extractLocation(Document doc) {
        // Try to extract location from university description
        String text = doc.text().toLowerCase();
        
        if (text.contains("hà nội") || text.contains("hanoi")) return "Hà Nội";
        if (text.contains("hồ chí minh") || text.contains("tp.hcm") || text.contains("tphcm")) return "TP.HCM";
        if (text.contains("đà nẵng")) return "Đà Nẵng";
        if (text.contains("hải phòng")) return "Hải Phòng";
        if (text.contains("cần thơ")) return "Cần Thơ";
        if (text.contains("huế")) return "Huế";
        
        return "Chưa xác định";
    }
    
    private String extractDescription(Document doc) {
        // Extract description from meta tags or main content
        Elements metaDesc = doc.select("meta[name=description]");
        if (!metaDesc.isEmpty()) {
            return metaDesc.attr("content").trim();
        }
        
        // Fallback to first paragraph
        Elements paragraphs = doc.select("p");
        if (!paragraphs.isEmpty()) {
            return paragraphs.first().text().trim();
        }
        
        return null;
    }
    
    private Double parseScore(String scoreText) {
        try {
            // Remove all non-digit and non-dot characters
            String cleaned = scoreText.replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return null;
            
            double score = Double.parseDouble(cleaned);
            // Validate score range (typically 0-30 for university entrance)
            return (score >= 0 && score <= 50) ? score : null;
            
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private String extractAdmissionMethod(String note) {
        String lowerNote = note.toLowerCase();
        
        if (lowerNote.contains("xét tuyển kết hợp")) return "Xét tuyển kết hợp";
        if (lowerNote.contains("điểm thi thpt") || lowerNote.contains("thpt")) return "Điểm thi THPT";
        if (lowerNote.contains("đgnl")) return "Đánh giá năng lực";
        if (lowerNote.contains("học bạ")) return "Xét học bạ";
        if (lowerNote.contains("đánh giá tư duy")) return "Đánh giá tư duy";
        
        return "Xét tuyển kết hợp"; // Default
    }
    
    private void updateExistingUniversity(University existing, University newData) {
        // Update university info
        existing.setName(newData.getName());
        existing.setFullName(newData.getFullName());
        if (newData.getLocation() != null) {
            existing.setLocation(newData.getLocation());
        }
        if (newData.getDescription() != null) {
            existing.setDescription(newData.getDescription());
        }
        
        // Merge programs
        for (Program newProgram : newData.getPrograms()) {
            Optional<Program> existingProgram = existing.getPrograms().stream()
                    .filter(p -> p.getName().equals(newProgram.getName()) && 
                               Objects.equals(p.getNote(), newProgram.getNote()))
                    .findFirst();
            
            if (existingProgram.isPresent()) {
                // Update existing program with new data
                Program existingP = existingProgram.get();
                if (newProgram.getBenchmarkScore2024() != null) {
                    existingP.setBenchmarkScore2024(newProgram.getBenchmarkScore2024());
                }
                if (newProgram.getSubjectCombination() != null) {
                    existingP.setSubjectCombination(newProgram.getSubjectCombination());
                }
                if (newProgram.getAdmissionMethod() != null) {
                    existingP.setAdmissionMethod(newProgram.getAdmissionMethod());
                }
            } else {
                // Add new program
                newProgram.setUniversity(existing);
                existing.getPrograms().add(newProgram);
            }
        }
        
        universityRepository.save(existing);
    }
    
    // Test method to crawl single university
    public University crawlSingleUniversity(String code) throws IOException {
        // Find university info by code
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
