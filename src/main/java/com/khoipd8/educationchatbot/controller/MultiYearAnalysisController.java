package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.entity.Program;
import com.khoipd8.educationchatbot.entity.University;
import com.khoipd8.educationchatbot.repository.ProgramRepository;
import com.khoipd8.educationchatbot.repository.UniversityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
@Slf4j
public class MultiYearAnalysisController {
    
    @Autowired
    private UniversityRepository universityRepository;
    
    @Autowired
    private ProgramRepository programRepository;
    
    /**
     * 📊 MULTI-YEAR DATA COVERAGE ANALYSIS
     * Operation: Phân tích coverage dữ liệu theo năm
     * Purpose: Kiểm tra chất lượng data crawling multi-year
     */
    @GetMapping("/year-coverage")
    public ResponseEntity<Map<String, Object>> analyzeYearCoverage() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Program> allPrograms = programRepository.findAll();
            
            long total = allPrograms.size();
            long with2024 = allPrograms.stream().filter(p -> p.getBenchmarkScore2024() != null).count();
            long with2023 = allPrograms.stream().filter(p -> p.getBenchmarkScore2023() != null).count();
            long with2022 = allPrograms.stream().filter(p -> p.getBenchmarkScore2022() != null).count();
            
            // Programs with multiple years data
            long multiYear = allPrograms.stream().filter(p -> 
                (p.getBenchmarkScore2024() != null ? 1 : 0) +
                (p.getBenchmarkScore2023() != null ? 1 : 0) +
                (p.getBenchmarkScore2022() != null ? 1 : 0) > 1
            ).count();
            
            // Complete data (all 3 years)
            long complete = allPrograms.stream().filter(p -> 
                p.getBenchmarkScore2024() != null &&
                p.getBenchmarkScore2023() != null &&
                p.getBenchmarkScore2022() != null
            ).count();
            
            Map<String, Object> coverage = new HashMap<>();
            coverage.put("total_programs", total);
            coverage.put("year_2024", Map.of("count", with2024, "percentage", total > 0 ? Math.round((double) with2024 / total * 100) : 0));
            coverage.put("year_2023", Map.of("count", with2023, "percentage", total > 0 ? Math.round((double) with2023 / total * 100) : 0));
            coverage.put("year_2022", Map.of("count", with2022, "percentage", total > 0 ? Math.round((double) with2022 / total * 100) : 0));
            coverage.put("multi_year_data", Map.of("count", multiYear, "percentage", total > 0 ? Math.round((double) multiYear / total * 100) : 0));
            coverage.put("complete_data_3_years", Map.of("count", complete, "percentage", total > 0 ? Math.round((double) complete / total * 100) : 0));
            
