package com.rfcopilot.app;

import javafx.scene.Scene;

import java.util.Objects;

/**
 * 主题管理：styles.css 定义布局并引用颜色变量，theme-*.css 提供变量配色。
 * 切换主题 = 替换场景上的主题样式表。
 */
public final class ThemeManager {

    public enum Theme {
        DARK("深色", "theme-dark.css"),
        LIGHT("浅色", "theme-light.css"),
        EYE("护眼绿", "theme-eye.css");

        private final String label;
        private final String cssFile;

        Theme(String label, String cssFile) {
            this.label = label;
            this.cssFile = cssFile;
        }

        public String label() {
            return label;
        }

        /** 按配置名解析主题，未知名称回退深色 */
        public static Theme fromName(String name) {
            for (Theme t : values()) {
                if (t.name().equalsIgnoreCase(name)) {
                    return t;
                }
            }
            return DARK;
        }
    }

    private static Theme current = Theme.DARK;

    private ThemeManager() {
    }

    public static Theme current() {
        return current;
    }

    public static void set(Theme theme) {
        current = theme;
    }

    /** 把当前主题应用到场景（基架 + 主题配色两张样式表） */
    public static void apply(Scene scene) {
        String base = url("/styles.css");
        String theme = url("/" + current.cssFile);
        scene.getStylesheets().setAll(base, theme);
    }

    private static String url(String path) {
        return Objects.requireNonNull(ThemeManager.class.getResource(path)).toExternalForm();
    }
}
