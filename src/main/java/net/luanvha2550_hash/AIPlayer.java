package net.luanvha2550_hash;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.luanvha2550_hash.AIProviders.LLMDecisionEngine;
import net.luanvha2550_hash.ChatUtils.NLPProcessor;
import net.luanvha2550_hash.ChatUtils.NLPProcessorV2;
import net.luanvha2550_hash.Commands.configCommand;
import net.luanvha2550_hash.Commands.modCommandRegistry;
import net.luanvha2550_hash.Database.QTable;
import net.luanvha2550_hash.Database.SQLiteDB;
import net.luanvha2550_hash.FilingSystem.ManualConfig;
import net.luanvha2550_hash.GameAI.BotEventHandler;

import net.luanvha2550_hash.Database.QTableStorage;
import net.luanvha2550_hash.Entity.AutoFaceEntity;
import net.luanvha2550_hash.GameAI.RLAgent;
import net.luanvha2550_hash.Network.OpenConfigPayload;
import net.luanvha2550_hash.Network.SaveAPIKeyPayload;
import net.luanvha2550_hash.Network.SaveConfigPayload;
import net.luanvha2550_hash.Network.SaveCustomProviderPayload;
import net.luanvha2550_hash.Network.configNetworkManager;
import net.luanvha2550_hash.WebSearch.AISearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;


public class AIPlayer implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
	public static final ManualConfig CONFIG = ManualConfig.load();
	public static MinecraftServer serverInstance = null; // default for now
	// NLPProcessorV2 é o sistema principal - BERT é legado
	public static boolean isNLPV2Initialized = false;


	@Override
	public void onInitialize() {

		LOGGER.info("Hello Fabric world!");

		LOGGER.debug("Running on environment type: {}", FabricLoader.getInstance().getEnvironmentType());

		// Fix DJL cache directory path on Windows (Issue #33)
		// DJL constructs paths incorrectly on Windows, missing backslash after username
		// Explicitly set the cache directory to avoid path construction bugs
		String userHome = System.getProperty("user.home");
		if (userHome != null && !userHome.isEmpty()) {
			String djlCacheDir = userHome + "/.djl.ai";
			System.setProperty("DJL_CACHE_DIR", djlCacheDir);
			LOGGER.info("Set DJL cache directory to: {}", djlCacheDir);
		}

		String llmProvider = ManualConfig.getActiveProvider();

		System.out.println("Using provider: " + llmProvider);

		// Debug: Print ALL system properties to see what's available
		System.out.println("=== ALL SYSTEM PROPERTIES ===");
		System.getProperties().forEach((key, value) -> {
			if (key.toString().contains("aiplayer") || key.toString().contains("llm")) {
				System.out.println(key + " = " + value);
			}
		});
		System.out.println("=== END DEBUG ===");


        // registering the packets on the global entrypoint to recognise them

		PayloadTypeRegistry.playC2S().register(SaveConfigPayload.ID, SaveConfigPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenConfigPayload.ID, OpenConfigPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveAPIKeyPayload.ID, SaveAPIKeyPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveCustomProviderPayload.ID, SaveCustomProviderPayload.CODEC);


		modCommandRegistry.register();
		configCommand.register();
		SQLiteDB.createDB();
		QTableStorage.setupQTableStorage();


		CompletableFuture.runAsync(() -> {

			AISearchConfig.setupIfMissing();
			NLPProcessor.ensureLocalNLPModel();

			// Inicializar NLPProcessorV2 (novo sistema de embeddings)
			try {
				NLPProcessorV2.initialize();
				LOGGER.info("✅ NLPProcessorV2 inicializado com sucesso!");
			} catch (Exception e) {
				LOGGER.error("❌ Falha ao inicializar NLPProcessorV2: {}", e.getMessage());
				// Fallback para NLP antigo já está inicializado
			}

			try {
				Thread.sleep(2000);
				System.out.println("NLP model deployment task complete");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

		});


		// Usar NLPProcessorV2 como sistema principal (mais estável e em português)
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			configNetworkManager.registerServerModelNameSaveReceiver(server);
			configNetworkManager.registerServerAPIKeySaveReceiver(server);
			configNetworkManager.registerServerCustomProviderSaveReceiver(server);
			serverInstance = server;
			LOGGER.info("Server instance stored!");

			System.out.println("Server instance is " + serverInstance);

			// NLPProcessorV2 já foi inicializado no async task acima
			// BERT model é legado e não será mais carregado para evitar crashes
			LOGGER.info("NLP Processor V2 está ativo. BERT model legado desativado.");
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {

			AutoFaceEntity.onServerStopped(server);

			// Cleanup NLPProcessorV2 resources
			try {
				NLPProcessorV2.shutdown();
				LOGGER.info("NLPProcessorV2 desligado com sucesso");
			} catch (Exception e) {
				LOGGER.error("Erro ao desligar NLPProcessorV2: {}", e.getMessage());
			}

		});

        // Handler de morte - captura causa e salva estado
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity serverPlayer) {
                if (BotEventHandler.bot != null && serverPlayer.getUuid().equals(BotEventHandler.bot.getUuid())) {
                    // Extrair causa da morte e salvar no handler (Minecraft 1.21.1 - getName() retorna String)
                    String deathCause = damageSource.getName();
                    BotEventHandler.lastDeathCause = deathCause;
                    LOGGER.info("💀 Bot morreu por: {}", deathCause);

                    // Salvar estado primeiro
                    QTableStorage.saveLastKnownState(BotEventHandler.getCurrentState(), BotEventHandler.qTableDir + "/lastKnownState.bin");

                    try {
                        // Load needed data for learning
                        QTable qTable = QTableStorage.loadQTable();
                        if (qTable == null) qTable = new QTable();

                        // Create a temporary agent wrapper for the learning process
                        RLAgent tempAgent = new RLAgent(0.1, qTable);

                        // Trigger death learning
                        BotEventHandler.handleBotDeath(qTable, tempAgent);

                        // Set death flag
                        BotEventHandler.botDied = true;

                    } catch (Exception e) {
                        LOGGER.error("Error during death learning trigger: ", e);
                    }
                }
            }
        });

        // Handler de respawn - re-inicializa autonomia e restaura estado
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Check if the respawned player is the bot
            if (BotEventHandler.bot != null &&
                oldPlayer.getUuid().equals(BotEventHandler.bot.getUuid()) &&
                newPlayer instanceof ServerPlayerEntity) {

                LOGGER.info("✨ Bot respawned! old={}, new={}, alive={}",
                    oldPlayer.getName().getString(),
                    newPlayer.getName().getString(),
                    alive);

                // Re-inicializar autonomia e restaurar estado
                BotEventHandler.handleBotRespawn((ServerPlayerEntity) newPlayer,
                    BotEventHandler.lastDeathCause != null ? BotEventHandler.lastDeathCause : "unknown");
            }
        });

		// Player retaliation tracking - track hits on bot players
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Check if the damaged entity is a bot player
			if (entity instanceof ServerPlayerEntity bot) {
				// Check if damage source is another player
				if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity attacker) {
					// Record the hit for retaliation tracking
					net.luanvha2550_hash.PlayerUtils.PlayerRetaliationTracker.recordPlayerHit(bot, attacker);
				}
			}
			return true; // Allow damage to proceed
		});

	}


}
