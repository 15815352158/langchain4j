package com.rfcopilot.app.ai;

import com.rfcopilot.tools.scpi.ScpiManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.stream.Collectors;

/**
 * 暴露给大模型的 SCPI 仪表工具。
 * 查询与控制命令都必须先经 UiConfirm 弹窗由用户人工确认，防止 AI 误操作真实仪表。
 */
public class ScpiAgentTools {

    private final ScpiManager manager;

    public ScpiAgentTools(ScpiManager manager) {
        this.manager = manager;
    }

    @Tool("列出用户已配置的全部 SCPI 仪表连接（名称、地址、端口、在线状态）。只读信息，用于确定后续命令的目标连接名。")
    public String listScpiConnections() {
        var configs = manager.list();
        if (configs.isEmpty()) {
            return "用户尚未配置任何 SCPI 仪表连接，请提示其在『SCPI 仪表控制』窗口添加。";
        }
        return configs.stream()
                .map(c -> "- " + c.getName() + "：" + c.getHost() + ":" + c.getPort()
                        + (manager.isConnected(c.getName()) ? "（已连接）" : "（未连接，发送命令时会自动连接）"))
                .collect(Collectors.joining("\n"));
    }

    @Tool("向指定 SCPI 仪表发送查询命令（必须以 ? 结尾，如 *IDN?、FREQ:CENT?），返回仪表的应答文本。发送前会弹窗由用户人工确认。")
    public String scpiQuery(@P("仪表连接名称，必须与 listScpiConnections 返回的名称一致") String connection,
                            @P("以 ? 结尾的 SCPI 查询命令") String command) {
        String cmd = command.strip();
        if (!cmd.endsWith("?")) {
            return "拒绝执行：查询命令必须以 ? 结尾；如需控制仪表请使用 scpiWrite。";
        }
        if (!UiConfirm.confirmSend(connection, cmd)) {
            return "用户取消了本次命令发送。";
        }
        try {
            String resp = manager.query(connection, cmd);
            return resp.isEmpty() ? "仪表返回空响应。" : resp;
        } catch (Exception e) {
            return "发送失败：" + e.getMessage();
        }
    }

    @Tool("向指定 SCPI 仪表发送写入/控制命令（不带 ?，如 *RST、*CLS、FREQ:CENT 2.445GHz），仪表不返回应答。发送前会弹窗由用户人工确认，仅在用户明确要求操作仪表时使用。")
    public String scpiWrite(@P("仪表连接名称，必须与 listScpiConnections 返回的名称一致") String connection,
                            @P("SCPI 写入/控制命令") String command) {
        String cmd = command.strip();
        if (cmd.endsWith("?")) {
            return "拒绝执行：该命令以 ? 结尾属于查询，请使用 scpiQuery。";
        }
        if (!UiConfirm.confirmSend(connection, cmd)) {
            return "用户取消了本次命令发送。";
        }
        try {
            manager.write(connection, cmd);
            return "已发送：" + cmd;
        } catch (Exception e) {
            return "发送失败：" + e.getMessage();
        }
    }
}
