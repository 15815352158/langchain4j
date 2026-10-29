package com.rfcopilot.app;

import com.rfcopilot.app.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * RF Copilot 桌面应用入口。
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        AppContext ctx = new AppContext();
        ctx.init();

        MainView view = new MainView(ctx);
        Scene scene = new Scene(view, 1280, 820);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());

        stage.setTitle("RF Copilot · 射频工程师智能助手");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
