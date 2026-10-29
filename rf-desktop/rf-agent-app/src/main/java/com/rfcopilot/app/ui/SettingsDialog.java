package com.rfcopilot.app.ui;

import com.rfcopilot.app.AppContext;
import com.rfcopilot.core.config.ModelProfile;
import com.rfcopilot.core.config.ModelType;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.io.IOException;

/**
 * 设置对话框：管理模型档位（云端 OpenAI 兼容 / 本地 Ollama）并切换当前档位。
 */
public class SettingsDialog extends Dialog<Void> {

    private final AppContext ctx;

    private final ComboBox<ModelProfile> profileCombo = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final ComboBox<ModelType> typeCombo = new ComboBox<>();
    private final TextField baseUrlField = new TextField();
    private final TextField apiKeyField = new TextField();
    private final TextField modelNameField = new TextField();
    private final TextField temperatureField = new TextField();

    public SettingsDialog(AppContext ctx) {
        this.ctx = ctx;
        setTitle("模型设置");
        setHeaderText("配置对话模型：支持云端 OpenAI 兼容接口与本地 Ollama");

        GridPane grid = buildGrid();
        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(
                ButtonType.CLOSE,
                new ButtonType("新增档位", ButtonType.CLOSE.getButtonData()),
                new ButtonType("设为当前", ButtonType.CLOSE.getButtonData()),
                new ButtonType("保存", ButtonType.CLOSE.getButtonData()));

        setupCombo();
        loadProfile(ctx.getConfig().activeProfile());

        setResultConverter(button -> {
            String text = button.getText();
            if ("保存".equals(text)) {
                saveToProfile();
                try {
                    ctx.getConfigStore().save(ctx.getConfig());
                    ctx.rebuildAgent();
                } catch (IOException e) {
                    error("保存配置失败：" + e.getMessage());
                }
            } else if ("新增档位".equals(text)) {
                ModelProfile p = new ModelProfile("新档位", ModelType.OPENAI_COMPATIBLE,
                        "https://api.openai.com/v1", "", "gpt-4o-mini", 0.3);
                ctx.getConfig().getProfiles().add(p);
                profileCombo.getItems().add(p);
                profileCombo.setValue(p);
            } else if ("设为当前".equals(text)) {
                saveToProfile();
                ModelProfile selected = profileCombo.getValue();
                if (selected != null) {
                    ctx.getConfig().setActiveProfileId(selected.getId());
                    try {
                        ctx.getConfigStore().save(ctx.getConfig());
                        ctx.rebuildAgent();
                    } catch (IOException e) {
                        error("保存配置失败：" + e.getMessage());
                    }
                }
            }
            return null;
        });

        profileCombo.valueProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                loadProfile(sel);
            }
        });
    }

    private GridPane buildGrid() {
        typeCombo.getItems().addAll(ModelType.values());
        typeCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ModelType t) {
                return t == null ? "" : (t == ModelType.OPENAI_COMPATIBLE ? "云端（OpenAI 兼容）" : "本地 Ollama");
            }

            @Override
            public ModelType fromString(String s) {
                return null;
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        int r = 0;
        grid.addRow(r++, new Label("档位："), profileCombo);
        grid.addRow(r++, new Label("名称："), nameField);
        grid.addRow(r++, new Label("类型："), typeCombo);
        grid.addRow(r++, new Label("Base URL："), baseUrlField);
        grid.addRow(r++, new Label("API Key："), apiKeyField);
        grid.addRow(r++, new Label("模型名："), modelNameField);
        grid.addRow(r, new Label("温度："), temperatureField);
        baseUrlField.setPrefColumnCount(38);
        apiKeyField.setPrefColumnCount(38);
        return grid;
    }

    private void setupCombo() {
        profileCombo.getItems().setAll(ctx.getConfig().getProfiles());
        profileCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ModelProfile p) {
                return p == null ? "" : p.displayName();
            }

            @Override
            public ModelProfile fromString(String s) {
                return null;
            }
        });
    }

    private void loadProfile(ModelProfile p) {
        nameField.setText(p.getName());
        typeCombo.setValue(p.getType());
        baseUrlField.setText(p.getBaseUrl());
        apiKeyField.setText(p.getApiKey());
        modelNameField.setText(p.getModelName());
        temperatureField.setText(p.getTemperature() == null ? "" : String.valueOf(p.getTemperature()));
    }

    private void saveToProfile() {
        ModelProfile p = profileCombo.getValue();
        if (p == null) {
            return;
        }
        p.setName(nameField.getText().strip());
        if (typeCombo.getValue() != null) {
            p.setType(typeCombo.getValue());
        }
        p.setBaseUrl(baseUrlField.getText().strip());
        p.setApiKey(apiKeyField.getText().strip());
        p.setModelName(modelNameField.getText().strip());
        String t = temperatureField.getText().strip();
        try {
            p.setTemperature(t.isEmpty() ? null : Double.parseDouble(t));
        } catch (NumberFormatException e) {
            error("温度必须是数字");
        }
    }

    private void error(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.showAndWait();
    }
}
