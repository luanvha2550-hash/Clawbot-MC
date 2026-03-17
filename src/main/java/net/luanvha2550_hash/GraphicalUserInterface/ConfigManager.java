package net.luanvha2550_hash.GraphicalUserInterface;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.luanvha2550_hash.AIPlayer;
import net.luanvha2550_hash.AIPlayerClient;
import net.luanvha2550_hash.GraphicalUserInterface.Widgets.DropdownMenuWidget;
import net.luanvha2550_hash.Network.configNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tela de configuração do Clawbot-MC com design moderno inspirado no OpenClaw.
 * Estilo escuro com acentos em azul/ciano e organização em seções.
 */
public class ConfigManager extends Screen {
    public static final Logger LOGGER = LoggerFactory.getLogger("ConfigMan");
    public Screen parent;
    private DropdownMenuWidget dropdownMenuWidget;
    private TextFieldWidget searchField;
    private List<String> allModels;
    private List<String> filteredModels;

    // Cores do tema OpenClaw
    private static final int BACKGROUND_COLOR = 0xFF1A1A2E;      // Azul escuro profundo
    private static final int HEADER_COLOR = 0xFF16213E;          // Azul mais claro para header
    private static final int ACCENT_COLOR = 0xFF0F3460;          // Azul acento
    private static final int HIGHLIGHT_COLOR = 0xFFE94560;       // Vermelho/rosa destaque
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;          // Branco
    private static final int TEXT_SECONDARY = 0xFFAAAAAA;        // Cinza claro
    private static final int TEXT_GOLD = 0xFFFFD700;             // Dourado
    private static final int BUTTON_HOVER = 0xFF533483;          // Roxo hover

