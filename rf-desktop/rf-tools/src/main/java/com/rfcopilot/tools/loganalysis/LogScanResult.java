package com.rfcopilot.tools.loganalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志预筛结果：总量统计 + 命中列表。
 */
public class LogScanResult {

    private final String fileName;
    private int totalLines;
    private int errorCount;
    private int warnCount;
    private int failCount;
    private boolean truncated; // 命中过多时仅保留前若干条
    private final List<LogFinding> findings = new ArrayList<>();
    private final List<String> clusters = new ArrayList<>();

    public LogScanResult(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }

    public void addFinding(LogFinding f) {
        findings.add(f);
        switch (f.level()) {
            case "ERROR", "CODE" -> errorCount++;
            case "WARN" -> warnCount++;
            case "FAIL" -> failCount++;
            default -> {
            }
        }
    }

    public void incLines() {
        totalLines++;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public void addCluster(String desc) {
        clusters.add(desc);
    }

    public int totalLines() {
        return totalLines;
    }

    public int errorCount() {
        return errorCount;
    }

    public int warnCount() {
        return warnCount;
    }

    public int failCount() {
        return failCount;
    }

    public boolean truncated() {
        return truncated;
    }

    public List<LogFinding> findings() {
        return findings;
    }

    public List<String> clusters() {
        return clusters;
    }

    public boolean hasIssues() {
        return !findings.isEmpty();
    }

    /** 一行统计摘要（界面与提示词共用） */
    public String statsLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(fileName).append("　共 ").append(totalLines).append(" 行　")
                .append("错误 ").append(errorCount)
                .append("　警告 ").append(warnCount)
                .append("　失败项 ").append(failCount);
        if (truncated) {
            sb.append("　（命中过多，仅展示前 ").append(findings.size()).append(" 条）");
        }
        return sb.toString();
    }
}
