package net.shasankp000.AIProviders;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified embedding provider that supports multiple AI providers
 * Automatically uses the appropriate API endpoint based on configuration
 *
 * Supports Matryoshka dimensions (768, 1536, 3072) for Gemini embeddings.
 * Supports task types for optimized embeddings (RETRIEVAL_DOCUMENT, RETRIEVAL_QUERY, etc.)
 */
public class EmbeddingProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-embeddings");
    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // Task type constants for Gemini API
    public static final String TASK_RETRIEVAL_DOCUMENT = "RETRIEVAL_DOCUMENT";
    public static final String TASK_RETRIEVAL_QUERY = "RETRIEVAL_QUERY";
    public static final String TASK_SEMANTIC_SIMILARITY = "SEMANTIC_SIMILARITY";
    public static final String TASK_CLASSIFICATION = "CLASSIFICATION";
    public static final String TASK_CLUSTERING = "CLUSTERING";

    // Valid dimensions for Gemini embeddings (Matryoshka Representation Learning)
    public static final int[] VALID_DIMENSIONS = {768, 1536, 3072};

    private final String baseUrl;
    private final String apiKey;
    private final String embeddingModel;
    private final AIProviderType providerType;
    private final int dimensions;  // Matryoshka: 768, 1536, or 3072
    private final String taskType;  // Task type for Gemini API
    private final OllamaAPI ollamaAPI; // Fallback for Ollama

    public enum AIProviderType {
        OLLAMA,
        OPENAI_COMPATIBLE,
        ANTHROPIC,
        GEMINI,
        MISTRAL,
        COHERE
    }

    /**
     * Constructor for Ollama provider (no API key needed)
     */
    public EmbeddingProvider(OllamaAPI ollamaAPI, String embeddingModel) {
        this.ollamaAPI = ollamaAPI;
        this.baseUrl = "http://localhost:11434";
        this.apiKey = null;
        this.embeddingModel = embeddingModel;
        this.providerType = AIProviderType.OLLAMA;
        this.dimensions = 768; // Default
        this.taskType = null;  // Not used for Ollama
        LOGGER.info("📊 Embedding provider initialized: Ollama ({})", embeddingModel);
    }

    /**
     * Constructor for non-Ollama providers (requires API key and endpoint)
     */
    public EmbeddingProvider(String baseUrl, String apiKey, String embeddingModel, AIProviderType providerType) {
        this(baseUrl, apiKey, embeddingModel, providerType, 768, null);
    }

    /**
     * Full constructor with all parameters including dimensions and task type
     *
     * @param baseUrl The API base URL
     * @param apiKey The API key
     * @param embeddingModel The embedding model name
     * @param providerType The provider type
     * @param dimensions The output dimensions (768, 1536, or 3072 for Matryoshka)
     * @param taskType The task type for optimized embeddings (e.g., RETRIEVAL_DOCUMENT)
     */
    public EmbeddingProvider(String baseUrl, String apiKey, String embeddingModel,
                             AIProviderType providerType, int dimensions, String taskType) {
        this.ollamaAPI = null;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.providerType = providerType;
        this.dimensions = validateDimensions(dimensions);
        this.taskType = taskType;
        LOGGER.info("📊 Embedding provider initialized: {} ({}, dimensions: {}, taskType: {})",
                    providerType, embeddingModel, this.dimensions, taskType);
    }

    /**
     * Validate dimensions against allowed values for Matryoshka
     */
    private int validateDimensions(int dims) {
        for (int valid : VALID_DIMENSIONS) {
            if (dims == valid) return dims;
        }
        LOGGER.warn("Invalid dimensions {}, defaulting to 768", dims);
        return 768;
    }

    /**
     * Generate embeddings using the configured provider
     *
     * @param text The text to generate embeddings for
     * @return List of embedding values (doubles)
     */
    public List<Double> generateEmbeddings(String text) throws IOException, InterruptedException {
        switch (providerType) {
            case OLLAMA:
                return generateOllamaEmbeddings(text);
            case OPENAI_COMPATIBLE:
                return generateOpenAICompatibleEmbeddings(text);
            case ANTHROPIC:
                throw new UnsupportedOperationException("Anthropic does not provide embedding endpoints");
            case GEMINI:
                return generateGeminiEmbeddings(text);
            case MISTRAL:
                return generateMistralEmbeddings(text);
            case COHERE:
                return generateCohereEmbeddings(text);
            default:
                throw new IllegalStateException("Unknown provider type: " + providerType);
        }
    }

    /**
     * Generate embeddings using Ollama API
     */
    private List<Double> generateOllamaEmbeddings(String text) throws IOException, InterruptedException {
        if (ollamaAPI != null) {
            try {
                return ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, text);
            } catch (Exception e) {
                LOGGER.error("❌ Failed to generate Ollama embeddings using library, trying direct API", e);
            }
        }

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", embeddingModel);
        requestJson.addProperty("prompt", text);

        String endpoint = baseUrl + "/api/embeddings";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Ollama embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray embeddingArray = responseJson.getAsJsonArray("embedding");

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < embeddingArray.size(); i++) {
            embeddings.add(embeddingArray.get(i).getAsDouble());
        }

        return embeddings;
    }

    /**
     * Generate embeddings using OpenAI-compatible API
     */
    private List<Double> generateOpenAICompatibleEmbeddings(String text) throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("input", text);
        requestJson.addProperty("model", embeddingModel);

        String endpoint = baseUrl + "/v1/embeddings";
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            LOGGER.error("OpenAI-compatible API error: {}", response.body());
            throw new IOException("OpenAI-compatible embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray dataArray = responseJson.getAsJsonArray("data");
        JsonObject firstEmbedding = dataArray.get(0).getAsJsonObject();
        JsonArray embeddingArray = firstEmbedding.getAsJsonArray("embedding");

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < embeddingArray.size(); i++) {
            embeddings.add(embeddingArray.get(i).getAsDouble());
        }

        LOGGER.debug("✅ Generated OpenAI-compatible embeddings: {} dimensions", embeddings.size());
        return embeddings;
    }

    /**
     * Generate embeddings using Google Gemini API
     * Supports task types and Matryoshka dimensions
     */
    private List<Double> generateGeminiEmbeddings(String text) throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();

        // Build content structure
        JsonObject contentObj = new JsonObject();
        JsonArray partsArray = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", text);
        partsArray.add(textPart);
        contentObj.add("parts", partsArray);
        requestJson.add("content", contentObj);

        // Build API endpoint - using v1beta for gemini-embedding-001 support
        String endpoint = baseUrl + "/v1beta/models/" + embeddingModel + ":embedContent?key=" + apiKey;

        // Add task type as query parameter if specified
        if (taskType != null && !taskType.isEmpty()) {
            endpoint += "&taskType=" + taskType;
        }

        LOGGER.debug("🔄 Requesting Gemini embedding for text (length: {})", text.length());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorBody = response.body();
            LOGGER.error("❌ Gemini API error: {} - {}", response.statusCode(), errorBody);
            throw new IOException("Gemini embeddings API returned status: " + response.statusCode() +
                                  ", body: " + errorBody);
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();

        // Handle response structure
        JsonObject embeddingObj = responseJson.getAsJsonObject("embedding");
        if (embeddingObj == null) {
            LOGGER.error("❌ Unexpected Gemini response structure: {}", response.body());
            throw new IOException("Missing 'embedding' in Gemini response");
        }

        JsonArray valuesArray = embeddingObj.getAsJsonArray("values");
        if (valuesArray == null) {
            throw new IOException("Missing 'values' in Gemini embedding response");
        }

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < valuesArray.size(); i++) {
            embeddings.add(valuesArray.get(i).getAsDouble());
        }

        // Apply dimension truncation for Matryoshka if needed
        if (dimensions < embeddings.size()) {
            embeddings = embeddings.subList(0, dimensions);
            LOGGER.debug("📊 Truncated embeddings to {} dimensions", dimensions);
        }

        LOGGER.debug("✅ Generated Gemini embeddings: {} dimensions", embeddings.size());
        return embeddings;
    }

    /**
     * Generate embeddings using Mistral API
     */
    private List<Double> generateMistralEmbeddings(String text) throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", embeddingModel);
        JsonArray inputArray = new JsonArray();
        inputArray.add(text);
        requestJson.add("input", inputArray);

        String endpoint = baseUrl + "/v1/embeddings";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Mistral embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray dataArray = responseJson.getAsJsonArray("data");
        JsonObject firstEmbedding = dataArray.get(0).getAsJsonObject();
        JsonArray embeddingArray = firstEmbedding.getAsJsonArray("embedding");

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < embeddingArray.size(); i++) {
            embeddings.add(embeddingArray.get(i).getAsDouble());
        }

        return embeddings;
    }

    /**
     * Generate embeddings using Cohere API
     */
    private List<Double> generateCohereEmbeddings(String text) throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", embeddingModel);
        JsonArray textsArray = new JsonArray();
        textsArray.add(text);
        requestJson.add("texts", textsArray);
        requestJson.addProperty("input_type", "search_document");

        String endpoint = baseUrl + "/v1/embed";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Cohere embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray embeddingsArray = responseJson.getAsJsonArray("embeddings");
        JsonArray firstEmbedding = embeddingsArray.get(0).getAsJsonArray();

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < firstEmbedding.size(); i++) {
            embeddings.add(firstEmbedding.get(i).getAsDouble());
        }

        return embeddings;
    }

    /**
     * Get the configured embedding model name
     */
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Get the provider type
     */
    public AIProviderType getProviderType() {
        return providerType;
    }

    /**
     * Get the configured dimensions
     */
    public int getDimensions() {
        return dimensions;
    }

    /**
     * Get the configured task type
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * Check if this provider supports dimension configuration
     */
    public boolean supportsDimensions() {
        return providerType == AIProviderType.GEMINI ||
               providerType == AIProviderType.OPENAI_COMPATIBLE;
    }

    /**
     * Get valid dimensions for the current provider
     */
    public static int[] getValidDimensions() {
        return VALID_DIMENSIONS.clone();
    }
}