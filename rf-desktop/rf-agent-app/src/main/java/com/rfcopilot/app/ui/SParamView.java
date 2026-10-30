package com.rfcopilot.app.ui;

import com.rfcopilot.tools.sparam.SParamAnalyzer;
import com.rfcopilot.tools.sparam.SParameterData;
import com.rfcopilot.tools.sparam.TouchstoneParser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;

/**
 * S 参数工具视图：打开 Touchstone 文件 → 曲线绘制 + 关键指标。
 */
public class SParamView extends BorderPane {

    private final Canvas canvas = new Canvas(860, 420);
    private final ListView<String> metricList = new ListView<>();
    private final Label fileInfo = new Label("未加载文件");
    private final HBox paramBar = new HBox(14);
    private SParameterData data;

    public SParamView() {
        setTop(buildToolbar());
        setCenter(buildChartArea());
        setRight(buildMetricsPane());
    }

    private HBox buildToolbar() {
        Button openBtn = new Button("打开 .s1p / .s2p 文件");
        openBtn.getStyleClass().add("send-btn");
        openBtn.setOnAction(e -> chooseFile());

        paramBar.setAlignment(Pos.CENTER_LEFT);
        HBox box = new HBox(16, openBtn, paramBar);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 16, 10, 16));
        box.getStyleClass().add("chat-header");
        return box;
    }

    private VBox buildChartArea() {
        canvas.widthProperty().bind(widthProperty().subtract(340));
        canvas.heightProperty().bind(heightProperty().subtract(140));
        canvas.widthProperty().addListener((o, a, b) -> redraw());
        canvas.heightProperty().addListener((o, a, b) -> redraw());

        VBox box = new VBox(6, canvas, fileInfo);
        box.setPadding(new Insets(12, 16, 12, 16));
        VBox.setVgrow(canvas, Priority.ALWAYS);
        return box;
    }

    private VBox buildMetricsPane() {
        Label title = new Label("关键指标");
        title.getStyleClass().add("section-title");
        metricList.setPrefWidth(300);
        metricList.getStyleClass().add("session-list");
        VBox box = new VBox(8, title, metricList);
        box.setPadding(new Insets(12));
        box.setPrefWidth(320);
        box.getStyleClass().add("tool-panel");
        VBox.setVgrow(metricList, Priority.ALWAYS);
        return box;
    }

    private void chooseFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择 Touchstone 文件");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Touchstone (*.s1p *.s2p *.s4p)", "*.s1p", "*.s2p", "*.s4p"),
                new FileChooser.ExtensionFilter("所有文件 (*.*)", "*.*"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            load(file);
        }
    }

    private void load(File file) {
        try {
            data = TouchstoneParser.parse(file.toPath());
            fileInfo.setText(file.getName() + "　" + data.ports() + " 端口　"
                    + data.pointCount() + " 个频点　参考阻抗 " + (int) data.referenceImpedance() + " Ω");
            rebuildParamBar();
            rebuildMetrics();
            redraw();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "解析失败：" + ex.getMessage());
            alert.setHeaderText("Touchstone 文件格式错误");
            alert.showAndWait();
        }
    }

    private void rebuildParamBar() {
        paramBar.getChildren().clear();
        for (String p : data.params()) {
            CheckBox cb = new CheckBox(p);
            cb.setSelected(true);
            cb.getStyleClass().add("param-check");
            cb.setOnAction(e -> {
                rebuildMetrics();
                redraw();
            });
            paramBar.getChildren().add(cb);
        }
    }

    private void rebuildMetrics() {
        metricList.getItems().clear();
        if (data == null) {
            return;
        }
        for (SParamAnalyzer.Metric m : SParamAnalyzer.analyze(data)) {
            metricList.getItems().add(m.name() + "：\u3000" + m.value());
        }
    }

    private void redraw() {
        if (data == null) {
            SParamChart.draw(canvas, null, java.util.Set.of());
            return;
        }
        java.util.Set<String> visible = new java.util.LinkedHashSet<>();
        for (var node : paramBar.getChildren()) {
            if (node instanceof CheckBox cb && cb.isSelected()) {
                visible.add(cb.getText());
            }
        }
        SParamChart.draw(canvas, data, visible);
    }
}
