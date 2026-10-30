package com.rfcopilot.tools.loganalysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用日志规则预筛引擎：错误/警告/失败关键词、SCPI 错误码、十六进制错误码、
 * 超时、异常时间窗聚类；并内置射频产测（Tx/Rx 校准与检测）专用规则与阶段判定。
 * 自动识别 UTF-8 / GBK 编码。
 */
public final class LogScanner {

    /** 命中上限，防止超大日志撑爆内存与提示词 */
    private static final int MAX_FINDINGS = 400;
    /** 单条保留的最大字符数 */
    private static final int MAX_TEXT = 240;

    private record Rule(String level, String name, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("ERROR", "错误/异常关键词",
                    Pattern.compile("\\b(ERROR|FATAL|CRITICAL|EXCEPTION)\\b|错误|严重|异常", Pattern.CASE_INSENSITIVE)),
            new Rule("WARN", "警告关键词",
                    Pattern.compile("\\bWARN(ING)?\\b|警告", Pattern.CASE_INSENSITIVE)),
            new Rule("FAIL", "校准/检测失败项",
                    Pattern.compile("(功率|频偏|IQ|RSSI|直流偏移|正交|灵敏度|EVM|杂散|底噪|模板|带宽|丢包|PER|帧同步|时序)[^\\n]{0,24}(FAIL|失败|不过|超差|超限|不合格|未通过|不收敛|未收敛|超窗口)|(FAIL|失败|超差)[^\\n]{0,24}(功率|频偏|IQ|RSSI|灵敏度|EVM)", Pattern.CASE_INSENSITIVE)),
            new Rule("PARAM", "校准参数读数",
                    Pattern.compile("(链路损耗|补偿值|基准功率|目标功率|增益码|增益档位|增益不平衡|偏置电压|迭代|收敛|步长|阈值|温度补偿|检波|晶振|负载电容|时基|环路|分频|FTUNE|DAC|PLL|RSSI|EVM|底噪|杂散|灵敏度|模板|占用带宽|丢包|PER|频偏|IQ|直流偏移|正交|信道|MCS|burst|积分时间|等待延时|平均次数)[^\\n]{0,18}[:：=]\\s*-?\\d[\\d.eE+-]*", Pattern.CASE_INSENSITIVE)),
            new Rule("FAIL", "失败/超限",
                    Pattern.compile("\\bFAIL(ED)?\\b|不通过|超限|out of limit|校验失败", Pattern.CASE_INSENSITIVE)),
            new Rule("TIMEOUT", "超时",
                    Pattern.compile("TIMEOUT|超时", Pattern.CASE_INSENSITIVE)),
            new Rule("CODE", "SCPI 错误队列码",
                    Pattern.compile("-\\d{3}\\s*,\\s*\"")),
            new Rule("CODE", "仪器错误码",
                    Pattern.compile("\\b(?:ERR|E)\\s*-?\\d{2,4}\\b|错误码\\s*[:：]?\\s*\\d+|0x[89a-fA-F][0-9a-fA-F]{2,}")),
            new Rule("ERROR", "通信/连接故障",
                    Pattern.compile("(connection|接口|链路|通信).{0,20}(lost|断开|fail|超时|reset)|GPIB.{0,25}(error|err|timeout|超时)|VISA.{0,25}(error|timeout|超时)", Pattern.CASE_INSENSITIVE))
    );

