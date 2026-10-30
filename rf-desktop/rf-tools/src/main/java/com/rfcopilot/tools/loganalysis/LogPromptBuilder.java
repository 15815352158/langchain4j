package com.rfcopilot.tools.loganalysis;

import java.util.List;

/**
 * 把预筛结果组装成发给大模型的分析提示词（中文）。
 */
public final class LogPromptBuilder {

    /** 进入提示词的命中条数上限（控制 token 规模） */
    private static final int PROMPT_FINDINGS = 150;

    private LogPromptBuilder() {
    }

    public static String build(LogScanResult scan) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深射频工程师的日志分析助手。以下是我从日志文件《")
                .append(scan.fileName()).append("》中用规则预筛出的可疑记录，请分析：\n\n");
        sb.append("【统计】").append(scan.statsLine()).append('\n');

        List<String> clusters = scan.clusters();
        if (!clusters.isEmpty()) {
            sb.append("【异常时间窗】\n");
            for (String c : clusters) {
                sb.append("  - ").append(c).append('\n');
            }
            sb.append('\n');
        }

        sb.append("【命中记录】（格式：行号 | 级别 | 规则 | 原文）\n");
        int n = 0;
        for (LogFinding f : scan.findings()) {
            if (n++ >= PROMPT_FINDINGS) {
                sb.append("  ...（其余记录已省略）\n");
                break;
            }
            sb.append("  ").append(f.lineNo()).append(" | ").append(f.level())
                    .append(" | ").append(f.rule()).append(" | ").append(f.text()).append('\n');
        }

        sb.append("""

                请按以下结构输出分析（用 Markdown，中文）：
                1. **异常摘要**：按严重程度归纳主要问题（不超过 5 条）
                2. **可能原因**：每条问题给出 1~3 个最可能的射频/硬件/软件原因假设
                3. **排查建议**：给出可执行的下一步排查步骤（优先用哪些仪器、测什么点）
                4. **需要补充的信息**：如果日志不足以定位，明确说明还需要哪些数据

                注意：只基于给出的记录推理，不要编造日志中不存在的内容；对不确定的结论请标注"存疑"。
                """);
        return sb.toString();
    }
}
