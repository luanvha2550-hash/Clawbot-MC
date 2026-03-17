package net.luanvha2550_hash;

import ai.djl.ModelException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.luanvha2550_hash.ChatUtils.BERTModel.BertModelManager;
import net.luanvha2550_hash.ChatUtils.NLPProcessor;
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
	public static BertModelManager modelManager;
	public static boolean loadedBERTModelIntoMemory = false;


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
			try {
				Thread.sleep(2000);
				System.out.println("NLP model deployment task complete");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

		});


		modelManager = BertModelManager.getInstance();

		// Inside AIPlayer.onInitialize()
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			configNetworkManager.registerServerModelNameSaveReceiver(server);
			configNetworkManager.registerServerAPIKeySaveReceiver(server);
			configNetworkManager.registerServerCustomProviderSaveReceiver(server);
			serverInstance = server;
			LOGGER.info("Server instance stored!");

			System.out.println("Server instance is " + serverInstance);

			LOGGER.info("Proceeding to load BERT model into memory");

			try {
				modelManager.loadModel();
				loadedBERTModelIntoMemory = true;
				LOGGER.info("BERT model loaded into memory. It will stay in memory as long as any bot stays active in game.");
			} catch (IOException | ModelException e) {
				LOGGER.error("BERT Model loading failed! {}", e.getMessage());
			}


		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {

			AutoFaceEntity.onServerStopped(server);


			try {
				if (modelManager.isModelLoaded() || loadedBERTModelIntoMemory) {
					modelManager.unloadModel();
					System.out.println("Unloaded BERT Model from memory");
				}
				else {
					System.out.println("BERT Model was not loaded, skipping unloading...");
				}

			} catch (IOException e) {
				LOGGER.error("BERT Model unloading failed!", e);
			}

		});

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity serverPlayer) {
                if (BotEventHandler.bot != null && serverPlayer.getUuid().equals(BotEventHandler.bot.getUuid())) {
                    // Save state first
                    QTableStorage.saveLastKnownState(BotEventHandler.getCurrentState(), BotEventHandler.qTableDir + "/lastKnownState.bin");

                    try {
                        // Load needed data for learning
                        QTable qTable = QTableStorage.loadQTable();
                        if (qTable == null) qTable = new QTable();

                        // Create a temporary agent wrapper for the learning process
                        RLAgent tempAgent = new RLAgent(0.1, qTable);

                        // Trigger death learning
                        BotEventHandler.handleBotDeath(qTable, tempAgent);

                    } catch (Exception e) {
                        LOGGER.error("Error during death learning trigger: ", e);
                    }
                }
            }
        });

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// Check if the respawned player is the bot
			if (oldPlayer instanceof ServerPlayerEntity && newPlayer instanceof ServerPlayerEntity && oldPlayer.getName().getString().equals(newPlayer.getName().getString())) {
				System.out.println("Bot has respawned. Updating state...");
				BotEventHandler.hasRespawned = true;
				BotEventHandler.botSpawnCount++;

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
