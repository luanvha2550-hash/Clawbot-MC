package net.luanvha2550_hash.ChatUtils;

import net.luanvha2550_hash.FilingSystem.EmbeddingClientFactory;
import net.luanvha2550_hash.ServiceLLMClients.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NLP Processor v2 - Sistema de processamento de linguagem natural.
 *
 * Usa embeddings via API (Gemini, OpenAI, Ollama, etc.) para classificar intents.
 * Substitui o sistema DJL local por chamadas de API configuráveis.
 *
 * Características:
 * - Embedding via API (configurável pelo usuário)
 * - Classificação de intents por similaridade de cosseno
 * - Suporte nativo a português brasileiro
 * - Cache de embeddings para performance
 */
public class NLPProcessorV2 {

    private static final Logger LOGGER = LoggerFactory.getLogger("NLPProcessorV2");

    // Dimensão padrão para embeddings (será atualizada pelo provider real)
    private static final int DEFAULT_EMBEDDING_DIMENSION = 768;

    // Thresholds de confiança
    private static final double HIGH_CONFIDENCE = 0.75;
    private static final double MEDIUM_CONFIDENCE = 0.60;
    private static final double LOW_CONFIDENCE = 0.40;

    // Cliente de embedding (via API)
    private static EmbeddingClient embeddingClient;
    private static boolean initialized = false;
    private static int embeddingDimension = DEFAULT_EMBEDDING_DIMENSION;

    // Cache de embeddings para intents conhecidas
    private static final Map<Intent, List<float[]>> intentEmbeddings = new HashMap<>();

    // Exemplos de treinamento para cada intent (em português)
    private static final Map<Intent, List<String>> trainingExamples = new HashMap<>();

    // Cache de intents por mensagem hash (TTL: 10 minutos)
    private static final Map<String, CachedIntent> intentCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutos

    // Cache de embeddings por mensagem (evita recomputar embedding da mesma mensagem)
    private static final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    static {
        initializeTrainingExamples();
    }

    public enum Intent {
        REQUEST_ACTION,      // Ações simples: "mine", "venha cá", "pegue ferro"
        ASK_INFORMATION,    // Perguntas: "onde está?", "que horas são?"
        GENERAL_CONVERSATION, // Conversa: "oi", "bom dia", "legal"
        COMPLEX_ACTION,     // Ações complexas: "construa uma casa", "faça uma farm"
        UNSPECIFIED         // Mensagem ambígua ou não reconhecida
    }

    /**
     * Inicializa os exemplos de treinamento em português brasileiro.
     */
    private static void initializeTrainingExamples() {
        // REQUEST_ACTION - Comandos diretos
        trainingExamples.put(Intent.REQUEST_ACTION, Arrays.asList(
            "mine", "minerar", "minera", "cave", "cavar", "cava",
            "venha cá", "vem aqui", "vem", "segue-me", "me segue",
            "pegue", "pega", "colete", "coleta", "busque", "busca",
            "ataque", "ataca", "mata", "mate", "elimine", "elimina",
            "construa", "constrói", "faça", "faz", "crafte", "crafta",
            "plante", "planta", "colha", "colhe", "quebre", "quebra"
        ));

        // ASK_INFORMATION - Perguntas
        trainingExamples.put(Intent.ASK_INFORMATION, Arrays.asList(
            "onde está", "onde fica", "onde tem", "onde",
            "que horas são", "que hora é", "que dia é",
            "como faz", "como faço", "como", "qual é",
            "quanto tem", "quantos", "quem é", "o que é",
            "por que", "porquê", "qual", "quando"
        ));

        // GENERAL_CONVERSATION - Conversas casuais
        trainingExamples.put(Intent.GENERAL_CONVERSATION, Arrays.asList(
            "oi", "olá", "eae", "fala", "salve",
            "bom dia", "boa tarde", "boa noite",
            "tudo bem", "tudo bom", "como vai", "beleza",
            "obrigado", "valeu", "vlw", "thanks",
            "legal", "massa", "daora", "show", "top",
            "haha", "kkk", "rsrs", "lol"
        ));

        // COMPLEX_ACTION - Ações que precisam de planejamento
        trainingExamples.put(Intent.COMPLEX_ACTION, Arrays.asList(
            "construa uma casa", "faça uma casa", "monte uma base",
            "crie uma farm", "faça uma farm de", "automatize",
            "organize o inventário", "ordene os itens",
            "prepare uma poção", "faça poções",
            "enchant", "encante itens", "encantar",
            "explore a dungeon", "explore a caverna",
            "encontre diamantes", "procure por",
            "domestique", "crie animais"
        ));
    }

