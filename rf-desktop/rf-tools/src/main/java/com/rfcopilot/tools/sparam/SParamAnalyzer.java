package com.rfcopilot.tools.sparam;

import java.util.ArrayList;
import java.util.List;

/**
 * S 参数关键指标分析：谐振点、回波损耗带宽、传输峰值与 -3dB 带宽。
 */
public final class SParamAnalyzer {

    /** 一条指标（名称 + 中文描述值） */
    public record Metric(String name, String value) {
    }

    private SParamAnalyzer() {
    }

    public static List<Metric> analyze(SParameterData data) {
        List<Metric> metrics = new ArrayList<>();
        if (data.pointCount() < 2) {
            metrics.add(new Metric("数据点", String.valueOf(data.pointCount())));
            return metrics;
        }
        metrics.add(new Metric("频范围", formatHz(data.startFreqHz()) + " ~ " + formatHz(data.stopFreqHz())));
        metrics.add(new Metric("数据点数", String.valueOf(data.pointCount())));
        metrics.add(new Metric("参考阻抗", trimNum(data.referenceImpedance()) + " Ω"));

        if (data.db("S11") != null) {
            analyzeReflection(metrics, "S11", data.db("S11"), data.freqHz());
        }
        if (data.db("S21") != null) {
            analyzeTransmission(metrics, "S21", data.db("S21"), data.freqHz());
        }
        return metrics;
    }

    /** 反射参数：谐振点（|S|最小）与 -10dB 带宽 */
    private static void analyzeReflection(List<Metric> metrics, String param, double[] db, double[] freq) {
        int minIdx = 0;
        for (int i = 1; i < db.length; i++) {
            if (db[i] < db[minIdx]) {
                minIdx = i;
            }
        }
        metrics.add(new Metric(param + " 谐振点",
                formatHz(freq[minIdx]) + "（" + trimNum(db[minIdx]) + " dB）"));

        double threshold = -10.0;
        int belowCount = 0;
        for (double v : db) {
            if (v <= threshold) {
                belowCount++;
            }
        }
        if (belowCount >= 2) {
            double[] band = crossingBand(db, freq, threshold, true);
            if (band != null) {
                metrics.add(new Metric(param + " ≤-10dB 带宽",
                        formatHz(band[1] - band[0]) + "（" + formatHz(band[0]) + " ~ " + formatHz(band[1]) + "）"));
            }
        } else {
            metrics.add(new Metric(param + " ≤-10dB 带宽", "无满足 -10dB 的连续区间"));
        }
    }

    /** 传输参数：峰值与 -3dB 带宽 */
    private static void analyzeTransmission(List<Metric> metrics, String param, double[] db, double[] freq) {
        int peakIdx = 0;
        for (int i = 1; i < db.length; i++) {
            if (db[i] > db[peakIdx]) {
                peakIdx = i;
            }
        }
        double peakDb = db[peakIdx];
        double threshold = peakDb - 3.0;
        metrics.add(new Metric(param + " 峰值",
                formatHz(freq[peakIdx]) + "（" + trimNum(peakDb) + " dB）"));

        // 从峰值向两侧搜索 -3dB 交点
        double left = -1, right = -1;
        for (int i = peakIdx; i > 0; i--) {
            if (db[i] >= threshold && db[i - 1] < threshold) {
                left = interpolate(freq[i - 1], db[i - 1], freq[i], db[i], threshold);
                break;
            }
        }
        for (int i = peakIdx; i < db.length - 1; i++) {
            if (db[i] >= threshold && db[i + 1] < threshold) {
                right = interpolate(freq[i], db[i], freq[i + 1], db[i + 1], threshold);
                break;
            }
        }
        if (left > 0 && right > 0) {
            metrics.add(new Metric(param + " -3dB 带宽",
                    formatHz(right - left) + "（" + formatHz(left) + " ~ " + formatHz(right) + "）"));
        } else {
            metrics.add(new Metric(param + " -3dB 带宽", "通带在扫频范围内未闭合"));
        }
    }

    /** 返回参数 <= threshold 的最大连续区间 [start, stop]（线性插值），无则 null */
    private static double[] crossingBand(double[] db, double[] freq, double threshold, boolean below) {
        double bestStart = -1, bestStop = -1;
        double bestWidth = -1;
        int i = 0;
        while (i < db.length) {
            boolean in = below ? db[i] <= threshold : db[i] >= threshold;
            if (in) {
                int j = i;
                while (j + 1 < db.length && (below ? db[j + 1] <= threshold : db[j + 1] >= threshold)) {
                    j++;
                }
                double width = freq[j] - freq[i];
                if (width > bestWidth) {
                    bestWidth = width;
                    bestStart = freq[i];
                    bestStop = freq[j];
                }
                i = j + 1;
            } else {
                i++;
            }
        }
        return bestWidth > 0 ? new double[]{bestStart, bestStop} : null;
    }

    private static double interpolate(double f1, double d1, double f2, double d2, double threshold) {
        if (Math.abs(d2 - d1) < 1e-12) {
            return (f1 + f2) / 2;
        }
        return f1 + (threshold - d1) / (d2 - d1) * (f2 - f1);
    }

    /** 频率格式化为 GHz/MHz/kHz/Hz */
    public static String formatHz(double hz) {
        if (hz >= 1e9) {
            return trimNum(hz / 1e9) + " GHz";
        }
        if (hz >= 1e6) {
            return trimNum(hz / 1e6) + " MHz";
        }
        if (hz >= 1e3) {
            return trimNum(hz / 1e3) + " kHz";
        }
        return trimNum(hz) + " Hz";
    }

    private static String trimNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) {
            return String.valueOf(Math.rint(v));
        }
        return String.format("%.2f", v);
    }
}
