package com.rfcopilot.core.agent;

import com.rfcopilot.core.config.ModelProfile;
import com.rfcopilot.core.model.ChatModelFactory;
import com.rfcopilot.core.session.ChatMessageDto;
import com.rfcopilot.core.session.ChatSession;
import com.rfcopilot.core.session.SessionStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;

/**
 * 智能体门面：封装模型创建、记忆加载与会话流式对话。
 */
public class RfAgent {

    private static final int MAX_MEMORY_MESSAGES = 30;

    private final RfAssistant assistant;

    private RfAgent(RfAssistant assistant) {
        this.assistant = assistant;
    }

    /** 不启用知识库的创建方式 */
    public static RfAgent create(ModelProfile profile, SessionStore sessionStore) {
        return create(profile, sessionStore, null);
    }

    /**
     * 创建智能体。记忆按 sessionId 隔离，并从会话存储回填历史，
     * 保证应用重启后上下文连续。
     *
     * @param contentRetriever 知识库检索器；为 null 时不启用 RAG
     */
    public static RfAgent create(ModelProfile profile, SessionStore sessionStore,
                                 ContentRetriever contentRetriever) {
        StreamingChatModel model = ChatModelFactory.createStreaming(profile);
        ChatMemoryProvider memoryProvider = memoryId -> {
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(MAX_MEMORY_MESSAGES);
            ChatSession session = sessionStore.load((String) memoryId);
            if (session != null) {
                for (ChatMessageDto dto : session.getMessages()) {
                    if (ChatMessageDto.ROLE_USER.equals(dto.getRole())) {
                        memory.add(UserMessage.from(dto.getText()));
                    } else {
                        memory.add(AiMessage.from(dto.getText()));
                    }
                }
            }
            return memory;
        };
        AiServices<RfAssistant> builder = AiServices.builder(RfAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(memoryProvider);
        if (contentRetriever != null) {
            builder.contentRetriever(contentRetriever);
        }
        RfAssistant assistant = builder.build();
        return new RfAgent(assistant);
    }

    /** 发起一次流式对话，调用方需依次注册回调后 start() */
    public TokenStream chat(String sessionId, String userMessage) {
        return assistant.chat(sessionId, userMessage);
    }
}
