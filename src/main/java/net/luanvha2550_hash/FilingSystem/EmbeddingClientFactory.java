package net.luanvha2550_hash.FilingSystem;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import net.luanvha2550_hash.AIPlayer;
import net.luanvha2550_hash.ServiceLLMClients.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating embedding clients based on configuration.
 * Supports independent embedding provider selection or uses LLM provider as fallback.
 *
 * Configuration:
 * - embeddingProvider: "same" (use LLM provider), "ollama", "gemini", "openai", etc.
 * - embeddingModel: Specific model name (optional, uses default if empty)
 * - ollamaEmbeddingModel: Ollama-specific model (default: nomic-embed-text)
 */
public class EmbeddingClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("embedding-client-factory");
    private static final String OLLAMA_HOST = "http://localhost:11434/";
    private static EmbeddingClient cachedClient = null;
    private static String cachedMode = null;
    private static String cachedModel = null;

    /**
     * Create an embedding client based on configuration.
     * Uses embeddingProvider setting, or falls back to LLM provider.
     * Uses a singleton pattern to reuse clients when configuration hasn't changed.
     *
     * @return An EmbeddingClient instance for the configured provider
     */
    public static EmbeddingClient createClient() {
        String mode = ManualConfig.getActiveEmbeddingProvider();
        String model = getEffectiveModel(mode);

        // Return cached client if mode and model haven't changed
        if (cachedClient != null && mode.equals(cachedMode) &&
            (model == null ? cachedModel == null : model.equals(cachedModel))) {
            return cachedClient;
        }

        LOGGER.info("Creating embedding client for provider: {} (model: {})", mode, model != null ? model : "default");

        EmbeddingClient client = createClientForProvider(mode, model);

        // Cache the client
        cachedClient = client;
        cachedMode = mode;
        cachedModel = model;

        return client;
    }

    /**
     * Get the effective model name for the given provider.
     */
    private static String getEffectiveModel(String provider) {
        String configuredModel = AIPlayer.CONFIG.getEmbeddingModel();
        if (configuredModel != null && !configuredModel.isEmpty()) {
            return configuredModel;
        }

        // Use provider-specific defaults
        if ("ollama".equals(provider)) {
            return AIPlayer.CONFIG.getOllamaEmbeddingModel();
        }

        // Return null to use default model for the provider
        return null;
    }

    /**
     * Create an embedding client for a specific provider.
     */
    private static EmbeddingClient createClientForProvider(String mode, String model) {
        return switch (mode) {
            case "openai", "gpt" -> {
                if (AIPlayer.CONFIG.getOpenAIKey().isEmpty()) {
                    LOGGER.warn("⚠ OpenAI API key not set - falling back to Ollama for embeddings");
                    yield createOllamaClient(model);
                }
                String useModel = model != null ? model : "text-embedding-3-small";
                LOGGER.info("Using OpenAI embeddings ({})", useModel);
                yield new OpenAIEmbeddingClient(AIPlayer.CONFIG.getOpenAIKey(), useModel);
            }
            case "anthropic", "claude" -> {
                if (AIPlayer.CONFIG.getClaudeKey().isEmpty()) {
                    LOGGER.warn("⚠ Claude API key not set - falling back to Ollama for embeddings");
                    yield createOllamaClient(model);
                }
                LOGGER.info("Using Anthropic embeddings (Voyage AI)");
                yield new AnthropicEmbeddingClient(AIPlayer.CONFIG.getClaudeKey());
            }
            case "google", "gemini" -> {
                if (AIPlayer.CONFIG.getGeminiKey().isEmpty()) {
                    LOGGER.warn("⚠ Gemini API key not set - falling back to Ollama for embeddings");
                    yield createOllamaClient(model);
                }
                // Use gemini-embedding-001 for Google AI Studio (text-embedding-004 is Vertex AI only)
                String useModel = model != null ? model : "gemini-embedding-001";
                LOGGER.info("Using Gemini embeddings ({})", useModel);
                yield new GeminiEmbeddingClient(AIPlayer.CONFIG.getGeminiKey(), useModel);
            }
            case "xAI", "xai", "grok" -> {
                if (AIPlayer.CONFIG.getGrokKey().isEmpty()) {
                    LOGGER.warn("⚠ Grok API key not set - falling back to Ollama for embeddings");
                    yield createOllamaClient(model);
                }
                LOGGER.info("Using xAI embeddings (embedding-large-1)");
                yield new GrokEmbeddingClient(AIPlayer.CONFIG.getGrokKey());
            }
            case "custom" -> {
                if (AIPlayer.CONFIG.getCustomApiUrl().isEmpty()) {
                    LOGGER.warn("⚠ Custom API URL not set - falling back to Ollama for embeddings");
                    yield createOllamaClient(model);
                }
                String baseUrl = AIPlayer.CONFIG.getCustomApiUrl();
                String embeddingUrl = deriveEmbeddingEndpoint(baseUrl);
                LOGGER.info("Using custom embeddings endpoint: {}", embeddingUrl);
                yield new GenericEmbeddingClient(
                        AIPlayer.CONFIG.getCustomApiKey(),
                        model != null ? model : "text-embedding-3-small",
                        embeddingUrl
                );
            }
            default -> {
                LOGGER.info("Using Ollama embeddings ({})", model != null ? model : "nomic-embed-text");
                yield createOllamaClient(model);
            }
        };
    }

    /**
     * Create an Ollama embedding client.
     *
     * @param model Model name, or null for default
     * @return An OllamaEmbeddingClient instance
     */
    private static OllamaEmbeddingClient createOllamaClient(String model) {
        String useModel = model != null ? model : AIPlayer.CONFIG.getOllamaEmbeddingModel();
        if (useModel == null || useModel.isEmpty()) {
            useModel = "nomic-embed-text";
        }
        return new OllamaEmbeddingClient(new OllamaAPI(OLLAMA_HOST), useModel);
    }

    /**
     * Create an embedding client for a specific provider, overriding the config.
     *
     * @param mode The provider mode (e.g., "openai", "anthropic", "gemini")
     * @return An EmbeddingClient instance for the specified provider
     */
    public static EmbeddingClient createClient(String mode) {
        return createClientForProvider(mode, null);
    }

    /**
     * Intelligently derive embedding endpoint from base API URL.
     * Handles common patterns like /v1/chat/completions, /chat/completions, etc.
     *
     * @param baseUrl The base API URL (e.g., https://api.provider.com/v1/chat/completions)
     * @return The embedding endpoint URL (e.g., https://api.provider.com/v1/embeddings)
     */
    private static String deriveEmbeddingEndpoint(String baseUrl) {
        // Normalize URL - remove trailing slashes
        String normalizedUrl = baseUrl.replaceAll("/+$", "");

        // Pattern 1: URL ends with /chat/completions -> replace with /embeddings
        if (normalizedUrl.matches(".*/(v1/)?chat/completions$")) {
            return normalizedUrl.replaceAll("/(chat/completions)$", "/embeddings");
        }

        // Pattern 2: URL ends with /completions -> replace with /embeddings
        if (normalizedUrl.matches(".*/(v1/)?completions$")) {
            return normalizedUrl.replaceAll("/completions$", "/embeddings");
        }

        // Pattern 3: URL ends with /chat -> replace with /embeddings
        if (normalizedUrl.matches(".*/(v1/)?chat$")) {
            return normalizedUrl.replaceAll("/chat$", "/embeddings");
        }

        // Pattern 4: URL ends with /v1 -> add /embeddings
        if (normalizedUrl.endsWith("/v1")) {
            return normalizedUrl + "/embeddings";
        }

        // Pattern 5: URL has /v1/ somewhere -> ensure /v1/embeddings
        if (normalizedUrl.contains("/v1/")) {
            // Extract base up to /v1/
            String base = normalizedUrl.substring(0, normalizedUrl.indexOf("/v1/") + 3);
            return base + "/embeddings";
        }

        // Default: append /v1/embeddings to base URL
        return normalizedUrl + "/v1/embeddings";
    }

    /**
     * Clear the cached client. Call this when provider configuration changes.
     */
    public static void clearCache() {
        cachedClient = null;
        cachedMode = null;
        cachedModel = null;
        LOGGER.info("Embedding client cache cleared");
    }

    /**
     * Test if the current embedding client is reachable.
     *
     * @return true if the embedding service is available, false otherwise
     */
    public static boolean testConnection() {
        try {
            EmbeddingClient client = createClient();
            if (client == null) {
                LOGGER.error("❌ Embedding client is null - configuration issue detected");
                return false;
            }

            boolean reachable = client.isReachable();
            if (reachable) {
                LOGGER.info("✅ Embedding service is reachable: {} ({})",
                    client.getProvider(), client.getEmbeddingModel());
            } else {
                LOGGER.warn("⚠ Embedding service is not reachable: {}", client.getProvider());
            }
            return reachable;
        } catch (Exception e) {
            LOGGER.error("❌ Error testing embedding connection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate and log embedding configuration on startup.
     * Provides detailed information about which embedding service will be used.
     */
    public static void validateConfiguration() {
        try {
            String mode = ManualConfig.getActiveEmbeddingProvider();
            String embeddingProviderSetting = AIPlayer.CONFIG.getEmbeddingProvider();

            LOGGER.info("═══════════════════════════════════════════════════════");
            LOGGER.info("🔧 Validating Embedding Configuration");
            LOGGER.info("═══════════════════════════════════════════════════════");
            LOGGER.info("Embedding Provider Setting: {}", embeddingProviderSetting.isEmpty() ? "(same as LLM)" : embeddingProviderSetting);
            LOGGER.info("LLM Provider: {}", ManualConfig.getActiveProvider());
            LOGGER.info("Effective Embedding Provider: {}", mode);

            // Check API keys
            if ((mode.equals("gemini") || mode.equals("google")) && AIPlayer.CONFIG.getGeminiKey().isEmpty()) {
                LOGGER.warn("⚠ Gemini API key not configured - will fall back to Ollama");
            }
            if (mode.equals("openai") && AIPlayer.CONFIG.getOpenAIKey().isEmpty()) {
                LOGGER.warn("⚠ OpenAI API key not configured - will fall back to Ollama");
            }

            EmbeddingClient client = createClient();
            if (client == null) {
                LOGGER.error("❌ Failed to create embedding client!");
                return;
            }

            LOGGER.info("📊 Embedding Provider: {}", client.getProvider());
            LOGGER.info("📊 Embedding Model: {}", client.getEmbeddingModel());
            LOGGER.info("📊 Embedding Dimension: {}", client.getEmbeddingDimension());

            // Test connection
            LOGGER.info("🔌 Testing embedding service connection...");
            boolean reachable = client.isReachable();
            if (reachable) {
                LOGGER.info("✅ Embedding service is operational");
            } else {
                LOGGER.warn("⚠ Embedding service is not reachable - embeddings may fail at runtime");
                if (mode.equals("ollama")) {
                    LOGGER.info("   To fix: Run 'ollama serve' and 'ollama pull nomic-embed-text'");
                } else if (mode.equals("gemini") || mode.equals("google")) {
                    LOGGER.info("   To fix: Verify your Gemini API key is correct");
                }
            }

            LOGGER.info("═══════════════════════════════════════════════════════");
        } catch (Exception e) {
            LOGGER.error("❌ Error during embedding configuration validation", e);
        }
    }

    /**
     * Get information about the current embedding client.
     *
     * @return A string describing the current embedding provider and model
     */
    public static String getEmbeddingInfo() {
        try {
            EmbeddingClient client = createClient();
            if (client == null) {
                return "No embedding client available";
            }
            return String.format("%s - %s (%d dimensions)",
                    client.getProvider(),
                    client.getEmbeddingModel(),
                    client.getEmbeddingDimension());
        } catch (Exception e) {
            return "Error retrieving embedding info: " + e.getMessage();
        }
    }
}