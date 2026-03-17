package net.luanvha2550_hash.Autonomy.Memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Long-term memory system for the AI-Player autonomy architecture.
 *
 * <p>This class provides persistent storage for learned information including:</p>
 * <ul>
 *   <li>Named world locations (bases, resource deposits, danger zones)</li>
 *   <li>Death spot records for danger avoidance learning</li>
 *   <li>Player relationship memories (trust levels, interaction history)</li>
 * </ul>
 *
 * <h2>Thread Safety:</h2>
 * <p>All collections use thread-safe implementations. This class is designed
 * for concurrent access from both game tick and AI decision threads.</p>
 *
 * <h2>Persistence:</h2>
 * <p>Memory is automatically saved to JSON files in the mod config directory
 * and loaded on first access. Call {@link #save()} explicitly to persist
 * important changes.</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * LongTermMemory memory = LongTermMemory.getInstance();
 *
 * // Save a discovered location
 * WorldLocation base = new WorldLocation("base_1", "Home Base", bot.getPos(), LocationType.BASE);
 * memory.saveLocation(base);
 *
 * // Find best resource location
 * Optional<WorldLocation> ironMine = memory.findBestResourceLocation("iron_ore");
 *
 * // Record a death
 * memory.recordDeath(deathPosition, "lava");
 *
 * // Check for dangerous areas
 * if (memory.isDangerousLocation(nearbyPos)) {
 *     // Take evasive action
 * }
 * }</pre>
 */
public class LongTermMemory {

    private static final Logger LOGGER = LoggerFactory.getLogger("LongTermMemory");

    /**
     * Singleton instance
     */
    private static volatile LongTermMemory instance;

    /**
     * File path for memory persistence
     */
    private static final String MEMORY_FILE = "ai_memory.json";

    /**
     * Location ID -> WorldLocation
     */
    private final ConcurrentHashMap<String, WorldLocation> locations;

    /**
     * Death spots recorded for danger avoidance
     */
    private final List<DeathSpot> deathSpots;

    /**
     * Player UUID -> Memory data
     */
    private final ConcurrentHashMap<UUID, PlayerMemory> playerMemories;

    /**
     * File path for persistence
     */
    private final Path storagePath;

    /**
     * Lock object for synchronization
     */
    private final Object lock = new Object();

    /**
     * Player memory for tracking relationships with other players.
     */
    public static class PlayerMemory {
        private final UUID playerId;
        private String playerName;
        private double trustLevel; // -1.0 to 1.0
        private int interactionCount;
        private int hostileActions;
        private int friendlyActions;
        private long firstMetTimestamp;
        private long lastInteractionTimestamp;
        private String notes;

        public PlayerMemory(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.trustLevel = 0.0; // Neutral
            this.interactionCount = 0;
            this.hostileActions = 0;
            this.friendlyActions = 0;
            this.firstMetTimestamp = System.currentTimeMillis();
            this.lastInteractionTimestamp = this.firstMetTimestamp;
            this.notes = "";
        }

        // Getters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public double getTrustLevel() { return trustLevel; }
        public int getInteractionCount() { return interactionCount; }
        public int getHostileActions() { return hostileActions; }
        public int getFriendlyActions() { return friendlyActions; }
        public long getFirstMetTimestamp() { return firstMetTimestamp; }
        public long getLastInteractionTimestamp() { return lastInteractionTimestamp; }
        public String getNotes() { return notes; }

        // Setters
        public void setPlayerName(String name) { this.playerName = name; }
        public void setNotes(String notes) { this.notes = notes; }

        /**
         * Records a hostile action by this player.
         */
        public void recordHostileAction() {
            this.hostileActions++;
            this.interactionCount++;
            this.lastInteractionTimestamp = System.currentTimeMillis();
            updateTrust();
        }

