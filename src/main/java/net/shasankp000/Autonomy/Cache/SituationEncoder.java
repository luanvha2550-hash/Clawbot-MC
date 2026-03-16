package net.shasankp000.Autonomy.Cache;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.shasankp000.AIProviders.EmbeddingProvider;
import net.shasankp000.Autonomy.AutonomyContext;
import net.shasankp000.Autonomy.InventorySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts the current world state into an embedding vector for similarity matching
 * in the KnowledgeCache.
 *
 * <p>SituationEncoder transforms game state information (position, health, threats,
 * inventory, biome, etc.) into a textual description, then uses an EmbeddingProvider
 * to generate a dense vector representation for semantic similarity search.</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * SituationEncoder encoder = new SituationEncoder(embeddingProvider);
 * List<Double> embedding = encoder.encode(context);
 *
 * // Use embedding to find similar situations in KnowledgeCache
 * Optional<String> action = cache.findActionForSituation(embedding);
 * }</pre>
 *
 * <h2>Description Format:</h2>
 * <p>The encoder builds descriptions like:</p>
 * <pre>
 * Position: (123, 64, -456). Health: 15/20. Hunger: 18/20. Threats: zombie, skeleton.
 * Inventory: iron_sword, bread. Biome: plains. Time: day.
 * </pre>
 */
public class SituationEncoder {

    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-situation-encoder");

    private final EmbeddingProvider embeddingProvider;

