package com.rfcopilot.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 应用数据目录：~/.rfcopilot/
 * 存放配置、会话记录与知识库向量文件。
 */
public final class RfPaths {

    public static final Path BASE = Path.of(System.getProperty("user.home"), ".rfcopilot");

    private RfPaths() {
    }

    public static Path configFile() {
        return BASE.resolve("config.json");
    }

    public static Path sessionsDir() {
        return BASE.resolve("sessions");
    }

    public static Path knowledgeDir() {
        return BASE.resolve("knowledge");
    }

    /** 确保数据目录存在 */
    public static void ensureDirs() throws IOException {
        Files.createDirectories(BASE);
        Files.createDirectories(sessionsDir());
        Files.createDirectories(knowledgeDir());
    }
}
