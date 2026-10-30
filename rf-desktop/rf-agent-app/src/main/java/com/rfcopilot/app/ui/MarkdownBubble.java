package com.rfcopilot.app.ui;

import com.rfcopilot.app.ThemeManager;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Markdown 渲染的 AI 回答气泡：只读 WebView（JavaFX 25 中 WebView 为 final，故组合包装），
 * 支持鼠标框选与 Ctrl+C 复制。高度按内容自适应；流式输出内部防抖，主题切换时全部重渲染。
 */
public class MarkdownBubble extends StackPane {

    /** 主题配色（与 theme-*.css 对应） */
    private record Palette(String bg, String fg, String code, String border, String link,
                           String errBg, String errFg) {
    }

    private static final List<MarkdownBubble> ALL = new CopyOnWriteArrayList<>();

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        MutableDataSet opts = new MutableDataSet();
        opts.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        PARSER = Parser.builder(opts).build();
        RENDERER = HtmlRenderer.builder(opts).build();
    }

    private final WebView web = new WebView();
    private String raw = "";
    private boolean errorStyle;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(120));

    public MarkdownBubble() {
        setMaxWidth(Double.MAX_VALUE);
        web.setMaxWidth(Double.MAX_VALUE);
        web.setMinWidth(200);
        setPrefHeight(36);
        web.getEngine().getLoadWorker().stateProperty().addListener((o, a, s) -> {
            if (s == Worker.State.SUCCEEDED) {
                measure();
            }
        });
        web.widthProperty().addListener((o, a, b) -> measure());
        debounce.setOnFinished(e -> doRender());
        getChildren().add(web);
        ALL.add(this);
    }

    /** 流式增量更新：120ms 防抖合并，避免每个 token 重渲染 */
    public void setMarkdown(String text) {
        raw = text == null ? "" : text;
        debounce.playFromStart();
    }

    /** 最终内容：立即渲染 */
    public void setMarkdownNow(String text) {
        raw = text == null ? "" : text;
        debounce.stop();
        doRender();
    }

    /** 切换为错误配色（请求失败气泡） */
    public void setErrorStyle(boolean v) {
        errorStyle = v;
        doRender();
    }

    /** 主题切换后重渲染所有存活气泡 */
    public static void rerenderAll() {
        for (MarkdownBubble b : ALL) {
            Platform.runLater(b::doRender);
        }
    }

    private void doRender() {
        web.getEngine().loadContent(buildHtml(RENDERER.render(PARSER.parse(raw))));
    }

    /** 加载完成后通过 DOM 测量文档实际高度（JavaFX 25 移除了 runScript，用 WebKit DOM 扩展） */
    private void measure() {
        Platform.runLater(() -> {
            try {
                org.w3c.dom.Document doc = web.getEngine().getDocument();
                // 用 body 而非 html 根元素测高：html 的 scrollHeight 含视口高度会循环增长
                org.w3c.dom.Element body = doc == null ? null : doc.getDocumentElement() == null
                        ? null : (org.w3c.dom.Element) doc.getDocumentElement().getElementsByTagName("body").item(0);
                if (body instanceof com.sun.webkit.dom.ElementImpl el) {
                    int h = el.getScrollHeight();
                    if (h > 0) {
                        setPrefHeight(Math.min(h + 8, 10000));
                    }
                }
            } catch (Exception ignored) {
                // 测量失败保持当前高度
            }
        });
    }

    private String buildHtml(String body) {
        Palette p = switch (ThemeManager.current()) {
            case DARK -> new Palette("#1f1f1f", "#e8e8e8", "#141414", "#2e2e2e", "#4fd1a5", "#3d2222", "#ff9d9d");
            case LIGHT -> new Palette("#fbfbfb", "#222222", "#f0f0f0", "#d8d8d8", "#0d8a6b", "#ffe3e3", "#b03030");
            case EYE -> new Palette("#e9f1e6", "#263a2a", "#dbe7d8", "#b9c9b0", "#2e8b57", "#f3d3c8", "#a03a20");
        };
        String bg = errorStyle ? p.errBg() : p.bg();
        String fg = errorStyle ? p.errFg() : p.fg();
        return """
                <!DOCTYPE html><html><head><meta charset="utf-8"><style>
                html { background: transparent; }
                body { margin:0; padding:12px 16px; background:%1$s; color:%2$s;
                       font-family:'Segoe UI','Microsoft YaHei',sans-serif; font-size:14px;
                       line-height:1.6; border-radius:10px; box-sizing:border-box; }
                h1,h2,h3,h4 { margin:10px 0 6px; line-height:1.35; }
                p { margin:6px 0; }
                pre { background:%3$s; border:1px solid %4$s; border-radius:6px;
                      padding:8px 10px; overflow-x:auto; white-space:pre-wrap; word-break:break-all; }
                code { background:%3$s; padding:1px 5px; border-radius:4px;
                       font-family:Consolas,'Courier New',monospace; font-size:13px; }
                pre code { background:none; padding:0; }
                table { border-collapse:collapse; margin:6px 0; }
                th,td { border:1px solid %4$s; padding:4px 10px; }
                th { background:%3$s; }
                blockquote { margin:6px 0; padding:2px 12px; border-left:3px solid %5$s; color:%2$s; }
                a { color:%5$s; }
                ul,ol { margin:6px 0; padding-left:24px; }
                hr { border:none; border-top:1px solid %4$s; margin:10px 0; }
                ::selection { background:%5$s; color:%1$s; }
                </style></head><body>%6$s</body></html>
                """.formatted(bg, fg, p.code(), p.border(), p.link(), body);
    }
}
