package com.rfcopilot.app.ui;

import com.rfcopilot.tools.sparam.SParamAnalyzer;
import com.rfcopilot.tools.sparam.SParameterData;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * S 参数曲线绘制（幅值 dB vs 频率），基于 Canvas 的轻量图表。
 */
public final class SParamChart {

    private static final Color BG = Color.web("#1a1a1a");
    private static final Color GRID = Color.web("#2e2e2e");
    private static final Color AXIS_TEXT = Color.web("#9a9a9a");
    private static final Color[] SERIES = {
            Color.web("#4fd1a5"), Color.web("#5b9bd5"),
            Color.web("#e0a458"), Color.web("#c678dd")
    };

    private SParamChart() {
    }

    public static void draw(Canvas canvas, SParameterData data, Set<String> visible) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.setFill(BG);
        g.fillRect(0, 0, w, h);

        List<String> params = new ArrayList<>(visible.stream().filter(p -> data.db(p) != null).toList());
        if (data == null || params.isEmpty() || data.pointCount() < 2) {
            g.setFill(AXIS_TEXT);
            g.fillText("加载 .s1p/.s2p 文件后显示曲线", 30, 30);
            return;
        }

        double mL = 70, mR = 20, mT = 20, mB = 46;
        double plotW = w - mL - mR;
        double plotH = h - mT - mB;

        double fMin = data.startFreqHz();
        double fMax = data.stopFreqHz();
        double dbMin = Double.MAX_VALUE, dbMax = -Double.MAX_VALUE;
        for (String p : params) {
            for (double v : data.db(p)) {
                dbMin = Math.min(dbMin, v);
                dbMax = Math.max(dbMax, v);
            }
        }
        // 取整到 5dB 边界，留白
        dbMin = Math.floor(dbMin / 5) * 5 - 5;
        dbMax = Math.ceil(dbMax / 5) * 5 + 5;
        if (dbMax - dbMin < 20) {
            dbMax = dbMin + 20;
        }

        // 网格与刻度
        g.setStroke(GRID);
        g.setLineWidth(1);
        g.setFill(AXIS_TEXT);
        int xTicks = 8;
        for (int i = 0; i <= xTicks; i++) {
            double f = fMin + (fMax - fMin) * i / xTicks;
            double x = mL + plotW * i / xTicks;
            g.strokeLine(x, mT, x, mT + plotH);
            String label = SParamAnalyzer.formatHz(f).replace(" ", "\u00A0");
            g.fillText(label, x - label.length() * 3.2, mT + plotH + 18);
        }
        int yStep = Math.max(5, (int) ((dbMax - dbMin) / 8 / 5) * 5);
        for (double db = dbMin; db <= dbMax; db += yStep) {
            double y = mT + plotH * (1 - (db - dbMin) / (dbMax - dbMin));
            g.strokeLine(mL, y, mL + plotW, y);
            String label = String.format("%.0f dB", db);
            g.fillText(label, 12, y + 4);
        }

        // 坐标轴
        g.setStroke(Color.web("#555555"));
        g.strokeLine(mL, mT, mL, mT + plotH);
        g.strokeLine(mL, mT + plotH, mL + plotW, mT + plotH);

        // 曲线
        double[] freq = data.freqHz();
        for (int s = 0; s < params.size(); s++) {
            String p = params.get(s);
            double[] db = data.db(p);
            g.setStroke(SERIES[s % SERIES.length]);
            g.setLineWidth(2);
            for (int i = 0; i < db.length - 1; i++) {
                double x1 = mL + plotW * (freq[i] - fMin) / (fMax - fMin);
                double y1 = mT + plotH * (1 - (db[i] - dbMin) / (dbMax - dbMin));
                double x2 = mL + plotW * (freq[i + 1] - fMin) / (fMax - fMin);
                double y2 = mT + plotH * (1 - (db[i + 1] - dbMin) / (dbMax - dbMin));
                g.strokeLine(x1, y1, x2, y2);
            }
        }

        // 图例
        double lx = mL + 12;
        for (int s = 0; s < params.size(); s++) {
            g.setFill(SERIES[s % SERIES.length]);
            g.fillRoundRect(lx, mT + 8, 26, 4, 2, 2);
            g.setFill(Color.web("#dddddd"));
            g.fillText(params.get(s), lx + 32, mT + 15);
            lx += 32 + params.get(s).length() * 8 + 18;
        }
    }
}
