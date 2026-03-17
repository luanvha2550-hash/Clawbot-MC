package net.luanvha2550_hash.Autonomy.Memory;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.util.math.Vec3d;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a recorded death location for danger avoidance learning.
 *
 * <p>DeathSpots track where and why the bot died, enabling the autonomy
 * system to avoid dangerous areas. Multiple deaths at the same location
 * increase its danger rating.</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * // Record a death event
 * LongTermMemory memory = LongTermMemory.getInstance();
 * memory.recordDeath(bot.getPos(), "lava");
 *
 * // Check if a location is dangerous
 * if (memory.isDangerousLocation(nearbyPos)) {
 *     // Avoid this area
 * }
 * }</pre>
 */
public class DeathSpot {

    /**
     * Unique identifier for this death record
     */
    private final String id;

    /**
     * World position where death occurred
     */
    private final Vec3d position;

    /**
     * Cause of death (e.g., "lava", "zombie", "fall_damage")
     */
    private final String cause;

    /**
     * Timestamp when the death occurred (epoch milliseconds)
     */
    private final long timestamp;

    /**
     * Number of deaths at this approximate location
     */
    private int deathCount;

    /**
     * World dimension identifier
     */
    private final String dimensionId;

    /**
     * Radius in blocks to consider as the "danger zone"
     */
    private static final double DANGER_RADIUS = 10.0;

    /**
     * Creates a new DeathSpot record.
     *
     * @param position World coordinates of death
     * @param cause Cause of death
     */
    public DeathSpot(Vec3d position, String cause) {
        this.id = generateId(position);
        this.position = position;
        this.cause = cause != null ? cause : "unknown";
        this.timestamp = System.currentTimeMillis();
        this.deathCount = 1;
        this.dimensionId = "minecraft:overworld";
    }

    /**
     * Full constructor for loading from storage.
     */
    public DeathSpot(String id, Vec3d position, String cause, long timestamp, int deathCount, String dimensionId) {
        this.id = id;
        this.position = position;
        this.cause = cause != null ? cause : "unknown";
        this.timestamp = timestamp;
        this.deathCount = deathCount;
        this.dimensionId = dimensionId != null ? dimensionId : "minecraft:overworld";
    }

    // ========== Getters ==========

    public String getId() {
        return id;
    }

    public Vec3d getPosition() {
        return position;
    }

    public String getCause() {
        return cause;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getDeathCount() {
        return deathCount;
    }

    public String getDimensionId() {
        return dimensionId;
    }

    // ========== Mutators ==========

    /**
     * Increments the death count for this location.
     */
    public void incrementDeathCount() {
        this.deathCount++;
    }

    // ========== Utility Methods ==========

    /**
     * Calculates distance from a given position.
     *
     * @param from The starting position
     * @return Distance in blocks
     */
    public double distanceFrom(Vec3d from) {
        return position.distanceTo(from);
    }

    /**
     * Checks if a position is within the danger radius of this death spot.
     *
     * @param pos Position to check
     * @return true if within danger radius
     */
    public boolean isInDangerZone(Vec3d pos) {
        return distanceFrom(pos) <= DANGER_RADIUS;
    }

    /**
     * Gets the danger radius for this death spot.
     */
    public static double getDangerRadius() {
        return DANGER_RADIUS;
    }

    /**
     * Calculates a danger score for this location.
     * Higher death counts and more recent deaths result in higher scores.
     *
     * @return Danger score from 0.0 to 1.0
     */
    public double calculateDangerScore() {
        // Base score from death count (capped at 10 deaths = max score)
        double countScore = Math.min(deathCount / 10.0, 1.0);

        // Age factor - older deaths are slightly less dangerous
        long ageMs = System.currentTimeMillis() - timestamp;
        long dayMs = 24 * 60 * 60 * 1000L;
        double ageFactor = Math.max(0.5, 1.0 - (ageMs / (7.0 * dayMs))); // Decays over a week

        return countScore * ageFactor;
    }

    /**
     * Calculates danger score at a specific position.
     * Accounts for distance from the death spot center.
     *
     * @param pos Position to evaluate
     * @return Danger score from 0.0 to 1.0
     */
    public double calculateDangerAtPosition(Vec3d pos) {
        double distance = distanceFrom(pos);
        if (distance > DANGER_RADIUS) {
            return 0.0;
        }

        double baseScore = calculateDangerScore();
        // Linear falloff from center
        double distanceFactor = 1.0 - (distance / DANGER_RADIUS);

        return baseScore * distanceFactor;
    }

    // ========== JSON Serialization ==========

    /**
     * Converts this death spot to a JSON object.
     */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);

        // Position
        JsonObject posObj = new JsonObject();
        posObj.addProperty("x", position.x);
        posObj.addProperty("y", position.y);
        posObj.addProperty("z", position.z);
        obj.add("position", posObj);

        obj.addProperty("cause", cause);
        obj.addProperty("timestamp", timestamp);
        obj.addProperty("deathCount", deathCount);
        obj.addProperty("dimensionId", dimensionId);

        return obj;
    }

    /**
     * Creates a DeathSpot from a JSON object.
     */
    public static DeathSpot fromJson(JsonObject obj) {
        String id = obj.get("id").getAsString();

        JsonObject posObj = obj.getAsJsonObject("position");
        double x = posObj.get("x").getAsDouble();
        double y = posObj.get("y").getAsDouble();
        double z = posObj.get("z").getAsDouble();
        Vec3d position = new Vec3d(x, y, z);

        String cause = obj.get("cause").getAsString();
        long timestamp = obj.get("timestamp").getAsLong();
        int deathCount = obj.has("deathCount") ? obj.get("deathCount").getAsInt() : 1;
        String dimensionId = obj.has("dimensionId") ? obj.get("dimensionId").getAsString() : "minecraft:overworld";

        return new DeathSpot(id, position, cause, timestamp, deathCount, dimensionId);
    }

    /**
     * Generates a unique ID for a death spot based on rounded position.
     */
    public static String generateId(Vec3d position) {
        // Round to block coordinates to group nearby deaths
        int x = (int) Math.floor(position.x);
        int y = (int) Math.floor(position.y);
        int z = (int) Math.floor(position.z);
        return "death_" + x + "_" + y + "_" + z;
    }

    /**
     * Gets a human-readable cause from a damage source.
     */
    public static String normalizeCause(String rawCause) {
        if (rawCause == null) return "unknown";

        // Normalize common causes
        rawCause = rawCause.toLowerCase();

        if (rawCause.contains("lava") || rawCause.contains("fire")) return "lava_fire";
        if (rawCause.contains("fall")) return "fall_damage";
        if (rawCause.contains("drown") || rawCause.contains("water")) return "drowning";
        if (rawCause.contains("explosion") || rawCause.contains("tnt")) return "explosion";
        if (rawCause.contains("zombie") || rawCause.contains("skeleton") || rawCause.contains("spider")
                || rawCause.contains("creeper") || rawCause.contains("enderman")) return "hostile_mob";
        if (rawCause.contains("player")) return "player";
        if (rawCause.contains("starve") || rawCause.contains("hunger")) return "starvation";
        if (rawCause.contains("void")) return "void";

        return rawCause.replace(" ", "_");
    }

    @Override
    public String toString() {
        return String.format("DeathSpot{id=%s, pos=(%.1f, %.1f, %.1f), cause=%s, deaths=%d}",
                id, position.x, position.y, position.z, cause, deathCount);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DeathSpot other = (DeathSpot) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}