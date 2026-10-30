package com.rfcopilot.tools.loganalysis;

/**
 * 一条预筛命中的日志记录。
 *
 * @param lineNo 原始行号（1 起，可追溯）
 * @param level  级别：ERROR / WARN / FAIL / TIMEOUT / CODE / CLUSTER
 * @param rule   命中的规则名（中文说明）
 * @param text   日志原文（截断）
 */
public record LogFinding(int lineNo, String level, String rule, String text) {
}
