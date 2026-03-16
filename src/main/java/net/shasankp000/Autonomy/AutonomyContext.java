package net.shasankp000.Autonomy;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Snapshot of the current world state for decision making.
 *
 * <p>Immutable context captured each tick for decision layers.</p>
 */
public class AutonomyContext {

    // ========== Bot State ==========
    private final Vec3d botPosition;
    private final Vec3d botVelocity;
    private final float botHealth;
    private final float botMaxHealth;
    private final int botHunger;
    private final int botAir;
    private final float botYaw;
    private final float botPitch;
    private final long worldTick;
    private final String currentBiome;
    private final boolean isDaytime;

    // ========== Environment ==========
    private final List<Entity> nearbyEntities;
    private final List<Entity> hostileEntities;
    private final Map<String, Integer> nearbyBlocks;
    private final boolean inWater;
    private final boolean inLava;
    private final boolean onFire;
    private final boolean falling;

    // ========== Inventory ==========
    private final InventorySnapshot inventory;

    // ========== Owner (Player) ==========
    private final Vec3d ownerPosition;
    private final UUID ownerUUID;
    private final double distanceToOwner;
    private final boolean ownerNearby;

    // ========== Computed ==========
    private List<Double> situationEmbedding;
    private String situationDescription;

    // ========== State Flags ==========
    private final boolean hasImmediateDanger;
    private final boolean hasHostileThreats;
    private final boolean hasPendingCommand;
    private final boolean hasAutoGoal;
    private final boolean isUnderwater;

    // ========== Current Goal ==========
    private final String currentGoalId;
    private final GoalType currentGoalType;

    /**
     * Goal types
     */
    public enum GoalType {
        NONE,
        GATHER_RESOURCE,
        BUILD_STRUCTURE,
        CRAFT_ITEM,
        EXPLORE_AREA,
        FOLLOW_PLAYER,
        DEFEND_LOCATION,
        FARM_RESOURCE
    }

    // ========== Builder Pattern ==========

    public static class Builder {
        private Vec3d botPosition = Vec3d.ZERO;
        private Vec3d botVelocity = Vec3d.ZERO;
        private float botHealth = 20.0f;
        private float botMaxHealth = 20.0f;
        private int botHunger = 20;
        private int botAir = 300;
        private float botYaw = 0.0f;
        private float botPitch = 0.0f;
        private long worldTick = 0;
        private String currentBiome = "unknown";
        private boolean isDaytime = true;

        private List<Entity> nearbyEntities = new ArrayList<>();
        private List<Entity> hostileEntities = new ArrayList<>();
        private Map<String, Integer> nearbyBlocks = new HashMap<>();
        private boolean inWater = false;
        private boolean inLava = false;
        private boolean onFire = false;
        private boolean falling = false;

        private InventorySnapshot inventory = new InventorySnapshot();

        private Vec3d ownerPosition = null;
        private UUID ownerUUID = null;
        private double distanceToOwner = -1;
        private boolean ownerNearby = false;

        private boolean hasPendingCommand = false;
        private String currentGoalId = null;
        private GoalType currentGoalType = GoalType.NONE;

        public Builder botPosition(Vec3d pos) {
            this.botPosition = pos;
            return this;
        }

        public Builder botVelocity(Vec3d velocity) {
            this.botVelocity = velocity;
            return this;
        }

        public Builder botHealth(float health) {
            this.botHealth = health;
            return this;
        }

        public Builder botMaxHealth(float maxHealth) {
            this.botMaxHealth = maxHealth;
            return this;
        }

        public Builder botHunger(int hunger) {
            this.botHunger = hunger;
            return this;
        }

        public Builder botAir(int air) {
            this.botAir = air;
            return this;
        }

        public Builder rotation(float yaw, float pitch) {
            this.botYaw = yaw;
            this.botPitch = pitch;
            return this;
        }

        public Builder worldTick(long tick) {
            this.worldTick = tick;
            return this;
        }

        public Builder biome(String biome) {
            this.currentBiome = biome;
            return this;
        }

        public Builder daytime(boolean isDay) {
            this.isDaytime = isDay;
            return this;
        }

        public Builder nearbyEntities(List<Entity> entities) {
            this.nearbyEntities = entities != null ? entities : new ArrayList<>();
            return this;
        }

        public Builder hostileEntities(List<Entity> entities) {
            this.hostileEntities = entities != null ? entities : new ArrayList<>();
            return this;
        }

        public Builder nearbyBlocks(Map<String, Integer> blocks) {
            this.nearbyBlocks = blocks != null ? blocks : new HashMap<>();
            return this;
        }

        public Builder inWater(boolean inWater) {
            this.inWater = inWater;
            return this;
        }

        public Builder inLava(boolean inLava) {
            this.inLava = inLava;
            return this;
        }

        public Builder onFire(boolean onFire) {
            this.onFire = onFire;
            return this;
        }

        public Builder falling(boolean falling) {
            this.falling = falling;
            return this;
        }

        public Builder inventory(InventorySnapshot inventory) {
            this.inventory = inventory != null ? inventory : new InventorySnapshot();
            return this;
        }

        public Builder ownerPosition(Vec3d pos) {
            this.ownerPosition = pos;
            this.ownerNearby = pos != null;
            return this;
        }

        public Builder ownerUUID(UUID uuid) {
            this.ownerUUID = uuid;
            return this;
        }

