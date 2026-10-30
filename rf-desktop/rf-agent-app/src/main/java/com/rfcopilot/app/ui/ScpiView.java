package com.rfcopilot.app.ui;

import com.rfcopilot.app.AppContext;
import com.rfcopilot.tools.scpi.ScpiConnectionConfig;
import com.rfcopilot.tools.scpi.ScpiManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SCPI 仪表控制窗口：连接管理 + 手动命令终端。
 * 所有网络 I/O 在后台单线程执行，避免阻塞 JavaFX 线程；
 * 仪表配置持久化在 ~/.rfcopilot/scpi.json，AI 聊天工具与本窗口共用同一份连接。
 */
public class ScpiView extends BorderPane {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AppContext ctx;
    private final ScpiManager manager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scpi-task");
        t.setDaemon(true);
        return t;
    });

    private final ListView<ScpiConnectionConfig> connList = new ListView<>();
    private final TextArea logArea = new TextArea();
    private final TextField cmdField = new TextField();
    private final Button sendBtn = new Button("发送");

    public ScpiView(AppContext ctx) {
        this.ctx = ctx;
        this.manager = ctx.getScpiManager();
        buildUi();
        refreshConnections();
        log("欢迎使用 SCPI 终端：先添加仪表连接（IP + 端口，LXI 仪表通常为 5025），再发送命令。");
        log("以 ? 结尾的命令会读取仪表应答，其余作为控制命令发送。AI 也可在聊天中操作仪表，每条命令都会弹窗请你确认。");
    }

    private void buildUi() {
        setLeft(buildLeftPanel());
        setCenter(buildTerminal());
    }

    // ---------- 左侧：连接管理 ----------

    private VBox buildLeftPanel() {
        Label title = new Label("仪表连接");
        title.getStyleClass().add("section-title");

        connList.getStyleClass().add("session-list");
        connList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ScpiConnectionConfig c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setText(null);
                } else {
                    setText(c.display() + (manager.isConnected(c.getName()) ? "　● 已连接" : "　○ 未连接"));
                }
            }
        });

        Button addBtn = new Button("添加");
        addBtn.setOnAction(e -> addOrEdit(null));
        Button editBtn = new Button("编辑");
        editBtn.setOnAction(e -> {
            ScpiConnectionConfig sel = selected();
            if (sel != null) {
                addOrEdit(sel);
            }
        });
        Button delBtn = new Button("删除");
        delBtn.setOnAction(e -> deleteSelected());
        Button connBtn = new Button("连接");
        connBtn.setOnAction(e -> {
            ScpiConnectionConfig sel = selected();
            if (sel != null) {
                executor.submit(() -> {
                    try {
                        manager.connect(sel.getName());
                        log("已连接：" + sel.display());
                    } catch (Exception ex) {
                        log("连接失败：" + sel.display() + "　" + ex.getMessage());
                    } finally {
                        Platform.runLater(() -> connList.refresh());
                    }
                });
            }
        });
        Button discBtn = new Button("断开");
        discBtn.setOnAction(e -> {
            ScpiConnectionConfig sel = selected();
            if (sel != null) {
                manager.disconnect(sel.getName());
                log("已断开：" + sel.display());
                connList.refresh();
            }
        });

        HBox row1 = new HBox(8, addBtn, editBtn, delBtn);
        HBox row2 = new HBox(8, connBtn, discBtn);
        for (Button b : new Button[]{addBtn, editBtn, delBtn, connBtn, discBtn}) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        HBox.setHgrow(addBtn, Priority.ALWAYS);
        HBox.setHgrow(editBtn, Priority.ALWAYS);
        HBox.setHgrow(delBtn, Priority.ALWAYS);
        HBox.setHgrow(connBtn, Priority.ALWAYS);
        HBox.setHgrow(discBtn, Priority.ALWAYS);

        VBox box = new VBox(10, title, connList, row1, row2);
        box.getStyleClass().add("tool-panel");
        box.setPadding(new Insets(16, 12, 16, 12));
        box.setPrefWidth(300);
        box.setMinWidth(260);
        VBox.setVgrow(connList, Priority.ALWAYS);
        return box;
    }

    private void addOrEdit(ScpiConnectionConfig existing) {
        Dialog<ScpiConnectionConfig> dlg = new Dialog<>();
        dlg.setTitle(existing == null ? "添加仪表连接" : "编辑仪表连接");
        dlg.setHeaderText("LAN 直连（原始 TCP），端口通常为 5025");

        TextField nameField = new TextField(existing == null ? "" : existing.getName());
        TextField hostField = new TextField(existing == null ? "" : existing.getHost());
        TextField portField = new TextField(existing == null ? "5025" : String.valueOf(existing.getPort()));
        TextField timeoutField = new TextField(existing == null ? "5000" : String.valueOf(existing.getTimeoutMs()));
        nameField.setPromptText("如：频谱仪 E5063A");
        hostField.setPromptText("仪表 IP，如 192.168.1.100");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("名称："), nameField);
        grid.addRow(1, new Label("地址："), hostField);
        grid.addRow(2, new Label("端口："), portField);
        grid.addRow(3, new Label("超时(ms)："), timeoutField);
        portField.setPrefColumnCount(8);
        timeoutField.setPrefColumnCount(8);
        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dlg.setResultConverter(b -> {
            if (b != ButtonType.OK) {
                return null;
            }
            String name = nameField.getText().strip();
            String host = hostField.getText().strip();
            if (name.isEmpty() || host.isEmpty()) {
                error("名称与地址不能为空");
                return null;
            }
            try {
                int port = Integer.parseInt(portField.getText().strip());
                int timeout = Integer.parseInt(timeoutField.getText().strip());
                if (port < 1 || port > 65535 || timeout < 500) {
                    error("端口需在 1-65535，超时不小于 500ms");
                    return null;
                }
                return new ScpiConnectionConfig(name, host, port, timeout);
            } catch (NumberFormatException e) {
                error("端口与超时必须是整数");
                return null;
            }
        });

        dlg.showAndWait().ifPresent(cfg -> {
            try {
                manager.addOrUpdate(cfg);
                refreshConnections();
                selectByName(cfg.getName());
                log("已保存连接：" + cfg.display());
            } catch (Exception e) {
                error("保存失败：" + e.getMessage());
            }
        });
    }

    private void deleteSelected() {
        ScpiConnectionConfig sel = selected();
        if (sel == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "删除连接「" + sel.getName() + "」？", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                manager.remove(sel.getName());
                refreshConnections();
                log("已删除连接：" + sel.display());
            } catch (Exception e) {
                error("删除失败：" + e.getMessage());
            }
        });
    }

    private void refreshConnections() {
        connList.getItems().setAll(manager.list());
        connList.refresh();
    }

    private void selectByName(String name) {
        for (int i = 0; i < connList.getItems().size(); i++) {
            if (connList.getItems().get(i).getName().equals(name)) {
                connList.getSelectionModel().select(i);
                return;
            }
        }
    }

    // ---------- 中央：命令终端 ----------

    private BorderPane buildTerminal() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("scpi-log");

        cmdField.setPromptText("输入 SCPI 命令，如 *IDN? 或 FREQ:CENT 2.445GHz");
        cmdField.setOnAction(e -> sendCommand());
        sendBtn.getStyleClass().add("send-btn");
        sendBtn.setOnAction(e -> sendCommand());
        HBox.setHgrow(cmdField, Priority.ALWAYS);
        HBox inputRow = new HBox(10, cmdField, sendBtn);
        inputRow.setPadding(new Insets(0, 0, 12, 0));

        HBox quickRow = buildQuickRow();
        VBox bottom = new VBox(8, quickRow, inputRow);

        BorderPane pane = new BorderPane(logArea);
        pane.setPadding(new Insets(16, 16, 12, 12));
        pane.setBottom(bottom);
        return pane;
    }

    private HBox buildQuickRow() {
        String[] queries = {"*IDN?", "*OPT?", "SYST:ERR?", "*RST", "*CLS"};
        HBox row = new HBox(8);
        row.getChildren().add(new Label("快捷命令："));
        for (String cmd : queries) {
            Button b = new Button(cmd);
            b.getStyleClass().add("quick-btn");
            b.setOnAction(e -> {
                cmdField.setText(cmd);
                cmdField.requestFocus();
            });
            row.getChildren().add(b);
        }
        return row;
    }

    private void sendCommand() {
        String cmd = cmdField.getText().strip();
        if (cmd.isEmpty()) {
            return;
        }
        ScpiConnectionConfig cfg = selected();
        if (cfg == null) {
            log("请先在左侧选择仪表连接");
            return;
        }
        cmdField.clear();
        sendBtn.setDisable(true);
        executor.submit(() -> {
            try {
                if (cmd.endsWith("?")) {
                    log("→ " + cmd);
                    String resp = manager.query(cfg.getName(), cmd);
                    log("← " + (resp.isEmpty() ? "（空响应）" : resp));
                } else {
                    manager.write(cfg.getName(), cmd);
                    log("→ " + cmd + "　（已发送，无响应）");
                }
            } catch (Exception e) {
                log("✗ " + cmd + "　失败：" + e.getMessage());
            } finally {
                Platform.runLater(() -> {
                    sendBtn.setDisable(false);
                    connList.refresh();
                });
            }
        });
    }

    private ScpiConnectionConfig selected() {
        ScpiConnectionConfig sel = connList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            log("请先在左侧选择仪表连接");
        }
        return sel;
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText("[" + LocalTime.now().format(TIME) + "] " + msg + "\n"));
    }

    private void error(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.showAndWait();
    }
}
