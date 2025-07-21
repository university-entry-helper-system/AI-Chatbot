package com.khoipd8.educationchatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ChatSessionService {
    
    // Store chat sessions in memory (for demo - production should use Redis/DB)
    private final Map<String, ChatSession> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * 🆕 Tạo session mới
     */
    public String createSession() {
        String sessionId = generateSessionId();
        ChatSession session = new ChatSession(sessionId);
        activeSessions.put(sessionId, session);
        
        log.info("Created new chat session: {}", sessionId);
        return sessionId;
    }
    
    /**
     * 💬 Thêm message vào session
     */
    public void addMessage(String sessionId, String role, String content) {
        ChatSession session = getOrCreateSession(sessionId);
        
        ChatMessage message = new ChatMessage(role, content, LocalDateTime.now());
        session.addMessage(message);
        
        log.debug("Added {} message to session {}: {}", role, sessionId, 
                 content.length() > 50 ? content.substring(0, 50) + "..." : content);
    }
    
    /**
     * 📋 Lấy toàn bộ lịch sử chat
     */
    public List<ChatMessage> getChatHistory(String sessionId) {
        ChatSession session = activeSessions.get(sessionId);
        if (session == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(session.getMessages());
    }
    
    /**
     * 🔄 Lấy messages format cho OpenAI API
     */
    public List<Map<String, String>> getMessagesForAPI(String sessionId, int maxMessages) {
        List<ChatMessage> history = getChatHistory(sessionId);
        
        // Lấy N messages gần nhất để tránh exceed token limit
        int startIndex = Math.max(0, history.size() - maxMessages);
        List<ChatMessage> recentMessages = history.subList(startIndex, history.size());
        
        List<Map<String, String>> apiMessages = new ArrayList<>();
        
        // Thêm system message nếu chưa có
        if (recentMessages.isEmpty() || !"system".equals(recentMessages.get(0).getRole())) {
            apiMessages.add(Map.of(
                "role", "system",
                "content", "Bạn là trợ lý tư vấn tuyển sinh đại học thông minh. Trả lời ngắn gọn, chính xác dựa trên dữ liệu được cung cấp."
            ));
        }
        
        // Thêm conversation history
        for (ChatMessage msg : recentMessages) {
            if (!"system".equals(msg.getRole())) {
                apiMessages.add(Map.of(
                    "role", msg.getRole(),
                    "content", msg.getContent()
                ));
            }
        }
        
        return apiMessages;
    }
    
    /**
     * 📊 Lấy thống kê session
     */
    public Map<String, Object> getSessionStats(String sessionId) {
        ChatSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Map.of("exists", false);
        }
        
        long userMessages = session.getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .count();
        
        long botMessages = session.getMessages().stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .count();
        
        return Map.of(
            "exists", true,
            "session_id", sessionId,
            "total_messages", session.getMessages().size(),
            "user_messages", userMessages,
            "bot_messages", botMessages,
            "created_at", session.getCreatedAt(),
            "last_activity", session.getLastActivity(),
            "duration_minutes", java.time.Duration.between(session.getCreatedAt(), LocalDateTime.now()).toMinutes()
        );
    }
    
    /**
     * 🗑️ Xóa session
     */
    public boolean deleteSession(String sessionId) {
        ChatSession removed = activeSessions.remove(sessionId);
        if (removed != null) {
            log.info("Deleted chat session: {} ({} messages)", sessionId, removed.getMessages().size());
            return true;
        }
        return false;
    }
    
    /**
     * 📋 Lấy danh sách tất cả sessions
     */
    public List<Map<String, Object>> getAllSessions() {
        List<Map<String, Object>> sessions = new ArrayList<>();
        
        for (ChatSession session : activeSessions.values()) {
            sessions.add(Map.of(
                "session_id", session.getSessionId(),
                "message_count", session.getMessages().size(),
                "created_at", session.getCreatedAt(),
                "last_activity", session.getLastActivity()
            ));
        }
        
        // Sort by last activity
        sessions.sort((a, b) -> 
            ((LocalDateTime) b.get("last_activity")).compareTo((LocalDateTime) a.get("last_activity")));
        
        return sessions;
    }
    
    /**
     * 🧹 Cleanup old sessions (call periodically)
     */
    public int cleanupOldSessions(int maxAgeHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(maxAgeHours);
        
        List<String> toRemove = activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().getLastActivity().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList();
        
        toRemove.forEach(activeSessions::remove);
        
        if (!toRemove.isEmpty()) {
            log.info("Cleaned up {} old chat sessions", toRemove.size());
        }
        
        return toRemove.size();
    }
    
    // Helper methods
    private ChatSession getOrCreateSession(String sessionId) {
        return activeSessions.computeIfAbsent(sessionId, id -> new ChatSession(id));
    }
    
    private String generateSessionId() {
        return "chat_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString(new Random().nextInt());
    }
    
    // Inner classes
    public static class ChatSession {
        private final String sessionId;
        private final LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        private final List<ChatMessage> messages;
        
        public ChatSession(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
            this.messages = new ArrayList<>();
        }
        
        public void addMessage(ChatMessage message) {
            messages.add(message);
            lastActivity = LocalDateTime.now();
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastActivity() { return lastActivity; }
        public List<ChatMessage> getMessages() { return messages; }
    }
    
    public static class ChatMessage {
        private final String role; // "user", "assistant", "system"
        private final String content;
        private final LocalDateTime timestamp;
        
        public ChatMessage(String role, String content, LocalDateTime timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getRole() { return role; }
        public String getContent() { return content; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}