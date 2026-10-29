package com.rfcopilot.app;

import com.rfcopilot.core.agent.RfAgent;
import com.rfcopilot.core.config.AppConfig;
import com.rfcopilot.core.config.ConfigStore;
import com.rfcopilot.core.session.SessionStore;

import java.io.IOException;

/**
 * 应用级上下文：持有配置、会话存储与当前智能体实例。
 */
public class AppContext {

    private final ConfigStore configStore = new ConfigStore();
    private final SessionStore sessionStore = new SessionStore();

    private AppConfig config;
    private RfAgent agent;

    public void init() throws IOException {
        config = configStore.load();
        rebuildAgent();
    }

    /** 配置变更后重建智能体 */
    public void rebuildAgent() {
        agent = RfAgent.create(config.activeProfile(), sessionStore);
    }

    public String activeProfileName() {
        return config.activeProfile().displayName();
    }

    public ConfigStore getConfigStore() {
        return configStore;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public AppConfig getConfig() {
        return config;
    }

    public RfAgent getAgent() {
        return agent;
    }
}