        public Builder distanceToOwner(double distance) {
            this.distanceToOwner = distance;
            return this;
        }

        public Builder pendingCommand(boolean hasCommand) {
            this.hasPendingCommand = hasCommand;
            return this;
        }

        public Builder currentGoal(String goalId, GoalType type) {
            this.currentGoalId = goalId;
            this.currentGoalType = type;
            return this;
        }

        public AutonomyContext build() {
            return new AutonomyContext(this);
        }
    }

    // ========== Constructor (private, use Builder) ==========

    private AutonomyContext(Builder b) {
        this.botPosition = b.botPosition;
        this.botVelocity = b.botVelocity;
        this.botHealth = b.botHealth;
        this.botMaxHealth = b.botMaxHealth;
        this.botHunger = b.botHunger;
        this.botAir = b.botAir;
        this.botYaw = b.botYaw;
        this.botPitch = b.botPitch;
        this.worldTick = b.worldTick;
        this.currentBiome = b.currentBiome;
        this.isDaytime = b.isDaytime;

        this.nearbyEntities = Collections.unmodifiableList(b.nearbyEntities);
        this.hostileEntities = Collections.unmodifiableList(b.hostileEntities);
        this.nearbyBlocks = Collections.unmodifiableMap(b.nearbyBlocks);
        this.inWater = b.inWater;
        this.inLava = b.inLava;
        this.onFire = b.onFire;
        this.falling = b.falling;

        this.inventory = b.inventory;

        this.ownerPosition = b.ownerPosition;
        this.ownerUUID = b.ownerUUID;
        this.distanceToOwner = b.distanceToOwner;
        this.ownerNearby = b.ownerNearby;

        this.hasPendingCommand = b.hasPendingCommand;
        this.currentGoalId = b.currentGoalId;
        this.currentGoalType = b.currentGoalType;

        // Computed flags
        this.hasImmediateDanger = inLava || onFire || (botAir < 30 && inWater) ||
                                   (falling && botPosition.y < -50);
        this.hasHostileThreats = !hostileEntities.isEmpty();
        this.isUnderwater = inWater && botAir < 280;
        this.hasAutoGoal = currentGoalType != GoalType.NONE;
    }

    // ========== Getters ==========

    // Bot state
    public Vec3d getBotPosition() { return botPosition; }
    public Vec3d getBotVelocity() { return botVelocity; }
    public float getBotHealth() { return botHealth; }
    public float getBotMaxHealth() { return botMaxHealth; }
    public int getBotHunger() { return botHunger; }
    public int getBotAir() { return botAir; }
    public float getBotYaw() { return botYaw; }
    public float getBotPitch() { return botPitch; }
    public long getWorldTick() { return worldTick; }
    public String getCurrentBiome() { return currentBiome; }
    public boolean isDaytime() { return isDaytime; }

    // Environment
    public List<Entity> getNearbyEntities() { return nearbyEntities; }
    public List<Entity> getHostileEntities() { return hostileEntities; }
    public Map<String, Integer> getNearbyBlocks() { return nearbyBlocks; }
    public boolean isInWater() { return inWater; }
    public boolean isInLava() { return inLava; }
    public boolean isOnFire() { return onFire; }
    public boolean isFalling() { return falling; }

    // Inventory
    public InventorySnapshot getInventory() { return inventory; }

    // Owner
    public Vec3d getOwnerPosition() { return ownerPosition; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public double getDistanceToOwner() { return distanceToOwner; }
    public boolean hasOwnerNearby() { return ownerNearby; }

    // State flags
    public boolean hasImmediateDanger() { return hasImmediateDanger; }
    public boolean hasHostileThreats() { return hasHostileThreats; }
    public boolean hasPendingCommand() { return hasPendingCommand; }
    public boolean hasAutoGoal() { return hasAutoGoal; }
    public boolean isUnderwater() { return isUnderwater; }

    // Goal
    public String getCurrentGoalId() { return currentGoalId; }
    public GoalType getCurrentGoalType() { return currentGoalType; }

    // Computed
    public List<Double> getSituationEmbedding() { return situationEmbedding; }
    public void setSituationEmbedding(List<Double> embedding) { this.situationEmbedding = embedding; }

    /**
     * Get a human-readable description of the current situation.
     */
    public String getSituationDescription() {
        if (situationDescription != null) {
            return situationDescription;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Health: %.0f/%.0f, Hunger: %d/20",
            botHealth, botMaxHealth, botHunger));

        if (!hostileEntities.isEmpty()) {
            sb.append(String.format(", Hostiles: %d", hostileEntities.size()));
        }

        if (inLava) sb.append(", IN_LAVA");
        else if (inWater) sb.append(", IN_WATER");
        else if (onFire) sb.append(", ON_FIRE");

        sb.append(", Biome: ").append(currentBiome);

        situationDescription = sb.toString();
        return situationDescription;
    }

    /**
     * Get health as a percentage (0.0 to 1.0).
     */
    public float getHealthPercent() {
        return botMaxHealth > 0 ? botHealth / botMaxHealth : 0.0f;
    }

    /**
     * Check if health is critical (below 30%).
     */
    public boolean isHealthCritical() {
        return getHealthPercent() < 0.3f;
    }

    /**
     * Check if hunger is critical (below 6 bars).
     */
    public boolean isHungerCritical() {
        return botHunger < 6;
    }
}