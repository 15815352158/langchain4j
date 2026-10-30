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

    /** 主题配色（与 theme-*.css 对应）：codeFg 为行内代码前景色，accent 为标题/表头强调色 */
    private record Palette(String bg, String fg, String code, String border, String link,
                           String errBg, String errFg, String codeFg) {
    }

    private static final List<MarkdownBubble> ALL = new CopyOnWriteArrayList<>();

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;
    /** 代码块语法高亮脚本（Kiro 风格：关键字/字符串/注释/数字着色） */
    private static final String HIGHLIGHT_JS;

    static {
        MutableDataSet opts = new MutableDataSet();
        opts.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        PARSER = Parser.builder(opts).build();
        RENDERER = HtmlRenderer.builder(opts).build();
        String js = "";
        try (var in = MarkdownBubble.class.getResourceAsStream("/md-highlight.js")) {
            if (in != null) {
                js = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        HIGHLIGHT_JS = js;
    }

    private final WebView web = new WebView();
    private String raw = "";
    private boolean errorStyle;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(120));
    /** 当前打开的快捷菜单（全局唯一，防止 WebView 内点击不触发弹层自动关闭导致残留） */
    private static javafx.scene.control.ContextMenu openMenu;

    private static void hideOpenMenu() {
        if (openMenu != null) {
            javafx.scene.control.ContextMenu m = openMenu;
            openMenu = null;
            m.hide();
        }
    }

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
        web.setContextMenuEnabled(false);
        web.widthProperty().addListener((o, a, b) -> measure());
        debounce.setOnFinished(e -> doRender());
        // WebView 吞掉鼠标事件导致弹层"点外部自动关闭"失效：
        // 任何鼠标按下先关闭上一个残留的快捷菜单
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> hideOpenMenu());
        web.setOnContextMenuRequested(e -> {
            hideOpenMenu();
            javafx.scene.control.MenuItem copySel = new javafx.scene.control.MenuItem("复制选中内容");
            copySel.setOnAction(x -> {
                String t = selectedText();
                if (!t.isBlank()) {
                    copyToClipboard(t);
                }
            });
            javafx.scene.control.MenuItem copyRaw =
                    new javafx.scene.control.MenuItem("复制原始内容");
            copyRaw.setOnAction(x -> copyToClipboard(raw));
            javafx.scene.control.ContextMenu menu =
                    new javafx.scene.control.ContextMenu(copySel, copyRaw);
            // 弹层叠在 WebView 上时点击会被其原生表面抢走：
            // 菜单显示期间禁用 WebView，保证点击落在菜单项上并能正常关闭
            menu.setOnShowing(x -> web.setDisable(true));
            menu.setOnHidden(x -> {
                web.setDisable(false);
                if (openMenu == menu) {
                    openMenu = null;
                }
            });
            openMenu = menu;
            menu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        getChildren().add(web);
        ALL.add(this);
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(java.util.Map.of(
                javafx.scene.input.DataFormat.PLAIN_TEXT, text));
    }

    /** 取 WebView 当前选中文本（JavaFX 25 无公开 API，反射内部 WebPage） */
    private String selectedText() {
        try {
            var m = javafx.scene.web.WebEngine.class.getDeclaredMethod("getPage");
            m.setAccessible(true);
            com.sun.webkit.WebPage p = (com.sun.webkit.WebPage) m.invoke(web.getEngine());
            String s = p.getClientSelectedText();
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
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
        web.getEngine().loadContent(buildHtml(decorateIcons(RENDERER.render(PARSER.parse(raw)))));
    }

    /** 常见 emoji -> 可着色文本符号（WebView 无彩色 emoji 字体，参照 Kiro 用颜色语义图标） */
    private static final java.util.Map<String, String> ICON_MAP = java.util.Map.ofEntries(
            java.util.Map.entry("\u2705", "<span style=\"color:#2ecc71;font-weight:bold\">\u2714</span>"),   // ✅
            java.util.Map.entry("\u274C", "<span style=\"color:#e74c3c;font-weight:bold\">\u2716</span>"),   // ❌
            java.util.Map.entry("\u274E", "<span style=\"color:#e74c3c;font-weight:bold\">\u2716</span>"),   // ❎
            java.util.Map.entry("\u26A0\uFE0F", "<span style=\"color:#f39c12;font-weight:bold\">\u26A0</span>"), // ⚠️
            java.util.Map.entry("\u26A0", "<span style=\"color:#f39c12;font-weight:bold\">\u26A0</span>"),   // ⚠
            java.util.Map.entry("\u2757", "<span style=\"color:#e74c3c;font-weight:bold\">!</span>"),        // ❗
            java.util.Map.entry("\u2B50", "<span style=\"color:#f1c40f\">\u2605</span>"),                    // ⭐
            java.util.Map.entry("\uD83D\uDD34", "<span style=\"color:#e74c3c\">\u25CF</span>"),              // 🔴
            java.util.Map.entry("\uD83D\uDFE0", "<span style=\"color:#f39c12\">\u25CF</span>"),              // 🟠
            java.util.Map.entry("\uD83D\uDFE1", "<span style=\"color:#f1c40f\">\u25CF</span>"),              // 🟡
            java.util.Map.entry("\uD83D\uDFE2", "<span style=\"color:#2ecc71\">\u25CF</span>"),              // 🟢
            java.util.Map.entry("\uD83D\uDD35", "<span style=\"color:#3498db\">\u25CF</span>"),              // 🔵
            java.util.Map.entry("\uD83D\uDFE3", "<span style=\"color:#9b59b6\">\u25CF</span>"),              // 🟣
            java.util.Map.entry("\u26AA", "<span style=\"color:#95a5a6\">\u25CB</span>"),                    // ⚪
            java.util.Map.entry("\uD83D\uDD3A", "<span style=\"color:#e74c3c\">\u25B2</span>"),              // 🔺
            java.util.Map.entry("\uD83D\uDD3B", "<span style=\"color:#2ecc71\">\u25BC</span>"),              // 🔻
            java.util.Map.entry("\uD83D\uDCC8", "<span style=\"color:#2ecc71;font-weight:bold\">\u2197</span>"), // 📈
            java.util.Map.entry("\uD83D\uDCC9", "<span style=\"color:#e74c3c;font-weight:bold\">\u2198</span>"), // 📉
            java.util.Map.entry("\u2139\uFE0F", "<span style=\"color:#3498db;font-weight:bold\">i</span>"),  // ℹ️
            java.util.Map.entry("\uD83D\uDCA1", "<span style=\"color:#f1c40f;font-weight:bold\">\u2726</span>"), // 💡
            java.util.Map.entry("\u27A1\uFE0F", "<span style=\"color:inherit\">\u2192</span>"),              // ➡️
            java.util.Map.entry("\u27A1", "<span style=\"color:inherit\">\u2192</span>"));                   // ➡

    private static String decorateIcons(String html) {
        String out = html;
        for (var e : ICON_MAP.entrySet()) {
            if (out.indexOf(e.getKey()) >= 0) {
                out = out.replace(e.getKey(), e.getValue());
            }
        }
        // 清掉未映射 emoji 的变体选择符，避免渲染出空白方块
        return out.replace("\uFE0F", "");
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
        Palette p;
        String hlCss;
        switch (ThemeManager.current()) {
            case DARK -> {
                p = new Palette("#1f1f1f", "#e8e8e8", "#141414", "#2e2e2e", "#4fd1a5",
                        "#3d2222", "#ff9d9d", "#e5c07b");
                hlCss = ".hk{color:#569cd6}.hs{color:#ce9178}.hc{color:#6a9955}"
                        + ".hn{color:#b5cea8}.hf{color:#dcdcaa}";
            }
            case LIGHT -> {
                p = new Palette("#fbfbfb", "#222222", "#f0f0f0", "#d8d8d8", "#0d8a6b",
                        "#ffe3e3", "#b03030", "#b25b09");
                hlCss = ".hk{color:#0f6bd4}.hs{color:#a31515}.hc{color:#008000}"
                        + ".hn{color:#098658}.hf{color:#7a3e9d}";
            }
            default -> {
                p = new Palette("#e9f1e6", "#263a2a", "#dbe7d8", "#b9c9b0", "#2e8b57",
                        "#f3d3c8", "#a03a20", "#8a5a00");
                hlCss = ".hk{color:#1a6fb5}.hs{color:#a31515}.hc{color:#2e7d32}"
                        + ".hn{color:#098658}.hf{color:#7a3e9d}";
            }
        }
        String bg = errorStyle ? p.errBg() : p.bg();
        String fg = errorStyle ? p.errFg() : p.fg();
        return """
                <!DOCTYPE html><html><head><meta charset="utf-8"><style>
                html { background: transparent; }
                body { margin:0; padding:12px 16px; background:%1$s; color:%2$s;
                       font-family:'Segoe UI','Microsoft YaHei',sans-serif; font-size:14px;
                       line-height:1.6; border-radius:10px; box-sizing:border-box; }
                h1,h2,h3,h4 { margin:12px 0 6px; line-height:1.35; color:%5$s; }
                h1 { font-size:19px; border-bottom:1px solid %4$s; padding-bottom:4px; }
                h2 { font-size:17px; border-bottom:1px solid %4$s; padding-bottom:3px; }
                h3 { font-size:15px; }
                p { margin:6px 0; }
                strong { color:%2$s; }
                pre { background:%3$s; border:1px solid %4$s; border-radius:6px;
                      padding:10px 12px; overflow-x:auto; white-space:pre-wrap; word-break:break-all; }
                code { background:%3$s; color:%7$s; padding:1px 5px; border-radius:4px;
                       font-family:Consolas,'Courier New',monospace; font-size:13px; }
                pre code { background:none; padding:0; color:inherit; }
                table { border-collapse:collapse; margin:6px 0; width:100%%; }
                th,td { border:1px solid %4$s; padding:5px 10px; text-align:left; }
                th { background:%3$s; color:%5$s; }
                tr:nth-child(even) td { background:rgba(128,128,128,0.06); }
                blockquote { margin:6px 0; padding:2px 12px; border-left:3px solid %5$s;
                             background:rgba(128,128,128,0.08); color:%2$s; }
                a { color:%5$s; }
                ul,ol { margin:6px 0; padding-left:24px; }
                li { margin:2px 0; }
                hr { border:none; border-top:1px solid %4$s; margin:10px 0; }
                ::selection { background:%5$s; color:%1$s; }
                %8$s
                </style></head><body>%6$s<script>%9$s</script></body></html>
                """.formatted(bg, fg, p.code(), p.border(), p.link(), body, p.codeFg(),
                hlCss, HIGHLIGHT_JS);
    }
}
