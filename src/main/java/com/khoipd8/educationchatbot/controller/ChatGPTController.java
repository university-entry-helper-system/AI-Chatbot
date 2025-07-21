package com.khoipd8.educationchatbot.controller;

import com.khoipd8.educationchatbot.service.ChatGPTRAGService;
import com.khoipd8.educationchatbot.service.ChatSessionService;
import com.khoipd8.educationchatbot.dto.ChatRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/chatgpt")
@Tag(name = "ChatGPT Chatbot", description = "AI Chatbot với ChatGPT API")
@Slf4j
public class ChatGPTController {
    
    @Autowired
    private ChatGPTRAGService chatGPTRAGService;
    
    @Autowired
    private ChatSessionService chatSessionService;

    /**
     * 💬 MAIN CHAT ENDPOINT
     */
    @PostMapping("/chat")
    @Operation(summary = "Chat với ChatGPT RAG")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequestDto request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("📝 Chat request - Message: '{}', Session: '{}'", 
                    request.getMessage(), request.getSession_id());
            
            String userMessage = request.getMessage();
            String sessionId = request.getSession_id();
            
            // Validation
            if (userMessage == null || userMessage.trim().isEmpty()) {
                response.put("status", "error");
                response.put("error", "Message không được để trống");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create session if needed
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = chatSessionService.createSession();
            }
            
            // Check budget
            Map<String, Object> costInfo = chatGPTRAGService.getCostInfo();
            double remainingBudget = (Double) costInfo.get("remaining_budget");
            
            if (remainingBudget <= 0.50) {
                response.put("status", "budget_warning");
                response.put("message", "⚠️ Ngân sách API gần hết!");
                response.put("remaining_budget", remainingBudget);
                return ResponseEntity.ok(response);
            }
            
            // Process with RAG
            Map<String, Object> ragResult = chatGPTRAGService.queryRAG(userMessage.trim(), sessionId);
            
            // Build response
            response.put("status", "success");
            response.put("user_message", userMessage);
            response.put("bot_response", ragResult.get("answer"));
            response.put("session_id", sessionId);
            response.put("sources", ragResult.get("sources"));
            response.put("cost_info", ragResult.get("cost_info"));
            response.put("source_type", ragResult.get("source"));
            
        } catch (Exception e) {
            log.error("❌ Chat error: {}", e.getMessage());
            response.put("status", "error");
            response.put("error", "Lỗi xử lý: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 🧪 TEST ENDPOINT - Bypass DTO
     */
    @PostMapping("/chat/test")
    @Operation(summary = "Test endpoint using Map")
    public ResponseEntity<Map<String, Object>> testChat(@RequestBody LinkedHashMap<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String message = (String) request.get("message");
            String sessionId = (String) request.get("session_id");
            
            log.info("🧪 Test - Message: '{}', Session: '{}'", message, sessionId);
            
            if (message == null || message.trim().isEmpty()) {
                response.put("error", "Message required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = chatSessionService.createSession();
            }
            
            // Call RAG service
            Map<String, Object> ragResult = chatGPTRAGService.queryRAG(message.trim(), sessionId);
            
            response.put("status", "success");
            response.put("user_message", message);
            response.put("bot_response", ragResult.get("answer"));
            response.put("session_id", sessionId);
            response.put("cost_info", ragResult.get("cost_info"));
            
        } catch (Exception e) {
            log.error("Test error: {}", e.getMessage());
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 📊 COST DASHBOARD
     */
    @GetMapping("/cost-dashboard")
    @Operation(summary = "Xem chi phí sử dụng")
    public ResponseEntity<Map<String, Object>> getCostDashboard() {
        try {
            Map<String, Object> costInfo = chatGPTRAGService.getCostInfo();
            costInfo.put("budget_total", 10.0);
            costInfo.put("last_updated", System.currentTimeMillis());
            return ResponseEntity.ok(costInfo);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 🧹 CLEAR CACHE
     */
    @PostMapping("/clear-cache")
    @Operation(summary = "Xóa cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        try {
            return ResponseEntity.ok(chatGPTRAGService.clearCache());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 📋 SESSION MANAGEMENT
     */
    @PostMapping("/session/new")
    @Operation(summary = "Tạo session mới")
    public ResponseEntity<Map<String, Object>> createSession() {
        try {
            String sessionId = chatSessionService.createSession();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "session_id", sessionId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sessions")
    @Operation(summary = "Lấy danh sách sessions")
    public ResponseEntity<Map<String, Object>> getAllSessions() {
        try {
            List<Map<String, Object>> sessions = chatSessionService.getAllSessions();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "session_count", sessions.size(),
                "sessions", sessions
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/session/{sessionId}/history")
    @Operation(summary = "Lấy lịch sử chat")
    public ResponseEntity<Map<String, Object>> getChatHistory(@PathVariable String sessionId) {
        try {
            List<ChatSessionService.ChatMessage> history = chatSessionService.getChatHistory(sessionId);
            List<Map<String, Object>> formattedHistory = history.stream()
                    .map(msg -> Map.of(
                        "role", (Object) msg.getRole(),
                        "content", (Object) msg.getContent(),
                        "timestamp", (Object) msg.getTimestamp().toString()
                    ))
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "session_id", sessionId,
                "message_count", history.size(),
                "chat_history", formattedHistory
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "Xóa session")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        try {
            boolean deleted = chatSessionService.deleteSession(sessionId);
            return ResponseEntity.ok(Map.of(
                "status", deleted ? "success" : "not_found",
                "message", deleted ? "Đã xóa session" : "Không tìm thấy session",
                "session_id", sessionId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}