package com.rfcopilot.tools.scpi;

/**
 * 一台 SCPI 仪表的连接配置（LAN 直连，通常为 5025 原始 TCP 端口）。
 * 为 Jackson 序列化提供无参构造与 getter/setter。
 */
public class ScpiConnectionConfig {

    private String name = "";
    private String host = "";
    private int port = 5025;
    /** 连接与读响应超时（毫秒） */
    private int timeoutMs = 5000;

    public ScpiConnectionConfig() {
    }

    public ScpiConnectionConfig(String name, String host, int port, int timeoutMs) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    /** 显示用：名称 (host:port) */
    public String display() {
        return name + "（" + host + ":" + port + "）";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
