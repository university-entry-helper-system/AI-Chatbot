package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.entity.University;
import com.khoipd8.educationchatbot.repository.UniversityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @Autowired
    private UniversityRepository universityRepository;
    
    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Education Chatbot API is running!");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/db-test")
    public ResponseEntity<Map<String, Object>> testDatabase() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            long count = universityRepository.count();
            List<University> universities = universityRepository.findAll();
            
            response.put("status", "success");
            response.put("university_count", count);
            response.put("database_connected", true);
            response.put("universities", universities);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
            response.put("database_connected", false);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/create-sample")
    public ResponseEntity<Map<String, Object>> createSampleUniversity() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if university already exists
            Optional<University> existingUniversity = universityRepository.findByCode("HUT");
            
            if (existingUniversity.isPresent()) {
                response.put("status", "already_exists");
                response.put("message", "University with code 'HUT' already exists");
                response.put("university", existingUniversity.get());
                return ResponseEntity.ok(response);
            }
            
            // Create new university
            University university = new University();
            university.setName("Đại học Bách Khoa Hà Nội");
            university.setCode("HUT");
            university.setFullName("Trường Đại học Bách Khoa Hà Nội");
            university.setLocation("Hà Nội");
            university.setType("Công lập");
            university.setWebsite("https://hust.edu.vn");
            university.setTotalQuota(5000);
            university.setDescription("Trường đại học kỹ thuật hàng đầu Việt Nam");
            
            University saved = universityRepository.save(university);
            
            response.put("status", "created");
            response.put("message", "Sample university created successfully");
            response.put("university", saved);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            response.put("error_type", e.getClass().getSimpleName());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/clear-sample")
    public ResponseEntity<Map<String, Object>> clearSampleData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<University> university = universityRepository.findByCode("HUT");
            
            if (university.isPresent()) {
                universityRepository.delete(university.get());
                response.put("status", "deleted");
                response.put("message", "Sample university deleted successfully");
            } else {
                response.put("status", "not_found");
                response.put("message", "No university with code 'HUT' found");
            }
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/create-multiple-samples")
    public ResponseEntity<Map<String, Object>> createMultipleSamples() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Sample universities
            University[] sampleUniversities = {
                createUniversity("HUST", "Đại học Bách Khoa Hà Nội", "Hà Nội", "Công lập"),
                createUniversity("UET", "Đại học Công nghệ - ĐHQGHN", "Hà Nội", "Công lập"),
                createUniversity("NEU", "Đại học Kinh tế Quốc dân", "Hà Nội", "Công lập"),
                createUniversity("HCMUT", "Đại học Bách Khoa TPHCM", "TP.HCM", "Công lập"),
                createUniversity("UIT", "Đại học Công nghệ Thông tin - ĐHQG TPHCM", "TP.HCM", "Công lập")
            };
            
            int created = 0;
            int existing = 0;
            
            for (University university : sampleUniversities) {
                Optional<University> existing_uni = universityRepository.findByCode(university.getCode());
                if (!existing_uni.isPresent()) {
                    universityRepository.save(university);
                    created++;
                } else {
                    existing++;
                }
            }
            
            response.put("status", "completed");
            response.put("created", created);
            response.put("already_existing", existing);
            response.put("total_universities", universityRepository.count());
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/universities")
    public ResponseEntity<List<University>> getAllUniversities() {
        List<University> universities = universityRepository.findAll();
        return ResponseEntity.ok(universities);
    }
    
    @GetMapping("/universities/{code}")
    public ResponseEntity<Map<String, Object>> getUniversityByCode(@PathVariable String code) {
        Map<String, Object> response = new HashMap<>();
        
        Optional<University> university = universityRepository.findByCode(code.toUpperCase());
        
        if (university.isPresent()) {
            response.put("status", "found");
            response.put("university", university.get());
        } else {
            response.put("status", "not_found");
            response.put("message", "University with code '" + code + "' not found");
        }
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            long count = universityRepository.count();
            universityRepository.deleteAll();
            
            response.put("status", "cleared");
            response.put("message", "All university data cleared");
            response.put("deleted_count", count);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    // Helper method
    private University createUniversity(String code, String name, String location, String type) {
        University university = new University();
        university.setCode(code);
        university.setName(name);
        university.setFullName(name);
        university.setLocation(location);
        university.setType(type);
        university.setTotalQuota(3000);
        return university;
    }
}