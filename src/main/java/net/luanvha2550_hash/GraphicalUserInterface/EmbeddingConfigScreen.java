package net.luanvha2550_hash.GraphicalUserInterface;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.luanvha2550_hash.AIPlayer;
import net.luanvha2550_hash.FilingSystem.EmbeddingClientFactory;
import net.luanvha2550_hash.FilingSystem.ManualConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Screen for configuring embedding provider and model settings.
 */
public class EmbeddingConfigScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget embeddingModelField;
    private TextFieldWidget ollamaEmbeddingModelField;

    // Provider buttons
    private ButtonWidget providerButton;
    private String selectedProvider;

    private static final Logger LOGGER = LoggerFactory.getLogger("EmbeddingConfig");

    // Available embedding providers
    private static final List<String> EMBEDDING_PROVIDERS = Arrays.asList(
            "same",      // Use same as LLM provider
            "ollama",    // Local Ollama embeddings
            "gemini",    // Google Gemini embeddings
            "openai"     // OpenAI embeddings
    );

    private int currentProviderIndex = 0;

    public EmbeddingConfigScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
        // Load current provider setting
        this.selectedProvider = AIPlayer.CONFIG.getEmbeddingProvider();
        if (this.selectedProvider == null || this.selectedProvider.isEmpty()) {
            this.selectedProvider = "same";
        }
        this.currentProviderIndex = EMBEDDING_PROVIDERS.indexOf(this.selectedProvider);
        if (this.currentProviderIndex < 0) {
            this.currentProviderIndex = 0;
        }
    }

    @Override
    protected void init() {
        super.init();

        int startY = this.height / 4 + 10;
        int fieldWidth = 250;
        int fieldHeight = 20;
        int labelWidth = 120;
        int startX = this.width / 2 - (fieldWidth / 2) + labelWidth;
        int buttonWidth = 150;

        // Provider Selection Button
        this.providerButton = ButtonWidget.builder(
                Text.of(getProviderDisplayName(this.selectedProvider)),
                (btn) -> {
                    // Cycle through providers
                    currentProviderIndex = (currentProviderIndex + 1) % EMBEDDING_PROVIDERS.size();
                    selectedProvider = EMBEDDING_PROVIDERS.get(currentProviderIndex);
                    btn.setMessage(Text.of(getProviderDisplayName(selectedProvider)));
                    updateModelFieldHint();
                }
        ).dimensions(startX, startY, fieldWidth, fieldHeight).build();
        this.addDrawableChild(this.providerButton);

        // Embedding Model Field (optional override)
        this.embeddingModelField = new TextFieldWidget(this.textRenderer, startX, startY + 40, fieldWidth, fieldHeight, Text.empty());
        this.embeddingModelField.setMaxLength(64);
        this.embeddingModelField.setText(AIPlayer.CONFIG.getEmbeddingModel());
        this.embeddingModelField.setPlaceholder(Text.of("Padrão do provider"));
        this.addDrawableChild(this.embeddingModelField);
        this.addSelectableChild(this.embeddingModelField);

        // Ollama Embedding Model Field
        this.ollamaEmbeddingModelField = new TextFieldWidget(this.textRenderer, startX, startY + 80, fieldWidth, fieldHeight, Text.empty());
        this.ollamaEmbeddingModelField.setMaxLength(64);
        this.ollamaEmbeddingModelField.setText(AIPlayer.CONFIG.getOllamaEmbeddingModel());
        this.ollamaEmbeddingModelField.setPlaceholder(Text.of("nomic-embed-text"));
        this.addDrawableChild(this.ollamaEmbeddingModelField);
        this.addSelectableChild(this.ollamaEmbeddingModelField);

        // Save Button
        ButtonWidget saveButton = ButtonWidget.builder(Text.of("Salvar"), (btn) -> {
            this.saveToFile();

            if (this.client != null) {
                this.client.getToastManager().add(
                        SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE,
                                Text.of("Config Salva!"),
                                Text.of("Embeddings configurados.")));
            }
        }).dimensions(this.width / 2 - buttonWidth - 10, startY + 130, buttonWidth, fieldHeight).build();
        this.addDrawableChild(saveButton);

        // Test Button
        ButtonWidget testButton = ButtonWidget.builder(Text.of("Testar"), (btn) -> {
            this.testConnection();

            if (this.client != null) {
                this.client.getToastManager().add(
                        SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE,
                                Text.of("Testando..."),
                                Text.of("Verifique o console para resultados.")));
            }
        }).dimensions(this.width / 2 + 10, startY + 130, buttonWidth, fieldHeight).build();
        this.addDrawableChild(testButton);

        // Done Button
        ButtonWidget doneButton = ButtonWidget.builder(Text.of("Voltar"), (btn) -> {
            assert this.client != null;
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - buttonWidth / 2, startY + 170, buttonWidth, fieldHeight).build();
        this.addDrawableChild(doneButton);

        // Update field hints based on provider
        updateModelFieldHint();
    }

    private void updateModelFieldHint() {
        // Show/hide fields based on provider
        String modelHint = switch (selectedProvider) {
            case "gemini" -> "gemini-embedding-001";
            case "openai" -> "text-embedding-3-small";
            case "ollama" -> "nomic-embed-text";
            default -> "Padrão do provider LLM";
        };
        this.embeddingModelField.setPlaceholder(Text.of(modelHint));
    }

    private String getProviderDisplayName(String provider) {
        return switch (provider) {
            case "same" -> "Igual ao LLM";
            case "ollama" -> "Ollama (Local)";
            case "gemini" -> "Google Gemini";
            case "openai" -> "OpenAI";
            default -> provider;
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int labelX = this.width / 2 - 170;
        int startY = this.height / 4 + 10;
        int spacing = 40;

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, "Configurar Embeddings", this.width / 2, startY - 20, 0xFFAA00);

        // Labels
        context.drawText(this.textRenderer, "Provider:", labelX, startY + 5, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "Modelo (opcional):", labelX, startY + spacing + 5, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "Modelo Ollama:", labelX, startY + (spacing * 2) + 5, 0xFFFFFFFF, true);

        // Hints
        int hintX = this.width / 2 + 100;
        context.drawTextWithShadow(this.textRenderer, "§7Gemini: gemini-embedding-001", hintX, startY + spacing + 5, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "§7OpenAI: text-embedding-3-small", hintX, startY + spacing + 15, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "§7Ollama: nomic-embed-text", hintX, startY + (spacing * 2) + 5, 0xAAAAAA);

        // Current status
        String currentProvider = ManualConfig.getActiveEmbeddingProvider();
        String status = "Provider atual: " + getProviderDisplayName(currentProvider);
        context.drawTextWithShadow(this.textRenderer, "§e" + status, labelX, startY + (spacing * 3) + 25, 0xFFFFAA);
    }

    private void saveToFile() {
        // Save embedding provider
        AIPlayer.CONFIG.setEmbeddingProvider(this.selectedProvider);

        // Save embedding model (optional)
        AIPlayer.CONFIG.setEmbeddingModel(this.embeddingModelField.getText());

        // Save Ollama embedding model
        String ollamaModel = this.ollamaEmbeddingModelField.getText();
        if (ollamaModel == null || ollamaModel.isEmpty()) {
            ollamaModel = "nomic-embed-text";
        }
        AIPlayer.CONFIG.setOllamaEmbeddingModel(ollamaModel);

        // Save config file
        AIPlayer.CONFIG.save();

        // Clear embedding client cache to force reload
        EmbeddingClientFactory.clearCache();

        LOGGER.info("Embedding configuration saved:");
        LOGGER.info("  Provider: {}", selectedProvider);
        LOGGER.info("  Model: {}", embeddingModelField.getText());
        LOGGER.info("  Ollama Model: {}", ollamaModel);
    }

    private void testConnection() {
        // Save first to apply settings
        this.saveToFile();

        // Test connection in background
        new Thread(() -> {
            try {
                LOGGER.info("Testing embedding connection...");
                boolean success = EmbeddingClientFactory.testConnection();
                if (success) {
                    LOGGER.info("✅ Embedding connection test PASSED");
                } else {
                    LOGGER.warn("❌ Embedding connection test FAILED");
                }
            } catch (Exception e) {
                LOGGER.error("❌ Embedding connection test error: {}", e.getMessage());
            }
        }).start();
    }

    @Override
    public void close() {
        assert this.client != null;
        this.client.setScreen(this.parent);
    }
}