package net.luanvha2550_hash.Autonomy.Cache;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a cached situation with learned action and success tracking.
 *
 * <p>Each cached situation stores:</p>
 * <ul>
 *   <li>A textual description of the situation</li>
 *   <li>The embedding vector for similarity comparison</li>
 *   <li>The best action determined for this situation</li>
 *   <li>Success/failure counts for reliability assessment</li>
 * </ul>
 *
 * <p>Situations are considered "reliable" when they have been tested multiple times
 * with a high success rate (at least 3 successes with 70%+ success rate).</p>
 */
public class CachedSituation {

    // ========== Core Fields ==========
    private final String id;
    private final String description;
    private final List<Double> embedding;
    private final String bestAction;
    private final Instant created;

    // ========== Statistics ==========
    private int successCount;
    private int failureCount;
    private Instant lastUsed;

    /**
     * Creates a new cached situation.
     *
     * @param description Human-readable description of the situation
     * @param embedding   Embedding vector for similarity comparison
     * @param bestAction   The action to take for this situation
     */
    public CachedSituation(String description, List<Double> embedding, String bestAction) {
        this.id = UUID.randomUUID().toString();
        this.description = description;
        this.embedding = new ArrayList<>(embedding);
        this.bestAction = bestAction;
        this.created = Instant.now();
        this.lastUsed = Instant.now();
        this.successCount = 0;
        this.failureCount = 0;
    }

    /**
     * Creates a cached situation with specified success/failure counts.
     * Used when loading from persistence.
     *
     * @param id            Unique identifier
     * @param description   Human-readable description
     * @param embedding     Embedding vector
     * @param bestAction    The action to take
     * @param successCount  Number of successful executions
     * @param failureCount  Number of failed executions
     * @param lastUsed      When this was last used
     * @param created       When this was created
     */
    public CachedSituation(String id, String description, List<Double> embedding,
                          String bestAction, int successCount, int failureCount,
                          Instant lastUsed, Instant created) {
        this.id = id;
        this.description = description;
        this.embedding = new ArrayList<>(embedding);
        this.bestAction = bestAction;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.lastUsed = lastUsed;
        this.created = created;
    }

    // ========== Getters ==========

    /**
     * Gets the unique identifier for this cached situation.
     * @return The UUID string
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the human-readable description of the situation.
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the embedding vector for this situation.
     * Returns a copy to prevent modification.
     * @return Copy of the embedding vector
     */
    public List<Double> getEmbedding() {
        return new ArrayList<>(embedding);
    }

    /**
     * Gets the best action for this situation.
     * @return The action string
     */
    public String getBestAction() {
        return bestAction;
    }

    /**
     * Gets the number of successful executions.
     * @return Success count
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * Gets the number of failed executions.
     * @return Failure count
     */
    public int getFailureCount() {
        return failureCount;
    }

    /**
     * Gets when this situation was last used.
     * @return Last used timestamp
     */
    public Instant getLastUsed() {
        return lastUsed;
    }

    /**
     * Gets when this situation was created.
     * @return Creation timestamp
     */
    public Instant getCreated() {
        return created;
    }

    // ========== Statistics Methods ==========

    /**
     * Calculates the success rate for this situation.
     *
     * @return Success rate as a percentage (0.0 to 1.0), or 0.0 if no attempts
     */
    public double getSuccessRate() {
        int total = successCount + failureCount;
        if (total == 0) {
            return 0.0;
        }
        return (double) successCount / total;
    }

    /**
     * Checks if this cached situation is reliable enough to use.
     *
     * <p>A situation is considered reliable when:</p>
     * <ul>
     *   <li>It has been tested at least 3 times (successCount >= 3)</li>
     *   <li>It has a success rate of at least 70%</li>
     * </ul>
     *
     * @return true if the situation meets reliability criteria
     */
    public boolean isReliable() {
        return successCount >= 3 && getSuccessRate() >= 0.70;
    }

    /**
     * Gets the total number of attempts (success + failure).
     * @return Total attempts
     */
    public int getTotalAttempts() {
        return successCount + failureCount;
    }

    // ========== Mutators ==========

    /**
     * Records a successful execution of this action.
     * Updates the last used timestamp.
     */
    public void recordSuccess() {
        this.successCount++;
        this.lastUsed = Instant.now();
    }

    /**
     * Records a failed execution of this action.
     * Updates the last used timestamp.
     */
    public void recordFailure() {
        this.failureCount++;
        this.lastUsed = Instant.now();
    }

    /**
     * Updates the last used timestamp to now.
     */
    public void touch() {
        this.lastUsed = Instant.now();
    }

    // ========== Object Methods ==========

    @Override
    public String toString() {
        return String.format("CachedSituation{id='%s', action='%s', success=%d, failure=%d, rate=%.1f%%}",
                id.substring(0, 8) + "...",
                bestAction,
                successCount,
                failureCount,
                getSuccessRate() * 100);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CachedSituation that = (CachedSituation) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}