            response.put("status", "success");
            response.put("operation", "year_coverage_analysis");
            response.put("coverage_stats", coverage);
            response.put("data_quality", Map.of(
                "excellent", complete > total * 0.7 ? "Most programs have complete 3-year data" : "",
                "good", multiYear > total * 0.5 ? "Good multi-year coverage" : "",
                "needs_improvement", complete < total * 0.3 ? "Many programs missing historical data" : ""
            ));
            
        } catch (Exception e) {
            log.error("Error analyzing year coverage", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 📈 SCORE TRENDS ANALYSIS
     * Operation: Phân tích xu hướng điểm chuẩn theo năm
     * Purpose: Tìm trend tăng/giảm điểm chuẩn
     */
    @GetMapping("/score-trends")
    public ResponseEntity<Map<String, Object>> analyzeScoreTrends(
            @RequestParam(required = false) String universityCode,
            @RequestParam(required = false) String programName,
            @RequestParam(defaultValue = "10") int limit) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Program> programs;
            
            if (universityCode != null) {
                Optional<University> university = universityRepository.findByCode(universityCode.toUpperCase());
                programs = university.map(u -> u.getPrograms()).orElse(new ArrayList<>());
            } else if (programName != null) {
                programs = programRepository.findByNameContainingIgnoreCase(programName);
            } else {
                programs = programRepository.findAll();
            }
            
            // Filter programs with multi-year data
            List<Program> multiYearPrograms = programs.stream()
                    .filter(p -> (p.getBenchmarkScore2024() != null ? 1 : 0) +
                                (p.getBenchmarkScore2023() != null ? 1 : 0) +
                                (p.getBenchmarkScore2022() != null ? 1 : 0) >= 2)
                    .limit(limit)
                    .collect(Collectors.toList());
            
            List<Map<String, Object>> trendAnalysis = new ArrayList<>();
            
            for (Program program : multiYearPrograms) {
                Map<String, Object> trend = analyzeProgramTrend(program);
                if (trend != null) {
                    trendAnalysis.add(trend);
                }
            }
            
            // Sort by trend strength
            trendAnalysis.sort((a, b) -> {
                Double trendA = (Double) a.get("trend_percentage");
                Double trendB = (Double) b.get("trend_percentage");
                return Double.compare(Math.abs(trendB != null ? trendB : 0), Math.abs(trendA != null ? trendA : 0));
            });
            
            response.put("status", "success");
            response.put("operation", "score_trends_analysis");
            response.put("filters", Map.of(
                "university_code", universityCode != null ? universityCode : "all",
                "program_name", programName != null ? programName : "all",
                "limit", limit
            ));
            response.put("analyzed_programs", trendAnalysis.size());
            response.put("trends", trendAnalysis);
            
        } catch (Exception e) {
            log.error("Error analyzing score trends", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🎯 PROGRAM MULTI-YEAR DETAIL
     * Operation: Xem chi tiết dữ liệu multi-year của một ngành cụ thể
     * Purpose: Deep dive vào data quality của ngành
     */
    @GetMapping("/program-detail/{universityCode}/{programName}")
    public ResponseEntity<Map<String, Object>> getProgramMultiYearDetail(
            @PathVariable String universityCode,
            @PathVariable String programName) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<University> university = universityRepository.findByCodeWithPrograms(universityCode.toUpperCase());
            
            if (university.isEmpty()) {
                response.put("status", "not_found");
                response.put("message", "University not found: " + universityCode);
                return ResponseEntity.ok(response);
            }
            
            List<Program> matchingPrograms = university.get().getPrograms().stream()
                    .filter(p -> p.getName().toLowerCase().contains(programName.toLowerCase()))
                    .collect(Collectors.toList());
            
            if (matchingPrograms.isEmpty()) {
                response.put("status", "not_found");
                response.put("message", "Program not found: " + programName);
                return ResponseEntity.ok(response);
            }
            
            List<Map<String, Object>> programDetails = new ArrayList<>();
            
            for (Program program : matchingPrograms) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("program_name", program.getName());
                detail.put("subject_combination", program.getSubjectCombination());
                detail.put("admission_method", program.getAdmissionMethod());
                detail.put("note", program.getNote());
                
                // Multi-year scores
                Map<String, Object> scores = new HashMap<>();
                scores.put("2024", program.getBenchmarkScore2024());
                scores.put("2023", program.getBenchmarkScore2023());
                scores.put("2022", program.getBenchmarkScore2022());
                detail.put("benchmark_scores", scores);
                
                // Data completeness
                int availableYears = (program.getBenchmarkScore2024() != null ? 1 : 0) +
                                   (program.getBenchmarkScore2023() != null ? 1 : 0) +
                                   (program.getBenchmarkScore2022() != null ? 1 : 0);
                detail.put("data_completeness", Map.of(
                    "available_years", availableYears,
                    "missing_years", 3 - availableYears,
                    "completeness_percentage", Math.round((double) availableYears / 3 * 100)
                ));
                
                // Trend analysis
                Map<String, Object> trend = analyzeProgramTrend(program);
                if (trend != null) {
                    detail.put("trend_analysis", trend);
                }
                
                programDetails.add(detail);
            }
            
            response.put("status", "success");
            response.put("operation", "program_multi_year_detail");
            response.put("university", Map.of(
                "code", university.get().getCode(),
                "name", university.get().getName(),
                "location", university.get().getLocation()
            ));
            response.put("search_term", programName);
            response.put("matching_programs", matchingPrograms.size());
            response.put("program_details", programDetails);
            
        } catch (Exception e) {
            log.error("Error getting program multi-year detail", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 🏆 TOP TRENDING PROGRAMS
     * Operation: Tìm các ngành có xu hướng tăng/giảm điểm mạnh nhất
     * Purpose: Highlight các ngành hot/cold
     */
    @GetMapping("/top-trending")
    public ResponseEntity<Map<String, Object>> getTopTrendingPrograms(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "both") String direction) { // "up", "down", "both"
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Program> allPrograms = programRepository.findAll();
            
            List<Map<String, Object>> trendsWithData = allPrograms.stream()
                    .map(this::analyzeProgramTrend)
                    .filter(Objects::nonNull)
                    .filter(trend -> {
                        Double percentage = (Double) trend.get("trend_percentage");
                        if (percentage == null) return false;
                        
                        switch (direction) {
                            case "up": return percentage > 0;
                            case "down": return percentage < 0;
                            default: return true;
                        }
                    })
                    .sorted((a, b) -> {
                        String directionA = (String) a.get("trend_direction");
                        String directionB = (String) b.get("trend_direction");
                        Double percentageA = (Double) a.get("trend_percentage");
                        Double percentageB = (Double) b.get("trend_percentage");
                        
                        if ("up".equals(direction)) {
                            return Double.compare(percentageB, percentageA); // Descending
                        } else if ("down".equals(direction)) {
                            return Double.compare(percentageA, percentageB); // Ascending (most negative first)
                        } else {
                            return Double.compare(Math.abs(percentageB), Math.abs(percentageA)); // By absolute change
                        }
                    })
                    .limit(limit)
                    .collect(Collectors.toList());
            
            // Summary statistics
            long totalWithTrends = trendsWithData.size();
            long upTrending = trendsWithData.stream().filter(t -> (Double) t.get("trend_percentage") > 0).count();
            long downTrending = trendsWithData.stream().filter(t -> (Double) t.get("trend_percentage") < 0).count();
            
            response.put("status", "success");
            response.put("operation", "top_trending_programs");
            response.put("filters", Map.of(
                "direction", direction,
                "limit", limit
            ));
            response.put("summary", Map.of(
                "total_programs_with_trends", totalWithTrends,
                "up_trending", upTrending,
                "down_trending", downTrending,
                "stable", totalWithTrends - upTrending - downTrending
            ));
            response.put("trending_programs", trendsWithData);
            
        } catch (Exception e) {
            log.error("Error getting top trending programs", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 📊 UNIVERSITY YEAR COVERAGE
     * Operation: Phân tích coverage data theo từng trường
     * Purpose: Tìm trường nào có data tốt nhất/tệ nhất
     */
    @GetMapping("/university-coverage")
    public ResponseEntity<Map<String, Object>> analyzeUniversityCoverage() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<University> universities = universityRepository.findAll();
            
            List<Map<String, Object>> universityCoverage = universities.stream()
                    .map(this::analyzeUniversityYearCoverage)
                    .sorted((a, b) -> {
                        Double scoreA = (Double) a.get("coverage_score");
                        Double scoreB = (Double) b.get("coverage_score");
                        return Double.compare(scoreB, scoreA); // Descending
                    })
                    .collect(Collectors.toList());
            
            response.put("status", "success");
            response.put("operation", "university_coverage_analysis");
            response.put("total_universities", universities.size());
            response.put("university_coverage", universityCoverage);
            
        } catch (Exception e) {
            log.error("Error analyzing university coverage", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    // Helper methods
    private Map<String, Object> analyzeProgramTrend(Program program) {
        List<Double> scores = new ArrayList<>();
        List<String> years = new ArrayList<>();
        
        if (program.getBenchmarkScore2022() != null) {
            scores.add(program.getBenchmarkScore2022());
            years.add("2022");
        }
        if (program.getBenchmarkScore2023() != null) {
            scores.add(program.getBenchmarkScore2023());
            years.add("2023");
        }
        if (program.getBenchmarkScore2024() != null) {
            scores.add(program.getBenchmarkScore2024());
            years.add("2024");
        }
        
        if (scores.size() < 2) {
            return null; // Need at least 2 data points
        }
        
        Map<String, Object> trend = new HashMap<>();
        trend.put("program_name", program.getName());
        trend.put("university_code", program.getUniversity().getCode());
        trend.put("university_name", program.getUniversity().getName());
        trend.put("admission_method", program.getAdmissionMethod());
        
        // Calculate trend
        double firstScore = scores.get(0);
        double lastScore = scores.get(scores.size() - 1);
        double change = lastScore - firstScore;
        double percentageChange = (change / firstScore) * 100;
        
        trend.put("score_change", Math.round(change * 100.0) / 100.0);
        trend.put("trend_percentage", Math.round(percentageChange * 100.0) / 100.0);
        trend.put("trend_direction", change > 0.5 ? "increasing" : change < -0.5 ? "decreasing" : "stable");
        trend.put("first_year", years.get(0));
        trend.put("last_year", years.get(years.size() - 1));
        trend.put("first_score", firstScore);
        trend.put("last_score", lastScore);
        trend.put("data_points", scores.size());
        
        // Trend strength
        String strength = Math.abs(percentageChange) > 10 ? "strong" : 
                         Math.abs(percentageChange) > 5 ? "moderate" : "weak";
        trend.put("trend_strength", strength);
        
        return trend;
    }
    
    private Map<String, Object> analyzeUniversityYearCoverage(University university) {
        List<Program> programs = university.getPrograms();
        
        if (programs.isEmpty()) {
            return Map.of(
                "university_code", university.getCode(),
                "university_name", university.getName(),
                "total_programs", 0,
                "coverage_score", 0.0
            );
        }
        
        long total = programs.size();
        long with2024 = programs.stream().filter(p -> p.getBenchmarkScore2024() != null).count();
        long with2023 = programs.stream().filter(p -> p.getBenchmarkScore2023() != null).count();
        long with2022 = programs.stream().filter(p -> p.getBenchmarkScore2022() != null).count();
        long complete = programs.stream().filter(p -> 
            p.getBenchmarkScore2024() != null &&
            p.getBenchmarkScore2023() != null &&
            p.getBenchmarkScore2022() != null
        ).count();
        
        // Coverage score (weighted)
        double coverageScore = (with2024 * 0.5 + with2023 * 0.3 + with2022 * 0.2) / total * 100;
        
        return Map.of(
            "university_code", university.getCode(),
            "university_name", university.getName(),
            "location", university.getLocation() != null ? university.getLocation() : "N/A",
            "total_programs", total,
            "year_coverage", Map.of(
                "2024", with2024,
                "2023", with2023,
                "2022", with2022,
                "complete_3_years", complete
            ),
            "coverage_percentages", Map.of(
                "2024", Math.round((double) with2024 / total * 100),
                "2023", Math.round((double) with2023 / total * 100),
                "2022", Math.round((double) with2022 / total * 100),
                "complete", Math.round((double) complete / total * 100)
            ),
            "coverage_score", Math.round(coverageScore * 100.0) / 100.0,
            "data_quality", coverageScore > 80 ? "excellent" : 
                          coverageScore > 60 ? "good" : 
                          coverageScore > 40 ? "fair" : "poor"
        );
    }
}