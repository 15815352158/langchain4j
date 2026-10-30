package com.rfcopilot.tools.sparam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Touchstone (.s1p/.s2p) 文件解析器。
 * 支持 HZ/KHZ/MHZ/GHZ 频率单位、RI/MA/DB 数据格式、参考阻抗 R，
 * 端口数由数据列数自动推断（1/2/4 端口）。
 */
public final class TouchstoneParser {

    private TouchstoneParser() {
    }

    public static SParameterData parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return parse(lines);
    }

    public static SParameterData parse(List<String> lines) {
        double freqScale = 1.0;      // 默认 HZ
        String format = "RI";        // 默认 实部/虚部
        double refZ = 50.0;
        List<double[]> rows = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            char first = line.charAt(0);
            if (first == '!') {
                continue; // 注释
            }
            if (first == '#') {
                String[] tok = line.substring(1).strip().toUpperCase().split("\\s+");
                for (int i = 0; i < tok.length; i++) {
                    switch (tok[i]) {
                        case "HZ" -> freqScale = 1;
                        case "KHZ" -> freqScale = 1e3;
                        case "MHZ" -> freqScale = 1e6;
                        case "GHZ" -> freqScale = 1e9;
                        case "RI", "MA", "DB" -> format = tok[i];
                        case "R" -> {
                            if (i + 1 < tok.length) {
                                refZ = Double.parseDouble(tok[i + 1]);
                            }
                        }
                        default -> { /* 忽略未知选项 */ }
                    }
                }
                continue;
            }
            String[] tok = line.split("\\s+");
            double[] vals = new double[tok.length];
            for (int i = 0; i < tok.length; i++) {
                vals[i] = Double.parseDouble(tok[i]);
            }
            rows.add(vals);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("文件中没有有效的数据行");
        }

        int cols = rows.get(0).length;
        int ports = switch (cols) {
            case 3 -> 1;   // f + 1 个复数
            case 9 -> 2;   // f + 4 个复数
            case 17 -> 4;
            default -> throw new IllegalArgumentException("不支持的数据列数：" + cols + "（仅支持 1/2/4 端口）");
        };

        double[] freq = new double[rows.size()];
        double[][] re = new double[ports * ports][rows.size()];
        double[][] im = new double[ports * ports][rows.size()];

        for (int r = 0; r < rows.size(); r++) {
            double[] row = rows.get(r);
            freq[r] = row[0] * freqScale;
            for (int p = 0; p < ports * ports; p++) {
                double a = row[1 + p * 2];
                double b = row[2 + p * 2];
                switch (format) {
                    case "RI" -> {
                        re[p][r] = a;
                        im[p][r] = b;
                    }
                    case "MA" -> {
                        re[p][r] = a * Math.cos(Math.toRadians(b));
                        im[p][r] = a * Math.sin(Math.toRadians(b));
                    }
                    case "DB" -> {
                        double mag = Math.pow(10, a / 20.0);
                        re[p][r] = mag * Math.cos(Math.toRadians(b));
                        im[p][r] = mag * Math.sin(Math.toRadians(b));
                    }
                    default -> throw new IllegalStateException("未知格式: " + format);
                }
            }
        }

        SParameterData data = new SParameterData(freq, ports, refZ);
        // Touchstone 列顺序为 S11 S21 S12 S22（列主序）
        for (int j = 0; j < ports; j++) {
            for (int i = 0; i < ports; i++) {
                int p = j * ports + i;
                String name = "S" + (i + 1) + (j + 1);
                double[] db = new double[rows.size()];
                double[] phase = new double[rows.size()];
                for (int r = 0; r < rows.size(); r++) {
                    double mag = Math.hypot(re[p][r], im[p][r]);
                    db[r] = mag <= 0 ? -120 : 20 * Math.log10(mag);
                    phase[r] = Math.toDegrees(Math.atan2(im[p][r], re[p][r]));
                }
                data.put(name, db, phase);
            }
        }
        return data;
    }
}
