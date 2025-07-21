package com.khoipd8.educationchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequestDto {
    
    @JsonProperty("message")
    @Schema(description = "Tin nhắn người dùng", example = "Điểm chuẩn CNTT là bao nhiêu?", required = true)
    private String message;
    
    @JsonProperty("session_id") 
    @Schema(description = "ID phiên chat", example = "chat_123456")
    private String session_id;

    // CRITICAL: Default constructor phải public và empty
    public ChatRequestDto() {
        // Explicitly empty - required for Jackson
    }

    // Constructor với tham số
    public ChatRequestDto(String message, String session_id) {
        this.message = message;
        this.session_id = session_id;
    }

    // Getters và Setters - QUAN TRỌNG: phải public
    public String getMessage() { 
        return message; 
    }
    
    public void setMessage(String message) { 
        this.message = message; 
    }
    
    public String getSession_id() { 
        return session_id; 
    }
    
    public void setSession_id(String session_id) { 
        this.session_id = session_id; 
    }

    // toString cho debugging
    @Override
    public String toString() {
        return String.format("ChatRequestDto{message='%s', session_id='%s'}", 
                           message, session_id);
    }

    // equals và hashCode (optional nhưng good practice)
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChatRequestDto that = (ChatRequestDto) obj;
        return java.util.Objects.equals(message, that.message) &&
               java.util.Objects.equals(session_id, that.session_id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(message, session_id);
    }
}