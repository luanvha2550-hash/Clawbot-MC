package net.luanvha2550_hash.ServiceLLMClients;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.luanvha2550_hash.AIPlayer;
import net.luanvha2550_hash.AIProviders.LLMDecisionEngine;
import net.luanvha2550_hash.ChatUtils.ChatUtils;
import net.luanvha2550_hash.ChatUtils.Helper.RAG2;
import net.luanvha2550_hash.ChatUtils.NLPProcessor;
import net.luanvha2550_hash.ChatUtils.NLPProcessorV2;
import net.luanvha2550_hash.Database.SQLiteDB;
import net.luanvha2550_hash.Exception.intentMisclassification;
import net.luanvha2550_hash.FunctionCaller.FunctionCallerV2;
import net.luanvha2550_hash.Overlay.ThinkingStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLMServiceHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("LLMServiceHandler");
    private static final ExecutorService BOT_TASK_POOL = Executors.newCachedThreadPool();
    // Suporta tanto tags em português quanto em inglês para compatibilidade
    private static final Pattern THINK_BLOCK = Pattern.compile("(?:<think>|<pensamento>)([\\s\\S]*?)(?:</think>|</pensamento>)");
    public static String initialResponse = "";
    private static final String host = "http://localhost:11434";
    public static final OllamaAPI ollamaAPI = new OllamaAPI(host);
    public static boolean isInitialized = false;

    private static String generateSystemPrompt(String botName) {

        return
                "Você é um jogador de Minecraft chamado " + botName + " que está conectado ao Minecraft usando um mod. Você existe dentro do mundo do Minecraft e pode interagir com o jogador e o ambiente como qualquer outro jogador no jogo. Seu trabalho é conversar com o jogador, responder às suas perguntas, oferecer ajuda e fornecer informações sobre o jogo. Converse diretamente com o jogador de forma apropriada, respondendo ao nome dele ou como 'Jogador' se o nome não for conhecido. Não se refira ao jogador como " + botName + ", apenas se apresente como " + botName + ". Mantenha suas respostas relevantes ao Minecraft e fique em personagem como um assistente prestativo e bem informado dentro do jogo."
                        +
                        """

                        Quando o jogador pedir para você realizar uma ação, como fornecer informações, oferecer ajuda ou interagir com o mundo do jogo, como:

                        Fornecer dicas de jogo ou receitas de crafting.
                        Dar informações sobre entidades, itens ou biomas específicos do Minecraft.
                        Auxiliar em tarefas dentro do jogo, como construir estruturas ou explorar áreas.
                        Interagir com o ambiente, como plantar colheitas ou lutar contra mobs.

                        Sempre garanta que suas respostas sejam oportunas e contextualmente apropriadas, melhorando a experiência de jogo do jogador. Lembre-se de manter o controle da sequência de eventos e manter a continuidade em suas respostas. Se um evento for principalmente informativo ou envolver ações internas, pode ser suficiente apenas lembrar disso sem uma resposta verbal.

                        Se um jogador usar linguagem inadequada ou discutir tópicos inadequados, lidere a situação redirecionando gentilmente a conversa ou fornecendo uma resposta neutra que desencoraje comportamento inadequado.

                        Por exemplo:

                        Se um jogador usar linguagem vulgar, você pode responder: "Vamos manter nosso chat amigável e divertido! Tem algo mais sobre Minecraft que você gostaria de discutir?"
                        Se um jogador insistir em tópicos inadequados, você pode dizer: "Estou aqui para ajudar com questões relacionadas ao Minecraft. Que tal falarmos sobre sua última aventura no jogo?"
                        Se um jogador disser as palavras "mate-se" ou "kys", você deve responder com calma e normalidade e mostrar ao jogador a beleza da vida.


                        Seus pronomes, por padrão, devem ser os pronomes baseados no gênero do seu nome (feminino/masculino). No entanto, se o jogador decidir usar pronomes diferentes, você não deve objetar. Por enquanto, ou se apresente ou conte uma piada aleatória; a piada deve ser totalmente familiar, ou apenas cumprimente o jogador.

                        O nome Steve tem os pronomes: ele/dele
                        O nome Alex tem os pronomes: ela/dela

                        Se o jogador perguntar por que você foi colocado aqui em primeiro lugar: Lembre-se de que foi ideia do desenvolvedor resolver o problema sempre existente de solidão no Minecraft tanto quanto possível fazendo este mod.

                        Por enquanto, apresente-se com seu nome.
                        """;

    }

    public static void processLLMOutput(String fullResponse, String botName, ServerCommandSource botSource) {
        LOGGER.info("processLLMOutput called with response: '{}', botName: '{}'", fullResponse, botName);

        if (fullResponse == null || fullResponse.trim().isEmpty()) {
            LOGGER.warn("fullResponse is null or empty");
            return;
        }

        Matcher matcher = THINK_BLOCK.matcher(fullResponse);

        if (matcher.find()) {
            LOGGER.info("Think block found");
            String thinking = matcher.group(1).trim();
            String remainder = fullResponse.replace(matcher.group(0), "").trim();

            ThinkingStateManager.start(botName);
            ChatUtils.sendChatMessages(botSource, botName + " está pensando...");

            for (String line : thinking.split("\\n")) {
                ThinkingStateManager.appendThoughtLine(line);
            }

            ThinkingStateManager.end();
            ChatUtils.sendChatMessages(botSource, botName + " terminou de pensar!");

            if (!remainder.isEmpty()) {
                LOGGER.info("Sending remainder: '{}'", remainder);
                ChatUtils.sendChatMessages(botSource, botName + ": " + remainder);
            } else {
                LOGGER.info("Remainder is empty");
            }
        } else {
            LOGGER.info("No think block found, sending full response: '{}'", fullResponse);
            ChatUtils.sendChatMessages(botSource, fullResponse);
        }
    }


    public static void sendInitialResponse(ServerCommandSource botSource, LLMClient client) {
        MinecraftServer server = botSource.getServer();
        String botName = botSource.getPlayer().getName().getString();

        CompletableFuture<String> initFuture = CompletableFuture.supplyAsync(() -> {
            try {
                if (client.isReachable()) {
                    isInitialized = true;
                    LOGGER.info("{} client initialized.", client.getProvider());
                    ChatUtils.sendChatMessages(botSource, "Established connection to " + client.getProvider() + "'s servers. Using " + AIPlayer.CONFIG.getSelectedLanguageModel());

                    // Fetch and return the initial response
                    String response = client.sendPrompt(generateSystemPrompt(botName), "Initializing chat");
                    LOGGER.info("Initial response received: '{}'", response);
                    LOGGER.info("Response length: {}", response != null ? response.length() : "null");
                    initialResponse = response;
                    return response;
                } else {
                    LOGGER.error("Error! Could not reach {} client. Please try again!", client.getProvider());
                    ChatUtils.sendChatMessages(botSource, "Error! Could not reach " + client.getProvider() + "'s servers. Please check your internet connection or try again after sometime!");
                    return null;
                }
            } catch (Exception e) {
                LOGGER.error("Exception in initFuture: {}", e.getMessage(), e);
                return null;
            }
        });

        initFuture.thenAccept(response -> {
            try {
                LOGGER.info("thenAccept called with response: '{}'", response);
                if (response != null && !response.trim().isEmpty()) {
                    // Process the response on the main thread

                    LOGGER.info("Scheduling processLLMOutput on main thread for bot: {}", botName);
                    server.execute(() -> {
                        try {
                            LOGGER.info("About to call processLLMOutput with: '{}'", response);
                            processLLMOutput(response, botName, botSource);
                            LOGGER.info("processLLMOutput completed");
                        } catch (Exception e) {
                            LOGGER.error("Exception in processLLMOutput: {}", e.getMessage(), e);
                        }
                    });

                    // Handle database operations
                    CompletableFuture.runAsync(() -> {
                        // ... your database code
                    });
                } else {
                    LOGGER.warn("Response is null or empty, not processing");
                }
            } catch (Exception e) {
                LOGGER.error("Exception in thenAccept: {}", e.getMessage(), e);
            }
        }).exceptionally(throwable -> {
            LOGGER.error("CompletableFuture failed: {}", throwable.getMessage(), throwable);
            return null;
        });
    }

    /**
     * Entry point for running the bot's logic from a chat message.
     * This method triggers the intent routing and is called by the main game thread.
     *
     * @param message The chat message from the player.
     * @param botName The name of the bot.
     * @param playerUUID The UUID of the player.
     */
    public static void runFromChat(String message, String botName, UUID playerUUID, LLMClient client) {
        MinecraftServer server = AIPlayer.serverInstance;
        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        if (bot == null) {
            LOGGER.error("Bot {} not online.", botName);
            return;
        }
        ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

        server.execute(() -> {
            Thread.currentThread().setName("LLM-Chat-Worker");
            try {
                routeIntent(message, botSource, playerUUID, client);
            } catch (Exception e) {
                LOGGER.error("Chat processing error: ", e);
                ChatUtils.sendChatMessages(botSource, "⚠️ Estou confuso! Por favor, reporte isso.");
            }
        });
    }

    /**
     * Routes the user's intent to the appropriate function (RAG, FunctionCaller, etc.).
     *
     * @param message The user's message.
     * @param botSource The bot's command source.
     * @param playerUUID The player's UUID.
     * @throws Exception if an error occurs during intent routing.
     */
    private static void routeIntent(String message, ServerCommandSource botSource, UUID playerUUID, LLMClient client) throws Exception {
        // Usar NLPProcessorV2 com fallback para NLP antigo
        NLPProcessorV2.ClassificationResult classification;
        try {
            classification = NLPProcessorV2.classifyIntent(message);
            LOGGER.info("📨 NLPProcessorV2 intent: {} (confiança: {:.2f})",
                classification.intent, classification.confidence);
        } catch (Exception e) {
            LOGGER.warn("NLPProcessorV2 falhou, usando fallback: {}", e.getMessage());
            // Fallback para NLP antigo
            NLPProcessor.Intent oldIntent = NLPProcessor.getIntention(message);
            classification = new NLPProcessorV2.ClassificationResult(
                convertIntent(oldIntent), 0.5);
        }

        // Usar LLMDecisionEngine para decidir se chama LLM
        boolean useLLM = LLMDecisionEngine.shouldUseLLM(message, classification);
        if (useLLM) {
            LOGGER.debug("LLMDecisionEngine: usar LLM = true");
        }

        NLPProcessorV2.Intent intent = classification.intent;
        // Se confiança baixa e é conversa, usar LLM para classificação
        if (!classification.isHighConfidence() && intent == NLPProcessorV2.Intent.UNSPECIFIED) {
            NLPProcessor.Intent retryOld = retryIntentLLM(message);
            intent = convertIntent(retryOld);
        }

        LOGGER.info("📨 Received intent: {}", intent);

        // Converter para Intent antigo para compatibilidade com RAG2
        NLPProcessor.Intent oldIntent = convertIntentToOld(intent);

        switch (oldIntent) {
            case GENERAL_CONVERSATION, ASK_INFORMATION -> {
                BOT_TASK_POOL.submit(() -> {
                    Thread.currentThread().setName("LLM-RAG2-Worker");
                    LOGGER.info("🧵 Started RAG2 worker thread");
                    RAG2.run(message, botSource, oldIntent, client);
                    LOGGER.info("✅ Finished RAG2 worker thread");
                });
            }

            case REQUEST_ACTION -> {
                BOT_TASK_POOL.submit(() -> {
                    Thread.currentThread().setName("LLM-Function-Caller-Worker");
                    LOGGER.info("🧵 Started FunctionCallerV2 worker thread");
                    new FunctionCallerV2(botSource, playerUUID);
                    FunctionCallerV2.run(message, client);
                    LOGGER.info("✅ Finished FunctionCallerV2 worker thread");
                });
            }

            default -> {
                LOGGER.warn("⚠️ Intent unclear, retrying with LLM classification...");
                ChatUtils.sendChatMessages(botSource, "🔍 Reanalyzing...");

                NLPProcessor.Intent retry = retryIntentLLM(message);

                LOGGER.info("📨 Retry intent: {}", retry);

                if (retry == NLPProcessor.Intent.GENERAL_CONVERSATION || retry == NLPProcessor.Intent.ASK_INFORMATION) {
                    BOT_TASK_POOL.submit(() -> {
                        Thread.currentThread().setName("LLM-RAG2-Retry-Worker");
                        LOGGER.info("🧵 Started RAG2 retry worker thread");
                        RAG2.run(message, botSource, retry, client);
                        LOGGER.info("✅ Finished RAG2 retry worker thread");
                    });
                } else if (retry == NLPProcessor.Intent.REQUEST_ACTION) {
                    BOT_TASK_POOL.submit(() -> {
                        Thread.currentThread().setName("LLM-Function-Caller-Retry-Worker");
                        LOGGER.info("🧵 Started FunctionCallerV2 retry worker thread");
                        new FunctionCallerV2(botSource, playerUUID);
                        FunctionCallerV2.run(message, client);
                        LOGGER.info("✅ Finished FunctionCallerV2 retry worker thread");
                    });
                } else {
                    throw new intentMisclassification("LLM failed to classify intent.");
                }
            }
        }
    }

    /**
     * Converte Intent antigo para novo NLPProcessorV2
     */
    private static NLPProcessorV2.Intent convertIntent(NLPProcessor.Intent oldIntent) {
        return switch (oldIntent) {
            case REQUEST_ACTION -> NLPProcessorV2.Intent.REQUEST_ACTION;
            case ASK_INFORMATION -> NLPProcessorV2.Intent.ASK_INFORMATION;
            case GENERAL_CONVERSATION -> NLPProcessorV2.Intent.GENERAL_CONVERSATION;
            default -> NLPProcessorV2.Intent.UNSPECIFIED;
        };
    }

    /**
     * Converte Intent novo para antigo (para compatibilidade com RAG2)
     */
    private static NLPProcessor.Intent convertIntentToOld(NLPProcessorV2.Intent newIntent) {
        return switch (newIntent) {
            case REQUEST_ACTION -> NLPProcessor.Intent.REQUEST_ACTION;
            case ASK_INFORMATION -> NLPProcessor.Intent.ASK_INFORMATION;
            case GENERAL_CONVERSATION -> NLPProcessor.Intent.GENERAL_CONVERSATION;
            case COMPLEX_ACTION -> NLPProcessor.Intent.REQUEST_ACTION;
            default -> NLPProcessor.Intent.UNSPECIFIED;
        };
    }

    private static NLPProcessor.Intent retryIntentLLM(String message) {
        // Usar LLM diretamente para classificação
        return NLPProcessor.getIntentionFromLLM(message);
    }
}
