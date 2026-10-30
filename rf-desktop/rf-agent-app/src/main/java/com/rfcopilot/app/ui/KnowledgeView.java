package com.rfcopilot.app.ui;

import com.rfcopilot.app.AppContext;
import com.rfcopilot.core.config.ModelProfile;
import com.rfcopilot.knowledge.KnowledgeBase;
import com.rfcopilot.knowledge.KnowledgeEntry;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库管理窗口：收录文档（PDF/Word/Excel/PPT/MD/TXT）、查看清单、删除、清空与重建索引。
 * 所有耗时操作在后台线程执行，进度回显在底部状态栏。
 */
public class KnowledgeView extends BorderPane {

    private final AppContext ctx;
    private final Runnable onChanged;

    private final ListView<KnowledgeEntry> listView = new ListView<>();
    private final Label statusLabel = new Label("就绪");
    private final Button addFileBtn = new Button("添加文件");
    private final Button addDirBtn = new Button("添加目录");
    private final Button deleteBtn = new Button("删除所选");
    private final Button rebuildBtn = new Button("重建索引");
    private final Button clearBtn = new Button("清空知识库");

    private volatile boolean busy;

    public KnowledgeView(AppContext ctx, Runnable onChanged) {
        this.ctx = ctx;
        this.onChanged = onChanged;
        buildUi();
        refreshList();
    }

    private void buildUi() {
        getStyleClass().add("tool-panel");

        Label title = new Label("知识库（RAG）");
        title.getStyleClass().add("section-title");
        Label note = new Label("收录射频规范、器件手册、调试笔记等文档后，助手回答将自动检索知识库内容作为参考。"
                + "支持 PDF / Word / Excel / PPT / Markdown / TXT。");
        note.getStyleClass().add("tool-note");
        note.setWrapText(true);
        VBox head = new VBox(6, title, note);
        head.setPadding(new Insets(16, 16, 8, 16));
        setTop(head);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(KnowledgeEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.getName() + "　·　" + item.getChunks() + " 个片段　·　" + item.getImportedAt());
                    setTooltip(new Tooltip(item.getPath()));
                }
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        HBox buttons = new HBox(10, addFileBtn, addDirBtn, deleteBtn, rebuildBtn, clearBtn);
        buttons.setPadding(new Insets(8, 16, 8, 16));
        addFileBtn.setOnAction(e -> onAddFiles());
        addDirBtn.setOnAction(e -> onAddDirectory());
        deleteBtn.setOnAction(e -> onDelete());
        rebuildBtn.setOnAction(e -> onRebuild());
        clearBtn.setOnAction(e -> onClear());

        statusLabel.setPadding(new Insets(4, 16, 12, 16));
        statusLabel.getStyleClass().add("tool-note");

