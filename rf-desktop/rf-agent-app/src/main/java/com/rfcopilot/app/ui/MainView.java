package com.rfcopilot.app.ui;

import com.rfcopilot.app.AppContext;
import com.rfcopilot.core.session.ChatMessageDto;
import com.rfcopilot.core.session.ChatSession;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 主界面：仿 Codex 桌面版布局（左侧栏 / 中央对话区 / 右侧工具面板占位），全中文。
 */
public class MainView extends BorderPane {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppContext ctx;

    private final ListView<ChatSession> sessionListView = new ListView<>();
    private final VBox chatBox = new VBox(12);
    private final ScrollPane scrollPane = new ScrollPane(chatBox);
    private final TextArea inputArea = new TextArea();
    private final Button sendButton = new Button("发送");
    private final Label headerTitle = new Label("新会话");
    private final Label headerModel = new Label();

    private ChatSession currentSession;
    private boolean streaming;
    private boolean refreshingSessions;

    public MainView(AppContext ctx) {
        this.ctx = ctx;
        buildUi();
        refreshSessions();
        updateModelLabel();
    }

    private void buildUi() {
        setLeft(buildSidebar());
        setCenter(buildChatArea());
        setRight(buildToolPanel());
    }

    // ---------- 左侧栏 ----------

