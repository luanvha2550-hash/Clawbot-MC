package net.shasankp000.Autonomy.Cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for storing learned behaviors with embedding similarity search.
 *
 * <p>The KnowledgeCache enables the AI-Player to learn from experience and reuse
 * successful actions in similar situations. It uses cosine similarity on embedding
 * vectors to find matching situations.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Thread-safe storage using ConcurrentHashMap</li>
 *   <li>Cosine similarity search for finding similar situations</li>
 *   <li>Success/failure tracking for reliability assessment</li>
 *   <li>Optional persistence to disk</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * KnowledgeCache cache = KnowledgeCache.getInstance();
 *
 * // Find a similar cached situation
 * Optional<String> action = cache.findActionForSituation(embedding);
 * if (action.isPresent()) {
 *     // Use the cached action
 * }
 *
 * // Learn from a new experience
 * cache.learnSituation("Low health, zombie nearby", "FLEE", embedding, true);
 * }</pre>
 */
public class KnowledgeCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-knowledge-cache");

    // Singleton instance
    private static volatile KnowledgeCache instance;

    // Similarity threshold for matching situations (0.0 to 1.0)
    public static final double SIMILARITY_THRESHOLD = 0.85;

    // Minimum attempts before a situation can be considered reliable
    private static final int MIN_ATTEMPTS_FOR_RELIABILITY = 3;

    // Minimum success rate for reliability (0.0 to 1.0)
    private static final double MIN_SUCCESS_RATE = 0.70;

    // ========== Storage ==========
    private final ConcurrentHashMap<String, CachedSituation> situations;
    private final Gson gson;

    // Configuration
    private Path persistencePath;
    private boolean autoSave = false;

    /**
     * Gets the singleton instance of the KnowledgeCache.
     * @return The KnowledgeCache instance
     */
    public static KnowledgeCache getInstance() {
        if (instance == null) {
            synchronized (KnowledgeCache.class) {
                if (instance == null) {
                    instance = new KnowledgeCache();
                }
            }
        }
        return instance;
    }

    /**
     * Private constructor for singleton pattern.
     */
    private KnowledgeCache() {
        this.situations = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .create();
        LOGGER.info("KnowledgeCache initialized with similarity threshold: {}", SIMILARITY_THRESHOLD);
    }

    // ========== Core Operations ==========

    /**
     * Finds the best matching cached action for a given situation embedding.
     *
     * <p>Uses cosine similarity to find situations above the threshold.
     * Only returns actions from reliable situations.</p>
     *
     * @param embedding The embedding vector for the current situation
     * @return Optional containing the best action, or empty if no reliable match found
     */
    public Optional<String> findActionForSituation(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return Optional.empty();
        }

        CachedSituation bestMatch = null;
        double bestSimilarity = SIMILARITY_THRESHOLD;

        for (CachedSituation situation : situations.values()) {
            // Only consider reliable situations
            if (!situation.isReliable()) {
                continue;
            }

            double similarity = cosineSimilarity(embedding, situation.getEmbedding());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = situation;
            }
        }

        if (bestMatch != null) {
            bestMatch.touch(); // Update last used timestamp
            LOGGER.debug("Found matching situation: {} (similarity: {:.2f})",
                    bestMatch.getDescription(), bestSimilarity);
            return Optional.of(bestMatch.getBestAction());
        }

        return Optional.empty();
    }

    /**
     * Learns a new situation-action pair or updates an existing one.
     *
     * <p>If a similar situation already exists, records success/failure for it.
     * Otherwise, creates a new cached situation.</p>
     *
     * @param description Human-readable description of the situation
     * @param action      The action taken
     * @param embedding   The embedding vector for the situation
     * @param success     Whether the action was successful
     * @return The ID of the cached situation (new or existing)
     */
    public String learnSituation(String description, String action,
                                 List<Double> embedding, boolean success) {
        if (embedding == null || embedding.isEmpty()) {
            LOGGER.warn("Attempted to learn situation with null/empty embedding");
            return null;
        }

        // Check if similar situation exists
        CachedSituation existing = findSimilarSituation(embedding);

        if (existing != null) {
            // Update existing situation
            if (success) {
                existing.recordSuccess();
                LOGGER.debug("Recorded success for situation: {} (rate: {:.1f}%)",
                        existing.getId().substring(0, 8), existing.getSuccessRate() * 100);
            } else {
                existing.recordFailure();
                LOGGER.debug("Recorded failure for situation: {} (rate: {:.1f}%)",
                        existing.getId().substring(0, 8), existing.getSuccessRate() * 100);
            }

            if (autoSave) {
                save();
            }

            return existing.getId();
        }

        // Create new cached situation
        CachedSituation newSituation = new CachedSituation(description, embedding, action);

        if (success) {
            newSituation.recordSuccess();
        } else {
            newSituation.recordFailure();
        }

        situations.put(newSituation.getId(), newSituation);
        LOGGER.info("Learned new situation: {} -> {} (success: {})",
                description.substring(0, Math.min(30, description.length())),
                action, success);

        if (autoSave) {
            save();
        }

        return newSituation.getId();
    }

    /**
     * Records a successful execution for a cached situation.
     *
     * @param situationId The ID of the cached situation
     * @param action       The action (for verification/logging)
     */
    public void recordSuccess(String situationId, String action) {
        CachedSituation situation = situations.get(situationId);
        if (situation != null) {
            situation.recordSuccess();
            LOGGER.debug("Recorded success for situation {} with action {}",
                    situationId.substring(0, 8), action);

            if (autoSave) {
                save();
            }
        }
    }

    /**
     * Records a failed execution for a cached situation.
     *
     * @param situationId The ID of the cached situation
     * @param action       The action (for verification/logging)
     */
    public void recordFailure(String situationId, String action) {
        CachedSituation situation = situations.get(situationId);
        if (situation != null) {
            situation.recordFailure();
            LOGGER.debug("Recorded failure for situation {} with action {}",
                    situationId.substring(0, 8), action);

            if (autoSave) {
                save();
            }
        }
    }

    // ========== Similarity Calculation ==========

    /**
     * Calculates cosine similarity between two embedding vectors.
     *
     * <p>Cosine similarity measures the cosine of the angle between two vectors.
     * Returns a value between -1 and 1, where 1 means identical direction.</p>
     *
     * @param a First embedding vector
     * @param b Second embedding vector
     * @return Cosine similarity (-1 to 1), or 0 if vectors are invalid
     */
    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        if (a.size() != b.size()) {
            LOGGER.warn("Embedding size mismatch: {} vs {}", a.size(), b.size());
            // Compare up to the smaller dimension
            int minSize = Math.min(a.size(), b.size());
            return cosineSimilarity(a.subList(0, minSize), b.subList(0, minSize));
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double va = a.get(i);
            double vb = b.get(i);
            dotProduct += va * vb;
            normA += va * va;
            normB += vb * vb;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Finds the most similar cached situation to the given embedding.
     *
     * @param embedding The embedding to match against
     * @return The most similar situation, or null if none found above threshold
     */
    private CachedSituation findSimilarSituation(List<Double> embedding) {
        CachedSituation bestMatch = null;
        double bestSimilarity = SIMILARITY_THRESHOLD;

        for (CachedSituation situation : situations.values()) {
            double similarity = cosineSimilarity(embedding, situation.getEmbedding());
            if (similarity >= bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = situation;
            }
        }

        return bestMatch;
    }

    /**
     * Finds all cached situations with similarity above a threshold.
     *
     * @param embedding  The embedding to match against
     * @param threshold   Minimum similarity (0.0 to 1.0)
     * @return List of matching situations sorted by similarity (highest first)
     */
    public List<SimilarityResult> findSimilarSituations(List<Double> embedding, double threshold) {
        List<SimilarityResult> results = new ArrayList<>();

        for (CachedSituation situation : situations.values()) {
            double similarity = cosineSimilarity(embedding, situation.getEmbedding());
            if (similarity >= threshold) {
                results.add(new SimilarityResult(situation, similarity));
            }
        }

        // Sort by similarity descending
        results.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        return results;
    }

    // ========== Statistics and Query ==========

    /**
     * Gets the total number of cached situations.
     * @return Number of situations in the cache
     */
    public int size() {
        return situations.size();
    }

    /**
     * Gets the number of reliable situations.
     * @return Number of situations meeting reliability criteria
     */
    public int reliableCount() {
        return (int) situations.values().stream()
                .filter(CachedSituation::isReliable)
                .count();
    }

    /**
     * Gets all cached situations.
     * @return Unmodifiable collection of situations
     */
    public Collection<CachedSituation> getAllSituations() {
        return Collections.unmodifiableCollection(situations.values());
    }

    /**
     * Gets a cached situation by ID.
     * @param id The situation ID
     * @return Optional containing the situation, or empty if not found
     */
    public Optional<CachedSituation> getSituation(String id) {
        return Optional.ofNullable(situations.get(id));
    }

    /**
     * Clears all cached situations.
     */
    public void clear() {
        situations.clear();
        LOGGER.info("KnowledgeCache cleared");
    }

    /**
     * Removes situations that have not been used recently and have low success rates.
     * Keeps reliable situations regardless of last used time.
     *
     * @param maxAgeMs Maximum age in milliseconds for unreliable situations
     * @return Number of situations removed
     */
    public int prune(long maxAgeMs) {
        Instant cutoff = Instant.now().minusMillis(maxAgeMs);
        int removed = 0;

        Iterator<Map.Entry<String, CachedSituation>> iterator = situations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedSituation> entry = iterator.next();
            CachedSituation situation = entry.getValue();

            // Keep reliable situations
            if (situation.isReliable()) {
                continue;
            }

            // Remove old unreliable situations
            if (situation.getLastUsed().isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            LOGGER.info("Pruned {} old unreliable situations", removed);
        }

        return removed;
    }

    // ========== Persistence ==========

    /**
     * Sets the path for persistence storage.
     * @param path Path to the cache file
     */
    public void setPersistencePath(Path path) {
        this.persistencePath = path;
    }

    /**
     * Sets the path for persistence storage using string.
     * @param path Path string to the cache file
     */
    public void setPersistencePath(String path) {
        this.persistencePath = Paths.get(path);
    }

    /**
     * Enables or disables automatic saving after modifications.
     * @param autoSave true to enable auto-save
     */
    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    /**
     * Saves the cache to disk.
     *
     * @return true if save was successful
     */
    public boolean save() {
        if (persistencePath == null) {
            LOGGER.warn("No persistence path set, cannot save");
            return false;
        }

        try {
            // Ensure parent directory exists
            Path parent = persistencePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Build JSON structure
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("savedAt", Instant.now().toString());
            root.addProperty("count", situations.size());

            JsonArray situationsArray = new JsonArray();
            for (CachedSituation situation : situations.values()) {
                JsonObject situationObj = new JsonObject();
                situationObj.addProperty("id", situation.getId());
                situationObj.addProperty("description", situation.getDescription());
                situationObj.addProperty("bestAction", situation.getBestAction());
                situationObj.addProperty("successCount", situation.getSuccessCount());
                situationObj.addProperty("failureCount", situation.getFailureCount());
                situationObj.addProperty("created", situation.getCreated().toString());
                situationObj.addProperty("lastUsed", situation.getLastUsed().toString());

                // Serialize embedding array
                JsonArray embeddingArray = new JsonArray();
                for (Double value : situation.getEmbedding()) {
                    embeddingArray.add(value);
                }
                situationObj.add("embedding", embeddingArray);

                situationsArray.add(situationObj);
            }
            root.add("situations", situationsArray);

            // Write to file
            Files.writeString(persistencePath, gson.toJson(root));
            LOGGER.info("Saved {} situations to {}", situations.size(), persistencePath);
            return true;

        } catch (IOException e) {
            LOGGER.error("Failed to save knowledge cache", e);
            return false;
        }
    }

    /**
     * Loads the cache from disk.
     *
     * @return true if load was successful
     */
    public boolean load() {
        if (persistencePath == null || !Files.exists(persistencePath)) {
            LOGGER.info("No cache file found at {}", persistencePath);
            return false;
        }

        try {
            String content = Files.readString(persistencePath);
            JsonObject root = gson.fromJson(content, JsonObject.class);

            if (root == null) {
                LOGGER.warn("Invalid cache file format");
                return false;
            }

            // Clear existing cache
            situations.clear();

            JsonArray situationsArray = root.getAsJsonArray("situations");
            if (situationsArray != null) {
                for (int i = 0; i < situationsArray.size(); i++) {
                    JsonObject obj = situationsArray.get(i).getAsJsonObject();

                    String id = obj.get("id").getAsString();
                    String description = obj.get("description").getAsString();
                    String bestAction = obj.get("bestAction").getAsString();
                    int successCount = obj.get("successCount").getAsInt();
                    int failureCount = obj.get("failureCount").getAsInt();
                    Instant created = Instant.parse(obj.get("created").getAsString());
                    Instant lastUsed = Instant.parse(obj.get("lastUsed").getAsString());

                    // Parse embedding array
                    JsonArray embeddingArray = obj.getAsJsonArray("embedding");
                    List<Double> embedding = new ArrayList<>();
                    for (int j = 0; j < embeddingArray.size(); j++) {
                        embedding.add(embeddingArray.get(j).getAsDouble());
                    }

                    CachedSituation situation = new CachedSituation(
                            id, description, embedding, bestAction,
                            successCount, failureCount, lastUsed, created
                    );

                    situations.put(id, situation);
                }
            }

            LOGGER.info("Loaded {} situations from {}", situations.size(), persistencePath);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load knowledge cache", e);
            return false;
        }
    }

    // ========== Inner Classes ==========

    /**
     * Result of a similarity search containing the situation and its similarity score.
     */
    public static class SimilarityResult {
        public final CachedSituation situation;
        public final double similarity;

        public SimilarityResult(CachedSituation situation, double similarity) {
            this.situation = situation;
            this.similarity = similarity;
        }
    }

    /**
     * Gson type adapter for Instant serialization.
     */
    private static class InstantAdapter implements com.google.gson.JsonSerializer<Instant>, com.google.gson.JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(com.google.gson.JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) throws com.google.gson.JsonParseException {
            return Instant.parse(json.getAsString());
        }
    }
}