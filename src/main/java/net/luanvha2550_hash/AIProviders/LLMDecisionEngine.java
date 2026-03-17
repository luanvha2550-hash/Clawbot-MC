package net.luanvha2550_hash.AIProviders;

import net.luanvha2550_hash.ChatUtils.NLPProcessorV2;
import net.luanvha2550_hash.ChatUtils.NLPProcessorV2.ClassificationResult;
import net.luanvha2550_hash.ChatUtils.NLPProcessorV2.Intent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * LLM Decision Engine - Sistema inteligente de decisão para uso do LLM.
 *
 * Reduz chamadas ao LLM de ~100% para ~30% através de:
 * 1. Classificação de intent via NLP local
 * 2. Cache de respostas comuns
 * 3. Lógica de decisão baseada em confiança
 *
 * Estratégia:
 * - Ações simples (REQUEST_ACTION): Executa direto
 * - Ações complexas (COMPLEX_ACTION): Chama LLM se confiança < 60%
 * - Perguntas (ASK_INFORMATION): Chama LLM se confiança < 50%
 * - Conversas: Usa LLM para manter contexto
 */
public class LLMDecisionEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("LLMDecisionEngine");

    // Thresholds de confiança para decisão
    private static final double COMPLEX_ACTION_THRESHOLD = 0.60;
    private static final double INFORMATION_THRESHOLD = 0.50;
    private static final double CONVERSATION_THRESHOLD = 0.40;

    // Cache de respostas LLM (TTL: 10 minutos)
    private static final Map<String, CachedResponse> responseCache = new HashMap<>();
    private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutos

    // Histórico de conversa (últimas 5 mensagens)
    private static final List<ConversationTurn> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 5;

    // Contadores para métricas
    private static int totalRequests = 0;
    private static int llmCalls = 0;
    private static int cacheHits = 0;

    /**
     * Decide se deve usar LLM baseado na mensagem e contexto.
     *
     * @param message Mensagem do jogador
     * @param classification Resultado da classificação NLP
     * @return true se deve chamar LLM, false para ação direta
     */
    public static boolean shouldUseLLM(String message, ClassificationResult classification) {
        totalRequests++;

        // Verificar cache primeiro
        String cacheKey = generateCacheKey(message);
        if (isInCache(cacheKey)) {
            cacheHits++;
            LOGGER.debug("Cache hit para mensagem: {}", message);
            return true; // Retorna true mas usa cache
        }

        // Se é conversa em andamento, usar LLM
        if (isOngoingConversation()) {
            LOGGER.debug("Conversa em andamento, usando LLM");
            llmCalls++;
            return true;
        }

        Intent intent = classification.intent;
        double confidence = classification.confidence;

        // Decisão baseada em intent e confiança
        boolean useLLM = switch (intent) {
            case COMPLEX_ACTION -> {
                // Ações complexas precisam de LLM se confiança baixa
                llmCalls++;
                yield confidence < COMPLEX_ACTION_THRESHOLD;
            }
            case ASK_INFORMATION -> {
                // Perguntas usam LLM se confiança baixa
                llmCalls++;
                yield confidence < INFORMATION_THRESHOLD;
            }
            case GENERAL_CONVERSATION -> {
                // Conversas sempre usam LLM para naturalidade
                llmCalls++;
                yield true;
            }
            case REQUEST_ACTION -> {
                // Ações simples executam direto
                yield false;
            }
            case UNSPECIFIED -> {
                // Mensagem ambígua, usar LLM
                llmCalls++;
                yield true;
            }
        };

        LOGGER.debug("Decisão LLM: intent={}, confiança={:.2f}, usarLLM={}",
            intent, confidence, useLLM);

        return useLLM;
    }

    /**
     * Constrói prompt otimizado em português brasileiro.
     *
     * @param message Mensagem do jogador
     * @param intent Intent detectada
     * @param context Contexto do jogo (opcional)
     * @return Prompt formatado para o LLM
     */
    public static String buildPrompt(String message, Intent intent, GameContext context) {
        StringBuilder prompt = new StringBuilder();

        // System prompt base
        prompt.append("Você é o Clawbot-MC, um agente de IA amigável e prestativo em Minecraft.\n");
        prompt.append("Responda de forma natural em português brasileiro.\n\n");

        // Adicionar histórico de conversa se relevante
        if (!conversationHistory.isEmpty() && intent == Intent.GENERAL_CONVERSATION) {
            prompt.append("Contexto da conversa:\n");
            for (ConversationTurn turn : getRecentHistory(3)) {
                prompt.append("- ").append(turn.role).append(": ")
                      .append(turn.message).append("\n");
            }
            prompt.append("\n");
        }

        // Prompt específico por intent
        switch (intent) {
            case COMPLEX_ACTION -> {
                prompt.append("O jogador pediu uma ação complexa: \"").append(message).append("\"\n\n");
                prompt.append("Analise e responda em JSON:\n");
                prompt.append("{\n");
                prompt.append("  \"ação\": \"nome_da_ação\",\n");
                prompt.append("  \"parâmetros\": {\n");
                prompt.append("    \"alvo\": \"objeto_alvo\",\n");
                prompt.append("    \"quantidade\": número,\n");
                prompt.append("    \"local\": \"localização\"\n");
                prompt.append("  },\n");
                prompt.append("  \"passos\": [\"passo1\", \"passo2\", ...],\n");
                prompt.append("  \"recursos_necessários\": [\"item1\", \"item2\", ...]\n");
                prompt.append("}\n\n");
                prompt.append("Se não entender, responda: {\"erro\": \"não entendi o comando\"}");
            }
            case ASK_INFORMATION -> {
                prompt.append("O jogador perguntou: \"").append(message).append("\"\n\n");
                prompt.append("Responda de forma clara e concisa.\n");
                if (context != null) {
                    prompt.append("Contexto atual: ").append(context.toString()).append("\n");
                }
            }
            case GENERAL_CONVERSATION -> {
                prompt.append("Responda à mensagem do jogador: \"").append(message).append("\"\n");
                prompt.append("Mantenha um tom amigável e natural.");
            }
            default -> {
                prompt.append("Mensagem do jogador: \"").append(message).append("\"\n");
                prompt.append("Responda apropriadamente.");
            }
        }

        return prompt.toString();
    }

    /**
     * Registra uma mensagem no histórico de conversa.
     */
    public static void recordMessage(String role, String message) {
        conversationHistory.add(new ConversationTurn(role, message, System.currentTimeMillis()));

        // Manter apenas últimas mensagens
        while (conversationHistory.size() > MAX_HISTORY_SIZE) {
            conversationHistory.remove(0);
        }
    }

    /**
     * Limpa o histórico de conversa.
     */
    public static void clearConversation() {
        conversationHistory.clear();
    }

    /**
     * Verifica se há uma conversa em andamento.
     */
    private static boolean isOngoingConversation() {
        if (conversationHistory.isEmpty()) {
            return false;
        }

        // Verificar se última mensagem foi há menos de 2 minutos
        ConversationTurn lastTurn = conversationHistory.get(conversationHistory.size() - 1);
        long timeSinceLastMessage = System.currentTimeMillis() - lastTurn.timestamp;
        return timeSinceLastMessage < 2 * 60 * 1000; // 2 minutos
    }

    /**
     * Retorna as últimas N mensagens do histórico.
     */
    private static List<ConversationTurn> getRecentHistory(int n) {
        if (conversationHistory.isEmpty()) {
            return new ArrayList<>();
        }
        int start = Math.max(0, conversationHistory.size() - n);
        return new ArrayList<>(conversationHistory.subList(start, conversationHistory.size()));
    }

    /**
     * Gera chave de cache para uma mensagem.
     */
    private static String generateCacheKey(String message) {
        // Normalizar mensagem para cache
        String normalized = message.toLowerCase().trim();
        // Usar primeiros 50 caracteres como chave
        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    /**
     * Verifica se resposta está em cache e válida.
     */
    private static boolean isInCache(String key) {
        CachedResponse cached = responseCache.get(key);
        if (cached == null) {
            return false;
        }

        // Verificar TTL
        if (System.currentTimeMillis() - cached.timestamp > CACHE_TTL_MS) {
            responseCache.remove(key);
            return false;
        }

        return true;
    }

    /**
     * Obtém resposta do cache.
     */
    public static String getFromCache(String key) {
        CachedResponse cached = responseCache.get(key);
        return cached != null ? cached.response : null;
    }

    /**
     * Adiciona resposta ao cache.
     */
    public static void cacheResponse(String key, String response) {
        responseCache.put(key, new CachedResponse(response, System.currentTimeMillis()));

        // Limpar entradas antigas se cache muito grande
        if (responseCache.size() > 1000) {
            cleanupCache();
        }
    }

    /**
     * Limpa entradas expiradas do cache.
     */
    private static void cleanupCache() {
        long now = System.currentTimeMillis();
        responseCache.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > CACHE_TTL_MS);
    }

    /**
     * Retorna métricas de uso.
     */
    public static Metrics getMetrics() {
        double llmPercentage = totalRequests > 0 ? (llmCalls * 100.0 / totalRequests) : 0;
        double cacheHitRate = totalRequests > 0 ? (cacheHits * 100.0 / totalRequests) : 0;
        return new Metrics(totalRequests, llmCalls, cacheHits, llmPercentage, cacheHitRate);
    }

    /**
     * Reseta métricas.
     */
    public static void resetMetrics() {
        totalRequests = 0;
        llmCalls = 0;
        cacheHits = 0;
    }

    // Classes auxiliares

    private static class ConversationTurn {
        final String role;
        final String message;
        final long timestamp;

        ConversationTurn(String role, String message, long timestamp) {
            this.role = role;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    private static class CachedResponse {
        final String response;
        final long timestamp;

        CachedResponse(String response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }
    }

    public static class GameContext {
        public final String botHealth;
        public final String botLocation;
        public final String nearbyEntities;
        public final String timeOfDay;

        public GameContext(String health, String location, String entities, String time) {
            this.botHealth = health;
            this.botLocation = location;
            this.nearbyEntities = entities;
            this.timeOfDay = time;
        }

        @Override
        public String toString() {
            return String.format("Saúde: %s, Local: %s, Entidades: %s, Hora: %s",
                botHealth, botLocation, nearbyEntities, timeOfDay);
        }
    }

    public static class Metrics {
        public final int totalRequests;
        public final int llmCalls;
        public final int cacheHits;
        public final double llmPercentage;
        public final double cacheHitRate;

        public Metrics(int total, int llm, int cache, double llmPct, double cachePct) {
            this.totalRequests = total;
            this.llmCalls = llm;
            this.cacheHits = cache;
            this.llmPercentage = llmPct;
            this.cacheHitRate = cachePct;
        }

        @Override
        public String toString() {
            return String.format("Total: %d, LLM: %d (%.1f%%), Cache: %d (%.1f%%)",
                totalRequests, llmCalls, llmPercentage, cacheHits, cacheHitRate);
        }
    }
}
