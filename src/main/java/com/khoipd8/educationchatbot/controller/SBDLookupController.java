package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.service.SBDLookupService;
import com.khoipd8.educationchatbot.repository.StudentScoreRepository;
import com.khoipd8.educationchatbot.repository.CombinationScoreRepository;
import com.khoipd8.educationchatbot.entity.CombinationScore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}