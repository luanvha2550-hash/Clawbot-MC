package net.luanvha2550_hash.Autonomy.Layers;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.luanvha2550_hash.Autonomy.ActionResult;
import net.luanvha2550_hash.Autonomy.AutonomyContext;
import net.luanvha2550_hash.Autonomy.DecisionLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Idle Layer - Default behavior when nothing else to do.
 *
 * <p>This layer has the LOWEST priority (5) and only activates when
 * all other layers have no actions. It handles passive behaviors.</p>
 *
 * <h3>Behaviors (in priority order):</h3>
 * <ol>
 *   <li>Follow owner if nearby and distance > FOLLOW_DISTANCE</li>
 *   <li>Observe player behavior patterns periodically</li>
 *   <li>Patrol nearby area if owner not nearby</li>
 *   <li>Return to patrol center if too far</li>
 * </ol>
 *
 * <h3>State tracked:</h3>
 * <ul>
 *   <li>patrolCenter - Center point for patrol behavior</li>
 *   <li>lastObservationTick - Last tick player was observed</li>
 *   <li>currentPatrolTarget - Current patrol destination</li>
 * </ul>
 */
public class IdleLayer implements DecisionLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger("idle-layer");

    // Configuration constants
    private static final int PRIORITY = 5; // Lowest priority
    private static final double FOLLOW_DISTANCE = 8.0; // Blocks - start following when farther
    private static final double CLOSE_DISTANCE = 5.0; // Blocks - stop following when this close
    private static final double PATROL_RADIUS = 16.0; // Blocks - patrol radius
    private static final int OBSERVATION_INTERVAL = 20; // Ticks between observations
    private static final double MAX_PATROL_DISTANCE = 32.0; // Max distance from patrol center

    // Bot reference
    private final ServerPlayerEntity bot;

    // State tracking
    private Vec3d patrolCenter = null;
    private long lastObservationTick = 0;
    private Vec3d currentPatrolTarget = null;
    private final Random random = new Random();

    /**
     * Create a new IdleLayer.
     *
     * @param bot The AI player entity
     */
    public IdleLayer(ServerPlayerEntity bot) {
        this.bot = bot;
    }

    @Override
    public String getLayerName() {
        return "Idle";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /**
     * Set the patrol center. Call this when the bot should patrol around a specific location.
     *
     * @param center The center point for patrol behavior
     */
    public void setPatrolCenter(Vec3d center) {
        this.patrolCenter = center;
        LOGGER.debug("[Idle] Patrol center set to ({}, {}, {})",
            center.x, center.y, center.z);
    }

    /**
     * Reset the patrol center to the bot's current position.
     */
    public void resetPatrolCenter() {
        if (bot != null) {
            this.patrolCenter = bot.getPos();
            LOGGER.debug("[Idle] Patrol center reset to bot position");
        }
    }

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // Initialize patrol center if not set
        if (patrolCenter == null) {
            patrolCenter = context.getBotPosition();
        }

        // 1. FOLLOW OWNER - If owner is nearby but too far away
        ActionResult followResult = checkFollowOwner(context);
        if (followResult.shouldExecute()) {
            return followResult;
        }

        // 2. OBSERVE PLAYER - Periodic behavior observation
        ActionResult observeResult = checkObservePlayer(context);
        if (observeResult.shouldExecute()) {
            return observeResult;
        }

        // 3. PATROL - If owner not nearby, patrol the area
        if (!context.hasOwnerNearby()) {
            ActionResult patrolResult = checkPatrol(context);
            if (patrolResult.shouldExecute()) {
                return patrolResult;
            }
        }

        // No idle actions needed
        return ActionResult.noAction();
    }

    /**
     * Check if we should follow the owner.
     */
    private ActionResult checkFollowOwner(AutonomyContext context) {
        // Only follow if owner is nearby
        if (!context.hasOwnerNearby()) {
            return ActionResult.noAction();
        }

        Vec3d ownerPosition = context.getOwnerPosition();
        double distance = context.getDistanceToOwner();

        // Follow if too far away
        if (distance > FOLLOW_DISTANCE) {
            LOGGER.debug("[Idle] Following owner at distance {}", distance);
            return ActionResult.followOwner(ownerPosition);
        }

        // Close enough, no need to follow
        return ActionResult.noAction();
    }

    /**
     * Check if we should observe the player's behavior.
     */
    private ActionResult checkObservePlayer(AutonomyContext context) {
        // Only observe if owner is nearby
        if (!context.hasOwnerNearby()) {
            return ActionResult.noAction();
        }

        long currentTick = context.getWorldTick();

        // Check if enough ticks have passed since last observation
        if (currentTick - lastObservationTick >= OBSERVATION_INTERVAL) {
            lastObservationTick = currentTick;
            LOGGER.debug("[Idle] Observing player behavior at tick {}", currentTick);
            return ActionResult.observePlayer();
        }

        return ActionResult.noAction();
    }

    /**
     * Check if we should patrol or return to patrol center.
     */
    private ActionResult checkPatrol(AutonomyContext context) {
        Vec3d botPosition = context.getBotPosition();

        // Check distance from patrol center
        double distanceFromCenter = botPosition.distanceTo(patrolCenter);

        // If too far from patrol center, return
        if (distanceFromCenter > MAX_PATROL_DISTANCE) {
            LOGGER.debug("[Idle] Returning to patrol center (distance {})", distanceFromCenter);
            return returnToPatrolCenter();
        }

        // Generate new patrol target if needed
        if (currentPatrolTarget == null || reachedPatrolTarget(context)) {
            currentPatrolTarget = generatePatrolTarget();
            LOGGER.debug("[Idle] New patrol target: ({}, {}, {})",
                currentPatrolTarget.x, currentPatrolTarget.y, currentPatrolTarget.z);
        }

        // Move to patrol target
        return ActionResult.moveTo(currentPatrolTarget);
    }

    /**
     * Generate a random patrol target within the patrol radius.
     */
    private Vec3d generatePatrolTarget() {
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * PATROL_RADIUS;

        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;

        return new Vec3d(
            patrolCenter.x + offsetX,
            patrolCenter.y, // Keep same Y level (could add variation)
            patrolCenter.z + offsetZ
        );
    }

    /**
     * Check if we've reached the patrol target.
     */
    private boolean reachedPatrolTarget(AutonomyContext context) {
        if (currentPatrolTarget == null) {
            return true;
        }

        Vec3d botPosition = context.getBotPosition();
        double distance = botPosition.distanceTo(currentPatrolTarget);

        // Consider reached if within 2 blocks
        return distance < 2.0;
    }

    /**
     * Create action to return to patrol center.
     */
    private ActionResult returnToPatrolCenter() {
        return ActionResult.moveTo(patrolCenter);
    }

    /**
     * Get the current patrol center.
     *
     * @return The patrol center position, or null if not set
     */
    public Vec3d getPatrolCenter() {
        return patrolCenter;
    }

    /**
     * Get the last observation tick.
     *
     * @return The tick when the last player observation occurred
     */
    public long getLastObservationTick() {
        return lastObservationTick;
    }

    /**
     * Get the current patrol target.
     *
     * @return The current patrol target position, or null if not set
     */
    public Vec3d getCurrentPatrolTarget() {
        return currentPatrolTarget;
    }
}