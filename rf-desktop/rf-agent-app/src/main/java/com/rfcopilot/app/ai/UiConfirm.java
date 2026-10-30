package com.rfcopilot.app.ai;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.concurrent.TimeUnit;

/**
 * AI 工具线程 → JavaFX 主线程的人工确认桥。
 * 工具在模型响应线程上执行，通过 Platform.runLater 弹出确认框并阻塞等待用户决定；
 * 超时（2 分钟）未操作视为取消，保证不会永久卡住对话。
 */
public final class UiConfirm {

    private UiConfirm() {
    }

    /** 弹出确认框，用户点击"确认发送"返回 true；取消/超时返回 false */
    public static boolean confirmSend(String connection, String command) {
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        Platform.runLater(() -> {
            ButtonType send = new ButtonType("确认发送", ButtonBar.ButtonData.OK_DONE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "目标仪表：" + connection + "\n命令：" + command + "\n\n确认向仪表发送该 SCPI 命令？",
                    send,
                    new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE));
            alert.setTitle("仪表命令确认");
            alert.setHeaderText("AI 请求向仪表发送命令");
            future.complete(alert.showAndWait().filter(b -> b == send).isPresent());
        });
        try {
            return future.get(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            return false;
        }
    }
}