    public ConfigManager(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        LOGGER.info("Atualizando lista de modelos do provedor...");
        AIPlayer.CONFIG.updateModels();

        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            AIPlayerClient.CONFIG.updateModels();
        }

        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            allModels = AIPlayerClient.CONFIG.getModelList();
        } else {
            allModels = AIPlayer.CONFIG.getModelList();
        }
        LOGGER.info("Obtidos {} modelos do provedor.", allModels.size());
        filteredModels = new ArrayList<>(allModels);

        int centerX = this.width / 2;
        int panelWidth = 400;
        int panelX = centerX - panelWidth / 2;
        int startY = 80;

        // Campo de busca com estilo moderno
        searchField = new TextFieldWidget(this.textRenderer, panelX + 20, startY + 40, panelWidth - 40, 22, Text.of("Buscar modelos..."));
        searchField.setMaxLength(256);
        searchField.setPlaceholder(Text.of("🔍 Buscar modelos..."));
        searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(searchField);
        this.addSelectableChild(searchField);

        // Dropdown de modelos
        dropdownMenuWidget = new DropdownMenuWidget(panelX + 20, startY + 80, panelWidth - 40, 24,
            Text.of("Selecione um modelo"), filteredModels);
        this.dropdownMenuWidget = dropdownMenuWidget;
        this.addSelectableChild(dropdownMenuWidget);

        // Botões estilizados
        int buttonY = this.height - 60;
        int buttonWidth = 85;
        int buttonHeight = 24;
        int spacing = 10;
        int totalWidth = buttonWidth * 5 + spacing * 4;
        int buttonsStartX = centerX - totalWidth / 2;

        // Botão Chaves API
        ButtonWidget apiKeysButton = createStyledButton("Chaves API", buttonsStartX, buttonY, buttonWidth, buttonHeight,
            (btn) -> Objects.requireNonNull(this.client).setScreen(new APIKeysScreen(Text.of("Chaves API"), this)));
        this.addDrawableChild(apiKeysButton);

        // Botão Embeddings (novo)
        ButtonWidget embeddingButton = createStyledButton("Embeddings", buttonsStartX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight,
            (btn) -> Objects.requireNonNull(this.client).setScreen(new EmbeddingConfigScreen(Text.of("Embeddings"), this)));
        this.addDrawableChild(embeddingButton);

        // Botão Log de Raciocínio
        ButtonWidget reasoningButton = createStyledButton("Log", buttonsStartX + (buttonWidth + spacing) * 2, buttonY, buttonWidth, buttonHeight,
            (btn) -> Objects.requireNonNull(this.client).setScreen(new ReasoningLogScreen(this)));
        this.addDrawableChild(reasoningButton);

        // Botão Atualizar
        ButtonWidget reloadButton = createStyledButton("🔄 Atualizar", buttonsStartX + (buttonWidth + spacing) * 3, buttonY, buttonWidth, buttonHeight,
            (btn) -> this.reloadModels());
        this.addDrawableChild(reloadButton);

        // Botão Salvar (destaque)
        ButtonWidget saveButton = createPrimaryButton("Salvar", buttonsStartX + (buttonWidth + spacing) * 4, buttonY, buttonWidth, buttonHeight,
            (btn) -> this.saveToFile());
        this.addDrawableChild(saveButton);

        // Botão Fechar (canto superior direito)
        ButtonWidget closeButton = createCloseButton(this.width - 30, 10, 20, 20);
        this.addDrawableChild(closeButton);

        this.addDrawableChild(dropdownMenuWidget);
    }

    private ButtonWidget createStyledButton(String text, int x, int y, int width, int height, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.of(text), action)
            .dimensions(x, y, width, height)
            .build();
    }

    private ButtonWidget createPrimaryButton(String text, int x, int y, int width, int height, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.literal(text).styled(s -> s.withColor(Formatting.GREEN)), action)
            .dimensions(x, y, width, height)
            .build();
    }

    private ButtonWidget createCloseButton(int x, int y, int width, int height) {
        return ButtonWidget.builder(Text.literal("✕").styled(s -> s.withColor(Formatting.RED)), (btn) -> this.close())
            .dimensions(x, y, width, height)
            .build();
    }

    private void reloadModels() {
        LOGGER.info("Atualizando lista de modelos...");
        AIPlayer.CONFIG.updateModels();

        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            allModels = AIPlayerClient.CONFIG.getModelList();
        } else {
            allModels = AIPlayer.CONFIG.getModelList();
        }
        filteredModels = new ArrayList<>(allModels);
        dropdownMenuWidget.updateOptions(filteredModels);

        if (this.client != null) {
            this.client.getToastManager().add(
                SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE,
                    Text.of("Modelos Atualizados"),
                    Text.of("Encontrados " + allModels.size() + " modelos")));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fundo com gradiente escuro
        this.renderBackground(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int panelWidth = 400;
        int panelX = centerX - panelWidth / 2;

        // Painel principal com borda sutil
        context.fill(panelX, 50, panelX + panelWidth, this.height - 80, 0xFF252540);
        context.fill(panelX, 50, panelX + panelWidth, 52, HIGHLIGHT_COLOR); // Linha de destaque no topo

        // Título principal estilizado
        String title = "⚙ Clawbot-MC Configuração";
        int titleWidth = this.textRenderer.getWidth(title);
        context.drawText(this.textRenderer, title, centerX - titleWidth / 2, 65, TEXT_GOLD, true);

        // Subtítulo
        String subtitle = "Selecione o modelo de linguagem para o bot";
        int subWidth = this.textRenderer.getWidth(subtitle);
        context.drawText(this.textRenderer, subtitle, centerX - subWidth / 2, 80, TEXT_SECONDARY, true);

        // Labels dos campos
        context.drawText(this.textRenderer, "Buscar Modelos:", panelX + 20, 105, TEXT_PRIMARY, true);
        context.drawText(this.textRenderer, "Modelo Selecionado:", panelX + 20, 145, TEXT_PRIMARY, true);

        // Informação do modelo atual
        String currentModel = AIPlayer.CONFIG.getSelectedLanguageModel();
        String currentText = "Atual: " + (currentModel != null ? currentModel : "Nenhum");
        context.drawText(this.textRenderer, currentText, panelX + 20, 185, 0xFF00FF00, true);

        // Contador de modelos
        String countText = filteredModels.size() + " / " + allModels.size() + " modelos";
        context.drawText(this.textRenderer, countText, panelX + panelWidth - 100, 185, TEXT_SECONDARY, true);

        // Linha divisória
        context.fill(panelX + 20, this.height - 80, panelX + panelWidth - 20, this.height - 79, 0xFF444444);

        // Rodapé com instrução
        String helpText = "Busque e selecione um modelo • Clique em Salvar para confirmar";
        int helpWidth = this.textRenderer.getWidth(helpText);
        context.drawText(this.textRenderer, helpText, centerX - helpWidth / 2, this.height - 25, TEXT_SECONDARY, true);

        super.render(context, mouseX, mouseY, delta);
    }

    private void onSearchChanged(String searchText) {
        if (searchText.trim().isEmpty()) {
            filteredModels = new ArrayList<>(allModels);
        } else {
            filteredModels = allModels.stream()
                .filter(model -> model.toLowerCase().contains(searchText.toLowerCase().trim()))
                .collect(Collectors.toList());
        }
        dropdownMenuWidget.updateOptions(filteredModels);
    }

    private void saveToFile() {
        String modelName = this.dropdownMenuWidget.getSelectedOption();

        if (modelName == null || modelName.trim().isEmpty()) {
            LOGGER.warn("Nenhum modelo selecionado. Salvamento cancelado.");

            if (this.client != null) {
                this.client.getToastManager().add(
                    SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE,
                        Text.of("Erro"), Text.of("Por favor, selecione um modelo primeiro!")));
            }
            return;
        }

        AIPlayer.CONFIG.setSelectedLanguageModel(modelName);
        AIPlayer.CONFIG.save();
        configNetworkManager.sendSaveConfigPacket(modelName);

        if (this.client != null) {
            this.client.getToastManager().add(
                SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE,
                    Text.of("Configurações Salvas!"),
                    Text.of("Modelo " + modelName + " selecionado.")));
        }

        close();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
