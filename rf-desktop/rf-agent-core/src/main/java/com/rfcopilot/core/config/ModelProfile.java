package com.rfcopilot.core.config;

import java.util.UUID;

/**
 * 一个模型接入配置（可在设置页编辑）。
 * 为 Jackson 序列化提供无参构造与 getter/setter。
 */
public class ModelProfile {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private ModelType type = ModelType.OPENAI_COMPATIBLE;
    private String baseUrl = "";
    private String apiKey = "";
    private String modelName = "";
    private Double temperature;
    /** 嵌入模型名（知识库 RAG 用），为空时按类型取默认值 */
    private String embeddingModel;

    public ModelProfile() {
    }

    public ModelProfile(String name, ModelType type, String baseUrl, String apiKey, String modelName, Double temperature) {
        this.name = name;
        this.type = type;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.temperature = temperature;
    }

    /** 显示名称，缺省时回退到模型名 */
    public String displayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return modelName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ModelType getType() {
        return type;
    }

    public void setType(ModelType type) {
        this.type = type;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}
