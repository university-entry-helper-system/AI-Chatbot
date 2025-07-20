package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.dto.UniversityInfo;
import com.khoipd8.educationchatbot.entity.Program;
import com.khoipd8.educationchatbot.entity.University;
import com.khoipd8.educationchatbot.repository.UniversityRepository;
import com.khoipd8.educationchatbot.service.TuyenSinh247CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/crawler")
@Tag(name = "Crawler Management", description = "API quản lý crawling dữ liệu từ tuyensinh247.com")
@Slf4j
public class CrawlerController {
    
    @Autowired
    private TuyenSinh247CrawlerService crawlerService;
    
    @Autowired
    private UniversityRepository universityRepository;
    
    // Global crawl status tracking
    private volatile boolean crawlInProgress = false;
    private CompletableFuture<Map<String, Object>> currentCrawlFuture = null;
    private Map<String, Object> lastCrawlResult = new HashMap<>();
    
    /**
     * 🔍 DISCOVER UNIVERSITIES
     * Operation: Tìm tất cả các trường đại học có trên website tuyensinh247.com
     * Purpose: Khám phá danh sách các trường để có thể crawl
     * No database operation: Chỉ scan website
     */
    @GetMapping("/discover")
    @Operation(summary = "Khám phá đại học", 
               description = "Tìm tất cả đại học có trên tuyensinh247.com và kiểm tra trạng thái trong database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Khám phá thành công"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<Map<String, Object>> discoverUniversities() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("🔍 Starting university discovery operation...");
            
            List<UniversityInfo> universities = crawlerService.discoverUniversities();
            
            // Check which universities already exist in database
            Map<String, Boolean> existenceCheck = new HashMap<>();
            for (UniversityInfo info : universities) {
                boolean exists = universityRepository.findByCode(info.getCode()).isPresent();
                existenceCheck.put(info.getCode(), exists);
            }
            
            long existingCount = existenceCheck.values().stream().mapToLong(exists -> exists ? 1 : 0).sum();
            long newCount = universities.size() - existingCount;
            
            response.put("status", "success");
            response.put("operation", "discover");
            response.put("discovered_count", universities.size());
            response.put("already_in_db", existingCount);
            response.put("new_universities", newCount);
            response.put("universities", universities);
            response.put("existence_check", existenceCheck);
            response.put("message", String.format("Discovered %d universities (%d new, %d already in database)", 
                    universities.size(), newCount, existingCount));
            
            log.info("✅ Discovery completed: {} total, {} new, {} existing", 
                    universities.size(), newCount, existingCount);
            
        } catch (Exception e) {
            log.error("❌ Discovery failed", e);
            response.put("status", "error");
            response.put("operation", "discover");
            response.put("message", e.getMessage());
            response.put("error_type", e.getClass().getSimpleName());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🎯 CRAWL SINGLE UNIVERSITY
     * Operation: Crawl chi tiết một trường cụ thể theo mã trường
     * Logic: Nếu trường đã tồn tại → UPDATE, nếu chưa có → CREATE
     * Purpose: Test crawling hoặc cập nhật dữ liệu một trường cụ thể
     */
    @PostMapping("/crawl-single/{code}")
    @Operation(summary = "Crawl một đại học", 
               description = "Crawl dữ liệu một đại học cụ thể theo mã trường")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Crawl thành công"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<Map<String, Object>> crawlSingleUniversity(
            @Parameter(description = "Mã đại học (3 ký tự)", example = "QSB", required = true)
            @PathVariable String code) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String upperCode = code.toUpperCase();
            log.info("🎯 Starting single university crawl for: {}", upperCode);
            
            // Check if university exists before crawling
            Optional<University> existingUniversity = universityRepository.findByCode(upperCode);
            boolean isUpdate = existingUniversity.isPresent();
            
            // Crawl fresh data from website
            University crawledUniversity = crawlerService.crawlSingleUniversity(upperCode);
            
            University saved;
            String action;
            int oldProgramCount = 0;
            
