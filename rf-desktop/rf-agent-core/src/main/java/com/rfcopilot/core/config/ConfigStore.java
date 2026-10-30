package com.rfcopilot.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rfcopilot.core.RfPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置持久化：~/.rfcopilot/config.json
 */
public class ConfigStore {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ConfigStore() {
        this(RfPaths.configFile());
    }

    public ConfigStore(Path file) {
        this.file = file;
    }

    /** 加载配置；文件不存在时写入默认配置 */
    public AppConfig load() throws IOException {
        RfPaths.ensureDirs();
        if (!Files.exists(file)) {
            AppConfig defaults = AppConfig.defaultConfig();
            save(defaults);
            return defaults;
        }
        AppConfig config = mapper.readValue(Files.readAllBytes(file), AppConfig.class);
        // 老配置自动补充内置离线嵌入档位
        boolean hasBuiltin = config.getProfiles().stream()
                .anyMatch(p -> p.getType() == ModelType.BUILTIN_ONNX);
        if (!hasBuiltin) {
            config.getProfiles().add(new ModelProfile(
                    "内置离线嵌入", ModelType.BUILTIN_ONNX, "", "", "bge-small-zh-v1.5", null));
            save(config);
        }
        return config;
    }

    public void save(AppConfig config) throws IOException {
        RfPaths.ensureDirs();
        Files.write(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config));
    }
}
