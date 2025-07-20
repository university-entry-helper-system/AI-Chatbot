package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.entity.Program;
import com.khoipd8.educationchatbot.repository.ProgramRepository;
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
@RequestMapping("/api/programs")
@Tag(name = "Program Management", description = "API quản lý thông tin ngành học")
public class ProgramController {

    @Autowired
    private ProgramRepository programRepository;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả ngành học", 
               description = "Trả về danh sách tất cả các ngành học trong hệ thống")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = Program.class))),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<List<Program>> getAllPrograms() {
        List<Program> programs = programRepository.findAll();
        return ResponseEntity.ok(programs);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin ngành học theo ID", 
               description = "Trả về thông tin chi tiết của một ngành học")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = Program.class))),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy ngành học"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<Program> getProgramById(
            @Parameter(description = "ID của ngành học", required = true)
            @PathVariable Long id) {
        Optional<Program> program = programRepository.findById(id);
        return program.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/university/{universityId}")
    @Operation(summary = "Lấy danh sách ngành học theo đại học", 
               description = "Trả về danh sách các ngành học của một trường đại học")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = Program.class))),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<List<Program>> getProgramsByUniversity(
            @Parameter(description = "ID của đại học", required = true)
            @PathVariable Long universityId) {
        List<Program> programs = programRepository.findByUniversityId(universityId);
        return ResponseEntity.ok(programs);
    }

    @PostMapping
    @Operation(summary = "Tạo ngành học mới", 
               description = "Tạo một ngành học mới trong hệ thống")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Tạo thành công",
                    content = @Content(schema = @Schema(implementation = Program.class))),
        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<Program> createProgram(
            @Parameter(description = "Thông tin ngành học cần tạo", required = true)
            @RequestBody Program program) {
        Program savedProgram = programRepository.save(program);
        return ResponseEntity.status(201).body(savedProgram);
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm ngành học", 
               description = "Tìm kiếm ngành học theo tên hoặc mã ngành")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = Program.class))),
        @ApiResponse(responseCode = "500", description = "Lỗi server")
    })
    public ResponseEntity<List<Program>> searchPrograms(
            @Parameter(description = "Từ khóa tìm kiếm (tên hoặc mã ngành)")
            @RequestParam(required = false) String keyword) {
        List<Program> programs;
        if (keyword != null && !keyword.trim().isEmpty()) {
            programs = programRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
        } else {
            programs = programRepository.findAll();
        }
        return ResponseEntity.ok(programs);
    }
} 