    /** 宽松时间戳：HH:mm:ss（用于时间窗聚类） */
    private static final Pattern TIME = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})");

    private LogScanner() {
    }

    public static LogScanResult scan(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String content = decode(bytes);
        LogScanResult result = new LogScanResult(file.getFileName().toString());

        List<Integer> errorSeconds = new ArrayList<>();
        List<int[]> errorLineIdx = new ArrayList<>(); // 秒数与行号配对用

        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(content))) {
            String line;
            int no = 0;
            while ((line = reader.readLine()) != null) {
                no++;
                result.incLines();
                if (line.isBlank()) {
                    continue;
                }
                for (Rule rule : RULES) {
                    if (rule.pattern().matcher(line).find()) {
                        if (result.findings().size() >= MAX_FINDINGS) {
                            result.setTruncated(true);
                        } else {
                            result.addFinding(new LogFinding(no, rule.level(), rule.name(), trim(line)));
                        }
                        if ("ERROR".equals(rule.level()) || "CODE".equals(rule.level())) {
                            Integer sec = extractSeconds(line);
                            if (sec != null) {
                                errorSeconds.add(sec);
                                errorLineIdx.add(new int[]{sec, no});
                            }
                        }
                        break; // 每行只记一次
                    }
                }
            }
        }

        detectBursts(result, errorSeconds);
        detectStages(result);
        return result;
    }

    /** 射频产测阶段判定：把 FAIL/ERROR 命中行归入 Tx/Rx 的校准或检测阶段 */
    private static final Pattern STAGE_TX_CAL = Pattern.compile(
            "(发射)?功率校准|频偏校准|IQ\\s*校准|直流偏移校准|(校准.{0,12}(FAIL|失败|超差|不收敛)|不收敛|未收敛)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAGE_TX_DET = Pattern.compile(
            "EVM|频谱模板|杂散|功率稳定度|占用带宽|发包计数|发射时序|功率复测|频偏复测|丢包率?|PER", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAGE_RX_CAL = Pattern.compile(
            "RSSI\\s*校准|接收直流偏移|接收IQ|RX.{0,6}(DC|IQ)\\s*校准", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAGE_RX_DET = Pattern.compile(
            "灵敏度|收包|RSSI\\s*精度|底噪|邻道|最大输入|帧同步", Pattern.CASE_INSENSITIVE);

    private static void detectStages(LogScanResult result) {
        int txCal = 0, txDet = 0, rxCal = 0, rxDet = 0;
        for (LogFinding f : result.findings()) {
            if (!"FAIL".equals(f.level()) && !"ERROR".equals(f.level())) {
                continue;
            }
            String t = f.text();
            if (STAGE_RX_CAL.matcher(t).find()) {
                rxCal++;
            } else if (STAGE_TX_CAL.matcher(t).find()) {
                txCal++;
            } else if (STAGE_RX_DET.matcher(t).find()) {
                rxDet++;
            } else if (STAGE_TX_DET.matcher(t).find()) {
                txDet++;
            }
        }
        if (txCal > 0) {
            result.addCluster("阶段判定：Tx 校准阶段失败 " + txCal + " 条（功率/频偏/IQ 校准不过，属校准收敛或超差问题）");
        }
        if (txDet > 0) {
            result.addCluster("阶段判定：Tx 发包检测阶段失败 " + txDet + " 条（EVM/模板/杂散/复测等，属发包检测不合格）");
        }
        if (rxCal > 0) {
            result.addCluster("阶段判定：Rx 校准阶段失败 " + rxCal + " 条（RSSI/直流偏移/接收 IQ 校准不过）");
        }
        if (rxDet > 0) {
            result.addCluster("阶段判定：Rx 接收检测阶段失败 " + rxDet + " 条（灵敏度/PER/底噪/帧同步等，属收包检测不合格）");
        }
    }

    /** 60 秒窗口内错误 ≥5 视为异常爆发时间窗 */
    private static void detectBursts(LogScanResult result, List<Integer> seconds) {
        if (seconds.size() < 5) {
            return;
        }
        List<Integer> sorted = new ArrayList<>(seconds);
        sorted.sort(Integer::compare);
        int i = 0;
        while (i < sorted.size()) {
            int j = i;
            while (j + 1 < sorted.size() && sorted.get(j + 1) - sorted.get(i) <= 60) {
                j++;
            }
            int count = j - i + 1;
            if (count >= 5) {
                result.addCluster(String.format("%s ~ %s 内集中出现 %d 条错误",
                        fmtSec(sorted.get(i)), fmtSec(sorted.get(j)), count));
                i = j + 1;
            } else {
                i++;
            }
        }
    }

    private static Integer extractSeconds(String line) {
        Matcher m = TIME.matcher(line);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) * 3600
                    + Integer.parseInt(m.group(2)) * 60
                    + Integer.parseInt(m.group(3));
        }
        return null;
    }

    private static String fmtSec(int s) {
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private static String trim(String line) {
        String t = line.strip();
        return t.length() <= MAX_TEXT ? t : t.substring(0, MAX_TEXT) + "…";
    }

    /** 优先按 UTF-8 严格解码，失败回退 GBK */
    private static String decode(byte[] bytes) {
        try {
            CharsetDecoder d = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return d.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }
}
