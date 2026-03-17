package net.luanvha2550_hash.ServiceLLMClients;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Google Gemini embedding client using Google AI Studio API.
 *
 * Supported models (Google AI Studio):
 * - gemini-embedding-001 (stable, 768-3072 dims)
 * - gemini-embedding-2-preview (multimodal, up to 8192 tokens)
 *
 * Note: text-embedding-004 is for Vertex AI, not Google AI Studio!
 *
 * API Reference: https://ai.google.dev/api/embeddings
 */
public class GeminiEmbeddingClient implements EmbeddingClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("Gemini-Embedding-Client");

    // Google AI Studio uses v1beta for embedding models
    private static final String EMBEDDING_ENDPOINT_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s";

    // Default to gemini-embedding-001 (stable model for Google AI Studio)
    // Note: text-embedding-004 is Vertex AI only!
    private static final String DEFAULT_MODEL = "gemini-embedding-001";

    // Available models for Google AI Studio
    private static final List<String> SUPPORTED_MODELS = List.of(
            "gemini-embedding-001",
            "gemini-embedding-2-preview",
            "text-embedding-004"  // May not work with AI Studio API
    );

    private final String apiKey;
    private final String modelName;
    private final HttpClient client;
    private final int embeddingDimension;

    public GeminiEmbeddingClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public GeminiEmbeddingClient(String apiKey, String modelName) {
        this.apiKey = apiKey;
        // Validate model name
        this.modelName = (modelName != null && !modelName.isEmpty()) ? modelName : DEFAULT_MODEL;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // Determine embedding dimension based on model name
        this.embeddingDimension = determineEmbeddingDimension(this.modelName);

        // Warn if using potentially unsupported model
        if (modelName != null && !modelName.isEmpty() &&
            !modelName.startsWith("gemini-embedding") &&
            !modelName.equals(DEFAULT_MODEL)) {
            LOGGER.warn("⚠ Model '{}' may not be available on Google AI Studio API.", modelName);
            LOGGER.warn("   Supported models: gemini-embedding-001, gemini-embedding-2-preview");
        }

        LOGGER.info("GeminiEmbeddingClient initialized with model: {} ({} dimensions)", this.modelName, embeddingDimension);
    }

    /**
     * Determine embedding dimension based on model name.
     * Gemini embedding models support output_dimensionality parameter.
     */
    private int determineEmbeddingDimension(String model) {
        // Gemini embedding models default to 3072, but we use 768 for efficiency
        // The API supports reducing dimensions via config
        return 768; // Use reduced dimension for efficiency
    }

    @Override
    public List<Double> generateEmbedding(String text) throws Exception {
        try {
            // Build request body for Gemini API
            // Format: {"content": {"parts": [{"text": "..."}]}}
            JsonObject parts = new JsonObject();
            parts.addProperty("text", text);

            JsonArray partsArray = new JsonArray();
            partsArray.add(parts);

            JsonObject content = new JsonObject();
            content.add("parts", partsArray);

            JsonObject requestBody = new JsonObject();
            requestBody.add("content", content);

            // Add output dimensionality config for reduced dimensions
            JsonObject config = new JsonObject();
            config.addProperty("outputDimensionality", embeddingDimension);
            requestBody.add("config", config);

            String endpoint = String.format(EMBEDDING_ENDPOINT_TEMPLATE, modelName, apiKey);

            LOGGER.debug("Calling Gemini embedding API: {} with text length: {}", modelName, text.length());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errorBody = response.body();
                LOGGER.error("Gemini API error {}: {}", response.statusCode(), errorBody);

                // Try to parse error message for helpful hints
                try {
                    JsonObject errorJson = JsonParser.parseString(errorBody).getAsJsonObject();
                    if (errorJson.has("error")) {
                        JsonObject error = errorJson.getAsJsonObject("error");
                        String message = error.has("message") ? error.get("message").getAsString() : errorBody;

                        // Provide helpful error hints
                        if (message.contains("not found") || message.contains("NOT_FOUND")) {
                            throw new Exception("Model '" + modelName + "' not found. " +
                                    "Use 'gemini-embedding-001' for Google AI Studio. " +
                                    "Note: 'text-embedding-004' is for Vertex AI only.");
                        }
                        throw new Exception("Gemini API error: " + message);
                    }
                } catch (Exception parseEx) {
                    // Fall back to raw error
                }

                throw new Exception("Gemini Embedding API error: " + response.statusCode() + " - " + errorBody);
            }

            // Parse response: {"embedding": {"values": [0.1, 0.2, ...]}}
            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

            if (!jsonResponse.has("embedding")) {
                LOGGER.error("Unexpected response format: {}", response.body());
                throw new Exception("Invalid response from Gemini API: missing 'embedding' field");
            }

            JsonObject embedding = jsonResponse.getAsJsonObject("embedding");
            if (!embedding.has("values")) {
                LOGGER.error("Unexpected embedding format: {}", embedding);
                throw new Exception("Invalid response from Gemini API: missing 'values' in embedding");
            }

            JsonArray embeddingArray = embedding.getAsJsonArray("values");

            List<Double> result = new ArrayList<>();
            for (int i = 0; i < embeddingArray.size(); i++) {
                result.add(embeddingArray.get(i).getAsDouble());
            }

            LOGGER.debug("Gemini embedding generated successfully: {} dimensions", result.size());
            return result;

        } catch (Exception e) {
            LOGGER.error("Error generating Gemini embedding: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public String getEmbeddingModel() {
        return modelName;
    }

    @Override
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    @Override
    public String getProvider() {
        return "Google Gemini";
    }

    @Override
    public boolean isReachable() {
        try {
            LOGGER.debug("Testing Gemini API reachability...");
            // Simple test with minimal text
            List<Double> testEmbedding = generateEmbedding("test");
            boolean success = testEmbedding != null && !testEmbedding.isEmpty();
            if (success) {
                LOGGER.info("✅ Gemini API is reachable - model: {}, dimensions: {}", modelName, testEmbedding.size());
            }
            return success;
        } catch (Exception e) {
            LOGGER.warn("❌ Gemini API not reachable: {}", e.getMessage());

            // Provide helpful hints
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                LOGGER.info("   Hint: Use 'gemini-embedding-001' for Google AI Studio");
                LOGGER.info("   Note: 'text-embedding-004' is for Vertex AI, not Google AI Studio");
            } else if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                LOGGER.info("   Hint: Check your Gemini API key is valid and has access to embedding models");
            }
            return false;
        }
    }

    /**
     * Get list of supported model names.
     */
    public static List<String> getSupportedModels() {
        return SUPPORTED_MODELS;
    }
}