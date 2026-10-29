package com.rfcopilot.core.session;

/**
 * 会话消息持久化载体（仅 user/assistant 纯文本，M1 简化模型）。
 */
public class ChatMessageDto {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private String role;
    private String text;
    private String time;

    public ChatMessageDto() {
    }

    public ChatMessageDto(String role, String text, String time) {
        this.role = role;
        this.text = text;
        this.time = time;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
