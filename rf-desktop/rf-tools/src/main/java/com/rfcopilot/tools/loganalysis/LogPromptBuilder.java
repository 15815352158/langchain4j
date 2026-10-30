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

                【射频产测日志背景知识】（若命中记录属于产测/校准日志，请结合以下框架分析）
                Tx 分两大阶段：
                ① Tx 校准阶段：发射功率校准、频偏校准、IQ 校准（直流偏移、IQ 增益不平衡、正交误差）。
                   该阶段 FAIL 属于"功率/频偏/IQ 校准不过"（收敛失败或结果超差）。
                   功率校准重点参数：链路损耗补偿值、仪表基准功率、Tx DAC 增益码、PA 增益档位、PA 偏置电压、
                     目标功率、上下限判定阈值、迭代步长、最大迭代次数、收敛误差、稳定等待延时、采样平均次数、
                     内置功率检波 ADC 反馈值、温度补偿系数、信道/MCS、burst 发包配置。
                   频偏校准重点参数：晶振标称频率、晶振负载电容、仪表时基基准、环境温度、本振调谐 FTUNE DAC 码、
                     PLL 分频/环路参数、频偏目标窗口、迭代步长、最大迭代次数、采样积分时间、采样平均次数、
                     PLL 稳定等待延时、信道、供电电压、校准后剩余频偏。
                ② Tx 发包检测阶段（校准完成后）：发射功率复测、频偏复测、EVM、频谱模板、杂散、功率稳定度、
                   占用带宽、发包计数、丢包率、发射时序。该阶段 FAIL 属于"Tx 发包检测不合格"。
                Rx 分两大阶段：
                ① Rx 校准阶段：RSSI 校准、接收直流偏移校准、接收 IQ 校准（部分芯片无此步骤）。
                ② Rx 接收检测阶段（校准完成后）：接收灵敏度、收包计数、丢包率 PER、RSSI 精度、接收底噪、
                   邻道选择性（抽检）、最大输入电平、帧同步。该阶段 FAIL 属于"Rx 收包检测不合格"。

                请按以下结构输出分析（用 Markdown，中文）：
                1. **异常摘要**：按严重程度归纳主要问题（不超过 5 条）；若属于产测日志，先明确每条 FAIL
                   归属的阶段（Tx/Rx × 校准/检测）与失败类别
                2. **可能原因**：每条问题给出 1~3 个最可能的原因假设，并区分四类：
                   仪表/测试链路问题（如损耗补偿值错误、线缆老化、仪表基准不稳）、
                   DUT 硬件问题（如 PA 偏置异常、晶振频偏超限、PLL 失锁、匹配网络故障）、
                   校准算法问题（如迭代不收敛、步长不当、目标窗口过窄、剩余误差超差）、
                   配置/环境问题（如信道/burst 配置错误、温度补偿缺失、供电电压不稳）
                3. **排查建议**：结合该阶段重点参数，指出应优先核对哪些参数读数是否异常/缺失，
                   以及下一步复测动作（优先用哪些仪器、测什么点）
                4. **需要补充的信息**：如果日志不足以定位，明确说明还需要哪些数据（如完整迭代过程值、
                   检波 ADC 原始读数、同批次其他 DUT 对比）

                注意：只基于给出的记录推理，不要编造日志中不存在的内容；对不确定的结论请标注"存疑"。
                """);
        return sb.toString();
    }
}