    /**
     * Creates a new SituationEncoder with the specified embedding provider.
     *
     * @param embeddingProvider The provider to use for generating embeddings
     */
    public SituationEncoder(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    /**
     * Encodes the current world state into an embedding vector.
     *
     * <p>This method builds a textual description of the situation and then
     * uses the configured EmbeddingProvider to generate a 768-dimensional
     * embedding vector.</p>
     *
     * @param context The autonomy context containing the current world state
     * @return A list of doubles representing the embedding vector
     * @throws Exception If embedding generation fails
     */
    public List<Double> encode(AutonomyContext context) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        String description = buildDescription(context);

        LOGGER.debug("Encoding situation: {}", description);

        try {
            List<Double> embedding = embeddingProvider.generateEmbeddings(description);

            if (embedding == null || embedding.isEmpty()) {
                LOGGER.warn("Embedding provider returned empty embedding");
                throw new IOException("Embedding provider returned empty embedding");
            }

            LOGGER.debug("Generated embedding with {} dimensions", embedding.size());
            return embedding;

        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to generate embedding: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Builds a textual description of the current situation for embedding.
     *
     * <p>The description includes all relevant aspects of the game state
     * that are important for decision making and similarity matching.</p>
     *
     * @param context The autonomy context containing the world state
     * @return A human-readable description string
     */
    public String buildDescription(AutonomyContext context) {
        StringBuilder sb = new StringBuilder();

        // Position
        sb.append("Position: ");
        sb.append(String.format("(%.0f, %.0f, %.0f). ",
                context.getBotPosition().x,
                context.getBotPosition().y,
                context.getBotPosition().z));

        // Health
        sb.append(String.format("Health: %.0f/%.0f. ",
                context.getBotHealth(),
                context.getBotMaxHealth()));

        // Hunger
        sb.append(String.format("Hunger: %d/20. ", context.getBotHunger()));

        // Air (for underwater situations)
        if (context.isInWater()) {
            sb.append(String.format("Air: %d/300. ", context.getBotAir()));
        }

        // Threats
        List<Entity> hostiles = context.getHostileEntities();
        if (!hostiles.isEmpty()) {
            sb.append("Threats: ");
            String threatList = hostiles.stream()
                    .map(entity -> {
                        String name = entity.getType().getName().getString();
                        return name.toLowerCase().replace(" ", "_");
                    })
                    .distinct()
                    .limit(5)  // Limit to 5 most relevant threats
                    .collect(Collectors.joining(", "));
            sb.append(threatList).append(". ");
        }

        // Inventory highlights
        InventorySnapshot inventory = context.getInventory();
        if (inventory != null) {
            List<String> inventoryHighlights = new ArrayList<>();

            if (inventory.getWeaponCount() > 0) {
                inventoryHighlights.add("has_weapon");
            }
            if (inventory.getFoodCount() > 0) {
                inventoryHighlights.add("has_food");
            }
            if (inventory.getBestWeaponTier() >= 2) {
                inventoryHighlights.add("good_weapon");
            }
            if (inventory.hasCompleteArmor()) {
                inventoryHighlights.add("full_armor");
            }
            if (inventory.getToolCount() > 0) {
                inventoryHighlights.add("has_tools");
            }

            if (!inventoryHighlights.isEmpty()) {
                sb.append("Inventory: ");
                sb.append(String.join(", ", inventoryHighlights));
                sb.append(". ");
            }
        }

        // Biome
        sb.append("Biome: ").append(context.getCurrentBiome()).append(". ");

        // Time
        sb.append("Time: ").append(context.isDaytime() ? "day" : "night").append(". ");

        // Danger conditions
        if (context.isInLava()) {
            sb.append("Condition: in_lava. ");
        } else if (context.isOnFire()) {
            sb.append("Condition: on_fire. ");
        } else if (context.isUnderwater()) {
            sb.append("Condition: underwater. ");
        } else if (context.isFalling()) {
            sb.append("Condition: falling. ");
        }

        // Owner status
        if (context.hasOwnerNearby()) {
            sb.append("Owner: nearby. ");
        } else if (context.getOwnerUUID() != null) {
            sb.append("Owner: distant. ");
        }

        // Current goal
        if (context.getCurrentGoalType() != AutonomyContext.GoalType.NONE) {
            sb.append("Goal: ").append(context.getCurrentGoalType().name().toLowerCase()).append(". ");
        }

        // Nearby blocks (if relevant)
        Map<String, Integer> nearbyBlocks = context.getNearbyBlocks();
        if (nearbyBlocks != null && !nearbyBlocks.isEmpty()) {
            List<String> blockHighlights = nearbyBlocks.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .filter(e -> isRelevantBlock(e.getKey()))
                    .map(e -> e.getKey())
                    .limit(3)
                    .collect(Collectors.toList());

            if (!blockHighlights.isEmpty()) {
                sb.append("Nearby blocks: ").append(String.join(", ", blockHighlights)).append(". ");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Determines if a block type is relevant for situation encoding.
     *
     * <p>Only includes blocks that are significant for decision making,
     * such as resources, hazards, or navigation-relevant blocks.</p>
     *
     * @param blockId The block identifier
     * @return true if the block is relevant for encoding
     */
    private boolean isRelevantBlock(String blockId) {
        if (blockId == null) return false;

        String lower = blockId.toLowerCase();

        // Resources
        if (lower.contains("ore") || lower.contains("diamond") ||
            lower.contains("iron") || lower.contains("gold") ||
            lower.contains("coal") || lower.contains("copper")) {
            return true;
        }

        // Hazards
        if (lower.contains("lava") || lower.contains("water") ||
            lower.contains("cactus") || lower.contains("magma") ||
            lower.contains("fire")) {
            return true;
        }

        // Navigation relevant
        if (lower.contains("door") || lower.contains("ladder") ||
            lower.contains("bed") || lower.contains("chest")) {
            return true;
        }

        // Farming relevant
        if (lower.contains("wheat") || lower.contains("carrot") ||
            lower.contains("potato") || lower.contains("crop")) {
            return true;
        }

        return false;
    }

    /**
     * Encodes the context and stores the embedding in the context object.
     *
     * <p>This is a convenience method that both generates the embedding
     * and caches it in the AutonomyContext for later retrieval.</p>
     *
     * @param context The autonomy context to encode and update
     * @return The generated embedding vector
     * @throws Exception If embedding generation fails
     */
    public List<Double> encodeAndStore(AutonomyContext context) throws Exception {
        List<Double> embedding = encode(context);
        context.setSituationEmbedding(embedding);
        return embedding;
    }

    /**
     * Gets the embedding provider used by this encoder.
     *
     * @return The EmbeddingProvider instance
     */
    public EmbeddingProvider getEmbeddingProvider() {
        return embeddingProvider;
    }

    /**
     * Gets the configured embedding dimensions from the provider.
     *
     * @return The number of dimensions in the embedding vector
     */
    public int getEmbeddingDimensions() {
        return embeddingProvider.getDimensions();
    }
}