        /**
         * Records a friendly action by this player.
         */
        public void recordFriendlyAction() {
            this.friendlyActions++;
            this.interactionCount++;
            this.lastInteractionTimestamp = System.currentTimeMillis();
            updateTrust();
        }

        /**
         * Updates trust level based on interaction history.
         */
        private void updateTrust() {
            if (interactionCount == 0) {
                trustLevel = 0.0;
                return;
            }

            // Trust is ratio of friendly to total actions, with bias toward recent
            double friendlyRatio = (double) friendlyActions / interactionCount;
            double hostileRatio = (double) hostileActions / interactionCount;

            // Trust ranges from -1 (hostile) to +1 (friendly)
            trustLevel = friendlyRatio - hostileRatio;
            trustLevel = Math.max(-1.0, Math.min(1.0, trustLevel));
        }

        /**
         * Checks if this player is considered friendly.
         */
        public boolean isFriendly() {
            return trustLevel > 0.3;
        }

        /**
         * Checks if this player is considered hostile.
         */
        public boolean isHostile() {
            return trustLevel < -0.3;
        }

        /**
         * Converts to JSON.
         */
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("playerId", playerId.toString());
            obj.addProperty("playerName", playerName);
            obj.addProperty("trustLevel", trustLevel);
            obj.addProperty("interactionCount", interactionCount);
            obj.addProperty("hostileActions", hostileActions);
            obj.addProperty("friendlyActions", friendlyActions);
            obj.addProperty("firstMetTimestamp", firstMetTimestamp);
            obj.addProperty("lastInteractionTimestamp", lastInteractionTimestamp);
            obj.addProperty("notes", notes);
            return obj;
        }

