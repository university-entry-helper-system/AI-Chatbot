package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.entity.ScoreRanking;
import com.khoipd8.educationchatbot.repository.ScoreRankingRepository;
import com.khoipd8.educationchatbot.service.ScoreRankingCrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rankings")
@Slf4j
public class ScoreRankingController {
    
    @Autowired
    private ScoreRankingCrawlerService crawlerService;
    
    @Autowired
    private ScoreRankingRepository scoreRankingRepository;
    
    /**
     * 🕷️ CRAWL SCORE RANKINGS
     * Operation: Crawl xếp hạng điểm thi cho năm cụ thể
     * Purpose: Thu thập dữ liệu xếp hạng từ tuyensinh247.com
     */
    @PostMapping("/crawl/{year}")
    public ResponseEntity<Map<String, Object>> crawlRankings(@PathVariable Integer year) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("Starting ranking crawl for year {}", year);
            
            CompletableFuture<Map<String, Object>> future = crawlerService.crawlScoreRankings(year);
            
            response.put("status", "started");
            response.put("operation", "crawl_rankings");
            response.put("year", year);
            response.put("message", "Score ranking crawl started for year " + year);
            response.put("estimated_time", "10-15 minutes");
            
        } catch (Exception e) {
            log.error("Error starting ranking crawl", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🔍 QUERY SCORE RANKING
     * Operation: Tra cứu xếp hạng theo điểm số
     * Purpose: Tìm vị trí xếp hạng của điểm số cụ thể
     */
    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupRanking(
            @RequestParam Integer year,
            @RequestParam String combination,
            @RequestParam Double score,
            @RequestParam(defaultValue = "Cả nước") String region) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<ScoreRanking> rankings = scoreRankingRepository
                    .findByExamYearAndSubjectCombinationAndRegion(year, combination, region);
            
            if (rankings.isEmpty()) {
                response.put("status", "no_data");
                response.put("message", "No ranking data found for the specified criteria");
                response.put("suggestion", "Try crawling data first using /crawl/" + year);
                return ResponseEntity.ok(response);
            }
            
            // Find closest ranking
            Optional<ScoreRanking> closestRanking = rankings.stream()
                    .min(Comparator.comparingDouble(r -> Math.abs(r.getTotalScore() - score)));
            
            if (closestRanking.isPresent()) {
                ScoreRanking ranking = closestRanking.get();
                
                // Calculate additional stats
                long betterScores = rankings.stream()
                        .filter(r -> r.getTotalScore() > score)
                        .count();
                
                long equalScores = rankings.stream()
                        .filter(r -> Math.abs(r.getTotalScore() - score) < 0.01)
                        .count();
                
                response.put("status", "found");
                response.put("ranking_info", Map.of(
                    "score", score,
                    "approximate_ranking", ranking.getRankingPosition(),
                    "percentile", ranking.getPercentile(),
                    "total_candidates", ranking.getTotalCandidates(),
                    "candidates_with_better_scores", betterScores,
                    "candidates_with_equal_scores", equalScores
                ));
                response.put("query", Map.of(
                    "year", year,
                    "combination", combination,
                    "region", region
                ));
                
            } else {
                response.put("status", "not_found");
                response.put("message", "Could not find ranking for the specified score");
            }
            
        } catch (Exception e) {
            log.error("Error looking up ranking", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 📊 RANKING STATISTICS
     * Operation: Thống kê xếp hạng theo năm/tổ hợp
     * Purpose: Cung cấp thống kê tổng quan về dữ liệu xếp hạng
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getRankingStatistics(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String combination) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<ScoreRanking> rankings;
            
            if (year != null && combination != null) {
                rankings = scoreRankingRepository.findByExamYearAndSubjectCombination(year, combination);
            } else if (year != null) {
                rankings = scoreRankingRepository.findAll().stream()
                        .filter(r -> r.getExamYear().equals(year))
                        .collect(Collectors.toList());
            } else {
                rankings = scoreRankingRepository.findAll();
            }
            
            if (rankings.isEmpty()) {
                response.put("status", "no_data");
                response.put("message", "No ranking data available");
                return ResponseEntity.ok(response);
            }
            
            // Calculate statistics
            DoubleSummaryStatistics scoreStats = rankings.stream()
                    .mapToDouble(ScoreRanking::getTotalScore)
                    .summaryStatistics();
            
            Map<String, Long> combinationCounts = rankings.stream()
                    .collect(Collectors.groupingBy(
                        ScoreRanking::getSubjectCombination,
                        Collectors.counting()
                    ));
            
            Map<String, Long> regionCounts = rankings.stream()
                    .collect(Collectors.groupingBy(
                        ScoreRanking::getRegion,
                        Collectors.counting()
                    ));
            
            response.put("status", "success");
            response.put("statistics", Map.of(
                "total_records", rankings.size(),
                "score_statistics", Map.of(
                    "min_score", scoreStats.getMin(),
                    "max_score", scoreStats.getMax(),
                    "average_score", Math.round(scoreStats.getAverage() * 100.0) / 100.0
                ),
                "combination_distribution", combinationCounts,
                "region_distribution", regionCounts,
                "available_years", rankings.stream()
                        .map(ScoreRanking::getExamYear)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList())
            ));
            
        } catch (Exception e) {
            log.error("Error getting ranking statistics", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🎯 SCORE PREDICTION
     * Operation: Dự đoán khả năng trúng tuyển
     * Purpose: Tư vấn khả năng đỗ các trường dựa trên xếp hạng
     */
    @GetMapping("/predict-admission")
    public ResponseEntity<Map<String, Object>> predictAdmission(
            @RequestParam Integer year,
            @RequestParam String combination,
            @RequestParam Double score,
            @RequestParam(defaultValue = "Cả nước") String region) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get ranking for the score
            List<ScoreRanking> rankings = scoreRankingRepository
                    .findCandidatesWithLowerScores(year, combination, score);
            
            if (rankings.isEmpty()) {
                response.put("status", "no_data");
                response.put("message", "Insufficient data for prediction");
                return ResponseEntity.ok(response);
            }
            
            ScoreRanking userRanking = rankings.get(0); // Closest match
            
            // Admission probability categories
            List<Map<String, Object>> predictions = new ArrayList<>();
            
            if (userRanking.getPercentile() >= 95) {
                predictions.add(Map.of(
                    "category", "Rất cao",
                    "probability", "90-100%",
                    "description", "Có khả năng trúng tuyển hầu hết các trường top",
                    "advice", "Có thể chọn những ngành/trường có điểm chuẩn cao nhất"
                ));
            } else if (userRanking.getPercentile() >= 80) {
                predictions.add(Map.of(
                    "category", "Cao",
                    "probability", "70-90%",
                    "description", "Có khả năng trúng tuyển các trường tốt",
                    "advice", "Nên cân nhắc mix các ngành ở trường top và trường an toàn"
                ));
            } else if (userRanking.getPercentile() >= 50) {
                predictions.add(Map.of(
                    "category", "Trung bình",
                    "probability", "40-70%",
                    "description", "Có cơ hội trúng tuyển các trường trung bình khá",
                    "advice", "Nên đăng ký đa dạng các mức điểm chuẩn"
                ));
            } else {
                predictions.add(Map.of(
                    "category", "Thấp",
                    "probability", "10-40%",
                    "description", "Nên cân nhắc các trường có điểm chuẩn thấp hơn",
                    "advice", "Tập trung vào các trường với điểm chuẩn phù hợp"
                ));
            }
            
            response.put("status", "success");
            response.put("prediction", Map.of(
                "user_score", score,
                "ranking_position", userRanking.getRankingPosition(),
                "percentile", userRanking.getPercentile(),
                "total_candidates", userRanking.getTotalCandidates(),
                "predictions", predictions
            ));
            
        } catch (Exception e) {
            log.error("Error predicting admission", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}
