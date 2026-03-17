package net.luanvha2550_hash.ServiceLLMClients;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Ollama embedding client that wraps the ollama4j library.
 * Uses nomic-embed-text model by default for embeddings.
 */
public class OllamaEmbeddingClient implements EmbeddingClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("Ollama-Embedding-Client");
    private static final String DEFAULT_MODEL = "nomic-embed-text";
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private final OllamaAPI ollamaAPI;
    private final String modelName;

    public OllamaEmbeddingClient(OllamaAPI ollamaAPI) {
        this(ollamaAPI, DEFAULT_MODEL);
    }

    public OllamaEmbeddingClient(OllamaAPI ollamaAPI, String modelName) {
        this.ollamaAPI = ollamaAPI;
        this.modelName = modelName;

        // Ensure proper timeout for embedding requests
        try {
            this.ollamaAPI.setRequestTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Could not set Ollama API timeout: {}", e.getMessage());
        }

        LOGGER.info("OllamaEmbeddingClient initialized with model: {}", modelName);
    }

    @Override
    public List<Double> generateEmbedding(String text) throws Exception {
        try {
            LOGGER.debug("Generating Ollama embedding for text (length: {})", text.length());

            // Use the Ollama4j library to generate embeddings
            String model = modelName != null && !modelName.isEmpty() ? modelName : OllamaModelType.NOMIC_EMBED_TEXT;

            List<Double> embedding = ollamaAPI.generateEmbeddings(model, text);

            if (embedding == null || embedding.isEmpty()) {
                throw new Exception("Ollama returned empty embedding");
            }

            LOGGER.debug("Ollama embedding generated successfully: {} dimensions", embedding.size());
            return embedding;

        } catch (Exception e) {
            LOGGER.error("Error generating Ollama embedding: {}", e.getMessage());

            // Provide helpful error message for common issues
            String errorHint = "";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("timeout") || e.getMessage().contains("Timeout")) {
                    errorHint = " - Try pulling the model first: 'ollama pull " + modelName + "'";
                } else if (e.getMessage().contains("connection") || e.getMessage().contains("refused")) {
                    errorHint = " - Ensure Ollama is running: 'ollama serve'";
                }
            }

            throw new Exception("Ollama embedding failed: " + e.getMessage() + errorHint);
        }
    }

    @Override
    public String getEmbeddingModel() {
        return modelName != null ? modelName : DEFAULT_MODEL;
    }

    @Override
    public int getEmbeddingDimension() {
        // nomic-embed-text produces 768-dimensional embeddings
        // Other models may vary
        if (modelName != null && modelName.contains("large")) {
            return 1024; // Some large embedding models
        }
        return 768; // Default for nomic-embed-text and most models
    }

    @Override
    public String getProvider() {
        return "Ollama";
    }

    @Override
    public boolean isReachable() {
        try {
            LOGGER.debug("Testing Ollama reachability...");

            // First check if Ollama server is running
            boolean pingSuccess = ollamaAPI.ping();
            if (!pingSuccess) {
                LOGGER.warn("Ollama server ping failed");
                return false;
            }

            // Check if the model is available
            try {
                var models = ollamaAPI.listModels();
                boolean modelExists = models.stream()
                        .anyMatch(m -> m.getName().equals(modelName) ||
                                       m.getName().startsWith(modelName + ":"));

                if (!modelExists) {
                    LOGGER.warn("Ollama model '{}' not found. Available models: {}",
                            modelName,
                            models.stream().map(m -> m.getName()).toList());
                    LOGGER.info("Run 'ollama pull {}' to download the embedding model", modelName);
                    return false;
                }
            } catch (Exception e) {
                LOGGER.debug("Could not list Ollama models: {}", e.getMessage());
                // Continue anyway - model might still exist
            }

            // Try generating a test embedding
            List<Double> testEmbedding = generateEmbedding("test");
            boolean success = testEmbedding != null && !testEmbedding.isEmpty();

            if (success) {
                LOGGER.info("✅ Ollama embedding service is reachable - model: {}", modelName);
            }
            return success;

        } catch (Exception e) {
            LOGGER.warn("❌ Ollama not reachable: {}", e.getMessage());
            LOGGER.info("Ensure Ollama is running and model is installed:");
            LOGGER.info("  1. Run 'ollama serve' to start Ollama");
            LOGGER.info("  2. Run 'ollama pull {}' to download the embedding model", modelName);
            return false;
        }
    }
}