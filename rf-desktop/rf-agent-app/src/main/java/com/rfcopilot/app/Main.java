package com.rfcopilot.app;

import com.rfcopilot.app.ThemeManager.Theme;
import com.rfcopilot.app.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * RF Copilot 桌面应用入口。
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        AppContext ctx = new AppContext();
        ctx.init();
        ThemeManager.set(Theme.fromName(ctx.getConfig().getTheme()));

        MainView view = new MainView(ctx);
        BorderPane root = new BorderPane(view);
        Scene scene = new Scene(root, 1280, 820);
        ThemeManager.apply(scene);
        root.setTop(buildMenuBar(ctx, scene));

        stage.setTitle("RF Copilot · 射频工程师智能助手 V1.0");
        var icon = Main.class.getResource("/app-icon.png");
        if (icon != null) {
            stage.getIcons().add(new javafx.scene.image.Image(icon.toExternalForm()));
        }
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    /** 菜单栏：视图 → 主题切换（选择持久化到配置） */
    private MenuBar buildMenuBar(AppContext ctx, Scene scene) {
        Menu viewMenu = new Menu("视图(&V)");
        ToggleGroup group = new ToggleGroup();
        for (Theme t : Theme.values()) {
            RadioMenuItem item = new RadioMenuItem(t.label());
            item.setToggleGroup(group);
            item.setSelected(t == ThemeManager.current());
            item.setOnAction(e -> {
                ThemeManager.set(t);
                ThemeManager.apply(scene);
                com.rfcopilot.app.ui.MarkdownBubble.rerenderAll();
                ctx.getConfig().setTheme(t.name());
                try {
                    ctx.getConfigStore().save(ctx.getConfig());
                } catch (Exception ex) {
                    // 主题已生效，持久化失败不影响使用
                }
            });
            viewMenu.getItems().add(item);
        }
        MenuBar bar = new MenuBar(viewMenu);
        bar.setUseSystemMenuBar(false);
        return bar;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
