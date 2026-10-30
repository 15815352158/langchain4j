package com.rfcopilot.app;

/**
 * 打包启动入口：jpackage/普通 java -jar 以 classpath 方式运行时，
 * 若 Main-Class 直接指向继承 Application 的 Main，JVM 会因缺少 JavaFX 模块检查而拒绝启动。
 * 由这个不继承 Application 的类中转即可正常工作。
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
