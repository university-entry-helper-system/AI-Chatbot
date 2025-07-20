package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.entity.University;
import com.khoipd8.educationchatbot.repository.UniversityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/universities")
@Tag(name = "University Management", description = "API quản lý thông tin đại học")
public class UniversityController {

    @Autowired
    private UniversityRepository universityRepository;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả đại học", 
               description = "Trả về danh sách tất cả các trường đại học trong hệ thống")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = University.class))),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<List<University>> getAllUniversities() {
        List<University> universities = universityRepository.findAll();
        return ResponseEntity.ok(universities);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin đại học theo ID", 
               description = "Trả về thông tin chi tiết của một trường đại học")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = University.class))),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy đại học"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<University> getUniversityById(
            @Parameter(description = "ID của đại học", required = true)
            @PathVariable Long id) {
        Optional<University> university = universityRepository.findById(id);
        return university.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Tạo đại học mới", 
               description = "Tạo một trường đại học mới trong hệ thống")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Tạo thành công",
                    content = @Content(schema = @Schema(implementation = University.class))),
        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<University> createUniversity(
            @Parameter(description = "Thông tin đại học cần tạo", required = true)
            @RequestBody University university) {
        University savedUniversity = universityRepository.save(university);
        return ResponseEntity.status(201).body(savedUniversity);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin đại học", 
               description = "Cập nhật thông tin của một trường đại học")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cập nhật thành công",
                    content = @Content(schema = @Schema(implementation = University.class))),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy đại học"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<University> updateUniversity(
            @Parameter(description = "ID của đại học", required = true)
            @PathVariable Long id,
            @Parameter(description = "Thông tin đại học cần cập nhật", required = true)
            @RequestBody University universityDetails) {
        Optional<University> university = universityRepository.findById(id);
        if (university.isPresent()) {
            University existingUniversity = university.get();
            existingUniversity.setName(universityDetails.getName());
            existingUniversity.setCode(universityDetails.getCode());
            existingUniversity.setFullName(universityDetails.getFullName());
            existingUniversity.setLocation(universityDetails.getLocation());
            existingUniversity.setType(universityDetails.getType());
            existingUniversity.setWebsite(universityDetails.getWebsite());
            existingUniversity.setDescription(universityDetails.getDescription());
            existingUniversity.setTotalQuota(universityDetails.getTotalQuota());
            
            University updatedUniversity = universityRepository.save(existingUniversity);
            return ResponseEntity.ok(updatedUniversity);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa đại học", 
               description = "Xóa một trường đại học khỏi hệ thống")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Xóa thành công"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy đại học"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<Void> deleteUniversity(
            @Parameter(description = "ID của đại học", required = true)
            @PathVariable Long id) {
        Optional<University> university = universityRepository.findById(id);
        if (university.isPresent()) {
            universityRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm đại học", 
               description = "Tìm kiếm đại học theo tên hoặc mã trường")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = University.class))),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<List<University>> searchUniversities(
            @Parameter(description = "Từ khóa tìm kiếm (tên hoặc mã trường)")
            @RequestParam(required = false) String keyword) {
        List<University> universities;
        if (keyword != null && !keyword.trim().isEmpty()) {
            universities = universityRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
        } else {
            universities = universityRepository.findAll();
        }
        return ResponseEntity.ok(universities);
    }
} 