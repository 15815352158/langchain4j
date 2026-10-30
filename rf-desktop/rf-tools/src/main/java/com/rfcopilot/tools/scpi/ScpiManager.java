package com.rfcopilot.tools.scpi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCPI 仪表连接管理器。
 * 采用 LXI 仪表通用的 5025 原始 TCP 端口（无需 NI-VISA），
 * 连接配置持久化为 JSON，socket 按需建立并复用，查询/写入均为同步阻塞（带超时）。
 * 所有公开方法加 synchronized，供 UI 线程与 AI 工具线程并发调用。
 */
public class ScpiManager {

    private final Path storeFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ScpiConnectionConfig> configs = new ArrayList<>();
    private final Map<String, Socket> sockets = new HashMap<>();

    public ScpiManager(Path storeFile) {
        this.storeFile = storeFile;
    }

    /** 从 JSON 文件加载连接配置（文件不存在则为空列表） */
    public synchronized void load() throws IOException {
        configs.clear();
        if (Files.exists(storeFile)) {
            List<ScpiConnectionConfig> loaded = mapper.readValue(
                    Files.readString(storeFile, StandardCharsets.UTF_8),
                    new TypeReference<List<ScpiConnectionConfig>>() {
                    });
            configs.addAll(loaded);
        }
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(storeFile.getParent());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(storeFile.toFile(), configs);
    }

    public synchronized List<ScpiConnectionConfig> list() {
        return List.copyOf(configs);
    }

    /** 新增或按名称更新一条连接配置（重名覆盖），并持久化 */
    public synchronized void addOrUpdate(ScpiConnectionConfig cfg) throws IOException {
        if (cfg.getName() == null || cfg.getName().isBlank()) {
            throw new IllegalArgumentException("连接名称不能为空");
        }
        disconnect(cfg.getName());
        configs.removeIf(c -> c.getName().equals(cfg.getName()));
        configs.add(cfg);
        save();
    }

    /** 删除连接（先断开 socket）并持久化 */
    public synchronized void remove(String name) throws IOException {
        disconnect(name);
        configs.removeIf(c -> c.getName().equals(name));
        save();
    }

    public synchronized boolean isConnected(String name) {
        Socket s = sockets.get(name);
        return s != null && s.isConnected() && !s.isClosed();
    }

    /** 建立 TCP 连接（已连接则直接返回） */
    public synchronized void connect(String name) throws IOException {
        if (isConnected(name)) {
            return;
        }
        ScpiConnectionConfig cfg = find(name);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(cfg.getHost(), cfg.getPort()), cfg.getTimeoutMs());
        socket.setSoTimeout(cfg.getTimeoutMs());
        sockets.put(name, socket);
    }

    public synchronized void disconnect(String name) {
        Socket s = sockets.remove(name);
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // 关闭失败无需处理
            }
        }
    }

    /** 断开全部连接（应用退出时调用） */
    public synchronized void closeAll() {
        new ArrayList<>(sockets.keySet()).forEach(this::disconnect);
    }

    /**
     * 发送查询命令（以 ? 结尾）并读取一行响应。
     * 未连接时自动连接；失败时断开以便下次重连。
     */
    public synchronized String query(String name, String command) throws IOException {
        Socket s = send(name, command);
        try {
            return readLine(s);
        } catch (IOException e) {
            disconnect(name);
            throw e;
        }
    }

    /** 发送写入/控制命令（不读取响应）。未连接时自动连接。 */
    public synchronized void write(String name, String command) throws IOException {
        send(name, command);
    }

    private Socket send(String name, String command) throws IOException {
        connect(name);
        Socket s = sockets.get(name);
        if (s == null || s.isClosed()) {
            throw new IOException("连接已断开：" + name);
        }
        OutputStream out = s.getOutputStream();
        out.write((command + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return s;
    }

    /** 读取一行响应（以 \n 结束，trim 顺带去掉 \r；个别仅以 \r 结尾的仪表会超时，属罕见兼容问题） */
    private String readLine(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            sb.append((char) b);
        }
        return sb.toString().trim();
    }

    private ScpiConnectionConfig find(String name) throws IOException {
        return configs.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IOException("未找到仪表连接：" + name));
    }
}
