package net.luanvha2550_hash.OllamaClient;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.models.chat.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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


import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ollamaClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final String host = "http://localhost:11434";
    public static String botName = "";
    public static boolean isInitialized = false;
    public static String initialResponse = "";
    public static final OllamaAPI ollamaAPI = new OllamaAPI(host);
    // Suporta tanto tags em português quanto em inglês para compatibilidade
    private static final Pattern THINK_BLOCK = Pattern.compile("(?:<think>|<pensamento>)([\\s\\S]*?)(?:</think>|</pensamento>)");
    private static final ExecutorService BOT_TASK_POOL = Executors.newCachedThreadPool();

    public static void runFromChat(String botName, String message, UUID playerUUID) {
        MinecraftServer server = AIPlayer.serverInstance;
        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        if (bot == null) {
            LOGGER.error("Bot {} não está online.", botName);
            return;
        }
        ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

        server.execute(() -> {
            try {
                routeIntent(message, botSource, playerUUID);
            } catch (Exception e) {
                LOGGER.error("Chat processing error: ", e);
                ChatUtils.sendChatMessages(botSource, "⚠️ Estou confuso! Por favor, reporte isso.");
            }
        });
    }

    public static void execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        botName = EntityArgumentType.getPlayer(context, "bot").getName().getLiteralString();
        String message = StringArgumentType.getString(context, "message");

        MinecraftServer server = context.getSource().getServer();
        ServerCommandSource playerSource = context.getSource();
        ServerCommandSource botSource = Objects.requireNonNull(server.getPlayerManager().getPlayer(botName))
                .getCommandSource().withSilent().withMaxLevel(4);

        String formatter = ChatUtils.getRandomColorCode();

        server.execute(() -> {
            server.getCommandManager().executeWithPrefix(playerSource, "/say " + formatter + message);
            server.getCommandManager().executeWithPrefix(botSource, "/say Processando sua mensagem, aguarde.");
        });

        server.execute(() -> {
            try {
                routeIntent(message, botSource, Objects.requireNonNull(playerSource.getPlayer()).getUuid());
            } catch (Exception e) {
                LOGGER.error("NLP error: ", e);
                ChatUtils.sendChatMessages(botSource, "⚠️ Problema no NLP. Reporte ao desenvolvedor.");
            }
        });
    }

    private static void routeIntent(String message, ServerCommandSource botSource, UUID playerUUID) throws Exception {
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

        switch (intent) {
            case GENERAL_CONVERSATION, ASK_INFORMATION -> {
                BOT_TASK_POOL.submit(() -> {
                    Thread.currentThread().setName("RAG2-Worker");
                    LOGGER.info("🧵 Thread worker RAG2 iniciada");
                    RAG2.run(message, botSource, oldIntent);
                    LOGGER.info("✅ Thread worker RAG2 finalizada");
                });
            }

            case REQUEST_ACTION -> {
                BOT_TASK_POOL.submit(() -> {
                    Thread.currentThread().setName("Function-Caller-Worker");
                    LOGGER.info("🧵 Thread worker FunctionCallerV2 iniciada");
                    new FunctionCallerV2(botSource, playerUUID);
                    FunctionCallerV2.run(message);
                    LOGGER.info("✅ Thread worker FunctionCallerV2 finalizada");
                });
            }

            default -> {
                LOGGER.warn("⚠️ Intento unclear, tentando novamente com classificação LLM...");
                ChatUtils.sendChatMessages(botSource, "🔍 Reanalisando...");

                NLPProcessor.Intent retry = retryIntentLLM(message);

                LOGGER.info("📨 Retry intent: {}", retry);

                if (retry == NLPProcessor.Intent.GENERAL_CONVERSATION || retry == NLPProcessor.Intent.ASK_INFORMATION) {
                    BOT_TASK_POOL.submit(() -> {
                        Thread.currentThread().setName("RAG2-Retry-Worker");
                        LOGGER.info("🧵 Thread worker retry RAG2 iniciada");
                        RAG2.run(message, botSource, retry);
                        LOGGER.info("✅ Thread worker retry RAG2 finalizada");
                    });
                } else if (retry == NLPProcessor.Intent.REQUEST_ACTION) {
                    BOT_TASK_POOL.submit(() -> {
                        Thread.currentThread().setName("Function-Caller-Retry-Worker");
                        LOGGER.info("🧵 Thread worker retry FunctionCallerV2 iniciada");
                        new FunctionCallerV2(botSource, playerUUID);
                        FunctionCallerV2.run(message);
                        LOGGER.info("✅ Thread worker retry FunctionCallerV2 finalizada");
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

    /**
     * Pings Ollama server to check if it's reachable.
     * Returns false instead of crashing if server is not available.
     *
     * @return true if server is reachable, false otherwise
     */
    public static boolean pingOllamaServer() {
        try {
            boolean reachable = ollamaAPI.ping();
            if (reachable) {
                LOGGER.info("✓ Servidor Ollama está ativo e respondendo");
            } else {
                LOGGER.warn("⚠ Ping do servidor Ollama retornou falso");
            }
            return reachable;
        } catch (Exception e) {
            LOGGER.warn("⚠ Servidor Ollama não está acessível: {}. Recursos de chat AI estarão indisponíveis.", e.getMessage());
            LOGGER.info("Verifique se o Ollama está instalado e rodando em localhost:11434");
            return false;
        }
    }

    public static void initializeOllamaClient() {
        if (isInitialized) return;

        MinecraftServer server = AIPlayer.serverInstance;
        if (server == null) {
            LOGGER.error("Server instance is null.");
            return;
        }

        ollamaAPI.setRequestTimeoutSeconds(90);
        String selectedLM = AIPlayer.CONFIG.getSelectedLanguageModel();
        LOGGER.info("Conectando ao Ollama usando modelo: {}", selectedLM);

        CompletableFuture.runAsync(() -> {
            int retries = 0;
            boolean success = false;

            while (!success && retries < 3) {
                try {
                    // Build messages for the new API format
                    List<OllamaChatMessage> messages = new ArrayList<>();
                    messages.add(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, generateSystemPrompt()));
                    messages.add(new OllamaChatMessage(OllamaChatMessageRole.USER, "Initializing chat."));

                    // Use smart chat that automatically detects reasoning models
                    OllamaThinkingResponse response = OllamaAPIHelper.smartChat(
                            ollamaAPI,
                            host,
                            selectedLM,
                            messages
                    );

                    initialResponse = response.getFullResponse();
                    LOGGER.info("Cliente Ollama inicializado. Resposta inicial: {}", response.getContent());

                    if (response.hasThinking()) {
                        LOGGER.info("💭 Modelo forneceu pensamento: {} chars", response.getThinking().length());
                    }

                    server.execute(() ->
                            server.sendMessage(Text.of("§9" + botName + " está pronto!"))
                    );

                    isInitialized = true;
                    success = true;

                } catch (HttpTimeoutException e) {
                    retries++;
                    LOGGER.error("Timeout ao inicializar Ollama (tentativa {}/3)", retries);
                } catch (Exception e) {
                    LOGGER.error("Falha ao inicializar Ollama: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }

            if (!success) {
                LOGGER.error("Falha ao inicializar Ollama após 3 tentativas.");
                server.sendMessage(Text.of("§c§lNão foi possível estabelecer conexão."));
            }
        });
    }

    private static String generateSystemPrompt() {

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

    public static void sendInitialResponse(ServerCommandSource botSource) {
        MinecraftServer server = botSource.getServer();

        // ✅ Schedule the WHOLE logic back to the main thread
        server.execute(() -> {
            processLLMOutput(initialResponse, botName, botSource);

            List<SQLiteDB.Memory> memories = SQLiteDB.fetchInitialResponse();
            if (memories.isEmpty()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        net.luanvha2550_hash.ServiceLLMClients.EmbeddingClient embeddingClient =
                                net.luanvha2550_hash.FilingSystem.EmbeddingClientFactory.createClient();
                        List<Double> embedding = embeddingClient.generateEmbedding(generateSystemPrompt());
                        SQLiteDB.storeMemory("conversation", generateSystemPrompt(), initialResponse, embedding);
                        LOGGER.info("✅ Resposta inicial salva usando {} embeddings.", embeddingClient.getProvider());
                    } catch (Exception e) {
                        LOGGER.error("❌ Falha ao salvar resposta inicial: {}", e.getMessage(), e);
                    }
                });
            } else {
                LOGGER.info("🗃️ Resposta inicial já está no DB.");
            }
        });
    }

    public static void processLLMOutput(String fullResponse, String botName, ServerCommandSource botSource) {
        Matcher matcher = THINK_BLOCK.matcher(fullResponse);

        if (matcher.find()) {
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
                ChatUtils.sendChatMessages(botSource, botName + ": " + remainder);
            }
        } else {
            ChatUtils.sendChatMessages(botSource, botName + ": " + fullResponse);
        }
    }

}
