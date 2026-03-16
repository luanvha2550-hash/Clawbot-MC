package net.shasankp000.Entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks and follows the main player (the user controlling the bot).
 * Provides position tracking, distance calculation, and follow behavior.
 */
public class PlayerTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-tracker");

    // Configuration
    private static final double DEFAULT_FOLLOW_DISTANCE = 3.0;  // Blocks behind player
    private static final double MAX_FOLLOW_DISTANCE = 50.0;      // Maximum distance to follow
    private static final double TELEPORT_DISTANCE = 100.0;        // Distance to teleport if too far
    private static final int POSITION_HISTORY_SIZE = 10;         // Number of positions to remember

    // State
    private UUID ownerUUID;                    // The player who owns/spawned the bot
    private Vec3d lastKnownPlayerPos;          // Last known position of the player
    private Vec3d playerVelocity;              // Estimated player velocity
    private long lastUpdateTick;               // Last tick we updated position
    private final PositionHistory positionHistory;

    /**
     * Represents a player's position at a point in time
     */
    public static class PlayerPosition {
        public final double x, y, z;
        public final long tick;
        public final float yaw, pitch;

        public PlayerPosition(double x, double y, double z, long tick, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.tick = tick;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Vec3d toVec3d() {
            return new Vec3d(x, y, z);
        }

        public double distanceTo(PlayerPosition other) {
            return Math.sqrt(
                (x - other.x) * (x - other.x) +
                (y - other.y) * (y - other.y) +
                (z - other.z) * (z - other.z)
            );
        }
    }

    /**
     * Circular buffer for position history
     */
    private static class PositionHistory {
        private final PlayerPosition[] positions;
        private int head = 0;
        private int count = 0;

        public PositionHistory(int size) {
            this.positions = new PlayerPosition[size];
        }

        public void add(PlayerPosition pos) {
            positions[head] = pos;
            head = (head + 1) % positions.length;
            if (count < positions.length) count++;
        }

        public PlayerPosition getLatest() {
            if (count == 0) return null;
            int latestIndex = (head - 1 + positions.length) % positions.length;
            return positions[latestIndex];
        }

        public PlayerPosition[] getAll() {
            PlayerPosition[] result = new PlayerPosition[count];
            for (int i = 0; i < count; i++) {
                int index = (head - 1 - i + positions.length) % positions.length;
                result[i] = positions[index];
            }
            return result;
        }

        public void clear() {
            head = 0;
            count = 0;
        }
    }

    /**
     * Result of tracking operation
     */
    public static class TrackingResult {
        public final boolean found;
        public final double distance;
        public final double horizontalDistance;
        public final double verticalDistance;
        public final Vec3d direction;          // Normalized vector from bot to player
        public final Vec3d playerPosition;
        public final Vec3d predictedPosition;  // Predicted future position
        public final String compassDirection;   // N, NE, E, SE, S, SW, W, NW

        public TrackingResult(boolean found, double distance, double horizontalDistance,
                             double verticalDistance, Vec3d direction, Vec3d playerPosition,
                             Vec3d predictedPosition, String compassDirection) {
            this.found = found;
            this.distance = distance;
            this.horizontalDistance = horizontalDistance;
            this.verticalDistance = verticalDistance;
            this.direction = direction;
            this.playerPosition = playerPosition;
            this.predictedPosition = predictedPosition;
            this.compassDirection = compassDirection;
        }

        public static TrackingResult notFound() {
            return new TrackingResult(false, -1, -1, -1, null, null, null, "UNKNOWN");
        }
    }

    public PlayerTracker() {
        this.positionHistory = new PositionHistory(POSITION_HISTORY_SIZE);
        this.lastUpdateTick = 0;
    }

    /**
     * Set the owner of the bot (the player to track)
     */
    public void setOwner(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        LOGGER.info("🎯 PlayerTracker owner set to: {}", ownerUUID);
    }

    /**
     * Get the owner UUID
     */
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    /**
     * Update player position from the world
     * Call this every tick from the bot's event handler
     */
    public void updatePlayerPosition(World world, long currentTick) {
        if (ownerUUID == null) return;

        PlayerEntity player = findPlayerByUUID(world, ownerUUID);
        if (player == null) return;

        PlayerPosition currentPos = new PlayerPosition(
            player.getX(), player.getY(), player.getZ(),
            currentTick,
            player.getYaw(), player.getPitch()
        );

        // Calculate velocity based on position history
        PlayerPosition previousPos = positionHistory.getLatest();
        if (previousPos != null && currentTick > previousPos.tick) {
            long tickDelta = currentTick - previousPos.tick;
            if (tickDelta > 0) {
                playerVelocity = new Vec3d(
                    (currentPos.x - previousPos.x) / tickDelta,
                    (currentPos.y - previousPos.y) / tickDelta,
                    (currentPos.z - previousPos.z) / tickDelta
                );
            }
        }

        positionHistory.add(currentPos);
        lastKnownPlayerPos = currentPos.toVec3d();
        lastUpdateTick = currentTick;
    }

    /**
     * Get tracking result from bot's position
     */
    public TrackingResult track(Vec3d botPosition, long currentTick) {
        if (lastKnownPlayerPos == null) {
            return TrackingResult.notFound();
        }

        double dx = lastKnownPlayerPos.x - botPosition.x;
        double dy = lastKnownPlayerPos.y - botPosition.y;
        double dz = lastKnownPlayerPos.z - botPosition.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double verticalDistance = Math.abs(dy);

        // Calculate direction (normalized)
        Vec3d direction = distance > 0 ? new Vec3d(dx / distance, dy / distance, dz / distance) : Vec3d.ZERO;

        // Predict future position based on velocity
        Vec3d predictedPosition = lastKnownPlayerPos;
        if (playerVelocity != null) {
            // Predict 1-2 seconds ahead (20-40 ticks)
            int predictionTicks = 20;
            predictedPosition = lastKnownPlayerPos.add(playerVelocity.multiply(predictionTicks));
        }

        // Calculate compass direction
        String compassDirection = calculateCompassDirection(dx, dz);

        return new TrackingResult(
            true, distance, horizontalDistance, verticalDistance,
            direction, lastKnownPlayerPos, predictedPosition, compassDirection
        );
    }

    /**
     * Calculate compass direction from dx and dz offsets
     */
    private String calculateCompassDirection(double dx, double dz) {
        // Angle in degrees: 0 = North, 90 = East, 180 = South, 270 = West
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;

        // Convert to compass direction
        if (angle >= 337.5 || angle < 22.5) return "N";
        if (angle >= 22.5 && angle < 67.5) return "NE";
        if (angle >= 67.5 && angle < 112.5) return "E";
        if (angle >= 112.5 && angle < 157.5) return "SE";
        if (angle >= 157.5 && angle < 202.5) return "S";
        if (angle >= 202.5 && angle < 247.5) return "SW";
        if (angle >= 247.5 && angle < 292.5) return "W";
        return "NW";
    }

    /**
     * Check if bot should follow the player
     */
    public boolean shouldFollow(double currentDistance) {
        return currentDistance > DEFAULT_FOLLOW_DISTANCE && currentDistance < MAX_FOLLOW_DISTANCE;
    }

    /**
     * Check if bot should teleport to player
     */
    public boolean shouldTeleport(double currentDistance) {
        return currentDistance > TELEPORT_DISTANCE;
    }

    /**
     * Get the follow position (a position behind the player)
     */
    public Vec3d getFollowPosition(Vec3d playerPos, float playerYaw) {
        // Calculate position 3 blocks behind the player based on their look direction
        double radians = Math.toRadians(playerYaw);
        double offsetX = -Math.sin(radians) * DEFAULT_FOLLOW_DISTANCE;
        double offsetZ = Math.cos(radians) * DEFAULT_FOLLOW_DISTANCE;

        return new Vec3d(
            playerPos.x + offsetX,
            playerPos.y,
            playerPos.z + offsetZ
        );
    }

    /**
     * Find a player by UUID in the world
     */
    private PlayerEntity findPlayerByUUID(World world, UUID uuid) {
        if (world == null) return null;

        List<? extends PlayerEntity> players = world.getPlayers();
        for (PlayerEntity player : players) {
            if (player.getUuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    /**
     * Get the last known player position
     */
    public Vec3d getLastKnownPosition() {
        return lastKnownPlayerPos;
    }

    /**
     * Get ticks since last update
     */
    public long getTicksSinceUpdate(long currentTick) {
        return currentTick - lastUpdateTick;
    }

    /**
     * Check if player tracking is active
     */
    public boolean isActive() {
        return ownerUUID != null && lastKnownPlayerPos != null;
    }

    /**
     * Clear all tracking state
     */
    public void clear() {
        ownerUUID = null;
        lastKnownPlayerPos = null;
        playerVelocity = null;
        lastUpdateTick = 0;
        positionHistory.clear();
    }
}