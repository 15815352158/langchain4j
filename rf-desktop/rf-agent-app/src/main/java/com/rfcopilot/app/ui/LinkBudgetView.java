package com.rfcopilot.app.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 链路预算计算器：自由空间路径损耗（FSPL）与接收功率/链路余量。
 */
public class LinkBudgetView extends VBox {

    private final TextField ptField = num("30", "dBm");
    private final TextField gtField = num("10", "dBi");
    private final TextField grField = num("10", "dBi");
    private final TextField lossField = num("2", "dB（线缆/接头总损耗）");
    private final TextField freqField = num("2400", "MHz");
    private final TextField distField = num("1", "km");
    private final TextField sensField = num("-85", "dBm（接收灵敏度）");
    private final Label result = new Label();

    public LinkBudgetView() {
        setPadding(new Insets(20));
        setSpacing(14);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        int r = 0;
        grid.addRow(r++, new Label("发射功率 Pt："), ptField);
        grid.addRow(r++, new Label("发射天线增益 Gt："), gtField);
        grid.addRow(r++, new Label("接收天线增益 Gr："), grField);
        grid.addRow(r++, new Label("馈线/接头损耗 Lf："), lossField);
        grid.addRow(r++, new Label("工作频率 f："), freqField);
        grid.addRow(r++, new Label("传输距离 d："), distField);
        grid.addRow(r++, new Label("接收灵敏度："), sensField);

        Button calc = new Button("计算");
        calc.getStyleClass().add("send-btn");
        calc.setOnAction(e -> calculate());

        result.setWrapText(true);
        result.getStyleClass().add("msg");
        result.setStyle("-fx-padding: 14; -fx-font-size: 14px;");
        result.setMaxWidth(Double.MAX_VALUE);

        // 结果区：默认占满剩余空间（右/下随窗口 20px 内边距对齐），内容超高时出垂直滚动条
        ScrollPane resultScroll = new ScrollPane(result);
        resultScroll.getStyleClass().add("link-result-scroll");
        // 水平：内容始终撑满可视区宽度（与输入框同宽）；垂直：不压缩，放不下才出滚动条
        resultScroll.setFitToWidth(true);
        resultScroll.setFitToHeight(false);
        resultScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        resultScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        resultScroll.setPrefHeight(160);
        VBox.setVgrow(resultScroll, Priority.ALWAYS);

        getChildren().addAll(new Label("自由空间链路预算（FSPL = 32.44 + 20·log₁₀f[MHz] + 20·log₁₀d[km]）"),
                grid, calc, resultScroll);
    }

    private TextField num(String def, String prompt) {
        TextField tf = new TextField(def);
        tf.setPromptText(prompt);
        tf.setPrefColumnCount(14);
        return tf;
    }

    private void calculate() {
        try {
            double pt = Double.parseDouble(ptField.getText().strip());
            double gt = Double.parseDouble(gtField.getText().strip());
            double gr = Double.parseDouble(grField.getText().strip());
            double lf = Double.parseDouble(lossField.getText().strip());
            double fMhz = Double.parseDouble(freqField.getText().strip());
            double dKm = Double.parseDouble(distField.getText().strip());
            double sens = Double.parseDouble(sensField.getText().strip());

            double fspl = 32.44 + 20 * Math.log10(fMhz) + 20 * Math.log10(dKm);
            double prx = pt - lf + gt + gr - fspl;
            double margin = prx - sens;

            result.setText(String.format(
                    """
                    自由空间路径损耗 FSPL：%.2f dB
                    接收功率 Prx：%.2f dBm
                    链路余量（Prx − 灵敏度）：%.2f dB
                    %s""",
                    fspl, prx, margin,
                    margin >= 10 ? "✅ 余量充足（建议工程上预留 ≥10 dB）"
                            : margin >= 0 ? "⚠️ 余量偏小，易受衰落/干扰影响，建议增加增益或缩短距离"
                            : "❌ 链路不可达，接收功率低于灵敏度"));
        } catch (NumberFormatException e) {
            result.setText("请输入有效的数值");
        }
    }
}
