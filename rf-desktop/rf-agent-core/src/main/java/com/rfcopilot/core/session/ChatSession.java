package com.rfcopilot.core.session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一次对话会话，持久化为 ~/.rfcopilot/sessions/{id}.json
 */
public class ChatSession {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String id = UUID.randomUUID().toString();
    private String title = "新会话";
    private String createdAt = LocalDateTime.now().format(FMT);
    private String updatedAt = LocalDateTime.now().format(FMT);
    private List<ChatMessageDto> messages = new ArrayList<>();

    public ChatSession() {
    }

    /** 用首条用户消息生成会话标题 */
    public void deriveTitleFromFirstMessage(String firstUserMessage) {
        if (messages.isEmpty() || "新会话".equals(title)) {
            String t = firstUserMessage.strip();
            title = t.length() > 24 ? t.substring(0, 24) + "…" : t;
        }
    }

    public void touch() {
        updatedAt = LocalDateTime.now().format(FMT);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessageDto> messages) {
        this.messages = messages;
    }
}