        BorderPane center = new BorderPane(listView);
        center.setTop(buttons);
        center.setBottom(statusLabel);
        setCenter(center);
    }

    // ---------- 操作 ----------

    private void onAddFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要收录的文档");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("支持的文档", supportedGlobs()),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("Word/Excel/PPT", "*.doc", "*.docx", "*.xls", "*.xlsx", "*.ppt", "*.pptx"),
                new FileChooser.ExtensionFilter("Markdown/TXT", "*.md", "*.markdown", "*.txt"));
        List<File> files = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }
        startTask("收录文件", () -> {
            int ok = 0;
            List<String> failed = new ArrayList<>();
            for (File f : files) {
                try {
                    int chunks = ctx.getKnowledgeBase().importFile(f.toPath(), profile(), this::progress);
                    ok++;
                    progress(f.getName() + "：新增 " + chunks + " 个片段");
                } catch (IllegalArgumentException | IOException ex) {
                    failed.add(f.getName() + "（" + ex.getMessage() + "）");
                }
            }
            return "收录完成：成功 " + ok + " 个" + (failed.isEmpty() ? "" : "，失败 " + failed.size() + " 个：" + String.join("；", failed));
        });
    }

    private void onAddDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择要收录的目录（递归扫描支持的文件）");
        File dir = chooser.showDialog(getScene().getWindow());
        if (dir == null) {
            return;
        }
        startTask("收录目录", () -> {
            List<Path> files;
            try (var stream = Files.walk(dir.toPath())) {
                files = stream.filter(Files::isRegularFile)
                        .filter(KnowledgeBase::isSupported)
                        .toList();
            }
            int ok = 0;
            int skip = 0;
            List<String> failed = new ArrayList<>();
            for (Path p : files) {
                if (ctx.getKnowledgeBase().contains(p)) {
                    skip++;
                    continue;
                }
                try {
                    ok += 1;
                    progress("收录：" + p.getFileName());
                    ctx.getKnowledgeBase().importFile(p, profile(), this::progress);
                } catch (IllegalArgumentException | IOException ex) {
                    ok--;
                    failed.add(p.getFileName() + "（" + ex.getMessage() + "）");
                }
            }
            return "目录收录完成：新增 " + ok + " 个，跳过重复 " + skip + " 个"
                    + (failed.isEmpty() ? "" : "，失败 " + failed.size() + " 个");
        });
    }

    private void onDelete() {
        KnowledgeEntry sel = listView.getSelectionModel().getSelectedItem();
        if (sel == null) {
            status("请先在列表中选择要删除的文档");
            return;
        }
        startTask("删除文档", () -> {
            ctx.getKnowledgeBase().delete(sel, this::progress);
            return "已删除：" + sel.getName();
        });
    }

    private void onRebuild() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "重建索引会按当前档位的嵌入模型重新解析并嵌入全部已收录文档，耗时取决于文档数量。是否继续？",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("重建索引");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        startTask("重建索引", () -> {
            int total = ctx.getKnowledgeBase().rebuild(profile(), this::progress);
            return "重建完成：共 " + total + " 个片段";
        });
    }

    private void onClear() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "将删除知识库全部向量与收录清单（不影响原始文档文件）。是否继续？",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("清空知识库");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        startTask("清空知识库", () -> {
            ctx.getKnowledgeBase().clear();
            return "知识库已清空";
        });
    }

    // ---------- 后台任务框架 ----------

    private interface Task {
        String run() throws Exception;
    }

    private void startTask(String name, Task task) {
        if (busy) {
            status("有任务正在执行，请稍候…");
            return;
        }
        busy = true;
        setButtonsDisabled(true);
        status(name + "：开始…");
        Thread t = new Thread(() -> {
            String result;
            try {
                result = task.run();
            } catch (Throwable ex) {
                // 捕获 Throwable：NoClassDefFoundError 等错误也必须恢复界面
                String cls = ex.getClass().getSimpleName();
                if (cls.contains("ModelNotFound")) {
                    result = name + "失败：嵌入模型不存在。当前KB嵌入档位（" + profile().displayName()
                            + "，模型 " + com.rfcopilot.core.model.EmbeddingModelFactory.effectiveModelName(profile())
                            + "）不提供该 Embedding 模型，请在设置页更换KB嵌入档位或嵌入模型名。";
                } else if (cls.contains("Retrieval") || cls.contains("Connection") || cls.contains("Timeout")) {
                    result = name + "失败：无法连接嵌入服务（" + profile().displayName() + "：" + ex.getMessage() + "）";
                } else {
                    result = name + "失败：" + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                }
            }
            final String msg = result;
            Platform.runLater(() -> {
                busy = false;
                setButtonsDisabled(false);
                refreshList();
                status(msg);
                try {
                    ctx.rebuildAgent();
                } catch (Exception e) {
                    // 智能体重建失败（如嵌入模型不可用）不影响知识库数据本身
                    status(msg + "（注意：对话检索未启用，" + e.getMessage() + "）");
                }
                onChanged.run();
            });
        }, "kb-task");
        t.setDaemon(true);
        t.start();
    }

    /** 知识库嵌入档位（可在设置页单独指定，不随聊天档位变化） */
    private ModelProfile profile() {
        return ctx.getConfig().embeddingProfile();
    }

    private void setButtonsDisabled(boolean disabled) {
        for (Button b : List.of(addFileBtn, addDirBtn, deleteBtn, rebuildBtn, clearBtn)) {
            b.setDisable(disabled);
        }
    }

    private void refreshList() {
        listView.getItems().setAll(ctx.getKnowledgeBase().entries());
    }

    private void progress(String msg) {
        Platform.runLater(() -> status(msg));
    }

    private void status(String msg) {
        statusLabel.setText(msg);
    }

    private static List<String> supportedGlobs() {
        List<String> globs = new ArrayList<>();
        for (String ext : KnowledgeBase.SUPPORTED_EXTENSIONS) {
            globs.add("*." + ext);
        }
        return globs;
    }
}
