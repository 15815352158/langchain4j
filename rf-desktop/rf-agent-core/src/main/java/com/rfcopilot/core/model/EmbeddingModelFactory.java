package com.rfcopilot.core.model;

import com.rfcopilot.core.config.ModelProfile;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import java.time.Duration;

/**
 * 根据模型档位创建嵌入模型（知识库 RAG 用）。
 * 与聊天模型共用同一档位的 baseUrl / apiKey，模型名取 embeddingModel 字段。
 */
public final class EmbeddingModelFactory {

    /** 云端缺省嵌入模型 */
    public static final String DEFAULT_OPENAI_EMBEDDING = "text-embedding-3-small";
    /** 本地缺省嵌入模型 */
    public static final String DEFAULT_OLLAMA_EMBEDDING = "nomic-embed-text";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);

    private EmbeddingModelFactory() {
    }

    public static EmbeddingModel create(ModelProfile profile) {
        String modelName = profile.getEmbeddingModel();
        boolean blank = modelName == null || modelName.isBlank();
        return switch (profile.getType()) {
            case OPENAI_COMPATIBLE -> OpenAiEmbeddingModel.builder()
                    .baseUrl(profile.getBaseUrl())
                    .apiKey(profile.getApiKey() == null || profile.getApiKey().isBlank()
                            ? "EMPTY" : profile.getApiKey())
                    .modelName(blank ? DEFAULT_OPENAI_EMBEDDING : modelName.strip())
                    .timeout(DEFAULT_TIMEOUT)
                    .build();
            case OLLAMA -> OllamaEmbeddingModel.builder()
                    .baseUrl(profile.getBaseUrl())
                    .modelName(blank ? DEFAULT_OLLAMA_EMBEDDING : modelName.strip())
                    .timeout(Duration.ofSeconds(60))
                    .build();
            // 内置离线：进程内 ONNX，单例复用
            case BUILTIN_ONNX -> OnnxEmbeddingModel.getInstance();
        };
    }

    /** 实际生效的嵌入模型名（用于界面展示） */
    public static String effectiveModelName(ModelProfile profile) {
        if (profile.getType() == com.rfcopilot.core.config.ModelType.BUILTIN_ONNX) {
            return "bge-small-zh-v1.5（内置离线）";
        }
        String modelName = profile.getEmbeddingModel();
        if (modelName != null && !modelName.isBlank()) {
            return modelName.strip();
        }
        return profile.getType() == com.rfcopilot.core.config.ModelType.OLLAMA
                ? DEFAULT_OLLAMA_EMBEDDING : DEFAULT_OPENAI_EMBEDDING;
    }
}
