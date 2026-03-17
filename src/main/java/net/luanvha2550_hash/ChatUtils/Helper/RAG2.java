package net.luanvha2550_hash.ChatUtils.Helper;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole;
import net.minecraft.server.command.ServerCommandSource;
import net.luanvha2550_hash.AIProviders.EmbeddingProvider;
import net.luanvha2550_hash.AIProviders.EmbeddingProviderFactory;
import net.luanvha2550_hash.ChatUtils.ChatUtils;
import net.luanvha2550_hash.ChatUtils.NLPProcessor;
import net.luanvha2550_hash.Database.SQLiteDB;
import net.luanvha2550_hash.OllamaClient.ollamaClient;
import net.luanvha2550_hash.Overlay.ThinkingStateManager;
import net.luanvha2550_hash.ServiceLLMClients.LLMClient;
import net.luanvha2550_hash.WebSearch.WebSearchTool;
import net.luanvha2550_hash.Commands.modCommandRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RAG2 {

    private static final Logger logger = LoggerFactory.getLogger("ai-player");
    private static final OllamaAPI ollamaAPI = new OllamaAPI("http://localhost:11434");
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>([\\s\\S]*?)</think>");
    private static final int TOP_K = 5;
    private static EmbeddingProvider embeddingProvider;

    /**
     * Initialize embedding provider if not already initialized
     */
    private static void ensureEmbeddingProvider() {
        if (embeddingProvider == null) {
            try {
                embeddingProvider = EmbeddingProviderFactory.createEmbeddingProvider(ollamaAPI);
                logger.info("✅ Embedding provider initialized successfully");
            } catch (Exception e) {
                logger.error("❌ Failed to initialize embedding provider: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to initialize embedding provider", e);
            }
        }
    }

    private static String buildPrompt() {
        return "Você é um jogador de Minecraft com contexto chamado " + modCommandRegistry.botName + """
            Você pode acessar conversas passadas e eventos do jogo para ajudar a responder a pergunta atual do jogador.

            Use as memórias de contexto fornecidas APENAS se forem relevantes e úteis.
            Se forem irrelevantes ou estiverem faltando, ignore-as e responda normalmente — NÃO mencione que o contexto estava faltando.

            Ao usar contexto, você deve descrevê-lo como eventos passados no PASSADO.

            📚 REGRAS DE MEMÓRIA:
               - Você tem acesso a conversas passadas e eventos armazenados no seu banco de dados local.
               - Use-os APENAS se forem relevantes para a pergunta do jogador.
               - Trate-os como experiências passadas confiáveis dentro do Minecraft — sempre refira-se a eles no PASSADO.
               - Não mencione que usou "memórias" — apenas as incorpore naturalmente.

            🌐 REGRAS DE CONTEXTO WEB:
               - Às vezes você receberá informações recuperadas da wiki oficial do Minecraft ou fontes confiáveis como Reddit.
               - Trate isso como informações factuais novas quando fornecidas.
               - Se houver conflito entre seu próprio treinamento e o resultado web fornecido, confie no resultado web para detalhes factuais (ex: receitas de crafting, status de itens).
               - Nunca alucine novas informações não presentes no contexto ou seu treinamento.

            🧭 QUANDO O CONTEXTO ESTÁ FALTANDO OU CONFLITANTE:
               - Se você não tem contexto ou dados de pesquisa web ou se a pesquisa web falhar, use seu próprio conhecimento do Minecraft.
               - Se tiver contexto parcial, faça o melhor para responder com precisão.
               - Se o jogador pedir especificamente por mecânicas do Minecraft do mundo real ou atualizadas, prefira o resultado da pesquisa web se fornecido.

            Seja conciso, mantenha-se em personagem como um companheiro de Minecraft prestativo, e evite repetir o contexto verbalmente a menos que necessário.

            Importante:
            - Se o jogador pedir informações do jogo, use também seu conhecimento interno do Minecraft.
            - Se o contexto incluir uma pergunta similar ou evento relacionado, resuma-o naturalmente.
            - Se múltiplas memórias forem similares, una-as para responder claramente.
            - Nunca invente detalhes não presentes no contexto.

            Lembre-se:
            - O prompt do jogador e o contexto são sempre fornecidos separadamente.
            - Você deve analisar o prompt do jogador cuidadosamente.
            - Não quebre o personagem — você está dentro do mundo do Minecraft.
            """;
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

    private static String getBestContextAnswer(String userPrompt, List<Double> queryEmbedding) {
        String webAnswer = WebSearchTool.search(userPrompt).trim();
        logger.info("🌐 Web search result: {}", webAnswer);

        List<SQLiteDB.Memory> localMemories = SQLiteDB.findRelevantMemories(queryEmbedding, "conversation", 1);
        boolean hasLocal = !localMemories.isEmpty();
        String localAnswer = hasLocal ? localMemories.get(0).response() : "";
        double localSimilarity = hasLocal ? localMemories.get(0).similarity() : 0.0;

        logger.info("🔍 Local similarity: {}", localSimilarity);

        // Decide which to trust
        String bestAnswer;
        if (!webAnswer.isBlank()) {
            if (!webAnswer.equalsIgnoreCase(localAnswer)) {
                bestAnswer = webAnswer;
                logger.info("✅ Using web answer, overwriting local DB");
                SQLiteDB.storeMemory("conversation", userPrompt, bestAnswer, queryEmbedding);
            } else {
                bestAnswer = localAnswer;
                logger.info("✅ Local and web match, using local");
            }
        } else if (hasLocal && localSimilarity >= 0.8) {
            bestAnswer = localAnswer;
            logger.info("✅ Using local answer, web empty");
        } else {
            bestAnswer = "❌ No relevant info found.";
            logger.warn("⚠️ Both web and local empty or not confident");
        }

        return bestAnswer;
    }


    public static void run(String userPrompt, ServerCommandSource botSource, NLPProcessor.Intent intent, LLMClient client) {
        ollamaAPI.setRequestTimeoutSeconds(120);
        logger.info("⚡ RAG v2: Running with intent = {} and using provider: {}", intent, client);

        try {
            ensureEmbeddingProvider();

            List<Double> queryEmbedding = embeddingProvider.generateEmbeddings(userPrompt);

            StringBuilder contextBuilder = new StringBuilder();

            if (intent == NLPProcessor.Intent.ASK_INFORMATION) {
                ChatUtils.sendChatMessages(botSource, "Running web search....");
                String bestAnswer = getBestContextAnswer(userPrompt, queryEmbedding);

                if (bestAnswer.equalsIgnoreCase("❌ No relevant info found.")) {
                    ChatUtils.sendChatMessages(botSource, "No info found. Either there is no info on this topic or my web search tool is not working properly. Please report this to developer!");
                }
                else {
                    ChatUtils.sendChatMessages(botSource, "Web search complete.");
                }

                contextBuilder.append("Web/Local best answer:\n").append(bestAnswer).append("\n\n");

            } else {
                // 🤝 Just normal local vector recall
                List<SQLiteDB.Memory> localMemories = SQLiteDB.findRelevantMemories(queryEmbedding, "conversation", TOP_K);
                contextBuilder.append("Relevant conversations:\n");
                for (SQLiteDB.Memory m : localMemories) {
                    contextBuilder.append("- Prompt: ").append(m.prompt()).append("\n");
                    contextBuilder.append("  Response: ").append(m.response()).append("\n");
                    contextBuilder.append("  Similarity: ").append(m.similarity()).append("\n\n");
                }
            }

            // 🗃️ Add relevant events in all cases
            List<SQLiteDB.Memory> events = SQLiteDB.findRelevantMemories(queryEmbedding, "event", TOP_K);
            contextBuilder.append("Relevant events:\n");
            for (SQLiteDB.Memory m : events) {
                contextBuilder.append("- Prompt: ").append(m.prompt()).append("\n");
                contextBuilder.append("  Response: ").append(m.response()).append("\n");
                contextBuilder.append("  Similarity: ").append(m.similarity()).append("\n\n");
            }

            // ✨ Final LLM prompt
            String systemPrompt = buildPrompt();
            String finalUserPrompt = "Context:\n" + contextBuilder.toString().trim() + "\n\nUser prompt:\n" + userPrompt;

            String finalResponse = client.sendPrompt(systemPrompt, finalUserPrompt);

            processLLMOutput(finalResponse, botSource.getName(), botSource);

            // 🔒 Always store final response
            SQLiteDB.storeMemory("conversation", userPrompt, finalResponse, queryEmbedding);

            logger.info("✅ RAG v2 finished with intent-aware strategy.");

        } catch (Exception e) {
            logger.error("❌ RAG v2 failed: {}", e.getMessage(), e);
            ChatUtils.sendChatMessages(botSource, "Sorry, I couldn't find enough context. Please try again!");
        }
    }

    // overloaded method for the existing ollama client to work with.

    public static void run(String userPrompt, ServerCommandSource botSource, NLPProcessor.Intent intent) {

        ollamaAPI.setRequestTimeoutSeconds(120);

        logger.info("⚡ RAG v2: Running with intent = {}", intent);


        try {
            // Initialize embedding provider if not already done
            if (embeddingProvider == null) {
                embeddingProvider = EmbeddingProviderFactory.createEmbeddingProvider(ollamaAPI);
            }

            List<Double> queryEmbedding = embeddingProvider.generateEmbeddings(userPrompt);



            StringBuilder contextBuilder = new StringBuilder();



            if (intent == NLPProcessor.Intent.ASK_INFORMATION) {

                ChatUtils.sendChatMessages(botSource, "Running web search....");

                String bestAnswer = getBestContextAnswer(userPrompt, queryEmbedding);



                if (bestAnswer.equalsIgnoreCase("❌ No relevant info found.")) {

                    ChatUtils.sendChatMessages(botSource, "No info found. Either there is no info on this topic or my web search tool is not working properly. Please report this to developer!");

                }

                else {

                    ChatUtils.sendChatMessages(botSource, "Web search complete.");

                }



                contextBuilder.append("Web/Local best answer:\n").append(bestAnswer).append("\n\n");



            } else {

            // 🤝 Just normal local vector recall

                List<SQLiteDB.Memory> localMemories = SQLiteDB.findRelevantMemories(queryEmbedding, "conversation", TOP_K);

                contextBuilder.append("Relevant conversations:\n");

                for (SQLiteDB.Memory m : localMemories) {

                    contextBuilder.append("- Prompt: ").append(m.prompt()).append("\n");

                    contextBuilder.append(" Response: ").append(m.response()).append("\n");

                    contextBuilder.append(" Similarity: ").append(m.similarity()).append("\n\n");

                }

            }



             // 🗃️ Add relevant events in all cases

            List<SQLiteDB.Memory> events = SQLiteDB.findRelevantMemories(queryEmbedding, "event", TOP_K);

            contextBuilder.append("Relevant events:\n");

            for (SQLiteDB.Memory m : events) {

                contextBuilder.append("- Prompt: ").append(m.prompt()).append("\n");

                contextBuilder.append(" Response: ").append(m.response()).append("\n");

                contextBuilder.append(" Similarity: ").append(m.similarity()).append("\n\n");

            }



            // ✨ Final LLM prompt

            // Use new API helper for thinking mode support
            List<io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage> messages = new java.util.ArrayList<>();
            messages.add(new io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage(
                    OllamaChatMessageRole.SYSTEM, buildPrompt()));
            messages.add(new io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage(
                    OllamaChatMessageRole.USER, "Context:\n" + contextBuilder));
            messages.add(new io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage(
                    OllamaChatMessageRole.USER, "User prompt:\n" + userPrompt));

            net.luanvha2550_hash.OllamaClient.OllamaThinkingResponse response =
                    net.luanvha2550_hash.OllamaClient.OllamaAPIHelper.smartChat(
                            ollamaAPI,
                            "http://localhost:11434",
                            net.luanvha2550_hash.AIPlayer.CONFIG.getSelectedLanguageModel(),
                            messages
                    );

            String finalResponse = response.getFullResponse();

            ollamaClient.processLLMOutput(finalResponse, botSource.getName(), botSource);


            // 🔒 Always store final response

            SQLiteDB.storeMemory("conversation", userPrompt, finalResponse, queryEmbedding);



            logger.info("✅ RAG v2 finished with intent-aware strategy.");



        } catch (Exception e) {

            logger.error("❌ RAG v2 failed: {}", e.getMessage(), e);

            ChatUtils.sendChatMessages(botSource, "Sorry, I couldn't find enough context. Please try again!");

        }

    }
}
