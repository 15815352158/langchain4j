package com.rfcopilot.app.ui;

import com.rfcopilot.app.AppContext;
import com.rfcopilot.core.config.ModelProfile;
import com.rfcopilot.core.model.ChatModelFactory;
import com.rfcopilot.tools.loganalysis.LogFinding;
import com.rfcopilot.tools.loganalysis.LogPromptBuilder;
import com.rfcopilot.tools.loganalysis.LogScanResult;
import com.rfcopilot.tools.loganalysis.LogScanner;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;

/**
 * 日志分析工具视图：规则预筛 → 命中列表 → 一键 AI 流式分析。
 */
public class LogView extends BorderPane {

    private final AppContext ctx;
    private final Label statsLabel = new Label("未加载日志文件");
    private final TableView<LogFinding> table = new TableView<>();
    private final TextArea aiResult = new TextArea();
    private final Button analyzeBtn = new Button("AI 分析");
    private LogScanResult scan;
    private boolean streaming;

    public LogView(AppContext ctx) {
        this.ctx = ctx;
        setTop(buildToolbar());
        // 可拖拽分隔条：命中记录区与 AI 分析区此消彼长
        SplitPane split = new SplitPane(buildTable(), buildResultArea());
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.55);
        setCenter(split);
    }

    private HBox buildToolbar() {
        Button openBtn = new Button("打开日志文件");
        openBtn.getStyleClass().add("send-btn");
        openBtn.setOnAction(e -> chooseFile());

        analyzeBtn.getStyleClass().add("send-btn");
        analyzeBtn.setDisable(true);
        analyzeBtn.setOnAction(e -> analyze());

        statsLabel.getStyleClass().add("tool-note");
        HBox box = new HBox(12, openBtn, analyzeBtn, statsLabel);
        box.setPadding(new Insets(10, 16, 10, 16));
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.getStyleClass().add("chat-header");
        return box;
    }

    private VBox buildTable() {
        TableColumn<LogFinding, Number> cLine = new TableColumn<>("行号");
        cLine.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().lineNo()));
        cLine.setPrefWidth(70);
        TableColumn<LogFinding, String> cLevel = new TableColumn<>("级别");
        cLevel.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().level()));
        cLevel.setPrefWidth(80);
        TableColumn<LogFinding, String> cRule = new TableColumn<>("命中规则");
        cRule.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().rule()));
        cRule.setPrefWidth(140);
        TableColumn<LogFinding, String> cText = new TableColumn<>("日志原文");
        cText.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().text()));
        cText.setPrefWidth(700);
        table.getColumns().setAll(cLine, cLevel, cRule, cText);
        table.getStyleClass().add("session-list");
        table.setPlaceholder(new Label("打开日志文件后显示预筛命中记录"));

        VBox box = new VBox(table);
        box.setPadding(new Insets(8, 16, 4, 16));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private VBox buildResultArea() {
        Label title = new Label("AI 分析结果");
        title.getStyleClass().add("section-title");
        aiResult.setEditable(false);
        aiResult.setWrapText(true);
        aiResult.setPrefRowCount(8);
        aiResult.setPromptText("加载日志后点击「AI 分析」，结果将流式显示在这里");
        aiResult.getStyleClass().add("input-area");
        VBox box = new VBox(6, title, aiResult);
        box.setPadding(new Insets(4, 16, 12, 16));
        VBox.setVgrow(aiResult, Priority.ALWAYS);
        return box;
    }

    private void chooseFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择日志文件");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("日志文件 (*.log *.txt *.csv *.out)", "*.log", "*.txt", "*.csv", "*.out"),
                new FileChooser.ExtensionFilter("所有文件 (*.*)", "*.*"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            scan = LogScanner.scan(file.toPath());
            table.getItems().setAll(scan.findings());
            statsLabel.setText(scan.statsLine());
            analyzeBtn.setDisable(!scan.hasIssues());
            aiResult.clear();
            if (!scan.clusters().isEmpty()) {
                aiResult.appendText("检测到异常时间窗：\n");
                scan.clusters().forEach(c -> aiResult.appendText("  - " + c + "\n"));
            }
        } catch (Exception ex) {
            statsLabel.setText("读取失败：" + ex.getMessage());
        }
    }

    private void analyze() {
        if (scan == null || streaming) {
            return;
        }
        ModelProfile profile = ctx.getConfig().activeProfile();
        if (profile == null) {
            aiResult.setText("请先在主界面「设置」中配置并选择模型档位");
            return;
        }
        streaming = true;
        analyzeBtn.setDisable(true);
        aiResult.clear();
        aiResult.appendText("正在分析（模型：" + profile.getModelName() + "）…\n\n");

        StringBuilder sb = new StringBuilder();
        try {
            StreamingChatModel model = ChatModelFactory.createStreaming(profile);
            model.chat(java.util.List.of(UserMessage.from(LogPromptBuilder.build(scan))),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String token) {
                            Platform.runLater(() -> {
                                sb.append(token);
                                aiResult.setText(sb.toString());
                            });
                        }

                        @Override
                        public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                            Platform.runLater(LogView.this::endStreaming);
                        }

                        @Override
                        public void onError(Throwable error) {
                            Platform.runLater(() -> {
                                aiResult.appendText("\n请求失败：" + error.getMessage());
                                endStreaming();
                            });
                        }
                    });
        } catch (Exception e) {
            aiResult.appendText("请求失败：" + e.getMessage());
            endStreaming();
        }
    }

    private void endStreaming() {
        streaming = false;
        analyzeBtn.setDisable(scan == null || !scan.hasIssues());
    }
}