    /**
     * Inicializa o sistema de embeddings usando o provedor configurado.
     * Deve ser chamado uma vez durante o startup.
     */
    public static void initialize() {
        try {
            LOGGER.info("Inicializando NLPProcessorV2...");

            // Mostrar qual provider está configurado
            String provider = net.luanvha2550_hash.FilingSystem.ManualConfig.getActiveProvider();
            LOGGER.info("   Provider configurado: {}", provider);

            // Obter cliente de embedding configurado
            embeddingClient = EmbeddingClientFactory.createClient();

            if (embeddingClient == null) {
                LOGGER.error("❌ Falha ao criar cliente de embedding!");
                LOGGER.error("   Verifique se o provider '{}' está configurado corretamente", provider);
                LOGGER.error("   Para Gemini: configure 'geminiKey' no arquivo settings.json5");
                LOGGER.error("   Para Ollama: verifique se Ollama está rodando em localhost:11434");
                initialized = false;
                return;
            }

            LOGGER.info("   Cliente criado: {} - {}", embeddingClient.getProvider(), embeddingClient.getEmbeddingModel());

            // Verificar se o cliente está acessível
            if (!embeddingClient.isReachable()) {
                LOGGER.warn("⚠ Cliente de embedding não está acessível: {}", embeddingClient.getProvider());
                if (provider.equals("gemini") || provider.equals("google")) {
                    LOGGER.warn("   Verifique se a API key do Gemini está correta");
                } else if (provider.equals("ollama")) {
                    LOGGER.warn("   Verifique se o Ollama está rodando: ollama serve");
                }
                LOGGER.warn("   NLPProcessorV2 funcionará em modo degradado (fallback de palavras-chave)");
                initialized = false;
                return;
            }

            // Obter dimensão do embedding
            embeddingDimension = embeddingClient.getEmbeddingDimension();

            LOGGER.info("✅ Cliente de embedding configurado: {} ({})",
                embeddingClient.getProvider(), embeddingClient.getEmbeddingModel());

            // Pré-computar embeddings dos exemplos de treinamento
            precomputeIntentEmbeddings();

            LOGGER.info("✅ NLPProcessorV2 inicializado com sucesso!");
            LOGGER.info("   Modelo: {} ({} dimensões)", embeddingClient.getEmbeddingModel(), embeddingDimension);
            LOGGER.info("   Intents treinadas: {}", trainingExamples.size());
            initialized = true;

        } catch (Exception e) {
            LOGGER.error("❌ Falha ao inicializar NLPProcessorV2: {}", e.getMessage());
            LOGGER.error("   Verifique se a API key está configurada corretamente");
            LOGGER.error("   O sistema funcionará sem classificação de intents");
            initialized = false;
        }
    }

    /**
     * Verifica se o NLPProcessorV2 está inicializado.
     * @return true se inicializado com sucesso
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Pré-computa os embeddings para todos os exemplos de treinamento.
     */
    private static void precomputeIntentEmbeddings() {
        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<Intent, List<String>> entry : trainingExamples.entrySet()) {
            List<float[]> embeddings = new ArrayList<>();
            for (String example : entry.getValue()) {
                try {
                    float[] embedding = getEmbedding(example);
                    if (embedding != null && embedding.length > 0) {
                        embeddings.add(embedding);
                        successCount++;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Falha ao gerar embedding para '{}': {}", example, e.getMessage());
                    failCount++;
                }
            }
            if (!embeddings.isEmpty()) {
                intentEmbeddings.put(entry.getKey(), embeddings);
            }
        }

        LOGGER.info("✅ Embeddings pré-computados: {} sucesso, {} falhas", successCount, failCount);

        if (failCount > successCount) {
            LOGGER.error("❌ Muitas falhas ao gerar embeddings - verifique a conexão com a API");
        }
    }

    /**
     * Obtém embedding do cache ou computa via API.
     */
    private static float[] getEmbedding(String text) throws Exception {
        if (embeddingClient == null) {
            throw new IllegalStateException("Embedding client não inicializado");
        }

        String key = text.toLowerCase().trim();

        // Verificar cache primeiro
        float[] cached = embeddingCache.get(key);
        if (cached != null) {
            return cached;
        }

        // Gerar embedding via API
        List<Double> embeddingList = embeddingClient.generateEmbedding(text);

        // Converter para float[]
        float[] embedding = new float[embeddingList.size()];
        for (int i = 0; i < embeddingList.size(); i++) {
            embedding[i] = embeddingList.get(i).floatValue();
        }

        // Armazenar no cache
        embeddingCache.put(key, embedding);

        return embedding;
    }

