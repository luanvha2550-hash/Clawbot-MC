package net.luanvha2550_hash.Commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.luanvha2550_hash.GameAI.BotEventHandler;
import net.luanvha2550_hash.Autonomy.AutonomyEngine;
import net.luanvha2550_hash.ChatUtils.NLPProcessorV2;
import net.luanvha2550_hash.ServiceLLMClients.LLMServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comando de debug do Clawbot-MC.
 * Salva logs detalhados para análise posterior.
 *
 * Uso: /clawdev [dump|status|log]
 */
public class ClawdevCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("clawdev-command");
    private static final Path LOG_DIR = Path.of("./clawbot-logs");

    /**
     * Registra o comando /clawdev
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("clawdev")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("dump")
                    .executes(ClawdevCommand::dumpDebugLog)
                )
                .then(literal("status")
                    .executes(ClawdevCommand::showStatus)
                )
                .then(literal("log")
                    .then(literal("start")
                        .executes(ctx -> toggleLogging(ctx, true))
                    )
                    .then(literal("stop")
                        .executes(ctx -> toggleLogging(ctx, false))
                    )
                )
                .then(literal("autonomy")
                    .executes(ClawdevCommand::showAutonomyStatus)
                )
                .then(literal("nlp")
                    .executes(ClawdevCommand::showNLPStatus)
                )
        );
    }

    /**
     * /clawdev dump - Gera log completo de debug
     */
    private static int dumpDebugLog(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        Path logFile = LOG_DIR.resolve("clawbot-dump_" + timestamp + ".txt");

        try {
            Files.createDirectories(LOG_DIR);
            StringBuilder log = new StringBuilder();

            log.append("=== CLAWBOT-MC DEBUG DUMP ===\n");
            log.append("Timestamp: ").append(timestamp).append("\n");
            log.append("Server: ").append(source.getServer().getServerPort()).append("\n\n");

            // Bot status
            if (BotEventHandler.bot != null) {
                log.append("=== BOT STATUS ===\n");
                log.append("Name: ").append(BotEventHandler.bot.getName().getString()).append("\n");
                log.append("Position: ").append(BotEventHandler.bot.getPos()).append("\n");
                log.append("Health: ").append(BotEventHandler.bot.getHealth()).append("/").append(BotEventHandler.bot.getMaxHealth()).append("\n");
                log.append("Dimension: ").append(BotEventHandler.bot.getWorld().getRegistryKey().getValue()).append("\n\n");
            } else {
                log.append("=== BOT: INACTIVE ===\n\n");
            }

            // Autonomy status
            AutonomyEngine engine = BotEventHandler.getAutonomyEngine();
            if (engine != null) {
                log.append("=== AUTONOMY STATUS ===\n");
                log.append("Running: ").append(engine.isRunning()).append("\n");
                log.append("Current Layer: ").append(engine.getCurrentLayer()).append("\n");
                log.append("Current Action: ").append(engine.getCurrentAction()).append("\n");
                log.append("Ticks Processed: ").append(engine.getTicksProcessed()).append("\n\n");
            } else {
                log.append("=== AUTONOMY: NOT INITIALIZED ===\n\n");
            }

            // NLP status
            log.append("=== NLP STATUS ===\n");
            log.append("NLPProcessorV2 Initialized: ").append(NLPProcessorV2.isInitialized()).append("\n\n");

            // Recent chat history
            log.append("=== RECENT CHAT HISTORY ===\n");
            log.append("(Last 10 messages from LLMServiceHandler)\n");
            log.append("Check database for full history\n\n");

            // System info
            log.append("=== SYSTEM INFO ===\n");
            log.append("Available Processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
            log.append("Free Memory: ").append(Runtime.getRuntime().freeMemory() / 1024 / 1024).append(" MB\n");
            log.append("Max Memory: ").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append(" MB\n");

            Files.writeString(logFile, log.toString());
            source.sendMessage(Text.literal("§9[Clawdev] §7Debug log salvo em: " + logFile.toString()));
            LOGGER.info("Debug dump created: {}", logFile);

            return 1;

        } catch (IOException e) {
            source.sendMessage(Text.literal("§c[Clawdev] Erro ao criar log: " + e.getMessage()));
            LOGGER.error("Failed to create debug dump", e);
            return 0;
        }
    }

    /**
     * /clawdev status - Mostra status rápido
     */
    private static int showStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        StringBuilder status = new StringBuilder();

        status.append("§9=== Clawbot Status ===\n");

        if (BotEventHandler.bot == null) {
            status.append("§cBot: INATIVO\n");
        } else {
            status.append("§aBot: ").append(BotEventHandler.bot.getName().getString()).append("\n");
            status.append("  Pos: ").append(String.format("%.1f, %.1f, %.1f",
                BotEventHandler.bot.getX(), BotEventHandler.bot.getY(), BotEventHandler.bot.getZ())).append("\n");
            status.append("  HP: ").append((int)BotEventHandler.bot.getHealth()).append("/").append((int)BotEventHandler.bot.getMaxHealth()).append("\n");
        }

        AutonomyEngine engine = BotEventHandler.getAutonomyEngine();
        if (engine == null) {
            status.append("§cAutonomia: NÃO INICIALIZADA\n");
        } else {
            status.append("§aAutonomia: ATIVA\n");
            status.append("  Layer: ").append(engine.getCurrentLayer()).append("\n");
            status.append("  Ticks: ").append(engine.getTicksProcessed()).append("\n");
        }

        status.append(NLPProcessorV2.isInitialized() ? "§aNLP: OK\n" : "§cNLP: OFFLINE\n");

        source.sendMessage(Text.literal(status.toString()));
        return 1;
    }

    /**
     * /clawdev autonomy - Status detalhado da autonomia
     */
    private static int showAutonomyStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        AutonomyEngine engine = BotEventHandler.getAutonomyEngine();

        if (engine == null) {
            source.sendMessage(Text.literal("§c[Clawdev] Autonomia não inicializada"));
            return 0;
        }

        StringBuilder info = new StringBuilder();
        info.append("§9=== Autonomy Engine Details ===\n");
        info.append("Running: ").append(engine.isRunning()).append("\n");
        info.append("Current Layer: ").append(engine.getCurrentLayer()).append("\n");
        info.append("Current Action: ").append(engine.getCurrentAction()).append("\n");
        info.append("Ticks: ").append(engine.getTicksProcessed()).append("\n");
        info.append("Last Tick Time: ").append(engine.getLastTickTime()).append("ms\n");

        source.sendMessage(Text.literal(info.toString()));
        return 1;
    }

    /**
     * /clawdev nlp - Status do NLP
     */
    private static int showNLPStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        StringBuilder info = new StringBuilder();
        info.append("§9=== NLP Status ===\n");
        info.append("Initialized: ").append(NLPProcessorV2.isInitialized()).append("\n");
        info.append("Model: all-MiniLM-L6-v2 (384 dims)\n");
        info.append("Intents: REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION, COMPLEX_ACTION, UNSPECIFIED\n");

        source.sendMessage(Text.literal(info.toString()));
        return 1;
    }

    /**
     * Toggle logging mode
     */
    private static int toggleLogging(CommandContext<ServerCommandSource> context, boolean enable) {
        ServerCommandSource source = context.getSource();

        if (enable) {
            source.sendMessage(Text.of("§9[Clawdev] §aLogging iniciado - logs em ./clawbot-logs/"));
            LOGGER.info("Logging enabled");
        } else {
            source.sendMessage(Text.of("§9[Clawdev] §7Logging parado"));
            LOGGER.info("Logging disabled");
        }

        return 1;
    }
}