            if (isUpdate) {
                // UPDATE existing university
                University existing = existingUniversity.get();
                oldProgramCount = existing.getPrograms().size();
                
                updateExistingUniversityData(existing, crawledUniversity);
                saved = universityRepository.save(existing);
                action = "updated";
                
                log.info("🔄 Updated existing university: {} (Programs: {} → {})", 
                        saved.getName(), oldProgramCount, saved.getPrograms().size());
            } else {
                // CREATE new university
                saved = universityRepository.save(crawledUniversity);
                action = "created";
                
                log.info("🆕 Created new university: {} ({} programs)", 
                        saved.getName(), saved.getPrograms().size());
            }
            
            response.put("status", "success");
            response.put("operation", "crawl_single");
            response.put("action", action);
            response.put("is_update", isUpdate);
            response.put("message", String.format("Successfully %s university: %s", action, saved.getName()));
            response.put("university", Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "code", saved.getCode(),
                "location", saved.getLocation() != null ? saved.getLocation() : "N/A",
                "programs_count", saved.getPrograms().size(),
                "old_programs_count", oldProgramCount,
                "programs_change", saved.getPrograms().size() - oldProgramCount,
                "last_updated", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("❌ Single crawl failed for: {}", code, e);
            response.put("status", "error");
            response.put("operation", "crawl_single");
            response.put("message", e.getMessage());
            response.put("university_code", code);
            response.put("error_type", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🚀 CRAWL ALL UNIVERSITIES
     * Operation: Crawl tất cả các trường từ website
     * Logic: Smart update - chỉ cập nhật trường có thay đổi, tạo mới nếu chưa có
     * Purpose: Đồng bộ toàn bộ dữ liệu từ website
     * Mode: Asynchronous - chạy background
     */
    @PostMapping("/crawl-all")
    @Operation(summary = "Crawl tất cả đại học", 
               description = "Crawl dữ liệu tất cả đại học từ tuyensinh247.com. Có thể chọn chế độ force update hoặc skip existing.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bắt đầu crawl thành công"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<Map<String, Object>> startFullCrawl(
            @Parameter(description = "Force update tất cả đại học (kể cả đã có)", example = "false")
            @RequestParam(defaultValue = "false") boolean forceUpdate,
            @Parameter(description = "Bỏ qua đại học đã tồn tại (chỉ crawl mới)", example = "false")
            @RequestParam(defaultValue = "false") boolean skipExisting) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (crawlInProgress) {
            response.put("status", "already_running");
            response.put("operation", "crawl_all");
            response.put("message", "Crawl process is already running. Please wait for completion or stop it first.");
            response.put("current_progress", getCurrentProgress());
            return ResponseEntity.ok(response);
        }
        
        try {
            crawlInProgress = true;
            log.info("🚀 Starting FULL CRAWL operation (forceUpdate: {}, skipExisting: {})", 
                    forceUpdate, skipExisting);
            
            currentCrawlFuture = crawlAllUniversitiesAsync(forceUpdate, skipExisting);
            
            // Setup completion handler
            currentCrawlFuture.whenComplete((result, throwable) -> {
                crawlInProgress = false;
                if (throwable != null) {
                    log.error("❌ Full crawl failed", throwable);
                    lastCrawlResult.put("status", "failed");
                    lastCrawlResult.put("error", throwable.getMessage());
                } else {
                    log.info("✅ Full crawl completed: {}", result);
                    lastCrawlResult = result;
                }
                lastCrawlResult.put("completed_at", System.currentTimeMillis());
            });
            
            response.put("status", "started");
            response.put("operation", "crawl_all");
            response.put("message", "Full crawl process started asynchronously");
            response.put("options", Map.of(
                "force_update", forceUpdate,
                "skip_existing", skipExisting
            ));
            response.put("started_at", System.currentTimeMillis());
            response.put("estimated_time_minutes", "15-30");
            
        } catch (Exception e) {
            crawlInProgress = false;
            log.error("❌ Failed to start full crawl", e);
            response.put("status", "error");
            response.put("operation", "crawl_all");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 📊 GET CRAWL STATUS
     * Operation: Kiểm tra trạng thái crawling và thống kê database
     * Purpose: Monitor tiến độ crawl và xem tổng quan dữ liệu
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCrawlStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Current crawl status
            status.put("operation", "status_check");
            status.put("crawl_in_progress", crawlInProgress);
            status.put("timestamp", System.currentTimeMillis());
            
            if (crawlInProgress && currentCrawlFuture != null) {
                status.put("crawl_status", "running");
                status.put("message", "Crawl is currently in progress...");
                status.put("progress", getCurrentProgress());
            } else if (!lastCrawlResult.isEmpty()) {
                status.put("crawl_status", "idle");
                status.put("last_crawl_result", lastCrawlResult);
                
                if (lastCrawlResult.containsKey("status") && "completed".equals(lastCrawlResult.get("status"))) {
                    status.put("message", "Last crawl completed successfully");
                } else {
                    status.put("message", "Last crawl failed or was interrupted");
                }
            } else {
                status.put("crawl_status", "never_run");
                status.put("message", "No crawl has been executed yet");
            }
            
            // Database statistics
            long totalUniversities = universityRepository.count();
            long totalPrograms = universityRepository.findAll().stream()
                    .mapToLong(u -> u.getPrograms().size())
                    .sum();
            
            // Get recent updates (universities updated in last 24 hours)
            long recentUpdates = universityRepository.findAll().stream()
                    .filter(u -> u.getUpdatedAt() != null)
                    .filter(u -> u.getUpdatedAt().isAfter(
                            java.time.LocalDateTime.now().minusHours(24)))
                    .count();
            
            status.put("database_stats", Map.of(
                "total_universities", totalUniversities,
                "total_programs", totalPrograms,
                "recent_updates_24h", recentUpdates,
                "avg_programs_per_university", totalUniversities > 0 ? 
                        Math.round((double) totalPrograms / totalUniversities * 100.0) / 100.0 : 0
            ));
            
        } catch (Exception e) {
            log.error("❌ Error getting crawl status", e);
            status.put("status", "error");
            status.put("operation", "status_check");
            status.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * 🔄 CRAWL BATCH UNIVERSITIES
     * Operation: Crawl một nhóm trường cụ thể theo danh sách mã trường
     * Logic: Update nếu có, tạo mới nếu chưa có
     * Purpose: Crawl selective cho testing hoặc cập nhật nhóm trường cụ thể
     */
    @PostMapping("/crawl-batch")
    public ResponseEntity<Map<String, Object>> crawlBatchUniversities(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<String> codes = (List<String>) request.get("codes");
            boolean forceUpdate = Boolean.TRUE.equals(request.get("force_update"));
            
            if (codes == null || codes.isEmpty()) {
                response.put("status", "error");
                response.put("operation", "crawl_batch");
                response.put("message", "Please provide a list of university codes in 'codes' field");
                return ResponseEntity.badRequest().body(response);
            }
            
            log.info("🔄 Starting BATCH CRAWL for {} universities (forceUpdate: {})", 
                    codes.size(), forceUpdate);
            
            AtomicInteger successful = new AtomicInteger(0);
            AtomicInteger updated = new AtomicInteger(0);
            AtomicInteger created = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            AtomicInteger skipped = new AtomicInteger(0);
            
            List<Map<String, Object>> results = new ArrayList<>();
            
            for (String code : codes) {
                try {
                    String upperCode = code.toUpperCase();
                    
                    // Check if exists
                    Optional<University> existing = universityRepository.findByCode(upperCode);
                    boolean shouldSkip = existing.isPresent() && !forceUpdate;
                    
                    if (shouldSkip) {
                        skipped.incrementAndGet();
                        results.add(Map.of(
                            "code", upperCode,
                            "status", "skipped",
                            "reason", "Already exists and force_update=false"
                        ));
                        continue;
                    }
                    
                    // Crawl university
                    University crawledUniversity = crawlerService.crawlSingleUniversity(upperCode);
                    
                    if (existing.isPresent()) {
                        // Update
                        University existingU = existing.get();
                        updateExistingUniversityData(existingU, crawledUniversity);
                        universityRepository.save(existingU);
                        updated.incrementAndGet();
                        
                        results.add(Map.of(
                            "code", upperCode,
                            "status", "updated",
                            "name", existingU.getName(),
                            "programs_count", existingU.getPrograms().size()
                        ));
                    } else {
                        // Create
                        University saved = universityRepository.save(crawledUniversity);
                        created.incrementAndGet();
                        
                        results.add(Map.of(
                            "code", upperCode,
                            "status", "created",
                            "name", saved.getName(),
                            "programs_count", saved.getPrograms().size()
                        ));
                    }
                    
                    successful.incrementAndGet();
                    
                    // Rate limiting
                    Thread.sleep(1500);
                    
                } catch (Exception e) {
                    log.error("❌ Failed to crawl university: {}", code, e);
                    failed.incrementAndGet();
                    
                    results.add(Map.of(
                        "code", code.toUpperCase(),
                        "status", "failed",
                        "error", e.getMessage()
                    ));
                }
            }
            
            response.put("status", "completed");
            response.put("operation", "crawl_batch");
            response.put("summary", Map.of(
                "total_requested", codes.size(),
                "successful", successful.get(),
                "created", created.get(),
                "updated", updated.get(),
                "skipped", skipped.get(),
                "failed", failed.get()
            ));
            response.put("options", Map.of("force_update", forceUpdate));
            response.put("results", results);
            response.put("message", String.format(
                "Batch crawl completed: %d successful (%d created, %d updated), %d skipped, %d failed", 
                successful.get(), created.get(), updated.get(), skipped.get(), failed.get()));
            
            log.info("✅ Batch crawl completed: {} successful, {} failed", 
                    successful.get(), failed.get());
            
        } catch (Exception e) {
            log.error("❌ Batch crawl error", e);
            response.put("status", "error");
            response.put("operation", "crawl_batch");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * ⏹️ STOP CRAWL
     * Operation: Dừng tiến trình crawl đang chạy
     * Purpose: Emergency stop hoặc cancel crawl
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopCrawl() {
        Map<String, Object> response = new HashMap<>();
        
        if (!crawlInProgress) {
            response.put("status", "not_running");
            response.put("operation", "stop_crawl");
            response.put("message", "No crawl process is currently running");
            return ResponseEntity.ok(response);
        }
        
        try {
            log.info("⏹️ Stopping crawl process...");
            
            if (currentCrawlFuture != null) {
                boolean cancelled = currentCrawlFuture.cancel(true);
                response.put("cancelled", cancelled);
            }
            
            crawlInProgress = false;
            
            response.put("status", "stopped");
            response.put("operation", "stop_crawl");
            response.put("message", "Crawl process has been stopped");
            response.put("stopped_at", System.currentTimeMillis());
            
            log.info("✅ Crawl process stopped");
            
        } catch (Exception e) {
            log.error("❌ Error stopping crawl", e);
            response.put("status", "error");
            response.put("operation", "stop_crawl");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🔗 TEST CONNECTION
     * Operation: Test kết nối đến website tuyensinh247.com
     * Purpose: Kiểm tra tính khả dụng của nguồn dữ liệu
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("🔗 Testing connection to tuyensinh247.com...");
            
            long startTime = System.currentTimeMillis();
            List<UniversityInfo> sample = crawlerService.discoverUniversities();
            long duration = System.currentTimeMillis() - startTime;
            
            response.put("status", "success");
            response.put("operation", "test_connection");
            response.put("message", "Connection to tuyensinh247.com is working perfectly");
            response.put("response_time_ms", duration);
            response.put("discovered_universities", sample.size());
            response.put("sample_universities", sample.stream().limit(3).toArray());
            response.put("test_timestamp", System.currentTimeMillis());
            
            log.info("✅ Connection test successful: {} universities found in {}ms", 
                    sample.size(), duration);
            
        } catch (Exception e) {
            log.error("❌ Connection test failed", e);
            response.put("status", "failed");
            response.put("operation", "test_connection");
            response.put("message", "Failed to connect to tuyensinh247.com: " + e.getMessage());
            response.put("error_type", e.getClass().getSimpleName());
            
            // Provide troubleshooting info
            response.put("troubleshooting", Map.of(
                "check_internet", "Verify your internet connection",
                "check_website", "Visit https://diemthi.tuyensinh247.com manually",
                "check_firewall", "Ensure firewall allows outbound connections"
            ));
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 📋 GET SAMPLE UNIVERSITIES
     * Operation: Lấy danh sách mẫu các trường để test crawl
     * Purpose: Cung cấp sample data cho testing
     */
    @GetMapping("/sample-universities")
    public ResponseEntity<Map<String, Object>> getSampleUniversities(
            @RequestParam(defaultValue = "10") int limit) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("📋 Getting sample universities (limit: {})...", limit);
            
            List<UniversityInfo> universities = crawlerService.discoverUniversities();
            
            // Get sample with mix of existing and new
            List<UniversityInfo> sample = universities.stream().limit(limit).toList();
            
            // Check which ones exist in database
            List<Map<String, Object>> sampleWithStatus = new ArrayList<>();
            for (UniversityInfo info : sample) {
                boolean exists = universityRepository.findByCode(info.getCode()).isPresent();
                Map<String, Object> item = new HashMap<>();
                item.put("code", info.getCode());
                item.put("name", info.getName());
                item.put("url", info.getUrl());
                item.put("exists_in_db", exists);
                item.put("action_needed", exists ? "update" : "create");
                sampleWithStatus.add(item);
            }
            
            long existingCount = sampleWithStatus.stream()
                    .mapToLong(item -> (Boolean) item.get("exists_in_db") ? 1 : 0)
                    .sum();
            
            response.put("status", "success");
            response.put("operation", "get_samples");
            response.put("total_available", universities.size());
            response.put("sample_size", sample.size());
            response.put("existing_in_db", existingCount);
            response.put("new_universities", sample.size() - existingCount);
            response.put("sample_universities", sampleWithStatus);
            response.put("message", String.format("Sample of %d universities (%d existing, %d new)", 
                    sample.size(), existingCount, sample.size() - existingCount));
            
            // Suggest batch crawl command
            List<String> codes = sample.stream().map(UniversityInfo::getCode).toList();
            response.put("suggested_batch_crawl", Map.of(
                "codes", codes,
                "force_update", false
            ));
            
        } catch (Exception e) {
            log.error("❌ Error getting sample universities", e);
            response.put("status", "error");
            response.put("operation", "get_samples");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    // ====== HELPER METHODS ======
    
    private CompletableFuture<Map<String, Object>> crawlAllUniversitiesAsync(boolean forceUpdate, boolean skipExisting) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                log.info("🚀 Starting async full crawl...");
                
                // Discover all universities
                List<UniversityInfo> universityInfos = crawlerService.discoverUniversities();
                log.info("📋 Discovered {} universities to process", universityInfos.size());
                
                AtomicInteger processed = new AtomicInteger(0);
                AtomicInteger created = new AtomicInteger(0);
                AtomicInteger updated = new AtomicInteger(0);
                AtomicInteger skipped = new AtomicInteger(0);
                AtomicInteger errors = new AtomicInteger(0);
                
                for (UniversityInfo info : universityInfos) {
                    try {
                        Optional<University> existing = universityRepository.findByCode(info.getCode());
                        boolean exists = existing.isPresent();
                        
                        // Skip logic
                        if (exists && skipExisting && !forceUpdate) {
                            skipped.incrementAndGet();
                            continue;
                        }
                        
                        // Crawl university data
                        University crawledUniversity = crawlerService.crawlUniversityDetails(info);
                        
                        if (exists) {
                            // Update existing
                            University existingU = existing.get();
                            updateExistingUniversityData(existingU, crawledUniversity);
                            universityRepository.save(existingU);
                            updated.incrementAndGet();
                        } else {
                            // Create new
                            universityRepository.save(crawledUniversity);
                            created.incrementAndGet();
                        }
                        
                        processed.incrementAndGet();
                        
                        // Progress logging
                        if (processed.get() % 10 == 0) {
                            log.info("📊 Progress: {}/{} processed (Created: {}, Updated: {}, Skipped: {}, Errors: {})", 
                                    processed.get(), universityInfos.size(), 
                                    created.get(), updated.get(), skipped.get(), errors.get());
                        }
                        
                        // Rate limiting
                        Thread.sleep(1000 + (long)(Math.random() * 1000));
                        
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        log.error("❌ Error processing university {}: {}", info.getName(), e.getMessage());
                        
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
                result.put("operation", "crawl_all");
                result.put("total_discovered", universityInfos.size());
                result.put("processed", processed.get());
                result.put("created", created.get());
                result.put("updated", updated.get());
                result.put("skipped", skipped.get());
                result.put("errors", errors.get());
                result.put("success_rate", processed.get() > 0 ? 
                        Math.round((double)(processed.get() - errors.get()) / processed.get() * 100.0) : 0);
                
                log.info("✅ Full crawl completed: {} processed, {} created, {} updated, {} skipped, {} errors", 
                        processed.get(), created.get(), updated.get(), skipped.get(), errors.get());
                
            } catch (Exception e) {
                log.error("❌ Fatal error in full crawl", e);
                result.put("status", "failed");
                result.put("operation", "crawl_all");
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    private void updateExistingUniversityData(University existing, University newData) {
        // Update university basic info
        existing.setName(newData.getName());
        existing.setFullName(newData.getFullName());
        
        if (newData.getLocation() != null && !newData.getLocation().equals("Chưa xác định")) {
            existing.setLocation(newData.getLocation());
        }
        if (newData.getDescription() != null) {
            existing.setDescription(newData.getDescription());
        }
        if (newData.getWebsite() != null) {
            existing.setWebsite(newData.getWebsite());
        }
        if (newData.getType() != null) {
            existing.setType(newData.getType());
        }
        
        // Smart program update: merge instead of replace
        Map<String, Program> existingProgramsMap = new HashMap<>();
        for (Program p : existing.getPrograms()) {
            String key = p.getName() + "|" + (p.getNote() != null ? p.getNote() : "");
            existingProgramsMap.put(key, p);
        }
        
        // Clear and rebuild program list
        existing.getPrograms().clear();
        
        for (Program newProgram : newData.getPrograms()) {
            String key = newProgram.getName() + "|" + (newProgram.getNote() != null ? newProgram.getNote() : "");
            Program existingProgram = existingProgramsMap.get(key);
            
            if (existingProgram != null) {
                // Update existing program
                if (newProgram.getBenchmarkScore2024() != null) {
                    existingProgram.setBenchmarkScore2024(newProgram.getBenchmarkScore2024());
                }
                if (newProgram.getSubjectCombination() != null) {
                    existingProgram.setSubjectCombination(newProgram.getSubjectCombination());
                }
                if (newProgram.getAdmissionMethod() != null) {
                    existingProgram.setAdmissionMethod(newProgram.getAdmissionMethod());
                }
                existing.getPrograms().add(existingProgram);
            } else {
                // Add new program
                newProgram.setUniversity(existing);
                existing.getPrograms().add(newProgram);
            }
        }
    }
    
    private Map<String, Object> getCurrentProgress() {
        // This could be enhanced to track actual progress
        return Map.of(
            "status", crawlInProgress ? "running" : "idle",
            "message", crawlInProgress ? "Crawl in progress..." : "No active crawl"
        );
    }
}       