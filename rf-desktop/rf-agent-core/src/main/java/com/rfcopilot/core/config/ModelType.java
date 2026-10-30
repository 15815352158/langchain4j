package com.rfcopilot.core.config;

/**
 * 模型接入类型：云端 OpenAI 兼容接口、本地 Ollama，或内置离线 ONNX 嵌入（仅供知识库）。
 */
public enum ModelType {
    OPENAI_COMPATIBLE,
    OLLAMA,
    BUILTIN_ONNX
}