        /**
         * Creates from JSON.
         */
        public static PlayerMemory fromJson(JsonObject obj) {
            UUID playerId = UUID.fromString(obj.get("playerId").getAsString());
            String playerName = obj.get("playerName").getAsString();

            PlayerMemory memory = new PlayerMemory(playerId, playerName);
            if (obj.has("trustLevel")) memory.trustLevel = obj.get("trustLevel").getAsDouble();
            if (obj.has("interactionCount")) memory.interactionCount = obj.get("interactionCount").getAsInt();
            if (obj.has("hostileActions")) memory.hostileActions = obj.get("hostileActions").getAsInt();
            if (obj.has("friendlyActions")) memory.friendlyActions = obj.get("friendlyActions").getAsInt();
            if (obj.has("firstMetTimestamp")) memory.firstMetTimestamp = obj.get("firstMetTimestamp").getAsLong();
            if (obj.has("lastInteractionTimestamp")) memory.lastInteractionTimestamp = obj.get("lastInteractionTimestamp").getAsLong();
            if (obj.has("notes")) memory.notes = obj.get("notes").getAsString();

            return memory;
        }
    }

    /**
     * Private constructor for singleton pattern.
     */
    private LongTermMemory() {
        this.locations = new ConcurrentHashMap<>();
        this.deathSpots = Collections.synchronizedList(new ArrayList<>());
        this.playerMemories = new ConcurrentHashMap<>();

        // Determine storage path
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.storagePath = configDir.resolve("ai-player").resolve(MEMORY_FILE);

        // Ensure directory exists
        File dir = storagePath.getParent().toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Load existing memory
        load();
    }

    /**
     * Gets the singleton instance of LongTermMemory.
     *
     * @return The LongTermMemory instance
     */
    public static LongTermMemory getInstance() {
        if (instance == null) {
            synchronized (LongTermMemory.class) {
                if (instance == null) {
                    instance = new LongTermMemory();
                }
            }
        }
        return instance;
    }

    // ========== Location Management ==========

    /**
     * Saves a location to memory.
     * If a location with the same ID exists, it will be updated.
     *
     * @param location The location to save
     */
    public void saveLocation(WorldLocation location) {
        if (location == null) return;
        locations.put(location.getId(), location);
        LOGGER.debug("Saved location: {}", location.getName());
    }

    /**
     * Gets a location by ID.
     *
     * @param id The location ID
     * @return Optional containing the location if found
     */
    public Optional<WorldLocation> getLocation(String id) {
        return Optional.ofNullable(locations.get(id));
    }

    /**
     * Gets all saved locations.
     *
     * @return Collection of all locations
     */
    public Collection<WorldLocation> getAllLocations() {
        return Collections.unmodifiableCollection(locations.values());
    }

    /**
     * Gets locations of a specific type.
     *
     * @param type The location type to filter by
     * @return List of matching locations
     */
    public List<WorldLocation> getLocationsByType(WorldLocation.LocationType type) {
        return locations.values().stream()
                .filter(loc -> loc.getType() == type)
                .toList();
    }

    /**
     * Removes a location from memory.
     *
     * @param id The location ID to remove
     * @return The removed location, or null if not found
     */
    public WorldLocation removeLocation(String id) {
        WorldLocation removed = locations.remove(id);
        if (removed != null) {
            LOGGER.debug("Removed location: {}", removed.getName());
        }
        return removed;
    }

    /**
     * Finds the best location for a specific resource.
     * Considers both resource availability and safety.
     *
     * @param resourceName The resource to search for
     * @return Optional containing the best location
     */
    public Optional<WorldLocation> findBestResourceLocation(String resourceName) {
        return locations.values().stream()
                .filter(loc -> loc.hasResource(resourceName))
                .filter(loc -> loc.getType() != WorldLocation.LocationType.DANGER)
                .max(Comparator.comparingDouble(loc -> {
                    double resourceScore = loc.getResourceCount(resourceName) / 100.0;
                    double safetyScore = loc.getSafetyRating();
                    return resourceScore * 0.4 + safetyScore * 0.6;
                }));
    }

    /**
     * Finds the nearest location of a specific type from a position.
     *
     * @param from Starting position
     * @param type Location type to find
     * @return Optional containing the nearest location
     */
    public Optional<WorldLocation> findNearestLocation(Vec3d from, WorldLocation.LocationType type) {
        return locations.values().stream()
                .filter(loc -> loc.getType() == type)
                .min(Comparator.comparingDouble(loc -> loc.distanceFrom(from)));
    }

    /**
     * Finds all locations within a radius of a position.
     *
     * @param from Center position
     * @param radius Search radius in blocks
     * @return List of nearby locations
     */
    public List<WorldLocation> findLocationsNearby(Vec3d from, double radius) {
        return locations.values().stream()
                .filter(loc -> loc.distanceFrom(from) <= radius)
                .toList();
    }

    // ========== Death Spot Management ==========

    /**
     * Records a death at a location.
     *
     * @param position Where the death occurred
     * @param cause Cause of death
     */
    public void recordDeath(Vec3d position, String cause) {
        String normalizedCause = DeathSpot.normalizeCause(cause);

        // Check if there's already a death spot nearby
        synchronized (deathSpots) {
            for (DeathSpot spot : deathSpots) {
                if (spot.isInDangerZone(position)) {
                    spot.incrementDeathCount();
                    LOGGER.debug("Incremented death count at existing spot: {}", spot.getId());
                    return;
                }
            }

            // Create new death spot
            DeathSpot newSpot = new DeathSpot(position, normalizedCause);
            deathSpots.add(newSpot);
            LOGGER.info("Recorded new death spot at ({}, {}, {}): {}",
                    position.x, position.y, position.z, normalizedCause);
        }
    }

    /**
     * Gets all recorded death spots.
     *
     * @return List of all death spots
     */
    public List<DeathSpot> getAllDeathSpots() {
        synchronized (deathSpots) {
            return new ArrayList<>(deathSpots);
        }
    }

    /**
     * Checks if a location is dangerous based on death records.
     *
     * @param position Position to check
     * @return true if the location has recorded deaths nearby
     */
    public boolean isDangerousLocation(Vec3d position) {
        synchronized (deathSpots) {
            for (DeathSpot spot : deathSpots) {
                if (spot.isInDangerZone(position) && spot.calculateDangerScore() > 0.3) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the danger score at a position.
     * Combines danger from all nearby death spots.
     *
     * @param position Position to evaluate
     * @return Danger score from 0.0 (safe) to 1.0 (very dangerous)
     */
    public double getDangerScore(Vec3d position) {
        double maxDanger = 0.0;
        synchronized (deathSpots) {
            for (DeathSpot spot : deathSpots) {
                double danger = spot.calculateDangerAtPosition(position);
                maxDanger = Math.max(maxDanger, danger);
            }
        }
        return maxDanger;
    }

    /**
     * Clears old death spots (older than specified days).
     *
     * @param daysOld Minimum age in days to remove
     */
    public void clearOldDeathSpots(int daysOld) {
        long cutoff = System.currentTimeMillis() - (daysOld * 24L * 60 * 60 * 1000);
        synchronized (deathSpots) {
            deathSpots.removeIf(spot -> spot.getTimestamp() < cutoff);
        }
        LOGGER.debug("Cleared death spots older than {} days", daysOld);
    }

    // ========== Player Memory Management ==========

    /**
     * Gets or creates memory for a player.
     *
     * @param playerId Player UUID
     * @param playerName Player name
     * @return PlayerMemory for this player
     */
    public PlayerMemory getOrCreatePlayerMemory(UUID playerId, String playerName) {
        return playerMemories.computeIfAbsent(playerId,
                id -> new PlayerMemory(id, playerName));
    }

    /**
     * Gets memory for a player if it exists.
     *
     * @param playerId Player UUID
     * @return Optional containing the player memory
     */
    public Optional<PlayerMemory> getPlayerMemory(UUID playerId) {
        return Optional.ofNullable(playerMemories.get(playerId));
    }

    /**
     * Records a hostile action by a player.
     *
     * @param playerId Player UUID
     * @param playerName Player name
     */
    public void recordHostilePlayerAction(UUID playerId, String playerName) {
        PlayerMemory memory = getOrCreatePlayerMemory(playerId, playerName);
        memory.recordHostileAction();
        LOGGER.debug("Recorded hostile action by player: {}", playerName);
    }

    /**
     * Records a friendly action by a player.
     *
     * @param playerId Player UUID
     * @param playerName Player name
     */
    public void recordFriendlyPlayerAction(UUID playerId, String playerName) {
        PlayerMemory memory = getOrCreatePlayerMemory(playerId, playerName);
        memory.recordFriendlyAction();
        LOGGER.debug("Recorded friendly action by player: {}", playerName);
    }

    /**
     * Checks if a player is considered hostile.
     *
     * @param playerId Player UUID
     * @return true if the player is considered hostile
     */
    public boolean isPlayerHostile(UUID playerId) {
        return playerMemories.values().stream()
                .filter(m -> m.getPlayerId().equals(playerId))
                .anyMatch(PlayerMemory::isHostile);
    }

    /**
     * Checks if a player is considered friendly.
     *
     * @param playerId Player UUID
     * @return true if the player is considered friendly
     */
    public boolean isPlayerFriendly(UUID playerId) {
        return playerMemories.values().stream()
                .filter(m -> m.getPlayerId().equals(playerId))
                .anyMatch(PlayerMemory::isFriendly);
    }

    // ========== Persistence ==========

    /**
     * Loads memory from disk.
     */
    public void load() {
        File file = storagePath.toFile();
        if (!file.exists()) {
            LOGGER.info("No existing memory file found. Starting with empty memory.");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(reader, JsonObject.class);

            if (root == null) {
                LOGGER.warn("Memory file is empty or invalid JSON");
                return;
            }

            // Load locations
            if (root.has("locations")) {
                JsonArray locationsArray = root.getAsJsonArray("locations");
                for (int i = 0; i < locationsArray.size(); i++) {
                    try {
                        WorldLocation loc = WorldLocation.fromJson(locationsArray.get(i).getAsJsonObject());
                        locations.put(loc.getId(), loc);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load location at index {}: {}", i, e.getMessage());
                    }
                }
            }

            // Load death spots
            if (root.has("deathSpots")) {
                JsonArray deathArray = root.getAsJsonArray("deathSpots");
                synchronized (deathSpots) {
                    for (int i = 0; i < deathArray.size(); i++) {
                        try {
                            DeathSpot spot = DeathSpot.fromJson(deathArray.get(i).getAsJsonObject());
                            deathSpots.add(spot);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to load death spot at index {}: {}", i, e.getMessage());
                        }
                    }
                }
            }

            // Load player memories
            if (root.has("playerMemories")) {
                JsonArray playerArray = root.getAsJsonArray("playerMemories");
                for (int i = 0; i < playerArray.size(); i++) {
                    try {
                        PlayerMemory memory = PlayerMemory.fromJson(playerArray.get(i).getAsJsonObject());
                        playerMemories.put(memory.getPlayerId(), memory);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load player memory at index {}: {}", i, e.getMessage());
                    }
                }
            }

            LOGGER.info("Loaded {} locations, {} death spots, {} player memories",
                    locations.size(), deathSpots.size(), playerMemories.size());

        } catch (IOException e) {
            LOGGER.error("Failed to load memory file: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error parsing memory file: {}", e.getMessage());
        }
    }

    /**
     * Saves memory to disk.
     */
    public void save() {
        JsonObject root = new JsonObject();

        // Save locations
        JsonArray locationsArray = new JsonArray();
        for (WorldLocation loc : locations.values()) {
            locationsArray.add(loc.toJson());
        }
        root.add("locations", locationsArray);

        // Save death spots
        JsonArray deathArray = new JsonArray();
        synchronized (deathSpots) {
            for (DeathSpot spot : deathSpots) {
                deathArray.add(spot.toJson());
            }
        }
        root.add("deathSpots", deathArray);

        // Save player memories
        JsonArray playerArray = new JsonArray();
        for (PlayerMemory memory : playerMemories.values()) {
            playerArray.add(memory.toJson());
        }
        root.add("playerMemories", playerArray);

        // Write to file
        try {
            File file = storagePath.toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(root, writer);
            }

            LOGGER.debug("Saved memory to {}", storagePath);

        } catch (IOException e) {
            LOGGER.error("Failed to save memory file: {}", e.getMessage());
        }
    }

    /**
     * Clears all memory data.
     */
    public void clear() {
        locations.clear();
        synchronized (deathSpots) {
            deathSpots.clear();
        }
        playerMemories.clear();
        LOGGER.info("Cleared all memory data");
    }

    /**
     * Gets statistics about memory usage.
     */
    public MemoryStats getStats() {
        return new MemoryStats(
                locations.size(),
                getLocationsByType(WorldLocation.LocationType.BASE).size(),
                getLocationsByType(WorldLocation.LocationType.RESOURCE).size(),
                getLocationsByType(WorldLocation.LocationType.DANGER).size(),
                getLocationsByType(WorldLocation.LocationType.INTERESTING).size(),
                deathSpots.size(),
                playerMemories.size()
        );
    }

    /**
     * Memory statistics container.
     */
    public record MemoryStats(
            int totalLocations,
            int baseCount,
            int resourceCount,
            int dangerCount,
            int interestingCount,
            int deathSpotCount,
            int playerMemoryCount
    ) {
        @Override
        public String toString() {
            return String.format("MemoryStats{locations=%d (bases=%d, resources=%d, danger=%d, interesting=%d), deaths=%d, players=%d}",
                    totalLocations, baseCount, resourceCount, dangerCount, interestingCount, deathSpotCount, playerMemoryCount);
        }
    }
}