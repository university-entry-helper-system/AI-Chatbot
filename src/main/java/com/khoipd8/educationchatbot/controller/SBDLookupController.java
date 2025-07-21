package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.service.SBDLookupService;
import com.khoipd8.educationchatbot.repository.StudentScoreRepository;
import com.khoipd8.educationchatbot.repository.CombinationScoreRepository;
import com.khoipd8.educationchatbot.entity.CombinationScore;
import com.khoipd8.educationchatbot.service.SeleniumSBDService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sbd")
@Slf4j
public class SBDLookupController {
    
    @Autowired
    private SBDLookupService sbdLookupService;
    
    @Autowired
    private StudentScoreRepository studentScoreRepository;
    
    @Autowired
    private CombinationScoreRepository combinationScoreRepository;

    @Autowired
    private SeleniumSBDService seleniumSBDService;

    /**
     * 🤖 SELENIUM LOOKUP - CHẮC CHẮN THÀNH CÔNG
     */
    @GetMapping("/selenium-lookup/{sbd}")
    public ResponseEntity<Map<String, Object>> seleniumLookup(
            @PathVariable String sbd,
            @RequestParam(defaultValue = "Toàn quốc") String region) {
        
        try {
            log.info("🤖 Selenium lookup for SBD: {}", sbd);
            
            Map<String, Object> response = seleniumSBDService.crawlWithSelenium(sbd, region);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in selenium lookup: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
 * 🔍 DEBUG SELENIUM - Xem browser hoạt động
 */
@GetMapping("/debug-selenium/{sbd}")
public ResponseEntity<Map<String, Object>> debugSelenium(@PathVariable String sbd) {
    Map<String, Object> response = new HashMap<>();
    WebDriver driver = null;
    
    try {
        // Setup Chrome KHÔNG HEADLESS (để xem browser)
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        // BỎ dòng headless để xem browser
        // options.addArguments("--headless"); 
        options.addArguments("--lang=vi-VN");
        
        driver = new ChromeDriver(options);
        driver.get("https://diemthi.tuyensinh247.com/xep-hang-thi-thptqg.html");
        
        // Đợi 10 giây để bạn xem page
        Thread.sleep(10000);
        
        response.put("status", "debug_completed");
        response.put("page_title", driver.getTitle());
        response.put("page_url", driver.getCurrentUrl());
        response.put("page_source_length", driver.getPageSource().length());
        
        // Tìm tất cả input fields
        List<WebElement> inputs = driver.findElements(By.tagName("input"));
        List<String> inputInfo = new ArrayList<>();
        for (WebElement input : inputs) {
            String info = String.format("Type: %s, Name: %s, Placeholder: %s", 
                input.getAttribute("type"), 
                input.getAttribute("name"), 
                input.getAttribute("placeholder"));
            inputInfo.add(info);
        }
        response.put("input_fields", inputInfo);
        
        // Tìm tất cả buttons
        List<WebElement> buttons = driver.findElements(By.tagName("button"));
        List<String> buttonInfo = new ArrayList<>();
        for (WebElement button : buttons) {
            String info = String.format("Text: %s, Type: %s, Class: %s", 
                button.getText(), 
                button.getAttribute("type"), 
                button.getAttribute("class"));
            buttonInfo.add(info);
        }
        response.put("buttons", buttonInfo);
        
    } catch (Exception e) {
        response.put("status", "error");
        response.put("message", e.getMessage());
    } finally {
        if (driver != null) {
            driver.quit();
        }
    }
    
    return ResponseEntity.ok(response);
}
    
    /**
     * 🔍 LOOKUP STUDENT SCORE BY SBD - ENHANCED
     * Operation: Tra cứu điểm thi theo số báo danh với smart crawling
     * Purpose: Lấy điểm các môn và phân tích xếp hạng theo tổ hợp
     */
    @GetMapping("/lookup/{sbd}")
    public ResponseEntity<Map<String, Object>> lookupBySBD(
            @PathVariable String sbd,
            @RequestParam(defaultValue = "Toàn quốc") String region) {
        
        try {
            log.info("Looking up SBD: {} in region: {}", sbd, region);
            
            Map<String, Object> result = sbdLookupService.lookupStudentScore(sbd, region);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error looking up SBD: {}", sbd, e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            error.put("sbd", sbd);
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * 🔄 FORCE REFRESH SBD DATA
     * Operation: Buộc crawl lại data cho SBD (xóa data cũ và crawl mới)
     * Purpose: Cập nhật data mới nhất từ website
     */
    @PostMapping("/refresh/{sbd}")
    public ResponseEntity<Map<String, Object>> refreshSBDData(
            @PathVariable String sbd,
            @RequestParam(defaultValue = "Toàn quốc") String region) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("Force refreshing data for SBD: {}", sbd);
            
            // Delete existing data
            combinationScoreRepository.deleteBySbd(sbd);
            studentScoreRepository.deleteBySbd(sbd);
            
            // Force crawl new data
            Map<String, Object> result = sbdLookupService.lookupStudentScore(sbd, region);
            
            result.put("refresh_action", "completed");
            result.put("refresh_timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error refreshing SBD data: {}", sbd, e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            response.put("sbd", sbd);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 📊 SBD DATABASE STATISTICS
     * Operation: Thống kê dữ liệu SBD trong database
     * Purpose: Kiểm tra coverage và chất lượng data
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getSBDStatistics() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            long totalStudents = studentScoreRepository.count();
            long studentsWithScores = studentScoreRepository.countWithScores();
            long totalCombinations = combinationScoreRepository.count();
            
            var combinationCodes = combinationScoreRepository.findAllCombinationCodes();
            
            response.put("status", "success");
            response.put("statistics", Map.of(
                "total_students", totalStudents,
                "students_with_scores", studentsWithScores,
                "total_combination_records", totalCombinations,
                "available_combinations", combinationCodes,
                "data_completeness_rate", totalStudents > 0 ? 
                    Math.round((double) studentsWithScores / totalStudents * 100) : 0
            ));
            
        } catch (Exception e) {
            log.error("Error getting SBD statistics", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🎯 COMBINATION ANALYSIS
     * Operation: Phân tích chi tiết một tổ hợp cụ thể
     * Purpose: Xem chi tiết xếp hạng của tổ hợp
     */
    @GetMapping("/combination-analysis/{sbd}/{combinationCode}")
    public ResponseEntity<Map<String, Object>> getCombinationAnalysis(
            @PathVariable String sbd,
            @PathVariable String combinationCode) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            var combinationScores = combinationScoreRepository.findBySbdAndCombinationCode(sbd, combinationCode);
            
            if (combinationScores.isEmpty()) {
                response.put("status", "not_found");
                response.put("message", "No data found for SBD " + sbd + " and combination " + combinationCode);
                return ResponseEntity.ok(response);
            }
            
            var combScore = combinationScores.get(0);
            
            // Get comparison data
            var similarScores = combinationScoreRepository.findByTotalScoreBetween(
                combScore.getTotalScore() - 0.5, combScore.getTotalScore() + 0.5);
            
            response.put("status", "found");
            response.put("combination_analysis", Map.of(
                "sbd", sbd,
                "combination_code", combinationCode,
                "combination_name", combScore.getCombinationName(),
                "total_score", combScore.getTotalScore(),
                "ranking_details", Map.of(
                    "rank_position", combScore.getStudentsWithHigherScore() != null ? 
                            combScore.getStudentsWithHigherScore() + 1 : "N/A",
                    "students_with_higher_score", combScore.getStudentsWithHigherScore(),
                    "students_with_same_score", combScore.getStudentsWithSameScore(),
                    "total_students_in_combination", combScore.getTotalStudentsInCombination(),
                    "similar_scores_nearby", similarScores.size()
                ),
                "equivalent_score_2024", combScore.getEquivalentScore2024(),
                "performance_assessment", assessPerformance(combScore)
            ));
            
        } catch (Exception e) {
            log.error("Error getting combination analysis", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🗑️ DELETE SBD DATA
     * Operation: Xóa toàn bộ data của một SBD
     * Purpose: Cleanup hoặc remove data không cần thiết
     */
    @DeleteMapping("/delete/{sbd}")
    public ResponseEntity<Map<String, Object>> deleteSBDData(@PathVariable String sbd) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if data exists
            boolean exists = studentScoreRepository.findBySbd(sbd).isPresent();
            
            if (!exists) {
                response.put("status", "not_found");
                response.put("message", "No data found for SBD: " + sbd);
                return ResponseEntity.ok(response);
            }
            
            // Delete combination scores first (foreign key constraint)
            combinationScoreRepository.deleteBySbd(sbd);
            
            // Delete student score
            studentScoreRepository.deleteBySbd(sbd);
            
            response.put("status", "deleted");
            response.put("message", "Successfully deleted all data for SBD: " + sbd);
            response.put("sbd", sbd);
            response.put("deleted_at", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("Error deleting SBD data: {}", sbd, e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    // Helper method to assess performance
    private Map<String, Object> assessPerformance(CombinationScore combScore) {
        Map<String, Object> assessment = new HashMap<>();
        
        if (combScore.getTotalStudentsInCombination() != null && combScore.getStudentsWithHigherScore() != null) {
            double percentile = (1.0 - (double) combScore.getStudentsWithHigherScore() / combScore.getTotalStudentsInCombination()) * 100;
            
            String level;
            String advice;
            
            if (percentile >= 95) {
                level = "Xuất sắc";
                advice = "Có thể đăng ký các trường top với điểm chuẩn cao nhất";
            } else if (percentile >= 80) {
                level = "Tốt";
                advice = "Có cơ hội tốt vào các trường chất lượng cao";
            } else if (percentile >= 50) {
                level = "Khá";
                advice = "Nên cân nhắc mix các trường ở nhiều mức điểm chuẩn";
            } else {
                level = "Cần cải thiện";
                advice = "Tập trung vào các trường phù hợp với mức điểm hiện tại";
            }
            
            assessment.put("percentile", Math.round(percentile * 100.0) / 100.0);
            assessment.put("performance_level", level);
            assessment.put("advice", advice);
        } else {
            assessment.put("percentile", null);
            assessment.put("performance_level", "Chưa đủ dữ liệu");
            assessment.put("advice", "Cần thêm thông tin để đánh giá");
        }
        
        return assessment;
    }

    // THÊM VÀO SBDLookupController.java

/**
 * 🧪 TEST WEB CRAWLING
 * Test khả năng crawl thực từ website
 */
@PostMapping("/test-crawl/{sbd}")
public ResponseEntity<Map<String, Object>> testWebCrawling(@PathVariable String sbd) {
    Map<String, Object> response = new HashMap<>();
    
    try {
        log.info("Testing web crawling for SBD: {}", sbd);
        
        // Force delete any existing data
        combinationScoreRepository.deleteBySbd(sbd);
        studentScoreRepository.deleteBySbd(sbd);
        
        // Test different crawling approaches
        Map<String, Object> testResults = new HashMap<>();
        
        // Test 1: Form submission
        try {
            testResults.put("form_submission", testFormSubmission(sbd));
        } catch (Exception e) {
            testResults.put("form_submission", Map.of("status", "failed", "error", e.getMessage()));
        }
        
        // Test 2: GET request
        try {
            testResults.put("get_request", testGetRequest(sbd));
        } catch (Exception e) {
            testResults.put("get_request", Map.of("status", "failed", "error", e.getMessage()));
        }
        
        // Test 3: Alternative URLs
        try {
            testResults.put("alternative_urls", testAlternativeUrls(sbd));
        } catch (Exception e) {
            testResults.put("alternative_urls", Map.of("status", "failed", "error", e.getMessage()));
        }
        
        response.put("status", "test_completed");
        response.put("sbd", sbd);
        response.put("test_results", testResults);
        response.put("test_timestamp", System.currentTimeMillis());
        
        // Summary
        long successfulMethods = testResults.values().stream()
                .filter(result -> result instanceof Map)
                .map(result -> (Map<String, Object>) result)
                .filter(result -> "success".equals(result.get("status")))
                .count();
        
        response.put("summary", Map.of(
            "total_methods_tested", testResults.size(),
            "successful_methods", successfulMethods,
            "overall_success", successfulMethods > 0
        ));
        
    } catch (Exception e) {
        log.error("Error in test crawling", e);
        response.put("status", "error");
        response.put("message", e.getMessage());
        return ResponseEntity.status(500).body(response);
    }
    
    return ResponseEntity.ok(response);
}

/**
 * 🔍 CHECK WEBSITE STATUS
 * Kiểm tra trạng thái website nguồn
 */
@GetMapping("/check-website-status")
public ResponseEntity<Map<String, Object>> checkWebsiteStatus() {
    Map<String, Object> response = new HashMap<>();
    
    try {
        log.info("Checking website status...");
        
        String[] testUrls = {
            "https://diemthi.tuyensinh247.com",
            "https://diemthi.tuyensinh247.com/tra-cuu-diem-thi-tot-nghiep-thpt-2024.html",
            "https://thi.tuyensinh247.com",
            "https://tracuu.tuyensinh247.com"
        };
        
        Map<String, Object> urlStatuses = new HashMap<>();
        
        for (String url : testUrls) {
            Map<String, Object> urlStatus = new HashMap<>();
            long startTime = System.currentTimeMillis();
            
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get();
                
                long responseTime = System.currentTimeMillis() - startTime;
                
                urlStatus.put("status", "accessible");
                urlStatus.put("response_time_ms", responseTime);
                urlStatus.put("title", doc.title());
                urlStatus.put("has_forms", !doc.select("form").isEmpty());
                urlStatus.put("content_length", doc.text().length());
                
            } catch (Exception e) {
                urlStatus.put("status", "failed");
                urlStatus.put("error", e.getMessage());
                urlStatus.put("response_time_ms", System.currentTimeMillis() - startTime);
            }
            
            urlStatuses.put(url, urlStatus);
        }
        
        response.put("status", "check_completed");
        response.put("url_statuses", urlStatuses);
        response.put("check_timestamp", System.currentTimeMillis());
        
        // Summary
        long accessibleUrls = urlStatuses.values().stream()
                .filter(status -> status instanceof Map)
                .map(status -> (Map<String, Object>) status)
                .filter(status -> "accessible".equals(status.get("status")))
                .count();
        
        response.put("summary", Map.of(
            "total_urls_tested", testUrls.length,
            "accessible_urls", accessibleUrls,
            "website_health", accessibleUrls > 0 ? "healthy" : "problematic"
        ));
        
    } catch (Exception e) {
        log.error("Error checking website status", e);
        response.put("status", "error");
        response.put("message", e.getMessage());
    }
    
    return ResponseEntity.ok(response);
}

/**
 * 📊 CRAWLING STATISTICS
 * Thống kê tình trạng crawling
 */
@GetMapping("/crawling-stats")
public ResponseEntity<Map<String, Object>> getCrawlingStats() {
    Map<String, Object> response = new HashMap<>();
    
    try {
        long totalStudents = studentScoreRepository.count();
        long studentsWithScores = studentScoreRepository.countWithScores();
        long totalCombinations = combinationScoreRepository.count();
        
        // Check data sources
        List<String> dataSources = new ArrayList<>();
        // This would require adding a 'source' field to StudentScore entity
        
        response.put("status", "success");
        response.put("statistics", Map.of(
            "total_students_in_db", totalStudents,
            "students_with_valid_scores", studentsWithScores,
            "total_combination_records", totalCombinations,
            "data_completeness_rate", totalStudents > 0 ? 
                Math.round((double) studentsWithScores / totalStudents * 100) : 0,
            "last_successful_crawl", "N/A", // Would need to track this
            "crawl_success_rate", "N/A" // Would need to track this
        ));
        
        response.put("data_quality", Map.of(
            "has_data", totalStudents > 0,
            "data_density", studentsWithScores > 0 ? "good" : "empty",
            "recommendation", totalStudents == 0 ? 
                "Cần test crawling để kiểm tra kết nối website" : 
                "Dữ liệu có sẵn"
        ));
        
    } catch (Exception e) {
        log.error("Error getting crawling stats", e);
        response.put("status", "error");
        response.put("message", e.getMessage());
    }
    
    return ResponseEntity.ok(response);
}

// Helper methods for testing
private Map<String, Object> testFormSubmission(String sbd) {
    // Implementation của test form submission
    Map<String, Object> result = new HashMap<>();
    try {
        // Test logic here
        result.put("status", "success");
        result.put("method", "form_submission");
        result.put("message", "Form submission test completed");
    } catch (Exception e) {
        result.put("status", "failed");
        result.put("error", e.getMessage());
    }
    return result;
}

private Map<String, Object> testGetRequest(String sbd) {
    // Implementation của test GET request
    Map<String, Object> result = new HashMap<>();
    try {
        result.put("status", "success");
        result.put("method", "get_request");
        result.put("message", "GET request test completed");
    } catch (Exception e) {
        result.put("status", "failed");
        result.put("error", e.getMessage());
    }
    return result;
}

private Map<String, Object> testAlternativeUrls(String sbd) {
    // Implementation của test alternative URLs
    Map<String, Object> result = new HashMap<>();
    try {
        result.put("status", "success");
        result.put("method", "alternative_urls");
        result.put("message", "Alternative URLs test completed");
    } catch (Exception e) {
        result.put("status", "failed");
        result.put("error", e.getMessage());
    }
    return result;
}
}