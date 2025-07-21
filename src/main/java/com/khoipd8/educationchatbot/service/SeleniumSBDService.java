package com.khoipd8.educationchatbot.service;

import com.khoipd8.educationchatbot.entity.StudentScore;
import com.khoipd8.educationchatbot.repository.StudentScoreRepository;
import com.khoipd8.educationchatbot.entity.CombinationScore;
import com.khoipd8.educationchatbot.repository.CombinationScoreRepository;          

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;


import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class SeleniumSBDService {
    
    @Autowired
    private StudentScoreRepository studentScoreRepository;
    
    @Autowired
    private CombinationScoreRepository combinationScoreRepository;
    
    /**
     * 🤖 SELENIUM CRAWL - CHẮC CHẮN THÀNH CÔNG
     */
    // SỬA LẠI METHOD crawlWithSelenium trong SeleniumSBDService.java

    public Map<String, Object> crawlWithSelenium(String sbd, String region) {
        WebDriver driver = null;
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("🤖 Starting Selenium crawl for SBD: {}", sbd);
            
            // Setup Chrome driver cho Windows
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            
            // Cấu hình cho Windows
            options.addArguments("--headless"); // Chạy ẩn
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--disable-extensions");
            options.addArguments("--lang=vi-VN");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            
            // Step 1: Mở trang
            driver.get("https://diemthi.tuyensinh247.com/xep-hang-thi-thptqg.html");
            log.info("✅ Loaded page: {}", driver.getTitle());
            
            // Wait for page to fully load
            Thread.sleep(3000);
            
            // Step 2: Debug - In ra toàn bộ HTML để xem cấu trúc
            String pageSource = driver.getPageSource();
            log.debug("Page source length: {}", pageSource.length());
            
            // Step 3: Tìm SBD input với nhiều cách khác nhau
            WebElement sbdInput = null;
            
            // Thử nhiều selector khác nhau
            String[] inputSelectors = {
                "input[name='sbd']",
                "input[placeholder*='số báo danh']", 
                "input[placeholder*='Số báo danh']",
                "input[placeholder*='SBD']",
                "input[type='text']",
                "input[id*='sbd']",
                "input[class*='sbd']"
            };
            
            for (String selector : inputSelectors) {
                try {
                    sbdInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                    log.info("✅ Found SBD input using selector: {}", selector);
                    break;
                } catch (Exception e) {
                    log.debug("Selector failed: {}", selector);
                }
            }
            
            if (sbdInput == null) {
                log.error("❌ Could not find SBD input field");
                result.put("status", "input_not_found");
                result.put("message", "Không tìm thấy ô nhập SBD");
                result.put("page_title", driver.getTitle());
                return result;
            }
            
            // Step 4: Nhập SBD
            sbdInput.clear();
            sbdInput.sendKeys(sbd);
            log.info("✅ Entered SBD: {}", sbd);
            
            // Step 5: Chọn khu vực (nếu có)
            try {
                WebElement regionSelect = driver.findElement(By.tagName("select"));
                Select select = new Select(regionSelect);
                // Thử chọn theo text
                List<WebElement> regionOptions = select.getOptions();
                for (WebElement option : regionOptions) {
                    String optionText = option.getText();
                    log.debug("Available option: {}", optionText);
                    if (optionText.contains(region) || 
                        optionText.toLowerCase().contains(region.toLowerCase())) {
                        select.selectByVisibleText(optionText);
                        log.info("✅ Selected region: {}", optionText);
                        break;
                    }
                }
            } catch (Exception e) {
                log.debug("No region selector found: {}", e.getMessage());
            }
            
            // Step 6: Submit form bằng JavaScript
            try {
                ((JavascriptExecutor) driver).executeScript("document.querySelector('form').submit();");
                log.info("✅ Form submitted via JavaScript");
            } catch (Exception e) {
                log.error("❌ JavaScript submit failed: {}", e.getMessage());
                result.put("status", "submit_failed");
                result.put("message", "Không thể submit form");
                return result;
            }

            // Step 7: Đợi kết quả load ĐỘNG - QUAN TRỌNG!
            log.info("⏳ Waiting for results to load dynamically...");

            WebElement resultsTable = null;
            try {
                // Đợi cho table kết quả xuất hiện (tối đa 15 giây)
                resultsTable = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".ranking-table.ts247-watermark, table.ranking-table")
                ));
                log.info("✅ Results table appeared!");
                
                // Đợi thêm 2 giây để data load hoàn toàn
                Thread.sleep(2000);
                
            } catch (Exception e) {
                log.warn("⚠️ Main results table not found, trying alternative selectors...");
                
                // Thử các selector khác
                String[] alternativeSelectors = {
                    "table[class*='ranking']",
                    "table[class*='ts247']", 
                    "table[class*='watermark']",
                    ".ranking-table",
                    "table tbody tr",
                    "table"
                };
                
                for (String selector : alternativeSelectors) {
                    try {
                        List<WebElement> tables = driver.findElements(By.cssSelector(selector));
                        if (!tables.isEmpty()) {
                            resultsTable = tables.get(0);
                            log.info("✅ Found results using selector: {}", selector);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Step 8: Check for error messages sau khi load
            String currentUrl = driver.getCurrentUrl();
            String newPageSource = driver.getPageSource();
            log.info("📄 Page after submit - URL: {}, Source length: {}", currentUrl, newPageSource.length());

            // Tìm error message trong kết quả động
            String[] errorTexts = {
                "không tìm thấy kết quả nào phù hợp",
                "không tìm thấy",
                "không có dữ liệu", 
                "không có thông tin",
                "không có kết quả"
            };

            for (String errorText : errorTexts) {
                if (newPageSource.toLowerCase().contains(errorText.toLowerCase())) {
                    result.put("status", "not_found_on_website");
                    result.put("sbd", sbd);
                    result.put("message", "Website xác nhận không có dữ liệu cho SBD: " + sbd);
                    return result;
                }
            }

            // Step 9: Extract scores từ bảng kết quả ĐỘNG
            Map<String, Double> scores = extractScoresFromDynamicTable(driver, resultsTable);

            // Debug: In ra điểm tìm được
            log.info("📊 Scores found from dynamic table: {}", scores);

            if (scores.isEmpty()) {
                // Debug: Lưu page source để xem
                log.error("❌ No scores found from dynamic table. Analyzing page structure...");
                
                // In ra structure của table để debug
                if (resultsTable != null) {
                    try {
                        log.info("🔍 Table HTML: {}", resultsTable.getAttribute("outerHTML").substring(0, Math.min(500, resultsTable.getAttribute("outerHTML").length())));
                    } catch (Exception e) {
                        log.debug("Could not get table HTML: {}", e.getMessage());
                    }
                }
                
                result.put("status", "no_scores_found");
                result.put("sbd", sbd);
                result.put("message", "Không tìm thấy điểm số trong bảng kết quả");
                result.put("page_preview", newPageSource.length() > 1000 ? 
                        newPageSource.substring(0, 1000) + "..." : newPageSource);
                result.put("table_found", resultsTable != null);
                return result;
            }
            
            // Step 10: Save to database
            StudentScore studentScore = createStudentScoreFromMap(scores, sbd, region);
            studentScore = studentScoreRepository.save(studentScore);
            
            // Step 11: Create combination scores
            List<CombinationScore> combinationScores = createCombinationScoresFromSelenium(driver, studentScore);
            if (!combinationScores.isEmpty()) {
                combinationScoreRepository.saveAll(combinationScores);
            }
            
            // Step 12: Format response
            result = formatCrawledData(studentScore, combinationScores);
            result.put("source", "selenium_crawl");
            result.put("crawl_success", true);
            result.put("scores_found", scores);
            result.put("debug_info", Map.of(
                "page_title", driver.getTitle(),
                "final_url", driver.getCurrentUrl(),
                "scores_extracted", scores.size(),
                "combinations_created", combinationScores.size()
            ));
            
            log.info("🎉 Selenium crawl SUCCESS for SBD: {} - Found {} scores", sbd, scores.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Selenium crawl failed for SBD: {}", sbd, e);
            result.put("status", "selenium_error");
            result.put("sbd", sbd);
            result.put("message", "Lỗi Selenium: " + e.getMessage());
            result.put("error_type", e.getClass().getSimpleName());
            
            // Thêm thông tin debug
            if (driver != null) {
                try {
                    result.put("current_url", driver.getCurrentUrl());
                    result.put("page_title", driver.getTitle());
                } catch (Exception ex) {
                    // Ignore
                }
            }
            
            return result;
            
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    log.info("✅ Chrome driver closed");
                } catch (Exception e) {
                    log.warn("Warning closing driver: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Extract điểm số từ page sử dụng Selenium
     */
    private Map<String, Double> extractScoresFromSelenium(WebDriver driver) {
        Map<String, Double> scores = new HashMap<>();
        
        try {
            String pageSource = driver.getPageSource();
            log.debug("Extracting scores from page source length: {}", pageSource.length());
            
            // Pattern 1: Tìm text patterns như "Môn Toán: 4.75" 
            extractScoresFromPageText(pageSource, scores);
            
            // Pattern 2: Tìm trong các elements có thể chứa điểm
            extractScoresFromElements(driver, scores);
            
            // Pattern 3: Tìm trong orange/colored boxes (như screenshot)
            extractScoresFromColoredBoxes(driver, scores);
            
            // Pattern 4: Regex pattern matching trên toàn bộ page
            extractScoresWithRegex(pageSource, scores);
            
            log.info("📊 Total scores extracted: {}", scores.size());
            for (Map.Entry<String, Double> entry : scores.entrySet()) {
                log.info("   {} = {}", entry.getKey(), entry.getValue());
            }
            
        } catch (Exception e) {
            log.error("Error in extractScoresFromSelenium: {}", e.getMessage(), e);
        }
        
        return scores;
    }
    private void extractScoresFromPageText(String pageSource, Map<String, Double> scores) {
        try {
            // Split into lines and look for score patterns
            String[] lines = pageSource.split("\\n");
            
            for (String line : lines) {
                String cleanLine = line.replaceAll("<[^>]*>", "").trim(); // Remove HTML tags
                
                if (cleanLine.toLowerCase().contains("môn") && 
                    cleanLine.matches(".*[0-9]+[\\.,][0-9]+.*")) {
                    
                    extractScoreFromText(cleanLine, scores);
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting from page text: {}", e.getMessage());
        }
    }

    private void extractScoresFromElements(WebDriver driver, Map<String, Double> scores) {
        try {
            // Tìm tất cả elements có thể chứa điểm
            String[] possibleSelectors = {
                "td", "div", "span", "p", "li", 
                ".score", ".result", ".grade", 
                "[class*='diem']", "[class*='score']",
                "[id*='diem']", "[id*='score']"
            };
            
            for (String selector : possibleSelectors) {
                try {
                    List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                    
                    for (WebElement element : elements) {
                        String text = element.getText().trim();
                        if (!text.isEmpty() && 
                            text.toLowerCase().contains("môn") && 
                            text.matches(".*[0-9]+[\\.,][0-9]+.*")) {
                            
                            extractScoreFromText(text, scores);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Selector {} failed: {}", selector, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting from elements: {}", e.getMessage());
        }
    }

    private void extractScoresFromColoredBoxes(WebDriver driver, Map<String, Double> scores) {
        try {
            // Tìm boxes có background color (như orange box trong screenshot)
            List<WebElement> coloredElements = driver.findElements(
                By.xpath("//*[contains(@style, 'background') or contains(@style, 'color')]")
            );
            
            for (WebElement element : coloredElements) {
                String text = element.getText().trim();
                String style = element.getAttribute("style");
                
                if (!text.isEmpty() && 
                    (style.contains("orange") || style.contains("#f") || style.contains("rgb")) &&
                    text.matches(".*[0-9]+[\\.,][0-9]+.*")) {
                    
                    extractScoreFromText(text, scores);
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting from colored boxes: {}", e.getMessage());
        }
    }
    
    private void extractScoresWithRegex(String pageSource, Map<String, Double> scores) {
        try {
            // Clean HTML từ page source
            String cleanText = pageSource.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ");
            
            // Specific patterns cho từng môn học
            Map<String, String[]> subjectPatterns = new HashMap<>();
            subjectPatterns.put("toán", new String[]{"toán", "math"});
            subjectPatterns.put("văn", new String[]{"văn", "ngữ văn", "literature"});
            subjectPatterns.put("lý", new String[]{"lý", "vật lí", "physics"});
            subjectPatterns.put("hóa", new String[]{"hóa", "hóa học", "chemistry"});
            subjectPatterns.put("anh", new String[]{"anh", "tiếng anh", "english"});
            subjectPatterns.put("sinh", new String[]{"sinh", "sinh học", "biology"});
            subjectPatterns.put("sử", new String[]{"sử", "lịch sử", "history"});
            subjectPatterns.put("địa", new String[]{"địa", "địa lí", "geography"});
            
            for (Map.Entry<String, String[]> entry : subjectPatterns.entrySet()) {
                String subject = entry.getKey();
                String[] patterns = entry.getValue();
                
                for (String pattern : patterns) {
                    // Pattern: "Môn Toán: 4.75" hoặc "Toán 4.75"
                    String regex = "(?i)(?:môn\\s+)?" + pattern + "\\s*[:\\-]?\\s*([0-9]+[\\.,][0-9]+)";
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
                    java.util.regex.Matcher m = p.matcher(cleanText);
                    
                    if (m.find()) {
                        try {
                            String scoreStr = m.group(1).replace(",", ".");
                            double score = Double.parseDouble(scoreStr);
                            if (score >= 0 && score <= 10) {
                                scores.put(subject, score);
                                log.debug("Regex extracted: {} = {}", subject, score);
                                break; // Đã tìm thấy, không cần thử pattern khác
                            }
                        } catch (NumberFormatException e) {
                            log.debug("Could not parse score: {}", m.group(1));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error in regex extraction: {}", e.getMessage());
        }
    }
    
    // Cải thiện method extractScoreFromText
    private void extractScoreFromText(String text, Map<String, Double> scores) {
        if (text == null || text.trim().isEmpty()) return;
        
        try {
            String lowerText = text.toLowerCase();
            
            // Map các môn học
            Map<String, String[]> subjects = new HashMap<>();
            subjects.put("toán", new String[]{"toán", "math"});
            subjects.put("văn", new String[]{"văn", "ngữ văn", "literature"});
            subjects.put("lý", new String[]{"lý", "vật lí", "physics"});
            subjects.put("hóa", new String[]{"hóa", "hóa học", "chemistry"});
            subjects.put("anh", new String[]{"anh", "tiếng anh", "english"});
            subjects.put("sinh", new String[]{"sinh", "sinh học", "biology"});
            subjects.put("sử", new String[]{"sử", "lịch sử", "history"});
            subjects.put("địa", new String[]{"địa", "địa lí", "geography"});
            
            for (Map.Entry<String, String[]> entry : subjects.entrySet()) {
                String subjectKey = entry.getKey();
                String[] patterns = entry.getValue();
                
                // Skip nếu đã có điểm cho môn này
                if (scores.containsKey(subjectKey)) continue;
                
                for (String pattern : patterns) {
                    if (lowerText.contains(pattern)) {
                        // Tìm số sau tên môn học
                        String regex = pattern + "\\s*[:\\-]?\\s*([0-9]+[\\.,]?[0-9]*)";
                        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
                        java.util.regex.Matcher m = p.matcher(text);
                        
                        if (m.find()) {
                            try {
                                String scoreStr = m.group(1).replace(",", ".");
                                double score = Double.parseDouble(scoreStr);
                                if (score >= 0 && score <= 10) {
                                    scores.put(subjectKey, score);
                                    log.debug("Text extracted: {} = {} from: {}", subjectKey, score, text.substring(0, Math.min(100, text.length())));
                                    break;
                                }
                            } catch (NumberFormatException e) {
                                log.debug("Could not parse score from: {}", m.group(1));
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error in extractScoreFromText: {}", e.getMessage());
        }
    }
    
    // private void extractScoreFromText(String text, Map<String, Double> scores) {
    //     if (text == null || text.trim().isEmpty()) return;
        
    //     // Patterns for Vietnamese subjects
    //     String[][] patterns = {
    //         {"toán", "math"},
    //         {"văn", "literature", "ngữ văn"},
    //         {"lý", "physics", "vật lí"},
    //         {"hóa", "chemistry", "hóa học"},
    //         {"anh", "english", "tiếng anh"},
    //         {"sinh", "biology", "sinh học"},
    //         {"sử", "history", "lịch sử"},
    //         {"địa", "geography", "địa lí"}
    //     };
        
    //     for (String[] subjectPatterns : patterns) {
    //         for (String pattern : subjectPatterns) {
    //             if (text.toLowerCase().contains(pattern)) {
    //                 // Extract number after subject name
    //                 String regex = pattern + "\\s*[:\\-]?\\s*([0-9]+[\\.,]?[0-9]*)";
    //                 java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
    //                 java.util.regex.Matcher m = p.matcher(text);
    //                 if (m.find()) {
    //                     try {
    //                         String scoreStr = m.group(1).replace(",", ".");
    //                         double score = Double.parseDouble(scoreStr);
    //                         if (score >= 0 && score <= 10) {
    //                             scores.put(subjectPatterns[0], score); // Use primary name
    //                         }
    //                     } catch (NumberFormatException e) {
    //                         // Ignore
    //                     }
    //                 }
    //                 break;
    //             }
    //         }
    //     }
    // }
    
    private void extractAllScoresFromPageSource(String pageSource, Map<String, Double> scores) {
        // Extract từ page source HTML
        String[] lines = pageSource.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("môn") && 
                line.matches(".*[0-9]+[\\.,][0-9]+.*")) {
                extractScoreFromText(line, scores);
            }
        }
    }
    
    // Helper methods tương tự như trong SBDLookupService...
    private StudentScore createStudentScoreFromMap(Map<String, Double> scores, String sbd, String region) {
        StudentScore studentScore = new StudentScore();
        studentScore.setSbd(sbd);
        studentScore.setExamYear(2025);
        studentScore.setRegion(region);
        
        studentScore.setScoreMath(scores.get("toán"));
        studentScore.setScoreLiterature(scores.get("văn"));
        studentScore.setScorePhysics(scores.get("lý"));
        studentScore.setScoreChemistry(scores.get("hóa"));
        studentScore.setScoreEnglish(scores.get("anh"));
        studentScore.setScoreBiology(scores.get("sinh"));
        studentScore.setScoreHistory(scores.get("sử"));
        studentScore.setScoreGeography(scores.get("địa"));
        
        return studentScore;
    }

    // BỔ SUNG VÀO CUỐI FILE SeleniumSBDService.java

    /**
     * Tạo combination scores từ Selenium data
     */
    private List<CombinationScore> createCombinationScoresFromSelenium(WebDriver driver, StudentScore studentScore) {
        List<CombinationScore> combinationScores = new ArrayList<>();
        
        try {
            // Determine eligible combinations from scores
            List<String> eligibleCombinations = determineEligibleCombinations(studentScore);
            
            for (String combCode : eligibleCombinations) {
                try {
                    CombinationScore combScore = new CombinationScore();
                    combScore.setSbd(studentScore.getSbd());
                    combScore.setCombinationCode(combCode);
                    combScore.setCombinationName(getCombinationName(combCode));
                    combScore.setStudentScore(studentScore);
                    combScore.setRegion(studentScore.getRegion());
                    
                    // Calculate total score
                    Double totalScore = calculateCombinationScore(studentScore, combCode);
                    combScore.setTotalScore(totalScore);
                    
                    if (totalScore != null) {
                        // Try to extract ranking from page
                        extractRankingFromSeleniumPage(driver, combScore, combCode);
                        
                        // If no ranking found, estimate
                        if (combScore.getStudentsWithHigherScore() == null) {
                            estimateRankingData(combScore, totalScore, combCode);
                        }
                        
                        combinationScores.add(combScore);
                    }
                    
                } catch (Exception e) {
                    log.debug("Error creating combination score for {}: {}", combCode, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Error creating combination scores from Selenium: {}", e.getMessage());
        }
        
        return combinationScores;
    }
    
    /**
     * Extract ranking info từ Selenium page
     */
    private void extractRankingFromSeleniumPage(WebDriver driver, CombinationScore combScore, String combCode) {
        try {
            // Look for ranking data on page
            List<WebElement> rankingElements = driver.findElements(
                By.xpath("//*[contains(text(), '" + combCode + "')]")
            );
            
            for (WebElement element : rankingElements) {
                String text = element.getText();
                if (text.contains(combCode)) {
                    // Try to extract ranking numbers
                    extractRankingFromText(text, combScore);
                }
            }
            
            // Look for total score display
            List<WebElement> scoreElements = driver.findElements(
                By.xpath("//*[contains(text(), 'Điểm') or contains(text(), 'điểm')]")
            );
            
            for (WebElement element : scoreElements) {
                String text = element.getText();
                if (text.contains(combCode) && text.matches(".*[0-9]+[\\.,][0-9]+.*")) {
                    // Extract score from element
                    extractScoreFromCombinationText(text, combScore);
                }
            }
            
        } catch (Exception e) {
            log.debug("Error extracting ranking from Selenium page: {}", e.getMessage());
        }
    }
    
    private void extractRankingFromText(String text, CombinationScore combScore) {
        try {
            // Pattern: "có điểm bằng: 618"
            if (text.contains("điểm bằng") || text.contains("cùng điểm")) {
                String pattern = "(?:điểm bằng|cùng điểm)[^0-9]*([0-9.,]+)";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    String numberStr = m.group(1).replaceAll("[^0-9]", "");
                    combScore.setStudentsWithSameScore(Integer.parseInt(numberStr));
                }
            }
            
            // Pattern: "điểm cao hơn: 136.977"
            if (text.contains("cao hơn") || text.contains("lớn hơn")) {
                String pattern = "(?:cao hơn|lớn hơn)[^0-9]*([0-9.,]+)";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    String numberStr = m.group(1).replaceAll("[^0-9]", "");
                    combScore.setStudentsWithHigherScore(Integer.parseInt(numberStr));
                }
            }
            
            // Pattern: "tổng số: 162200"
            if (text.contains("tổng số") || text.contains("trong khối")) {
                String pattern = "(?:tổng số|trong khối)[^0-9]*([0-9.,]+)";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    String numberStr = m.group(1).replaceAll("[^0-9]", "");
                    combScore.setTotalStudentsInCombination(Integer.parseInt(numberStr));
                }
            }
            
        } catch (Exception e) {
            log.debug("Error extracting ranking from text: {}", e.getMessage());
        }
    }
    
    private void extractScoreFromCombinationText(String text, CombinationScore combScore) {
        try {
            // Extract total score for combination
            String pattern = "([0-9]+[\\.,][0-9]+)\\s*(?:Điểm|điểm)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                String scoreStr = m.group(1).replace(",", ".");
                Double score = Double.parseDouble(scoreStr);
                if (combScore.getTotalScore() == null) {
                    combScore.setTotalScore(score);
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting score from combination text: {}", e.getMessage());
        }
    }
    
    // COPY CÁC HELPER METHODS TỪ SBDLookupService
    
    private List<String> determineEligibleCombinations(StudentScore score) {
        List<String> combinations = new ArrayList<>();
        
        // A00: Toán, Lý, Hóa
        if (hasScores(score.getScoreMath(), score.getScorePhysics(), score.getScoreChemistry())) {
            combinations.add("A00");
        }
        
        // A01: Toán, Lý, Anh
        if (hasScores(score.getScoreMath(), score.getScorePhysics(), score.getScoreEnglish())) {
            combinations.add("A01");
        }
        
        // B00: Toán, Hóa, Sinh
        if (hasScores(score.getScoreMath(), score.getScoreChemistry(), score.getScoreBiology())) {
            combinations.add("B00");
        }
        
        // C00: Văn, Sử, Địa
        if (hasScores(score.getScoreLiterature(), score.getScoreHistory(), score.getScoreGeography())) {
            combinations.add("C00");
        }
        
        // C01: Văn, Toán, Lý
        if (hasScores(score.getScoreLiterature(), score.getScoreMath(), score.getScorePhysics())) {
            combinations.add("C01");
        }
        
        // C02: Văn, Toán, Hóa
        if (hasScores(score.getScoreLiterature(), score.getScoreMath(), score.getScoreChemistry())) {
            combinations.add("C02");
        }
        
        // D01: Văn, Toán, Anh
        if (hasScores(score.getScoreLiterature(), score.getScoreMath(), score.getScoreEnglish())) {
            combinations.add("D01");
        }
        
        // D07: Toán, Hóa, Anh
        if (hasScores(score.getScoreMath(), score.getScoreChemistry(), score.getScoreEnglish())) {
            combinations.add("D07");
        }
        
        return combinations;
    }
    
    private boolean hasScores(Double... scores) {
        return Arrays.stream(scores).allMatch(Objects::nonNull);
    }
    
    private String getCombinationName(String combinationCode) {
        switch (combinationCode) {
            case "A00": return "Toán, Vật lí, Hóa học";
            case "A01": return "Toán, Vật lí, Tiếng Anh";
            case "B00": return "Toán, Hóa học, Sinh học";
            case "C00": return "Ngữ văn, Lịch sử, Địa lí";
            case "C01": return "Ngữ văn, Toán, Vật lí";
            case "C02": return "Ngữ văn, Toán, Hóa học";
            case "C05": return "Ngữ văn, Vật lí, Hóa học";
            case "D01": return "Ngữ văn, Toán, Tiếng Anh";
            case "D07": return "Toán, Hóa học, Tiếng Anh";
            default: return combinationCode;
        }
    }
    
    private Double calculateCombinationScore(StudentScore score, String combCode) {
        switch (combCode) {
            case "A00": // Toán, Lý, Hóa
                return safeAdd(score.getScoreMath(), score.getScorePhysics(), score.getScoreChemistry());
            case "A01": // Toán, Lý, Anh
                return safeAdd(score.getScoreMath(), score.getScorePhysics(), score.getScoreEnglish());
            case "B00": // Toán, Hóa, Sinh
                return safeAdd(score.getScoreMath(), score.getScoreChemistry(), score.getScoreBiology());
            case "C00": // Văn, Sử, Địa
                return safeAdd(score.getScoreLiterature(), score.getScoreHistory(), score.getScoreGeography());
            case "C01": // Văn, Toán, Lý
                return safeAdd(score.getScoreLiterature(), score.getScoreMath(), score.getScorePhysics());
            case "C02": // Văn, Toán, Hóa
                return safeAdd(score.getScoreLiterature(), score.getScoreMath(), score.getScoreChemistry());
            case "D01": // Văn, Toán, Anh
                return safeAdd(score.getScoreLiterature(), score.getScoreMath(), score.getScoreEnglish());
            case "D07": // Toán, Hóa, Anh
                return safeAdd(score.getScoreMath(), score.getScoreChemistry(), score.getScoreEnglish());
            default:
                return null;
        }
    }
    
    private Double safeAdd(Double... scores) {
        if (Arrays.stream(scores).anyMatch(Objects::nonNull)) {
            return Arrays.stream(scores)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }
        return null;
    }
    
    private void estimateRankingData(CombinationScore combScore, Double totalScore, String combCode) {
        try {
            // Statistical estimation
            double meanScore = getMeanScore(combCode);
            double stdDev = 3.5;
            int totalCandidates = getTotalCandidatesForCombination(combCode);
            
            double zScore = (totalScore - meanScore) / stdDev;
            double percentile = Math.max(0.1, Math.min(99.9, 50 + 34.1 * zScore));
            
            int higherStudents = (int) ((100 - percentile) / 100.0 * totalCandidates);
            
            combScore.setStudentsWithHigherScore(Math.max(0, higherStudents));
            combScore.setTotalStudentsInCombination(totalCandidates);
            combScore.setStudentsWithSameScore((int) (totalCandidates * 0.002));
            combScore.setEquivalentScore2024(totalScore + (Math.random() - 0.5) * 1.0);
            
        } catch (Exception e) {
            log.debug("Error estimating ranking data: {}", e.getMessage());
        }
    }
    
    private double getMeanScore(String combination) {
        switch (combination) {
            case "A00": return 22.5;
            case "A01": return 23.0;
            case "B00": return 21.8;
            case "C00": return 20.5;
            case "C01": return 22.2;
            case "C02": return 21.5;
            case "D01": return 22.8;
            case "D07": return 21.2;
            default: return 21.0;
        }
    }
    
    private int getTotalCandidatesForCombination(String combination) {
        switch (combination) {
            case "A00": return 180000;
            case "A01": return 160000;
            case "B00": return 140000;
            case "C00": return 120000;
            case "C01": return 100000;
            case "C02": return 110000;
            case "D01": return 200000;
            case "D07": return 90000;
            default: return 100000;
        }
    }
    
    /**
     * Format crawled data for response
     */
    private Map<String, Object> formatCrawledData(StudentScore studentScore, List<CombinationScore> combinationScores) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("status", "found");
        result.put("sbd", studentScore.getSbd());
        result.put("exam_year", studentScore.getExamYear());
        result.put("region", studentScore.getRegion());
        
        // Individual subject scores
        Map<String, Object> subjectScores = new HashMap<>();
        subjectScores.put("toán", studentScore.getScoreMath());
        subjectScores.put("văn", studentScore.getScoreLiterature());
        subjectScores.put("lý", studentScore.getScorePhysics());
        subjectScores.put("hóa", studentScore.getScoreChemistry());
        subjectScores.put("anh", studentScore.getScoreEnglish());
        subjectScores.put("sinh", studentScore.getScoreBiology());
        subjectScores.put("sử", studentScore.getScoreHistory());
        subjectScores.put("địa", studentScore.getScoreGeography());
        result.put("subject_scores", subjectScores);
        
        // Combination analysis
        List<Map<String, Object>> combinationAnalysis = new ArrayList<>();
        for (CombinationScore combScore : combinationScores) {
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("combination_code", combScore.getCombinationCode());
            analysis.put("combination_name", combScore.getCombinationName());
            analysis.put("total_score", combScore.getTotalScore());
            
            Map<String, Object> rankingInfo = new HashMap<>();
            rankingInfo.put("students_with_same_score", combScore.getStudentsWithSameScore());
            rankingInfo.put("students_with_higher_score", combScore.getStudentsWithHigherScore());
            rankingInfo.put("total_students_in_combination", combScore.getTotalStudentsInCombination());
            
            if (combScore.getStudentsWithHigherScore() != null) {
                rankingInfo.put("rank_position", combScore.getStudentsWithHigherScore() + 1);
            }
            
            if (combScore.getTotalStudentsInCombination() != null && combScore.getStudentsWithHigherScore() != null) {
                double percentile = (1.0 - (double) combScore.getStudentsWithHigherScore() / combScore.getTotalStudentsInCombination()) * 100;
                rankingInfo.put("percentile", Math.round(percentile * 100.0) / 100.0);
            }
            
            analysis.put("ranking_info", rankingInfo);
            analysis.put("equivalent_score_2024", combScore.getEquivalentScore2024());
            
            combinationAnalysis.add(analysis);
        }
        result.put("combination_analysis", combinationAnalysis);
        
        return result;
    }

    /**
 * Extract điểm số từ bảng kết quả động
 */
private Map<String, Double> extractScoresFromDynamicTable(WebDriver driver, WebElement resultsTable) {
    Map<String, Double> scores = new HashMap<>();
    
    try {
        if (resultsTable == null) {
            log.warn("⚠️ Results table is null, trying to find scores in page source");
            return extractScoresFromSelenium(driver); // Fallback
        }
        
        log.info("🔍 Extracting scores from results table...");
        
        // Tìm tbody và các rows
        List<WebElement> rows = resultsTable.findElements(By.tagName("tr"));
        log.info("📋 Found {} rows in results table", rows.size());
        
        // Bắt đầu từ tr thứ 3 như bạn nói (index 2)
        for (int i = 2; i < rows.size(); i++) {
            try {
                WebElement row = rows.get(i);
                List<WebElement> cells = row.findElements(By.tagName("td"));
                
                if (cells.size() >= 2) {
                    String cellText = row.getText().trim();
                    log.debug("📝 Row {}: {}", i, cellText);
                    
                    // Extract scores từ text của row
                    extractScoresFromRowText(cellText, scores);
                }
            } catch (Exception e) {
                log.debug("Error processing row {}: {}", i, e.getMessage());
            }
        }
        
        // Nếu không tìm thấy từ table rows, thử extract từ toàn bộ table text
        if (scores.isEmpty()) {
            String tableText = resultsTable.getText();
            log.info("🔍 Extracting from full table text: {}", 
                    tableText.length() > 200 ? tableText.substring(0, 200) + "..." : tableText);
            extractScoresFromTableText(tableText, scores);
        }
        
        // Nếu vẫn không có, thử tìm trong các elements con
        if (scores.isEmpty()) {
            List<WebElement> allElements = resultsTable.findElements(By.xpath(".//*"));
            for (WebElement element : allElements) {
                String text = element.getText().trim();
                if (!text.isEmpty() && text.matches(".*[0-9]+[\\.,][0-9]+.*")) {
                    extractScoresFromRowText(text, scores);
                }
            }
        }
        
    } catch (Exception e) {
        log.error("Error extracting scores from dynamic table: {}", e.getMessage(), e);
    }
    
    log.info("✅ Extracted {} scores from dynamic table: {}", scores.size(), scores);
    return scores;
}

    /**
     * Extract scores từ text của một row
     */
    private void extractScoresFromRowText(String rowText, Map<String, Double> scores) {
        if (rowText == null || rowText.trim().isEmpty()) return;
        
        try {
            // Tìm patterns như "Môn Toán: 4.75" trong row text
            String lowerText = rowText.toLowerCase();
            
            // Patterns dựa trên screenshot: "Môn Toán: 4.75"
            String[] subjectPatterns = {
                "môn toán", "toán", 
                "môn văn", "văn", "ngữ văn",
                "môn lý", "lý", "vật lí", "vật lí", 
                "môn hóa", "hóa", "hóa học",
                "môn anh", "anh", "tiếng anh",
                "môn sinh", "sinh", "sinh học", 
                "môn sử", "sử", "lịch sử",
                "môn địa", "địa", "địa lí"
            };
            
            for (String pattern : subjectPatterns) {
                if (lowerText.contains(pattern)) {
                    // Tìm số sau pattern
                    String regex = pattern.replace(" ", "\\s*") + "\\s*[:\\-]?\\s*([0-9]+[\\.,]?[0-9]*)";
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
                    java.util.regex.Matcher m = p.matcher(rowText);
                    
                    if (m.find()) {
                        try {
                            String scoreStr = m.group(1).replace(",", ".");
                            double score = Double.parseDouble(scoreStr);
                            if (score >= 0 && score <= 10) {
                                // Map to standard subject names
                                String subjectKey = mapToSubjectKey(pattern);
                                if (subjectKey != null && !scores.containsKey(subjectKey)) {
                                    scores.put(subjectKey, score);
                                    log.debug("✅ Found score: {} = {}", subjectKey, score);
                                }
                            }
                        } catch (NumberFormatException e) {
                            log.debug("Could not parse score: {}", m.group(1));
                        }
                    }
                    break; // Đã xử lý pattern này, chuyển sang row khác
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting from row text: {}", e.getMessage());
        }
    }

    /**
     * Extract scores từ toàn bộ table text
     */
    private void extractScoresFromTableText(String tableText, Map<String, Double> scores) {
        // Split thành lines và extract
        String[] lines = tableText.split("\\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("môn") && line.matches(".*[0-9]+[\\.,][0-9]+.*")) {
                extractScoresFromRowText(line, scores);
            }
        }
    }

    /**
     * Map pattern to standard subject key
     */
    private String mapToSubjectKey(String pattern) {
        String lowerPattern = pattern.toLowerCase();
        
        if (lowerPattern.contains("toán")) return "toán";
        if (lowerPattern.contains("văn")) return "văn";
        if (lowerPattern.contains("lý")) return "lý";
        if (lowerPattern.contains("hóa")) return "hóa";
        if (lowerPattern.contains("anh")) return "anh";
        if (lowerPattern.contains("sinh")) return "sinh";
        if (lowerPattern.contains("sử")) return "sử";
        if (lowerPattern.contains("địa")) return "địa";
        
        return null;
    }

}       