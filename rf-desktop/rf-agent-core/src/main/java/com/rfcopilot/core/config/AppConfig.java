package com.rfcopilot.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用全局配置：模型档位列表 + 当前激活档位。
 */
public class AppConfig {

    private String activeProfileId;
    /** 知识库嵌入使用的档位 id；为空时跟随当前激活档位 */
    private String embeddingProfileId;
    private List<ModelProfile> profiles = new ArrayList<>();

    public AppConfig() {
    }

    /** 首次运行的默认配置：一个本地 Ollama 档位 + 一个云端 OpenAI 兼容档位 */
    public static AppConfig defaultConfig() {
        AppConfig config = new AppConfig();
        ModelProfile ollama = new ModelProfile(
                "本地 Ollama", ModelType.OLLAMA, "http://localhost:11434", "", "qwen2.5:14b", 0.3);
        ollama.setEmbeddingModel("nomic-embed-text");
        ModelProfile cloud = new ModelProfile(
                "云端（OpenAI 兼容）", ModelType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "", "gpt-4o-mini", 0.3);
        cloud.setEmbeddingModel("text-embedding-3-small");
        config.getProfiles().add(cloud);
        config.getProfiles().add(ollama);
        config.setActiveProfileId(cloud.getId());
        return config;
    }

    /** 当前激活的模型档位；找不到时回退到第一个 */
    public ModelProfile activeProfile() {
        if (profiles.isEmpty()) {
            throw new IllegalStateException("未配置任何模型档位，请在设置页添加");
        }
        return profiles.stream()
                .filter(p -> p.getId().equals(activeProfileId))
                .findFirst()
                .orElse(profiles.get(0));
    }

    /** 知识库嵌入档位；未设置或对应档位已删除时回退到当前激活档位 */
    public ModelProfile embeddingProfile() {
        if (embeddingProfileId != null && !embeddingProfileId.isBlank()) {
            ModelProfile p = profiles.stream()
                    .filter(x -> x.getId().equals(embeddingProfileId))
                    .findFirst()
                    .orElse(null);
            if (p != null) {
                return p;
            }
        }
        return activeProfile();
    }

    public String getActiveProfileId() {
        return activeProfileId;
    }

    public void setActiveProfileId(String activeProfileId) {
        this.activeProfileId = activeProfileId;
    }

    public String getEmbeddingProfileId() {
        return embeddingProfileId;
    }

    public void setEmbeddingProfileId(String embeddingProfileId) {
        this.embeddingProfileId = embeddingProfileId;
    }

    public List<ModelProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<ModelProfile> profiles) {
        this.profiles = profiles;
    }
}