    private VBox buildSidebar() {
        Label logo = new Label("RF Copilot");
        logo.getStyleClass().add("logo");

        Button newBtn = new Button("＋  新建会话");
        newBtn.getStyleClass().add("sidebar-primary");
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setOnAction(e -> newSession());

        Label historyLabel = new Label("历史会话");
        historyLabel.getStyleClass().add("section-title");

        sessionListView.getStyleClass().add("session-list");
        sessionListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChatSession item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle());
                    setTooltip(new javafx.scene.control.Tooltip(
                            item.getTitle() + "\n更新于 " + item.getUpdatedAt()));
                }
            }
        });
        sessionListView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null && !refreshingSessions && !streaming) {
                openSession(sel);
            }
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button kbBtn = new Button("知识库");
        kbBtn.setMaxWidth(Double.MAX_VALUE);
        kbBtn.setOnAction(e -> openKnowledgeWindow());

        Button settingsBtn = new Button("设置");
        settingsBtn.setMaxWidth(Double.MAX_VALUE);
        settingsBtn.setOnAction(e -> openSettings());

        VBox box = new VBox(10, logo, newBtn, historyLabel, sessionListView, spacer, kbBtn, settingsBtn);
        box.getStyleClass().add("sidebar");
        box.setPadding(new Insets(16, 12, 16, 12));
        VBox.setVgrow(sessionListView, Priority.ALWAYS);
        return box;
    }

    // ---------- 中央对话区 ----------

    private BorderPane buildChatArea() {
        headerTitle.getStyleClass().add("header-title");
        headerModel.getStyleClass().add("header-model");
        HBox header = new HBox(12, headerTitle, headerModel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 10, 20));
        header.getStyleClass().add("chat-header");

        chatBox.setPadding(new Insets(8, 24, 16, 24));
        scrollPane.setContent(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("chat-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // 欢迎提示
        Label welcome = new Label("我是你的射频工程师助手，可以帮你：调板排障、解读 S 参数与测试数据、"
                + "分析日志异常、编写 SCPI 指令。直接在下方输入问题开始吧。");
        welcome.getStyleClass().addAll("msg", "msg-assistant");
        welcome.setWrapText(true);
        welcome.setMaxWidth(780);
        chatBox.getChildren().add(welcome);

        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        inputArea.setMaxWidth(Double.MAX_VALUE);
        inputArea.getStyleClass().add("input-area");
        inputArea.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER
                    && !e.isShiftDown() && !e.isControlDown()) {
                e.consume();
                send();
            }
        });

        sendButton.getStyleClass().add("send-btn");
        sendButton.setOnAction(e -> send());
        // 输入框与发送按钮同行：输入框占满整行，按钮在右侧且与输入框等高
        HBox inputRow = new HBox(8, inputArea, sendButton);
        HBox.setHgrow(inputArea, Priority.ALWAYS);
        sendButton.setMinWidth(88);
        sendButton.setPrefWidth(88);
        sendButton.maxHeightProperty().bind(inputRow.heightProperty());
        VBox inputCol = new VBox(inputRow);
        inputCol.setPadding(new Insets(10, 24, 16, 24));

        VBox center = new VBox(header, scrollPane, inputCol);
        center.getStyleClass().add("chat-center");
        BorderPane pane = new BorderPane(center);
        // 拖拽文件到对话区：先收录向量化，再继续提问
        pane.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
        });
        pane.setOnDragDropped(e -> {
            if (e.getDragboard().hasFiles()) {
                handleFileDrop(e.getDragboard().getFiles());
                e.setDropCompleted(true);
                e.consume();
            }
        });
        return pane;
    }

    /** 处理拖入的文件：后台收录进知识库，完成后重建检索器；若输入框已有问题则自动发送 */
    private void handleFileDrop(java.util.List<java.io.File> files) {
        var supported = new java.util.ArrayList<java.io.File>();
        var rejected = new java.util.ArrayList<java.io.File>();
        for (var f : files) {
            if (com.rfcopilot.knowledge.KnowledgeBase.isSupported(f.toPath())) {
                supported.add(f);
            } else {
                rejected.add(f);
            }
        }
        if (supported.isEmpty()) {
            addBubble("msg-error", "拖入的文件类型都不支持。支持："
                    + String.join(" ", com.rfcopilot.knowledge.KnowledgeBase.SUPPORTED_EXTENSIONS));
            return;
        }
        String pending = inputArea.getText().strip();
        var status = addBubble("msg-assistant", "正在向量化 " + supported.size() + " 个文件…");
        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            if (!rejected.isEmpty()) {
                report.append("不支持已跳过：")
                        .append(rejected.stream().map(java.io.File::getName)
                                .collect(java.util.stream.Collectors.joining("、")))
                        .append('\n');
            }
            var profile = ctx.getConfig().embeddingProfile();
            for (var f : supported) {
                var p = f.toPath();
                String name = f.getName();
                try {
                    if (ctx.getKnowledgeBase().contains(p)) {
                        report.append("《").append(name).append("》已在知识库中\n");
                        continue;
                    }
                    int chunks = ctx.getKnowledgeBase().importFile(p, profile,
                            msg -> Platform.runLater(() -> status.setText("正在向量化《" + name + "》：" + msg)));
                    report.append("已收录《").append(name).append("》，共 ").append(chunks).append(" 个片段\n");
                } catch (Throwable ex) {
                    report.append("《").append(name).append("》收录失败：").append(ex.getMessage()).append('\n');
                }
            }
            Platform.runLater(() -> {
                ctx.rebuildAgent();
                updateModelLabel();
                status.setText(report.toString().strip());
                scrollToBottom();
                if (!pending.isEmpty() && !streaming) {
                    send();
                }
            });
        }, "kb-drop-import").start();
    }

    // ---------- 右侧工具面板（占位） ----------

    private VBox buildToolPanel() {
        Label title = new Label("工具面板");
        title.getStyleClass().add("section-title");
        VBox panel = new VBox(10, title,
                toolCard("S 参数解析与绘图", "Touchstone 文件 · 谐振点/带宽分析", this::openSParamWindow),
                toolCard("链路预算计算", "FSPL · 接收功率 · 链路余量", this::openLinkBudgetWindow),
                toolCard("日志分析", "规则预筛 · AI 异常定位与排查建议", this::openLogWindow),
                toolCard("知识库 RAG", "收录文档 · 回答自动检索参考", this::openKnowledgeWindow),
                toolCard("SCPI 仪表控制", "TCP 直连 · 命令终端 · AI 操作需人工确认", this::openScpiWindow));
        panel.getStyleClass().add("tool-panel");
        panel.setPadding(new Insets(16, 12, 16, 12));
        panel.setMinWidth(240);
        panel.setPrefWidth(260);
        return panel;
    }

    private VBox toolCard(String name, String note, Runnable action) {
        Label l1 = new Label(name);
        l1.getStyleClass().add("tool-name");
        Label l2 = new Label(note);
        l2.getStyleClass().add("tool-note");
        l2.setWrapText(true);
        Button open = new Button("打开");
        open.getStyleClass().add("tool-open");
        open.setOnAction(e -> action.run());
        HBox head = new HBox(10, l1, open);
        head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(l1, javafx.scene.layout.Priority.ALWAYS);
        VBox box = new VBox(8, head, l2);
        box.getStyleClass().add("tool-card");
        return box;
    }

    private void openSParamWindow() {
        openToolWindow("S 参数解析与绘图", new SParamView(), 1000, 680);
    }

    private void openLinkBudgetWindow() {
        openToolWindow("链路预算计算器", new LinkBudgetView(), 560, 520);
    }

    private void openLogWindow() {
        openToolWindow("日志分析", new LogView(ctx), 1080, 720);
    }

    private void openKnowledgeWindow() {
        openToolWindow("知识库", new KnowledgeView(ctx, this::updateModelLabel), 860, 600);
    }

    private void openScpiWindow() {
        openToolWindow("SCPI 仪表控制", new ScpiView(ctx), 920, 620);
    }

    private void openToolWindow(String name, javafx.scene.Parent content, double w, double h) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.initOwner(getScene().getWindow());
        stage.setTitle(name + " · RF Copilot");
        var icon = MainView.class.getResource("/app-icon.png");
        if (icon != null) {
            stage.getIcons().add(new javafx.scene.image.Image(icon.toExternalForm()));
        }
        javafx.scene.Scene scene = new javafx.scene.Scene(content, w, h);
        com.rfcopilot.app.ThemeManager.apply(scene);
        stage.setScene(scene);
        stage.show();
    }

    // ---------- 会话操作 ----------

    private void newSession() {
        if (streaming) {
            return;
        }
        currentSession = null;
        refreshingSessions = true;
        sessionListView.getSelectionModel().clearSelection();
        refreshingSessions = false;
        chatBox.getChildren().clear();
        headerTitle.setText("新会话");
        inputArea.requestFocus();
    }

    private void openSession(ChatSession session) {
        currentSession = session;
        headerTitle.setText(session.getTitle());
        chatBox.getChildren().clear();
        for (ChatMessageDto dto : session.getMessages()) {
            if (ChatMessageDto.ROLE_USER.equals(dto.getRole())) {
                addBubble("msg-user", dto.getText());
            } else {
                MarkdownBubble mb = addMarkdownBubble();
                mb.setMarkdownNow(dto.getText());
            }
        }
        scrollToBottom();
    }

    private void refreshSessions() {
        refreshingSessions = true;
        var items = sessionListView.getItems();
        items.setAll(ctx.getSessionStore().listSessions());
        if (currentSession != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getId().equals(currentSession.getId())) {
                    sessionListView.getSelectionModel().select(i);
                    break;
                }
            }
        } else {
            sessionListView.getSelectionModel().clearSelection();
        }
        refreshingSessions = false;
    }

    // ---------- 发送与流式渲染 ----------

    private void send() {
        String text = inputArea.getText().strip();
        if (text.isEmpty() || streaming) {
            return;
        }
        if (currentSession == null) {
            currentSession = new ChatSession();
        }
        currentSession.deriveTitleFromFirstMessage(text);
        currentSession.getMessages().add(
                new ChatMessageDto(ChatMessageDto.ROLE_USER, text, LocalDateTime.now().format(FMT)));
        headerTitle.setText(currentSession.getTitle());

        addBubble("msg-user", text);
        inputArea.clear();

        var assistantBubble = addMarkdownBubble();
        StringBuilder sb = new StringBuilder();
        streaming = true;
        sendButton.setDisable(true);
        inputArea.setDisable(true);

        final ChatSession session = currentSession;
        try {
            ctx.getAgent().chat(session.getId(), text)
                    .onPartialResponse(token -> Platform.runLater(() -> {
                        sb.append(token);
                        assistantBubble.setMarkdown(sb.toString());
                        scrollToBottom();
                    }))
                    .onCompleteResponse(response -> Platform.runLater(() -> {
                        String finalText = !sb.isEmpty()
                                ? sb.toString()
                                : (response.aiMessage() != null ? response.aiMessage().text() : "");
                        assistantBubble.setMarkdownNow(finalText);
                        session.getMessages().add(new ChatMessageDto(
                                ChatMessageDto.ROLE_ASSISTANT, finalText, LocalDateTime.now().format(FMT)));
                        try {
                            ctx.getSessionStore().save(session);
                            refreshSessions();
                        } catch (Exception e) {
                            // 会话保存失败不影响继续使用，仅在气泡下方追加提示
                            showSaveWarning();
                        } finally {
                            endStreaming();
                        }
                    }))
                    .onError(err -> Platform.runLater(() -> {
                        assistantBubble.setErrorStyle(true);
                        assistantBubble.setMarkdownNow("请求失败：" + err.getMessage());
                        endStreaming();
                    }))
                    .start();
        } catch (Exception e) {
            assistantBubble.setErrorStyle(true);
            assistantBubble.setMarkdownNow("请求失败：" + e.getMessage());
            endStreaming();
        }
    }

    /** AI 回答气泡：Markdown 渲染（WebView），支持选中复制 */
    private MarkdownBubble addMarkdownBubble() {
        MarkdownBubble mb = new MarkdownBubble();
        forwardWheel(mb);
        HBox row = new HBox(mb);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(mb, javafx.scene.layout.Priority.ALWAYS);
        chatBox.getChildren().add(row);
        mb.setMarkdownNow("思考中…");
        scrollToBottom();
        return mb;
    }

    /**
     * 滚轮事件转发：TextArea/WebView 气泡会吞掉滚轮事件，
     * 用事件过滤器拦截后手动滚动外层 ScrollPane。
     */
    private void forwardWheel(javafx.scene.Node bubble) {
        bubble.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            double dy = e.getDeltaY();
            if (dy == 0) {
                return;
            }
            e.consume();
            double contentH = chatBox.prefHeight(scrollPane.getWidth());
            double viewH = scrollPane.getViewportBounds().getHeight();
            if (contentH <= viewH) {
                return;
            }
            double v = scrollPane.getVvalue() - dy * 40.0 / (contentH - viewH);
            scrollPane.setVvalue(Math.min(scrollPane.getVmax(), Math.max(scrollPane.getVmin(), v)));
        });
    }

    private void showSaveWarning() {
        Label warn = new Label("（注意：会话历史保存失败，重启后将丢失本次记录）");
        warn.getStyleClass().add("tool-note");
        chatBox.getChildren().add(warn);
    }

    private void endStreaming() {
        streaming = false;
        sendButton.setDisable(false);
        inputArea.setDisable(false);
    }

    /** 气泡用只读 TextArea 实现：支持鼠标框选与 Ctrl+C 复制，高度随内容自适应 */
    private TextArea addBubble(String styleClass, String text) {
        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.getStyleClass().addAll("msg", styleClass, "msg-ta");
        forwardWheel(ta);
        // 回答/状态气泡铺满内容列宽度；用户提问气泡保留 780 上限靠右显示
        if ("msg-user".equals(styleClass)) {
            ta.setMaxWidth(780);
        } else {
            ta.setMaxWidth(Double.MAX_VALUE);
        }
        ta.textProperty().addListener((o, a, b) -> autoGrow(ta));
        ta.widthProperty().addListener((o, a, b) -> autoGrow(ta));
        HBox row = new HBox(ta);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(ta, Priority.ALWAYS);
        chatBox.getChildren().add(row);
        autoGrow(ta);
        scrollToBottom();
        return ta;
    }

    /** 按文本行数与当前宽度估算所需显示行高 */
    private void autoGrow(TextArea ta) {
        double usable = (ta.getWidth() > 80 ? ta.getWidth() : 740) - 44;
        int rows = 0;
        for (String line : ta.getText().split("\n", -1)) {
            double lineWidth = 0;
            for (char ch : line.toCharArray()) {
                lineWidth += ch > 0x2E7F ? 14 : 8; // 中文按全宽、西文按半宽估算
            }
            rows += Math.max(1, (int) Math.ceil(lineWidth / usable));
        }
        ta.setPrefRowCount(Math.min(rows + 1, 500));
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            scrollPane.applyCss();
            scrollPane.layout();
            scrollPane.setVvalue(1.0);
        });
    }

    // ---------- 设置 ----------

    private void openSettings() {
        SettingsDialog dialog = new SettingsDialog(ctx);
        dialog.showAndWait();
        updateModelLabel();
    }

    private void updateModelLabel() {
        int kbCount = ctx.getKnowledgeBase().entries().size();
        String kbPart = kbCount == 0 ? "知识库未启用" : "知识库已启用（" + kbCount + " 篇）";
        headerModel.setText("当前模型：" + ctx.activeProfileName() + "　·　" + kbPart);
    }
}
