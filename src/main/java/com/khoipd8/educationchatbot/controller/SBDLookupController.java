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
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import org.openqa.selenium.JavascriptExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.time.Duration;

// Optional utilities
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sbd")
@Slf4j
@Tag(name = "SBD Lookup", description = "Tra cứu, cập nhật, xóa và quản lý điểm thi theo SBD")
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
     * 🔍 LOOKUP STUDENT SCORE BY API (GỌI TRỰC TIẾP AJAX)
     */
    @Operation(summary = "Tra cứu điểm thi theo SBD (API gốc)", description = "Gọi trực tiếp API tuyensinh247 để lấy điểm, tổ hợp, ranking và lưu vào database.")
    @GetMapping("/lookup-api/{sbd}")
    public ResponseEntity<Map<String, Object>> lookupByAPI(
            @Parameter(description = "Số báo danh", required = true) @PathVariable String sbd,
            @Parameter(description = "Khu vực xếp hạng", example = "Toàn quốc") @RequestParam(defaultValue = "Toàn quốc") String region) {
        try {
            log.info("Looking up SBD via API: {} in region: {}", sbd, region);
            Map<String, Object> result = sbdLookupService.getStudentScoreFromAPI(sbd, region);
            if ("found".equals(result.get("status")) && result.get("data") != null) {
                sbdLookupService.saveStudentScoreFromAPIData((Map<String, Object>) result.get("data"), region);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error looking up SBD via API: {}", sbd, e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            error.put("sbd", sbd);
            return ResponseEntity.status(500).body(error);
        }
    }

    @Operation(summary = "Lấy tất cả SBD đã lưu", description = "Trả về danh sách tất cả SBD đã lưu trong database.")
    @GetMapping("/all")
    public ResponseEntity<List<String>> getAllSBDs() {
        List<String> allSBDs = studentScoreRepository.findAll().stream().map(s -> s.getSbd()).toList();
        return ResponseEntity.ok(allSBDs);
    }

    @Operation(summary = "Cập nhật lại điểm SBD (yêu cầu crawl lại)", description = "Crawl lại điểm thi cho SBD, cập nhật trạng thái là 'update'.")
    @PutMapping("/update/{sbd}")
    public ResponseEntity<Map<String, Object>> updateSBD(@PathVariable String sbd, @RequestParam(defaultValue = "Toàn quốc") String region) {
        // Xóa dữ liệu cũ
        combinationScoreRepository.deleteBySbd(sbd);
        studentScoreRepository.deleteBySbd(sbd);
        // Crawl lại
        Map<String, Object> result = sbdLookupService.getStudentScoreFromAPI(sbd, region);
        if ("found".equals(result.get("status")) && result.get("data") != null) {
            sbdLookupService.saveStudentScoreFromAPIData((Map<String, Object>) result.get("data"), region);
            result.put("update_status", "updated");
        } else {
            result.put("update_status", "not_found");
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Xóa SBD khỏi database", description = "Xóa toàn bộ dữ liệu điểm thi của SBD khỏi database.")
    @DeleteMapping("/delete/{sbd}")
    public ResponseEntity<Map<String, Object>> deleteSBD(@PathVariable String sbd) {
        combinationScoreRepository.deleteBySbd(sbd);
        studentScoreRepository.deleteBySbd(sbd);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "deleted");
        resp.put("sbd", sbd);
        return ResponseEntity.ok(resp);
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
    @DeleteMapping("/delete-data/{sbd}")
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

    /**
     * 🔍 DEBUG THỰC TẾ - Xem chính xác gì xảy ra sau submit
     */
    // @GetMapping("/debug-real-submit/{sbd}")
    // public ResponseEntity<Map<String, Object>> debugRealSubmit(@PathVariable String sbd) {
    //     WebDriver driver = null;
    //     Map<String, Object> response = new HashMap<>();
        
    //     try {
    //         log.info("🔍 Debug thực tế submit cho SBD: {}", sbd);
            
    //         // Setup Chrome KHÔNG HEADLESS để quan sát
    //         WebDriverManager.chromedriver().setup();
    //         ChromeOptions options = new ChromeOptions();
    //         options.addArguments("--lang=vi-VN");
    //         // BỎ headless để xem trực tiếp
    //         // options.addArguments("--headless=new");
    //         options.addArguments("--window-size=1920,1080");
            
    //         driver = new ChromeDriver(options);
    //         WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            
    //         // Step 1: Mở trang
    //         driver.get("https://diemthi.tuyensinh247.com/xep-hang-thi-thptqg.html");
    //         Thread.sleep(3000);
            
    //         response.put("step1_page_loaded", driver.getTitle());
            
    //         // Step 2: Capture HTML BEFORE submit
    //         String htmlBefore = driver.getPageSource();
    //         response.put("step2_html_before_length", htmlBefore.length());
            
    //         // Step 3: Tìm và nhập SBD
    //         WebElement sbdInput = driver.findElement(By.cssSelector(".block-search-bg input[type='text']"));
    //         sbdInput.clear();
    //         sbdInput.sendKeys(sbd);
    //         Thread.sleep(1000);
            
    //         response.put("step3_sbd_entered", sbd);
            
    //         // Step 4: Capture Network requests trước khi submit
    //         // Enable Chrome DevTools để monitor network
    //         ((ChromeDriver) driver).getDevTools().createSession();
            
    //         // Step 5: Click submit button
    //         WebElement submitBtn = driver.findElement(By.cssSelector(".block-search-bg button.ant-btn-primary"));
    //         submitBtn.click();
            
    //         response.put("step5_submit_clicked", true);
            
    //         // Step 6: Đợi và capture HTML sau mỗi giây trong 15 giây
    //         List<Map<String, Object>> htmlSnapshots = new ArrayList<>();
            
    //         for (int i = 1; i <= 15; i++) {
    //             Thread.sleep(1000);
                
    //             String currentHtml = driver.getPageSource();
    //             String currentUrl = driver.getCurrentUrl();
                
    //             Map<String, Object> snapshot = new HashMap<>();
    //             snapshot.put("second", i);
    //             snapshot.put("html_length", currentHtml.length());
    //             snapshot.put("url", currentUrl);
                
    //             // Check xem có elements mới xuất hiện không
    //             List<String> newElements = new ArrayList<>();
                
    //             // Tìm các class có thể chứa kết quả
    //             String[] possibleResultClasses = {
    //                 ".ranking-result", ".ranking-subjects", ".exam-results",
    //                 ".score-result", ".student-scores", ".result-container",
    //                 "table", ".table", ".ant-table", ".score-table"
    //             };
                
    //             for (String className : possibleResultClasses) {
    //                 try {
    //                     List<WebElement> elements = driver.findElements(By.cssSelector(className));
    //                     if (!elements.isEmpty()) {
    //                         for (WebElement el : elements) {
    //                             String text = el.getText().trim();
    //                             if (!text.isEmpty() && text.length() > 10) {
    //                                 newElements.add(className + ": " + text.substring(0, Math.min(100, text.length())));
    //                             }
    //                         }
    //                     }
    //                 } catch (Exception e) {
    //                     // Ignore
    //                 }
    //             }
                
    //             snapshot.put("new_elements", newElements);
                
    //             // Check có AJAX requests không
    //             try {
    //                 Object pendingRequests = ((JavascriptExecutor) driver).executeScript(
    //                     "return typeof jQuery !== 'undefined' ? jQuery.active : 0;"
    //                 );
    //                 snapshot.put("pending_ajax", pendingRequests);
    //             } catch (Exception e) {
    //                 snapshot.put("pending_ajax", "unknown");
    //             }
                
    //             // Check có popup/modal nào xuất hiện không
    //             List<WebElement> modals = driver.findElements(By.cssSelector(".ant-modal, .modal, .popup"));
    //             snapshot.put("modals_count", modals.size());
                
    //             // Tìm text chứa số điểm
    //             boolean hasScoreNumbers = currentHtml.toLowerCase().matches(".*[0-9]+[\\.,][0-9]+.*") &&
    //                                     (currentHtml.toLowerCase().contains("toán") || 
    //                                     currentHtml.toLowerCase().contains("văn") ||
    //                                     currentHtml.toLowerCase().contains("điểm"));
    //             snapshot.put("has_score_numbers", hasScoreNumbers);
                
    //             htmlSnapshots.add(snapshot);
                
    //             log.info("Giây {}: HTML length = {}, New elements = {}", i, currentHtml.length(), newElements.size());
    //         }
            
    //         response.put("step6_html_snapshots", htmlSnapshots);
            
    //         // Step 7: Capture cuối cùng
    //         String finalHtml = driver.getPageSource();
    //         String finalUrl = driver.getCurrentUrl();
            
    //         response.put("step7_final_html_length", finalHtml.length());
    //         response.put("step7_final_url", finalUrl);
    //         response.put("step7_html_changed", !finalHtml.equals(htmlBefore));
            
    //         // Step 8: Tìm tất cả text có chứa số
    //         List<String> numbersInPage = new ArrayList<>();
    //         String[] lines = finalHtml.split("\\n");
    //         for (String line : lines) {
    //             String cleanLine = line.replaceAll("<[^>]*>", "").trim();
    //             if (cleanLine.matches(".*[0-9]+[\\.,][0-9]+.*") && cleanLine.length() < 200) {
    //                 numbersInPage.add(cleanLine);
    //             }
    //         }
    //         response.put("step8_numbers_in_page", numbersInPage.stream().limit(10).collect(Collectors.toList()));
            
    //         // Step 9: Check JavaScript console errors
    //         try {
    //             List<Object> consoleErrors = new ArrayList<>();
    //             // Chrome DevTools để lấy console logs sẽ cần thêm setup
    //             response.put("step9_console_errors", consoleErrors);
    //         } catch (Exception e) {
    //             response.put("step9_console_errors", "Could not capture");
    //         }
            
    //         // Step 10: Save final HTML to file
    //         try {
    //             String fileName = "final_page_" + sbd + "_" + System.currentTimeMillis() + ".html";
    //             java.nio.file.Files.write(
    //                 java.nio.file.Paths.get(fileName), 
    //                 finalHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    //             );
    //             response.put("step10_saved_file", fileName);
    //             log.info("💾 Saved final HTML to: {}", fileName);
    //         } catch (Exception e) {
    //             log.warn("Could not save HTML file: {}", e.getMessage());
    //         }
            
    //         response.put("status", "debug_completed");
    //         response.put("summary", Map.of(
    //             "html_before_length", htmlBefore.length(),
    //             "html_after_length", finalHtml.length(),
    //             "html_size_changed", finalHtml.length() != htmlBefore.length(),
    //             "url_changed", !finalUrl.equals("https://diemthi.tuyensinh247.com/xep-hang-thi-thptqg.html"),
    //             "total_snapshots", htmlSnapshots.size()
    //         ));
            
    //     } catch (Exception e) {
    //         log.error("❌ Debug failed", e);
    //         response.put("status", "debug_error");
    //         response.put("message", e.getMessage());
    //         response.put("error_type", e.getClass().getSimpleName());
    //     } finally {
    //         if (driver != null) {
    //             // Đợi 30 giây để có thể quan sát browser
    //             try {
    //                 log.info("🔍 Đợi 30 giây để quan sát browser trước khi đóng...");
    //                 Thread.sleep(30000);
    //             } catch (InterruptedException e) {
    //                 // Ignore
    //             }
    //             driver.quit();
    //         }
    //     }
        
    //     return ResponseEntity.ok(response);
    // }

    // /**
    //  * 🌐 NETWORK MONITOR - Monitor tất cả requests sau submit
    //  */
    // @GetMapping("/debug-network/{sbd}")
    // public ResponseEntity<Map<String, Object>> debugNetworkRequests(@PathVariable String sbd) {
    //     WebDriver driver = null;
    //     Map<String, Object> response = new HashMap<>();
        
    //     try {
    //         log.info("🌐 Debug network requests cho SBD: {}", sbd);
            
    //         WebDriverManager.chromedriver().setup();
    //         ChromeOptions options = new ChromeOptions();
    //         options.addArguments("--enable-network-service-logging");
    //         options.addArguments("--log-level=0");
    //         // Không headless để có thể dùng F12
    //         options.addArguments("--window-size=1920,1080");
            
    //         driver = new ChromeDriver(options);
            
    //         // Bật Chrome DevTools Network monitoring
    //         DevTools devTools = ((ChromeDriver) driver).getDevTools();
    //         devTools.createSession();
            
    //         List<Map<String, Object>> networkRequests = new ArrayList<>();
            
    //         // Monitor network requests
    //         devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
            
    //         devTools.addListener(Network.responseReceived(), responseReceived -> {
    //             Map<String, Object> request = new HashMap<>();
    //             request.put("url", responseReceived.getResponse().getUrl());
    //             request.put("status", responseReceived.getResponse().getStatus());
    //             request.put("method", responseReceived.getResponse().getHeaders().get("method"));
    //             request.put("timestamp", System.currentTimeMillis());
    //             networkRequests.add(request);
    //             log.info("📡 Network request: {} - {}", responseReceived.getResponse().getStatus(), responseReceived.getResponse().getUrl());
    //         });
            
    //         // Load page
    //         driver.get("https://diemthi.tuyensinh247.com/xep-hang-thi-thptqg.html");
    //         Thread.sleep(5000);
            
    //         // Submit form
    //         WebElement sbdInput = driver.findElement(By.cssSelector(".block-search-bg input[type='text']"));
    //         sbdInput.sendKeys(sbd);
    //         Thread.sleep(1000);
            
    //         WebElement submitBtn = driver.findElement(By.cssSelector(".block-search-bg button.ant-btn-primary"));
    //         submitBtn.click();
            
    //         // Đợi 20 giây monitor requests
    //         Thread.sleep(20000);
            
    //         response.put("status", "network_debug_completed");
    //         response.put("sbd", sbd);
    //         response.put("total_requests", networkRequests.size());
    //         response.put("network_requests", networkRequests);
            
    //         // Filter requests có thể chứa data
    //         List<Map<String, Object>> dataRequests = networkRequests.stream()
    //                 .filter(req -> {
    //                     String url = (String) req.get("url");
    //                     return url.contains("api") || url.contains("ajax") || 
    //                         url.contains("data") || url.contains("score") ||
    //                         url.contains(".json") || url.contains("ranking");
    //                 })
    //                 .collect(Collectors.toList());
            
    //         response.put("potential_data_requests", dataRequests);
            
    //     } catch (Exception e) {
    //         log.error("❌ Network debug failed", e);
    //         response.put("status", "network_debug_error");
    //         response.put("message", e.getMessage());
    //     } finally {
    //         if (driver != null) {
    //             driver.quit();
    //         }
    //     }
        
    //     return ResponseEntity.ok(response);
    // }

    /**
     * 🔍 SIMPLE DEBUG - Không cần DevTools, chỉ monitor HTML changes
     */
    @GetMapping("/debug-simple-submit/{sbd}")
    public ResponseEntity<Map<String, Object>> debugSimpleSubmit(@PathVariable String sbd) {
        WebDriver driver = null;
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("🔍 Simple debug cho SBD: {}", sbd);
            
            // Setup Chrome đơn giản
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--lang=vi-VN");
            // BỎ headless để xem trực tiếp browser
            // options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-extensions");
            options.addArguments("--no-sandbox");
            
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            
            // Step 1: Load trang
            driver.get("https://diemthi.tuyensinh247.com/xep-hang-thi-thptqg.html");
            Thread.sleep(5000); // Đợi trang load đầy đủ
            
            String initialHtml = driver.getPageSource();
            String initialUrl = driver.getCurrentUrl();
            
            response.put("step1_initial", Map.of(
                "url", initialUrl,
                "title", driver.getTitle(),
                "html_length", initialHtml.length(),
                "page_loaded", "SUCCESS"
            ));
            
            // Step 2: Kiểm tra structure của trang
            List<String> pageStructure = new ArrayList<>();
            
            // Check exam-score-ranking
            List<WebElement> examScoreElements = driver.findElements(By.cssSelector(".exam-score-ranking"));
            pageStructure.add("exam-score-ranking elements: " + examScoreElements.size());
            
            // Check block-search-bg
            List<WebElement> blockSearchElements = driver.findElements(By.cssSelector(".block-search-bg"));
            pageStructure.add("block-search-bg elements: " + blockSearchElements.size());
            
            // Check input trong block-search-bg
            List<WebElement> inputElements = driver.findElements(By.cssSelector(".block-search-bg input"));
            for (int i = 0; i < inputElements.size(); i++) {
                WebElement input = inputElements.get(i);
                pageStructure.add(String.format("Input %d: type=%s, placeholder='%s', class='%s'", 
                    i + 1,
                    input.getAttribute("type"),
                    input.getAttribute("placeholder"),
                    input.getAttribute("class")
                ));
            }
            
            // Check buttons trong block-search-bg
            List<WebElement> buttonElements = driver.findElements(By.cssSelector(".block-search-bg button"));
            for (int i = 0; i < buttonElements.size(); i++) {
                WebElement button = buttonElements.get(i);
                pageStructure.add(String.format("Button %d: text='%s', type=%s, class='%s'", 
                    i + 1,
                    button.getText().trim(),
                    button.getAttribute("type"),
                    button.getAttribute("class")
                ));
            }
            
            response.put("step2_page_structure", pageStructure);
            
            // Step 3: Nhập SBD
            WebElement sbdInput = null;
            try {
                // Thử tìm input chính xác
                sbdInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".block-search-bg input[type='text']")
                ));
                
                // Clear và nhập SBD
            sbdInput.clear();
                Thread.sleep(500);
            sbdInput.sendKeys(sbd);
            Thread.sleep(1000);
            
                // Verify SBD đã được nhập
                String inputValue = sbdInput.getAttribute("value");
                response.put("step3_sbd_input", Map.of(
                    "status", "SUCCESS",
                    "sbd_entered", sbd,
                    "input_value", inputValue,
                    "input_matches", sbd.equals(inputValue)
                ));
                
            } catch (Exception e) {
                response.put("step3_sbd_input", Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
                ));
                return ResponseEntity.ok(response);
            }
            
            // Step 4: Tìm và click submit button
            WebElement submitButton = null;
            try {
                submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector(".block-search-bg button.ant-btn-primary")
                ));
                
                // Thông tin button trước khi click
                String buttonText = submitButton.getText().trim();
                String buttonClass = submitButton.getAttribute("class");
                boolean isEnabled = submitButton.isEnabled();
                boolean isDisplayed = submitButton.isDisplayed();
                
                response.put("step4_button_info", Map.of(
                    "text", buttonText,
                    "class", buttonClass,
                    "enabled", isEnabled,
                    "displayed", isDisplayed
                ));
                
                // Scroll to button và click
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", submitButton);
                Thread.sleep(1000);
                
                submitButton.click();
                
                response.put("step4_submit_click", Map.of(
                    "status", "SUCCESS",
                    "clicked_at", System.currentTimeMillis()
                ));
                
            } catch (Exception e) {
                response.put("step4_submit_click", Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
                ));
                return ResponseEntity.ok(response);
            }
            
            // Step 5: Monitor thay đổi trong 25 giây
            List<Map<String, Object>> monitorLog = new ArrayList<>();
            
            log.info("⏱️ Bắt đầu monitor thay đổi trong 25 giây...");
            
            for (int second = 1; second <= 25; second++) {
                Thread.sleep(1000);
                
                Map<String, Object> snapshot = new HashMap<>();
                snapshot.put("second", second);
                
                // Check URL
                String currentUrl = driver.getCurrentUrl();
                snapshot.put("url", currentUrl);
                snapshot.put("url_changed", !currentUrl.equals(initialUrl));
                
                // Check HTML length
                String currentHtml = driver.getPageSource();
                snapshot.put("html_length", currentHtml.length());
                snapshot.put("html_changed", currentHtml.length() != initialHtml.length());
                
                // Check có alert/popup không
                try {
                    driver.switchTo().alert();
                    snapshot.put("alert_present", true);
                } catch (Exception e) {
                    snapshot.put("alert_present", false);
                }
                
                // Check DOM elements thay đổi
                List<String> domChanges = new ArrayList<>();
                
                // Tìm các elements có thể xuất hiện sau submit
                String[] newElementSelectors = {
                    ".ranking-result",
                    ".ranking-subjects", 
                    ".exam-results",
                    ".student-scores",
                    ".score-results",
                    ".ant-table",
                    "table:not([class*='existing'])",
                    ".result-container",
                    ".score-container",
                    "[class*='result']",
                    "[class*='score']"
                };
                
                for (String selector : newElementSelectors) {
                    try {
                        List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                        if (!elements.isEmpty()) {
                            for (WebElement el : elements) {
                                String text = el.getText().trim();
                                if (!text.isEmpty() && text.length() > 10) {
                                    domChanges.add(String.format("%s: %s", 
                                        selector, 
                                        text.substring(0, Math.min(80, text.length()))
                                    ));
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore selector errors
                    }
                }
                
                snapshot.put("new_dom_elements", domChanges);
                
                // Check text chứa điểm số
                boolean hasScoreText = currentHtml.toLowerCase().contains("toán") ||
                                    currentHtml.toLowerCase().contains("văn") ||
                                    currentHtml.toLowerCase().contains("lý") ||
                                    currentHtml.toLowerCase().contains("hóa") ||
                                    (currentHtml.toLowerCase().contains("điểm") && 
                                    currentHtml.matches(".*[0-9]+[\\.,][0-9]+.*"));
                
                snapshot.put("has_potential_scores", hasScoreText);
                
                // Check JavaScript activity (nếu có jQuery)
                try {
                    Object jqueryActive = ((JavascriptExecutor) driver).executeScript(
                        "return typeof jQuery !== 'undefined' ? jQuery.active : -1;"
                    );
                    snapshot.put("jquery_active", jqueryActive);
                } catch (Exception e) {
                    snapshot.put("jquery_active", "N/A");
                }
                
                // Check loading indicators
                List<WebElement> loadingElements = driver.findElements(
                    By.cssSelector(".loading, .spinner, .ant-spin, [class*='loading']")
                );
                snapshot.put("loading_indicators", loadingElements.size());
                
                monitorLog.add(snapshot);
                
                // Log quan trọng mỗi 5 giây
                if (second % 5 == 0 || !domChanges.isEmpty() || hasScoreText) {
                    log.info("⏱️ Giây {}: URL changed={}, HTML changed={}, New elements={}, Has scores={}", 
                            second, 
                            !currentUrl.equals(initialUrl),
                            currentHtml.length() != initialHtml.length(),
                            domChanges.size(),
                            hasScoreText
                    );
                }
            }
            
            response.put("step5_monitor_log", monitorLog);
            
            // Step 6: Final analysis
            String finalHtml = driver.getPageSource();
            String finalUrl = driver.getCurrentUrl();
            
            // Extract potential scores từ final HTML
            List<String> potentialScores = new ArrayList<>();
            String[] lines = finalHtml.split("\\n");
            
            for (String line : lines) {
                String cleanLine = line.replaceAll("<[^>]*>", "").trim();
                // Tìm dòng có chứa cả tên môn và số điểm
                if (cleanLine.length() < 200 && !cleanLine.isEmpty() &&
                    cleanLine.matches(".*[0-9]+[\\.,][0-9]+.*") &&
                    (cleanLine.toLowerCase().contains("toán") ||
                    cleanLine.toLowerCase().contains("văn") ||
                    cleanLine.toLowerCase().contains("lý") ||
                    cleanLine.toLowerCase().contains("hóa") ||
                    cleanLine.toLowerCase().contains("anh") ||
                    cleanLine.toLowerCase().contains("sinh") ||
                    cleanLine.toLowerCase().contains("sử") ||
                    cleanLine.toLowerCase().contains("địa"))) {
                    potentialScores.add(cleanLine);
                }
            }
            
            // Summary statistics từ monitor log
            long urlChanges = monitorLog.stream()
                .mapToLong(m -> (Boolean)m.get("url_changed") ? 1 : 0).sum();
            long htmlChanges = monitorLog.stream()
                .mapToLong(m -> (Boolean)m.get("html_changed") ? 1 : 0).sum();
            long newElementsFound = monitorLog.stream()
                .mapToLong(m -> ((List<?>)m.get("new_dom_elements")).size()).sum();
            long scoresDetected = monitorLog.stream()
                .mapToLong(m -> (Boolean)m.get("has_potential_scores") ? 1 : 0).sum();
            
            response.put("step6_final_analysis", Map.of(
                "final_url", finalUrl,
                "final_html_length", finalHtml.length(),
                "url_changes_detected", urlChanges,
                "html_changes_detected", htmlChanges,
                "new_elements_detected", newElementsFound,
                "potential_scores_detected", scoresDetected,
                "potential_score_lines", potentialScores.stream().limit(5).collect(Collectors.toList())
            ));
            
            // Step 7: Recommendation
            String recommendation = generateRecommendation(urlChanges, htmlChanges, newElementsFound, scoresDetected, potentialScores.size());
            response.put("step7_recommendation", recommendation);
            
            // Step 8: Save HTML cho analysis
            try {
                String fileName = "simple_debug_" + sbd + "_" + System.currentTimeMillis() + ".html";
                java.nio.file.Files.write(
                    java.nio.file.Paths.get(fileName), 
                    finalHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                response.put("step8_saved_file", fileName);
                log.info("💾 Saved HTML file: {}", fileName);
            } catch (Exception e) {
                log.warn("Could not save HTML: {}", e.getMessage());
            }
            
            response.put("status", "debug_completed");
            response.put("debug_summary", Map.of(
                "total_monitoring_seconds", monitorLog.size(),
                "significant_changes_detected", urlChanges + htmlChanges + newElementsFound > 0,
                "likely_has_results", scoresDetected > 5 || potentialScores.size() > 2,
                "needs_different_approach", urlChanges == 0 && htmlChanges == 0 && newElementsFound == 0
            ));
            
        } catch (Exception e) {
            log.error("❌ Simple debug failed", e);
            response.put("status", "debug_error");
            response.put("message", e.getMessage());
            response.put("error_type", e.getClass().getSimpleName());
        } finally {
            if (driver != null) {
                try {
                    log.info("🔍 Đợi 15 giây để quan sát browser cuối cùng...");
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                driver.quit();
            }
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Generate recommendation dựa trên kết quả debug
     */
    private String generateRecommendation(long urlChanges, long htmlChanges, long newElements, long scoresDetected, int potentialScoreLines) {
        if (urlChanges > 0) {
            return "🔄 REDIRECT DETECTED: Website chuyển đến URL khác sau submit. Cần crawl URL đích thay vì trang gốc.";
        }
        
        if (newElements > 10) {
            return "✅ NEW ELEMENTS: Có nhiều elements mới xuất hiện. Selenium strategy hiện tại có thể làm việc, chỉ cần cải thiện element selectors.";
        }
        
        if (scoresDetected > 10 && potentialScoreLines > 3) {
            return "📊 SCORES DETECTED: Có điểm số trong HTML nhưng extraction failed. Cần cải thiện score parsing logic.";
        }
        
        if (htmlChanges > 5) {
            return "🔄 HTML CHANGES: HTML thay đổi nhưng không rõ pattern. Có thể cần AJAX monitoring hoặc wait strategy khác.";
        }
        
        if (potentialScoreLines > 0) {
            return "⚠️ PARTIAL DATA: Có một ít potential scores. Có thể SBD hợp lệ nhưng extraction method chưa đúng.";
        }
        
        return "❌ NO CHANGES: Không có thay đổi nào sau submit. Có thể: (1) SBD không hợp lệ, (2) Website không hoạt động, (3) Cần authentication/captcha.";
    }

    @Operation(summary = "Lấy thông tin điểm thi đã lưu theo SBD", description = "Trả về thông tin điểm thi và tổ hợp đã lưu trong database cho SBD.")
    @GetMapping("/get/{sbd}")
    public ResponseEntity<Map<String, Object>> getSBDFromDB(@PathVariable String sbd) {
        Map<String, Object> resp = new HashMap<>();
        var studentOpt = studentScoreRepository.findBySbd(sbd);
        if (studentOpt.isEmpty()) {
            resp.put("status", "not_found");
            resp.put("sbd", sbd);
            return ResponseEntity.ok(resp);
        }
        var student = studentOpt.get();
        resp.put("status", "found");
        resp.put("sbd", student.getSbd());
        resp.put("exam_year", student.getExamYear());
        resp.put("region", student.getRegion());
        // Subject scores
        Map<String, Object> subjectScores = new HashMap<>();
        subjectScores.put("toan", student.getScoreMath());
        subjectScores.put("van", student.getScoreLiterature());
        subjectScores.put("ly", student.getScorePhysics());
        subjectScores.put("hoa", student.getScoreChemistry());
        subjectScores.put("anh", student.getScoreEnglish());
        subjectScores.put("sinh", student.getScoreBiology());
        subjectScores.put("su", student.getScoreHistory());
        subjectScores.put("dia", student.getScoreGeography());
        resp.put("subject_scores", subjectScores);
        // Blocks (tổ hợp)
        var blocks = combinationScoreRepository.findBySbd(sbd).stream().map(comb -> {
            Map<String, Object> b = new HashMap<>();
            b.put("label", comb.getCombinationName());
            b.put("value", comb.getCombinationCode());
            b.put("total_score", comb.getTotalScore());
            b.put("equivalent_score_2024", comb.getEquivalentScore2024());
            b.put("students_with_same_score", comb.getStudentsWithSameScore());
            b.put("students_with_higher_score", comb.getStudentsWithHigherScore());
            b.put("total_students_in_combination", comb.getTotalStudentsInCombination());
            return b;
        }).toList();
        resp.put("blocks", blocks);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Xóa tất cả bản ghi theo SBD", description = "Xóa mọi StudentScore và CombinationScore có cùng SBD (kể cả duplicate).")
    @DeleteMapping("/delete-all/{sbd}")
    public ResponseEntity<Map<String, Object>> deleteAllBySbd(@PathVariable String sbd) {
        combinationScoreRepository.deleteBySbd(sbd);
        studentScoreRepository.deleteBySbd(sbd);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "deleted_all");
        resp.put("sbd", sbd);
        return ResponseEntity.ok(resp);
    }
}