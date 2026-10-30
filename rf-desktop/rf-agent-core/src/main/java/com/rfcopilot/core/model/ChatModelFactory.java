package com.rfcopilot.core.model;

import com.rfcopilot.core.config.ModelProfile;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;

/**
 * 根据模型档位创建流式聊天模型。
 */
public final class ChatModelFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private ChatModelFactory() {
    }

    public static StreamingChatModel createStreaming(ModelProfile profile) {
        return switch (profile.getType()) {
            case OPENAI_COMPATIBLE -> {
                OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                        .baseUrl(profile.getBaseUrl())
                        .apiKey(profile.getApiKey() == null || profile.getApiKey().isBlank()
                                ? "EMPTY" : profile.getApiKey())
                        .modelName(profile.getModelName())
                        .timeout(DEFAULT_TIMEOUT);
                if (profile.getTemperature() != null) {
                    builder.temperature(profile.getTemperature());
                }
                yield builder.build();
            }
            case OLLAMA -> {
                OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder = OllamaStreamingChatModel.builder()
                        .baseUrl(profile.getBaseUrl())
                        .modelName(profile.getModelName());
                if (profile.getTemperature() != null) {
                    builder.temperature(profile.getTemperature());
                }
                yield builder.build();
            }
            // 内置档位只做知识库嵌入，不能当对话模型
            case BUILTIN_ONNX -> throw new IllegalArgumentException(
                    "「内置离线嵌入」档位仅供知识库使用，对话请选择云端或 Ollama 档位");
        };
    }
}
