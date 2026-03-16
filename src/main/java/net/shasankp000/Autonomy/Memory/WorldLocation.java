package net.shasankp000.Autonomy.Memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonParseException;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a named location in the world with metadata.
 *
 * <p>WorldLocations are stored in LongTermMemory and persist across
 * game sessions. They can represent bases, resource deposits, dangerous
 * areas, or points of interest.</p>
 *
 * <h2>Location Types:</h2>
 * <ul>
 *   <li>BASE - A safe location the bot considers home</li>
 *   <li>RESOURCE - A location with valuable resources</li>
 *   <li>DANGER - A hazardous area to avoid</li>
 *   <li>INTERESTING - A point of interest worth revisiting</li>
 * </ul>
 */
public class WorldLocation {

    /**
     * Unique identifier for this location
     */
    private final String id;

    /**
     * Human-readable name for this location
     */
    private final String name;

    /**
     * World position coordinates
     */
    private final Vec3d position;

    /**
     * Classification of this location type
     */
    private final LocationType type;

    /**
     * Resources available at this location (resource name -> estimated count)
     */
    private final Map<String, Integer> resources;

    /**
     * Safety rating from 0.0 (extremely dangerous) to 1.0 (completely safe)
     */
    private double safetyRating;

    /**
     * Game tick when this location was last visited
     */
    private long lastVisited;

    /**
     * World dimension identifier (for multi-world support)
     */
    private String dimensionId;

    /**
     * Location type classification
     */
    public enum LocationType {
        BASE,
        RESOURCE,
        DANGER,
        INTERESTING
    }

    /**
     * Creates a new WorldLocation.
     *
     * @param id Unique identifier
     * @param name Human-readable name
     * @param position World coordinates
     * @param type Location classification
     */
    public WorldLocation(String id, String name, Vec3d position, LocationType type) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.type = type;
        this.resources = new HashMap<>();
        this.safetyRating = type == LocationType.DANGER ? 0.1 : (type == LocationType.BASE ? 0.95 : 0.7);
        this.lastVisited = System.currentTimeMillis();
        this.dimensionId = "minecraft:overworld";
    }

    /**
     * Full constructor with all fields.
     */
    public WorldLocation(String id, String name, Vec3d position, LocationType type,
                         Map<String, Integer> resources, double safetyRating,
                         long lastVisited, String dimensionId) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.type = type;
        this.resources = resources != null ? resources : new HashMap<>();
        this.safetyRating = safetyRating;
        this.lastVisited = lastVisited;
        this.dimensionId = dimensionId != null ? dimensionId : "minecraft:overworld";
    }

    // ========== Getters ==========

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Vec3d getPosition() {
        return position;
    }

    public LocationType getType() {
        return type;
    }

    public Map<String, Integer> getResources() {
        return resources;
    }

    public double getSafetyRating() {
        return safetyRating;
    }

    public long getLastVisited() {
        return lastVisited;
    }

    public String getDimensionId() {
        return dimensionId;
    }

    // ========== Setters ==========

    /**
     * Sets the safety rating.
     * @param rating Safety rating from 0.0 to 1.0
     */
    public void setSafetyRating(double rating) {
        this.safetyRating = Math.max(0.0, Math.min(1.0, rating));
    }

    /**
     * Updates the last visited timestamp to now.
     */
    public void updateLastVisited() {
        this.lastVisited = System.currentTimeMillis();
    }

    /**
     * Sets the last visited timestamp.
     */
    public void setLastVisited(long timestamp) {
        this.lastVisited = timestamp;
    }

    /**
     * Adds or updates a resource at this location.
     *
     * @param resourceName Name of the resource
     * @param count Estimated count
     */
    public void addResource(String resourceName, int count) {
        resources.put(resourceName, count);
    }

    /**
     * Removes a resource from this location.
     *
     * @param resourceName Name of the resource to remove
     */
    public void removeResource(String resourceName) {
        resources.remove(resourceName);
    }

    /**
     * Checks if this location has a specific resource.
     */
    public boolean hasResource(String resourceName) {
        return resources.containsKey(resourceName);
    }

    /**
     * Gets the estimated count of a resource, or 0 if not present.
     */
    public int getResourceCount(String resourceName) {
        return resources.getOrDefault(resourceName, 0);
    }

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
     * Checks if this location is dangerous.
     */
    public boolean isDangerous() {
        return type == LocationType.DANGER || safetyRating < 0.3;
    }

    /**
     * Checks if this location is considered safe.
     */
    public boolean isSafe() {
        return safetyRating > 0.7;
    }

    // ========== JSON Serialization ==========

    /**
     * Converts this location to a JSON object.
     */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);

        // Position as nested object
        JsonObject posObj = new JsonObject();
        posObj.addProperty("x", position.x);
        posObj.addProperty("y", position.y);
        posObj.addProperty("z", position.z);
        obj.add("position", posObj);

        obj.addProperty("type", type.name());
        obj.addProperty("safetyRating", safetyRating);
        obj.addProperty("lastVisited", lastVisited);
        obj.addProperty("dimensionId", dimensionId);

        // Resources
        JsonObject resourcesObj = new JsonObject();
        for (Map.Entry<String, Integer> entry : resources.entrySet()) {
            resourcesObj.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("resources", resourcesObj);

        return obj;
    }

    /**
     * Creates a WorldLocation from a JSON object.
     */
    public static WorldLocation fromJson(JsonObject obj) {
        String id = obj.get("id").getAsString();
        String name = obj.get("name").getAsString();

        JsonObject posObj = obj.getAsJsonObject("position");
        double x = posObj.get("x").getAsDouble();
        double y = posObj.get("y").getAsDouble();
        double z = posObj.get("z").getAsDouble();
        Vec3d position = new Vec3d(x, y, z);

        LocationType type = LocationType.valueOf(obj.get("type").getAsString());

        Map<String, Integer> resources = new HashMap<>();
        if (obj.has("resources")) {
            JsonObject resourcesObj = obj.getAsJsonObject("resources");
            for (Map.Entry<String, JsonElement> entry : resourcesObj.entrySet()) {
                resources.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }

        double safetyRating = obj.has("safetyRating") ? obj.get("safetyRating").getAsDouble() : 0.7;
        long lastVisited = obj.has("lastVisited") ? obj.get("lastVisited").getAsLong() : System.currentTimeMillis();
        String dimensionId = obj.has("dimensionId") ? obj.get("dimensionId").getAsString() : "minecraft:overworld";

        return new WorldLocation(id, name, position, type, resources, safetyRating, lastVisited, dimensionId);
    }

    /**
     * Generates a unique ID for a new location.
     */
    public static String generateId(LocationType type, String name) {
        return type.name().toLowerCase() + "_" + name.toLowerCase().replace(" ", "_") + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public String toString() {
        return String.format("WorldLocation{id=%s, name=%s, type=%s, pos=(%.1f, %.1f, %.1f), safety=%.2f}",
                id, name, type, position.x, position.y, position.z, safetyRating);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        WorldLocation other = (WorldLocation) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}