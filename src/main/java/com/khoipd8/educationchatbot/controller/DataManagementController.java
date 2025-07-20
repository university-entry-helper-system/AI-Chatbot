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
@RequestMapping("/api/data")
@Slf4j
public class DataManagementController {
    
    @Autowired
    private UniversityRepository universityRepository;
    
    @Autowired
    private ProgramRepository programRepository;
    
    @GetMapping("/universities")
    public ResponseEntity<Map<String, Object>> getAllUniversities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<University> universities = universityRepository.findAll();
            
            // Apply pagination manually (or use Pageable if preferred)
            int start = page * size;
            int end = Math.min(start + size, universities.size());
            List<University> paginatedUniversities = universities.subList(start, end);
            
            // Create summary data
            List<Map<String, Object>> universitySummaries = paginatedUniversities.stream()
                    .map(this::createUniversitySummary)
                    .collect(Collectors.toList());
            
            response.put("status", "success");
            response.put("total", universities.size());
            response.put("page", page);
            response.put("size", size);
            response.put("universities", universitySummaries);
            
        } catch (Exception e) {
            log.error("Error getting universities", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/universities/{code}")
    public ResponseEntity<Map<String, Object>> getUniversityByCode(@PathVariable String code) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<University> university = universityRepository.findByCodeWithPrograms(code.toUpperCase());
            
            if (university.isPresent()) {
                University u = university.get();
                
                response.put("status", "found");
                response.put("university", createDetailedUniversityData(u));
            } else {
                response.put("status", "not_found");
                response.put("message", "University with code '" + code + "' not found");
            }
            
        } catch (Exception e) {
            log.error("Error getting university by code: {}", code, e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchUniversities(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String major) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<University> universities = new ArrayList<>();
            
            if (name != null && !name.trim().isEmpty()) {
                universities = universityRepository.findByNameContainingIgnoreCase(name.trim());
            } else if (location != null && !location.trim().isEmpty()) {
                universities = universityRepository.findByLocationContainingIgnoreCase(location.trim());
            } else {
                universities = universityRepository.findAll();
            }
            
            // Filter by major if specified
            if (major != null && !major.trim().isEmpty()) {
                universities = universities.stream()
                        .filter(u -> u.getPrograms().stream()
                                .anyMatch(p -> p.getName().toLowerCase()
                                        .contains(major.toLowerCase())))
                        .collect(Collectors.toList());
            }
            
            List<Map<String, Object>> results = universities.stream()
                    .map(this::createUniversitySummary)
                    .collect(Collectors.toList());
            
            response.put("status", "success");
            response.put("query", Map.of(
                "name", name != null ? name : "",
                "location", location != null ? location : "",
                "major", major != null ? major : ""
            ));
            response.put("total_results", results.size());
            response.put("universities", results);
            
        } catch (Exception e) {
            log.error("Error searching universities", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/programs")
    public ResponseEntity<Map<String, Object>> searchPrograms(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Double minScore,
            @RequestParam(required = false) Double maxScore,
            @RequestParam(required = false) String combination,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Program> programs = new ArrayList<>();
            
            // Apply filters
            if (name != null && !name.trim().isEmpty()) {
                programs = programRepository.findByNameContainingIgnoreCase(name.trim());
            } else if (minScore != null && maxScore != null) {
                programs = programRepository.findByScoreRange(minScore, maxScore);
            } else if (combination != null && !combination.trim().isEmpty()) {
                programs = programRepository.findBySubjectCombination(combination.trim());
            } else {
                programs = programRepository.findAll();
            }
            
            // Apply pagination
            int start = page * size;
            int end = Math.min(start + size, programs.size());
            List<Program> paginatedPrograms = programs.subList(start, end);
            
            List<Map<String, Object>> programData = paginatedPrograms.stream()
                    .map(this::createProgramSummary)
                    .collect(Collectors.toList());
            
            response.put("status", "success");
            response.put("total", programs.size());
            response.put("page", page);
            response.put("size", size);
            response.put("filters", Map.of(
                "name", name != null ? name : "",
                "minScore", minScore != null ? minScore : "",
                "maxScore", maxScore != null ? maxScore : "",
                "combination", combination != null ? combination : ""
            ));
            response.put("programs", programData);
            
        } catch (Exception e) {
            log.error("Error searching programs", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Basic counts
            long totalUniversities = universityRepository.count();
            long totalPrograms = programRepository.count();
            
            // Location distribution
            List<String> locations = universityRepository.findAllLocations();
            Map<String, Long> locationStats = locations.stream()
                    .collect(Collectors.groupingBy(
                            location -> location,
                            Collectors.counting()
                    ));
            
            // Admission method distribution
            List<String> admissionMethods = programRepository.findAllAdmissionMethods();
            Map<String, Long> admissionMethodStats = admissionMethods.stream()
                    .collect(Collectors.groupingBy(
                            method -> method,
                            Collectors.counting()
                    ));
            
            // Score statistics
            List<Program> programsWithScores = programRepository.findAllOrderByScore2024Desc();
            List<Double> scores = programsWithScores.stream()
                    .map(Program::getBenchmarkScore2024)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            Map<String, Object> scoreStats = new HashMap<>();
            if (!scores.isEmpty()) {
                scoreStats.put("highest", scores.get(0));
                scoreStats.put("lowest", scores.get(scores.size() - 1));
                scoreStats.put("average", scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
                scoreStats.put("count", scores.size());
            }
            
            response.put("status", "success");
            response.put("statistics", Map.of(
                "total_universities", totalUniversities,
                "total_programs", totalPrograms,
                "location_distribution", locationStats,
                "admission_method_distribution", admissionMethodStats,
                "score_statistics", scoreStats,
                "last_updated", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error getting statistics", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/universities/{code}")
    public ResponseEntity<Map<String, Object>> deleteUniversity(@PathVariable String code) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<University> university = universityRepository.findByCode(code.toUpperCase());
            
            if (university.isPresent()) {
                University u = university.get();
                String name = u.getName();
                int programCount = u.getPrograms().size();
                
                universityRepository.delete(u);
                
                response.put("status", "deleted");
                response.put("message", "Successfully deleted university: " + name);
                response.put("deleted_programs", programCount);
            } else {
                response.put("status", "not_found");
                response.put("message", "University with code '" + code + "' not found");
            }
            
        } catch (Exception e) {
            log.error("Error deleting university: {}", code, e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            long universityCount = universityRepository.count();
            long programCount = programRepository.count();
            
            universityRepository.deleteAll();
            
            response.put("status", "cleared");
            response.put("message", "All data has been cleared");
            response.put("deleted_universities", universityCount);
            response.put("deleted_programs", programCount);
            
        } catch (Exception e) {
            log.error("Error clearing all data", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    // Helper methods
    private Map<String, Object> createUniversitySummary(University university) {
        return Map.of(
            "id", university.getId(),
            "name", university.getName(),
            "code", university.getCode(),
            "location", university.getLocation() != null ? university.getLocation() : "N/A",
            "type", university.getType() != null ? university.getType() : "N/A",
            "programs_count", university.getPrograms().size(),
            "last_updated", university.getUpdatedAt() != null ? university.getUpdatedAt().toString() : "N/A"
        );
    }
    
    private Map<String, Object> createDetailedUniversityData(University university) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", university.getId());
        data.put("name", university.getName());
        data.put("code", university.getCode());
        data.put("full_name", university.getFullName());
        data.put("location", university.getLocation());
        data.put("type", university.getType());
        data.put("website", university.getWebsite());
        data.put("description", university.getDescription());
        data.put("total_quota", university.getTotalQuota());
        data.put("created_at", university.getCreatedAt());
        data.put("updated_at", university.getUpdatedAt());
        
        // Programs data
        List<Map<String, Object>> programs = university.getPrograms().stream()
                .map(this::createProgramSummary)
                .collect(Collectors.toList());
        data.put("programs", programs);
        data.put("programs_count", programs.size());
        
        return data;
    }
    
    private Map<String, Object> createProgramSummary(Program program) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", program.getId());
        summary.put("name", program.getName());
        summary.put("code", program.getCode() != null ? program.getCode() : "N/A");
        summary.put("subject_combination", program.getSubjectCombination() != null ? program.getSubjectCombination() : "N/A");
        summary.put("admission_method", program.getAdmissionMethod() != null ? program.getAdmissionMethod() : "N/A");
        summary.put("benchmark_score_2024", program.getBenchmarkScore2024() != null ? program.getBenchmarkScore2024() : "N/A");
        summary.put("benchmark_score_2023", program.getBenchmarkScore2023() != null ? program.getBenchmarkScore2023() : "N/A");
        summary.put("quota", program.getQuota() != null ? program.getQuota() : "N/A");
        summary.put("note", program.getNote() != null ? program.getNote() : "N/A");
        summary.put("university_code", program.getUniversity().getCode());
        summary.put("university_name", program.getUniversity().getName());
        return summary;
    }
}