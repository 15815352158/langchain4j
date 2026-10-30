package com.rfcopilot.app;

import com.rfcopilot.core.agent.RfAgent;
import com.rfcopilot.core.config.AppConfig;
import com.rfcopilot.core.config.ConfigStore;
import com.rfcopilot.core.session.SessionStore;
import com.rfcopilot.knowledge.KnowledgeBase;
import dev.langchain4j.rag.content.retriever.ContentRetriever;

import java.io.IOException;

/**
 * 应用级上下文：持有配置、会话存储、知识库与当前智能体实例。
 */
public class AppContext {

    private final ConfigStore configStore = new ConfigStore();
    private final SessionStore sessionStore = new SessionStore();
    private final KnowledgeBase knowledgeBase = new KnowledgeBase();

    private AppConfig config;
    private RfAgent agent;

    public void init() throws IOException {
        config = configStore.load();
        knowledgeBase.load();
        rebuildAgent();
    }

    /** 配置或知识库变更后重建智能体（知识库非空时启用 RAG，嵌入走独立档位） */
    public void rebuildAgent() {
        ContentRetriever retriever = knowledgeBase.contentRetriever(config.embeddingProfile());
        agent = RfAgent.create(config.activeProfile(), sessionStore, retriever);
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

    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }
}