    /**
     * Classifica a intent de uma mensagem do jogador.
     *
     * @param message Mensagem do jogador
     * @return Resultado da classificação com intent e confiança
     */
    public static ClassificationResult classifyIntent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new ClassificationResult(Intent.UNSPECIFIED, 0.0);
        }

        // Se não inicializado, usar fallback simples
        if (!initialized || embeddingClient == null) {
            return classifyIntentFallback(message);
        }

        // Verificar cache de intents primeiro
        String cacheKey = normalizeCacheKey(message);
        CachedIntent cached = intentCache.get(cacheKey);
        if (cached != null && !isExpired(cached)) {
            LOGGER.debug("Cache hit de intent para: {}", message);
            return cached.result;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Gerar embedding da mensagem
            float[] messageEmbedding = getEmbedding(message);

            // Calcular similaridade com cada intent
            Map<Intent, Double> similarities = new HashMap<>();
            for (Intent intent : intentEmbeddings.keySet()) {
                double maxSimilarity = 0.0;
                for (float[] intentEmbedding : intentEmbeddings.get(intent)) {
                    double similarity = cosineSimilarity(messageEmbedding, intentEmbedding);
                    maxSimilarity = Math.max(maxSimilarity, similarity);
                }
                similarities.put(intent, maxSimilarity);
            }

            // Encontrar intent com maior similaridade
            Intent bestIntent = Intent.UNSPECIFIED;
            double bestSimilarity = 0.0;
            for (Map.Entry<Intent, Double> entry : similarities.entrySet()) {
                if (entry.getValue() > bestSimilarity) {
                    bestSimilarity = entry.getValue();
                    bestIntent = entry.getKey();
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            ClassificationResult result = new ClassificationResult(bestIntent, bestSimilarity);

            // Armazenar no cache
            intentCache.put(cacheKey, new CachedIntent(result, System.currentTimeMillis()));

            // Limpar cache expirado periodicamente
            if (intentCache.size() > 500) {
                cleanupExpiredCache();
            }

            LOGGER.debug("Intent classificada: {} (confiança: {:.2f}, tempo: {}ms)",
                bestIntent, bestSimilarity, duration);

            return result;

        } catch (Exception e) {
            LOGGER.error("Erro ao classificar intent: {}", e.getMessage());
            return classifyIntentFallback(message);
        }
    }

    /**
     * Fallback para classificação de intent quando embedding não está disponível.
     * Usa correspondência de palavras-chave simples.
     */
    private static ClassificationResult classifyIntentFallback(String message) {
        String lower = message.toLowerCase().trim();

        // REQUEST_ACTION - Comandos diretos
        if (containsAny(lower, "mine", "minerar", "minera", "cave", "cavar", "cava",
            "venha", "vem", "segue", "pegue", "pega", "colete", "coleta", "busque", "busca",
            "ataque", "ataca", "mata", "mate", "construa", "constrói", "faça", "faz",
            "crafte", "crafta", "plante", "planta", "colha", "colhe", "quebre", "quebra")) {
            return new ClassificationResult(Intent.REQUEST_ACTION, 0.6);
        }

        // ASK_INFORMATION - Perguntas
        if (containsAny(lower, "onde", "que horas", "que hora", "que dia", "como faz",
            "como faço", "qual é", "quanto", "quantos", "quem é", "o que é", "por que")) {
            return new ClassificationResult(Intent.ASK_INFORMATION, 0.6);
        }

        // GENERAL_CONVERSATION - Conversas casuais
        if (containsAny(lower, "oi", "olá", "eae", "fala", "salve", "bom dia",
            "boa tarde", "boa noite", "tudo bem", "tudo bom", "como vai", "beleza",
            "obrigado", "valeu", "vlw", "legal", "massa", "daora", "show", "top")) {
            return new ClassificationResult(Intent.GENERAL_CONVERSATION, 0.6);
        }

        // COMPLEX_ACTION - Ações complexas
        if (containsAny(lower, "construa uma", "faça uma", "monte uma", "crie uma",
            "farm", "automatize", "organize", "poção", "encant", "explore", "encontre",
            "domestique", "crie animais")) {
            return new ClassificationResult(Intent.COMPLEX_ACTION, 0.6);
        }

        return new ClassificationResult(Intent.UNSPECIFIED, 0.0);
    }

    /**
     * Verifica se a string contém qualquer uma das palavras-chave.
     */
    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normaliza mensagem para chave de cache.
     */
    private static String normalizeCacheKey(String message) {
        return message.toLowerCase().trim();
    }

    /**
     * Verifica se cache entry está expirado.
     */
    private static boolean isExpired(CachedIntent entry) {
        return System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS;
    }

    /**
     * Limpa entradas expiradas do cache.
     */
    private static void cleanupExpiredCache() {
        long now = System.currentTimeMillis();
        intentCache.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > CACHE_TTL_MS);
        embeddingCache.entrySet().removeIf(entry -> {
            // Embeddings não expiram, mas removemos se intent correspondente expirou
            return !intentCache.containsKey(entry.getKey());
        });
    }

    /**
     * Libera recursos ao desligar.
     */
    public static void shutdown() {
        intentCache.clear();
        embeddingCache.clear();
        intentEmbeddings.clear();
        embeddingClient = null;
        initialized = false;
        LOGGER.info("NLPProcessorV2 desligado com sucesso");
    }

    /**
     * Calcula a similaridade de cosseno entre dois vetores.
     */
    private static double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Verifica se deve usar LLM baseado na intent e confiança.
     *
     * @param result Resultado da classificação
     * @return true se deve chamar LLM, false caso contrário
     */
    public static boolean shouldUseLLM(ClassificationResult result) {
        // Sempre usar LLM para ações complexas com confiança média
        if (result.intent == Intent.COMPLEX_ACTION && result.confidence < HIGH_CONFIDENCE) {
            return true;
        }

        // Usar LLM para perguntas com confiança baixa
        if (result.intent == Intent.ASK_INFORMATION && result.confidence < MEDIUM_CONFIDENCE) {
            return true;
        }

        // Usar LLM se confiança muito baixa (mensagem ambígua)
        if (result.confidence < LOW_CONFIDENCE) {
            return true;
        }

        return false;
    }

    /**
     * Obtém informações sobre o estado do NLPProcessorV2.
     */
    public static String getStatusInfo() {
        String provider = net.luanvha2550_hash.FilingSystem.ManualConfig.getActiveProvider();

        if (!initialized) {
            StringBuilder sb = new StringBuilder();
            sb.append("§c=== NLP Status ===\n");
            sb.append("§cInitialized: false\n");
            sb.append("§7Provider config: ").append(provider).append("\n");

            // Verificar razão da falha
            if (embeddingClient == null) {
                sb.append("§7Reason: Cliente de embedding não criado\n");
                if (provider.equals("gemini") || provider.equals("google")) {
                    sb.append("§7Action: Configure 'geminiKey' em settings.json5\n");
                } else if (provider.equals("ollama")) {
                    sb.append("§7Action: Execute 'ollama serve' e 'ollama pull nomic-embed-text'\n");
                }
            } else {
                sb.append("§7Reason: ").append(embeddingClient.getProvider()).append(" não acessível\n");
                sb.append("§7Model: ").append(embeddingClient.getEmbeddingModel()).append("\n");
                if (provider.equals("ollama")) {
                    sb.append("§7Action: Verifique se Ollama está rodando\n");
                }
            }
            sb.append("§eFallback: Classificação por palavras-chave ativa");
            return sb.toString();
        }

        return String.format("§a=== NLP Status ===\n§7Initialized: true\n§7Provider: %s\n§7Model: %s\n§7Dimensions: %d\n§7Intents: %d\n§7Cache size: %d",
            embeddingClient.getProvider(),
            embeddingClient.getEmbeddingModel(),
            embeddingDimension,
            intentEmbeddings.size(),
            intentCache.size());
    }

    /**
     * Resultado da classificação de intent.
     */
    public static class ClassificationResult {
        public final Intent intent;
        public final double confidence;

        public ClassificationResult(Intent intent, double confidence) {
            this.intent = intent;
            this.confidence = confidence;
        }

        public boolean isHighConfidence() {
            return confidence >= HIGH_CONFIDENCE;
        }

        public boolean isMediumConfidence() {
            return confidence >= MEDIUM_CONFIDENCE;
        }
    }

    /**
     * Cache entry para intents com timestamp.
     */
    private static class CachedIntent {
        final ClassificationResult result;
        final long timestamp;

        CachedIntent(ClassificationResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
    }
}