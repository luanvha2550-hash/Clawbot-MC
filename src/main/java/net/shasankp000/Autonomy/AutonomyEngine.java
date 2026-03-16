package net.shasankp000.Autonomy;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.shasankp000.Autonomy.Layers.CombatLayer;
import net.shasankp000.Autonomy.Layers.CommandLayer;
import net.shasankp000.Autonomy.Layers.GoalsLayer;
import net.shasankp000.Autonomy.Layers.IdleLayer;
import net.shasankp000.Autonomy.Layers.SurvivalLayer;
import net.shasankp000.PathFinding.PathFinder;
import net.shasankp000.PathFinding.PathTracer;
import net.shasankp000.PathFinding.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main decision engine for AI-Player autonomy.
 *
 * <p>AutonomyEngine is the central coordinator that runs the decision loop
 * for the AI player. It processes all decision layers in priority order
 * and executes the first applicable action.</p>
 *
 * <h3>Decision Priority (highest to lowest):</h3>
 * <ol>
 *   <li>Survival (priority 1) - Health, hunger, immediate dangers</li>
 *   <li>Combat (priority 2) - Hostile entity engagement</li>
 *   <li>Goals (priority 3) - Player-defined objectives</li>
 *   <li>Command (priority 4) - Owner commands</li>
 *   <li>Idle (priority 5) - Default behaviors</li>
 * </ol>
 *
 * <h3>Tick Loop:</h3>
 * <ul>
 *   <li>Runs at 100ms intervals (10 ticks per second)</li>
 *   <li>Gathers world context each tick</li>
 *   <li>Evaluates layers in priority order</li>
 *   <li>Executes first action that shouldExecute()</li>
 * </ul>
 */
public class AutonomyEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutonomyEngine");

    // Tick interval in milliseconds
    private static final long TICK_INTERVAL_MS = 100;

    // Detection ranges
    private static final double ENTITY_DETECTION_RANGE = 32.0;
    private static final double HOSTILE_DETECTION_RANGE = 24.0;
    private static final double OWNER_DETECTION_RANGE = 64.0;

    // The AI player this engine controls
    private final ServerPlayerEntity bot;

    // Decision layers (in priority order)
    private final List<DecisionLayer> layers;

    // Scheduled executor for tick loop
    private final ScheduledExecutorService scheduler;

    // Running state
    private final AtomicBoolean running;

    // Memory systems (to be integrated)
    private final Object knowledgeCache;  // Will be KnowledgeCache
    private final Object longTermMemory;   // Will be LongTermMemory
    private final Object alertSystem;      // Will be AlertSystem

    // Current action being executed
    private ActionResult currentAction;
    private String currentLayer;

    // Performance tracking
    private long lastTickTime;
    private int ticksProcessed;

    // Owner tracking
    private UUID ownerUUID;
    private ServerPlayerEntity owner;

    /**
     * Create a new AutonomyEngine.
     *
     * @param bot The AI player to control
     * @param knowledgeCache Cache for world knowledge (can be null initially)
     * @param longTermMemory Long-term memory system (can be null initially)
     * @param alertSystem Alert/notification system (can be null initially)
     */
    public AutonomyEngine(ServerPlayerEntity bot,
                          Object knowledgeCache,
                          Object longTermMemory,
                          Object alertSystem) {
        this.bot = bot;
        this.knowledgeCache = knowledgeCache;
        this.longTermMemory = longTermMemory;
        this.alertSystem = alertSystem;

        this.layers = new ArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutonomyEngine-Tick");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
        this.currentAction = ActionResult.noAction();
        this.currentLayer = "none";
        this.lastTickTime = System.currentTimeMillis();
        this.ticksProcessed = 0;

        // Initialize layers
        initializeLayers();
    }

    /**
     * Create an AutonomyEngine without memory systems.
     * Used for testing or when memory systems aren't needed.
     *
     * @param bot The AI player to control
     */
    public AutonomyEngine(ServerPlayerEntity bot) {
        this(bot, null, null, null);
    }

    /**
     * Initialize all decision layers in priority order.
     */
    private void initializeLayers() {
        // Layer 1: Survival (instant reflexes)
        layers.add(new SurvivalLayer(bot));

        // Layer 2: Combat - handles hostile mob engagement
        layers.add(new CombatLayer(bot));

        // Layer 3: Goals - auto-generated objectives
        layers.add(new GoalsLayer(bot));

        // Layer 4: Command - player commands
        layers.add(new CommandLayer(bot));

        // Layer 5: Idle - default behavior (follow, patrol)
        layers.add(new IdleLayer(bot));

        // Sort by priority (lower number = higher priority)
        layers.sort(Comparator.comparingInt(DecisionLayer::getPriority));

        LOGGER.info("[AutonomyEngine] Initialized {} decision layers", layers.size());
        for (DecisionLayer layer : layers) {
            LOGGER.info("[AutonomyEngine]   - {} (priority {})", layer.getLayerName(), layer.getPriority());
        }
    }

    /**
     * Set the owner of this AI player.
     *
     * @param ownerUUID UUID of the owner player
     */
    public void setOwner(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        LOGGER.info("[AutonomyEngine] Owner set to: {}", ownerUUID);
    }

    /**
     * Set the owner player reference directly.
     *
     * @param owner The owner player entity
     */
    public void setOwner(ServerPlayerEntity owner) {
        this.owner = owner;
        this.ownerUUID = owner != null ? owner.getUuid() : null;
        if (owner != null) {
            LOGGER.info("[AutonomyEngine] Owner set to: {}", owner.getName().getString());
        }
    }

    /**
     * Start the autonomy tick loop.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("[AutonomyEngine] Starting autonomy tick loop ({}ms interval)", TICK_INTERVAL_MS);
            scheduler.scheduleAtFixedRate(
                this::tick,
                0,
                TICK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        } else {
            LOGGER.warn("[AutonomyEngine] Already running");
        }
    }

    /**
     * Stop the autonomy tick loop.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("[AutonomyEngine] Stopping autonomy tick loop");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Check if the autonomy engine is currently running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Main tick method - called every 100ms.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Gathers current world context</li>
     *   <li>Evaluates all layers in priority order</li>
     *   <li>Executes the first applicable action</li>
     * </ol>
     */
    public void tick() {
        if (!running.get()) {
            return;
        }

        long tickStart = System.currentTimeMillis();
        ticksProcessed++;

        try {
            // 1. Gather context from world state
            AutonomyContext context = gatherContext();

            // 2. Evaluate layers in priority order
            ActionResult action = evaluateLayers(context);

            // 3. Execute action if needed
            if (action.shouldExecute()) {
                executeAction(action, context);
            }

            // 4. Update performance tracking
            lastTickTime = System.currentTimeMillis();
            long tickDuration = lastTickTime - tickStart;

            // Log slow ticks (over 50ms)
            if (tickDuration > 50) {
                LOGGER.warn("[AutonomyEngine] Slow tick: {}ms (tick #{})", tickDuration, ticksProcessed);
            }

        } catch (Exception e) {
            LOGGER.error("[AutonomyEngine] Error during tick #{}: {}", ticksProcessed, e.getMessage(), e);
        }
    }

    /**
     * Gather context from current world state.
     *
     * <p>Builds a complete snapshot of the bot's current situation
     * including position, health, nearby entities, inventory, etc.</p>
     *
     * @return AutonomyContext with current world state
     */
    public AutonomyContext gatherContext() {
        AutonomyContext.Builder builder = new AutonomyContext.Builder();

        try {
            World world = bot.getWorld();

            // Bot state
            builder.botPosition(bot.getPos())
                   .botVelocity(bot.getVelocity())
                   .botHealth(bot.getHealth())
                   .botMaxHealth(bot.getMaxHealth())
                   .botHunger(bot.getHungerManager().getFoodLevel())
                   .botAir(bot.getAir())
                   .rotation(bot.getYaw(), bot.getPitch())
                   .worldTick(world.getTime())
                   .biome(world.getBiome(bot.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("unknown"))
                   .daytime(world.isDay());

            // Environment
            Vec3d botPos = bot.getPos();
            List<Entity> nearbyEntities = new ArrayList<>();
            List<Entity> hostileEntities = new ArrayList<>();

            // Scan for nearby entities using bounding box
            Box searchBox = new Box(
                botPos.x - ENTITY_DETECTION_RANGE, botPos.y - ENTITY_DETECTION_RANGE, botPos.z - ENTITY_DETECTION_RANGE,
                botPos.x + ENTITY_DETECTION_RANGE, botPos.y + ENTITY_DETECTION_RANGE, botPos.z + ENTITY_DETECTION_RANGE
            );

            // Get all entities in range using the world's entity lookup
            if (world instanceof ServerWorld serverWorld) {
                List<Entity> allEntities = serverWorld.getEntitiesByClass(
                    Entity.class,
                    searchBox,
                    entity -> entity != bot // Exclude the bot itself
                );

                for (Entity entity : allEntities) {
                    double distance = entity.squaredDistanceTo(bot);
                    if (distance < ENTITY_DETECTION_RANGE * ENTITY_DETECTION_RANGE) {
                        nearbyEntities.add(entity);

                        // Check for hostiles
                        if (entity instanceof HostileEntity && distance < HOSTILE_DETECTION_RANGE * HOSTILE_DETECTION_RANGE) {
                            hostileEntities.add(entity);
                        }
                    }
                }
            }

            builder.nearbyEntities(nearbyEntities)
                   .hostileEntities(hostileEntities);

            // Environment hazards
            builder.inWater(bot.isTouchingWater())
                   .inLava(bot.isInLava())
                   .onFire(bot.isOnFire())
                   .falling(bot.getVelocity().y < -0.5 && !bot.isOnGround());

            // Inventory (placeholder - would integrate with actual inventory)
            InventorySnapshot inventory = buildInventorySnapshot();
            builder.inventory(inventory);

            // Owner tracking - dynamically find nearest player if no owner set
            if (owner != null && owner.isAlive()) {
                builder.ownerPosition(owner.getPos())
                       .ownerUUID(owner.getUuid())
                       .distanceToOwner(owner.squaredDistanceTo(bot));
            } else if (ownerUUID != null) {
                // Try to find owner by UUID
                ServerPlayerEntity ownerEntity = world.getServer() != null ?
                    world.getServer().getPlayerManager().getPlayer(ownerUUID) : null;
                if (ownerEntity != null && ownerEntity.isAlive()) {
                    this.owner = ownerEntity;
                    builder.ownerPosition(ownerEntity.getPos())
                           .ownerUUID(ownerUUID)
                           .distanceToOwner(ownerEntity.squaredDistanceTo(bot));
                }
            } else {
                // Fallback: find nearest player dynamically
                ServerPlayerEntity nearestPlayer = findNearestPlayerDynamically(bot);
                if (nearestPlayer != null) {
                    this.owner = nearestPlayer;
                    this.ownerUUID = nearestPlayer.getUuid();
                    builder.ownerPosition(nearestPlayer.getPos())
                           .ownerUUID(nearestPlayer.getUuid())
                           .distanceToOwner(nearestPlayer.squaredDistanceTo(bot));
                }
            }

            // Pending command check (placeholder)
            builder.pendingCommand(false);

            // Current goal (placeholder)
            builder.currentGoal(null, AutonomyContext.GoalType.NONE);

        } catch (Exception e) {
            LOGGER.error("[AutonomyEngine] Error gathering context: {}", e.getMessage());
        }

        return builder.build();
    }

    /**
     * Build inventory snapshot from bot's inventory.
     *
     * @return InventorySnapshot of current inventory state
     */
    private InventorySnapshot buildInventorySnapshot() {
        // Placeholder - would integrate with actual inventory system
        // For now, return empty inventory
        return new InventorySnapshot();
    }

    /**
     * Dynamically find the nearest player to follow.
     * This is used when no owner has been explicitly set.
     *
     * @param bot The bot entity
     * @return The nearest player, or null if none found
     */
    private ServerPlayerEntity findNearestPlayerDynamically(ServerPlayerEntity bot) {
        MinecraftServer server = bot.getServer();
        if (server == null) return null;

        ServerPlayerEntity nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Skip the bot itself
            if (player.getUuid().equals(bot.getUuid())) continue;

            double distance = bot.squaredDistanceTo(player);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = player;
            }
        }

        if (nearest != null) {
            LOGGER.debug("[AutonomyEngine] Found nearest player: {} at distance {}",
                nearest.getName().getString(), Math.sqrt(minDistance));
        }

        return nearest;
    }

    /**
     * Evaluate all decision layers and return the first applicable action.
     *
     * @param context Current world context
     * @return The first action that shouldExecute(), or noAction if none
     */
    private ActionResult evaluateLayers(AutonomyContext context) {
        for (DecisionLayer layer : layers) {
            try {
                ActionResult action = layer.evaluate(context);

                if (action.shouldExecute()) {
                    currentLayer = layer.getLayerName();
                    currentAction = action;

                    // Log based on action type
                    switch (action.getType()) {
                        case IMMEDIATE:
                            LOGGER.warn("[AutonomyEngine] [{}] IMMEDIATE: {}",
                                currentLayer, action.getDescription());
                            break;
                        case NORMAL:
                            LOGGER.debug("[AutonomyEngine] [{}] NORMAL: {}",
                                currentLayer, action.getDescription());
                            break;
                        case SCHEDULED:
                            LOGGER.info("[AutonomyEngine] [{}] SCHEDULED: {}",
                                currentLayer, action.getDescription());
                            break;
                        default:
                            break;
                    }

                    return action;
                }

            } catch (Exception e) {
                LOGGER.error("[AutonomyEngine] Error in layer {}: {}",
                    layer.getLayerName(), e.getMessage(), e);
            }
        }

        // No layer produced an action
        currentLayer = "none";
        currentAction = ActionResult.noAction();
        return currentAction;
    }

    /**
     * Execute an action returned by a layer.
     *
     * @param action The action to execute
     * @param context Current world context
     */
    private void executeAction(ActionResult action, AutonomyContext context) {
        if (!action.shouldExecute()) {
            return;
        }

        String actionId = action.getActionId();
        LOGGER.info("[AutonomyEngine] Executing action: {} - {}", actionId, action.getDescription());

        // Check if we have a movement target
        if (action.hasMovementTarget()) {
            executeMovementAction(action, context);
            return;
        }

        // Check for executor
        Runnable executor = action.getExecutor();
        if (executor != null) {
            try {
                executor.run();
            } catch (Exception e) {
                LOGGER.error("[AutonomyEngine] Failed to execute action {}: {}",
                    actionId, e.getMessage(), e);
            }
        } else {
            // Handle action by ID for actions without explicit executors
            handleActionById(actionId, context);
        }
    }

    /**
     * Execute a movement action using the navigation system.
     */
    private void executeMovementAction(ActionResult action, AutonomyContext context) {
        Vec3d targetPos = action.getTargetPosition();
        if (targetPos == null) {
            LOGGER.warn("[AutonomyEngine] Movement action {} has null target", action.getActionId());
            return;
        }

        BlockPos targetBlockPos = new BlockPos((int) targetPos.x, (int) targetPos.y, (int) targetPos.z);
        ServerWorld world = (ServerWorld) bot.getWorld();

        LOGGER.info("[AutonomyEngine] Moving to ({}, {}, {})",
            (int) targetPos.x, (int) targetPos.y, (int) targetPos.z);

        try {
            // Use PathFinder for navigation
            List<PathFinder.PathNode> path = PathFinder.calculatePath(
                bot.getBlockPos(), targetBlockPos, world);

            if (path != null && !path.isEmpty()) {
                // Simplify and convert to segments
                List<PathFinder.PathNode> simplified = PathFinder.simplifyPath(path, world);
                java.util.Queue<Segment> segments = PathFinder.convertPathToSegments(simplified, false);

                // Execute movement
                MinecraftServer server = bot.getServer();
                if (server != null) {
                    PathTracer.BotSegmentManager manager = new PathTracer.BotSegmentManager(
                        server, bot.getCommandSource(), bot.getName().getString());
                    segments.forEach(manager::addSegmentJob);
                    manager.startProcessing();
                    LOGGER.debug("[AutonomyEngine] Navigation started with {} segments", segments.size());
                }
            } else {
                // Fallback: direct movement for short distances
                LOGGER.warn("[AutonomyEngine] PathFinder returned no path, trying direct movement");
                moveDirectlyTo(targetPos);
            }
        } catch (Exception e) {
            LOGGER.error("[AutonomyEngine] Movement failed: {}", e.getMessage());
            // Try direct movement as fallback
            moveDirectlyTo(targetPos);
        }
    }

    /**
     * Direct movement fallback using Carpet Mod commands.
     */
    private void moveDirectlyTo(Vec3d targetPos) {
        MinecraftServer server = bot.getServer();
        if (server == null) return;

        Vec3d currentPos = bot.getPos();
        double dx = targetPos.x - currentPos.x;
        double dz = targetPos.z - currentPos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 0.5) {
            // Already close enough
            return;
        }

        // Normalize direction
        double dirX = dx / distance;
        double dirZ = dz / distance;

        // Use Carpet Mod commands to move
        String botName = bot.getName().getString();

        // Determine direction and move
        if (Math.abs(dirX) > Math.abs(dirZ)) {
            if (dirX > 0) {
                server.getCommandManager().executeWithPrefix(bot.getCommandSource(), "/player " + botName + " move forward");
            } else {
                server.getCommandManager().executeWithPrefix(bot.getCommandSource(), "/player " + botName + " move backward");
            }
        } else {
            if (dirZ > 0) {
                server.getCommandManager().executeWithPrefix(bot.getCommandSource(), "/player " + botName + " move forward");
            } else {
                server.getCommandManager().executeWithPrefix(bot.getCommandSource(), "/player " + botName + " move backward");
            }
        }

        // Also update look direction to face target
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        bot.setYaw(yaw);

        LOGGER.debug("[AutonomyEngine] Moving toward target using Carpet Mod commands");
    }

    /**
     * Handle actions by their ID when no executor is provided.
     */
    private void handleActionById(String actionId, AutonomyContext context) {
        switch (actionId) {
            case "OBSERVE_PLAYER":
                // Observation is passive - just log it
                LOGGER.debug("[AutonomyEngine] Observing player behavior");
                break;

            case "IDLE":
                // No action needed
                break;

            case "FOLLOW_OWNER":
                // Follow owner - execute movement to owner position
                Vec3d ownerPos = context.getOwnerPosition();
                if (ownerPos != null) {
                    LOGGER.info("[AutonomyEngine] Following owner to position: {}", ownerPos);
                    moveDirectlyTo(ownerPos);
                }
                break;

            default:
                LOGGER.warn("[AutonomyEngine] Unknown action ID: {}", actionId);
        }
    }

    /**
     * Get the name of the layer currently controlling the bot.
     *
     * @return Current layer name, or "none" if idle
     */
    public String getCurrentLayer() {
        return currentLayer;
    }

    /**
     * Get the current action being executed.
     *
     * @return Current ActionResult
     */
    public ActionResult getCurrentAction() {
        return currentAction;
    }

    /**
     * Get the number of ticks processed since start.
     *
     * @return Tick count
     */
    public int getTicksProcessed() {
        return ticksProcessed;
    }

    /**
     * Get the time of the last tick.
     *
     * @return Timestamp of last tick
     */
    public long getLastTickTime() {
        return lastTickTime;
    }

    /**
     * Add a decision layer to the engine.
     *
     * @param layer Layer to add
     */
    public void addLayer(DecisionLayer layer) {
        layers.add(layer);
        layers.sort(Comparator.comparingInt(DecisionLayer::getPriority));
        LOGGER.info("[AutonomyEngine] Added layer: {} (priority {})",
            layer.getLayerName(), layer.getPriority());
    }

    /**
     * Remove a decision layer from the engine.
     *
     * @param layerName Name of layer to remove
     * @return true if layer was removed
     */
    public boolean removeLayer(String layerName) {
        boolean removed = layers.removeIf(layer -> layer.getLayerName().equals(layerName));
        if (removed) {
            LOGGER.info("[AutonomyEngine] Removed layer: {}", layerName);
        }
        return removed;
    }

    /**
     * Get a snapshot of current state for debugging/monitoring.
     *
     * @return Debug snapshot string
     */
    public String getDebugSnapshot() {
        return String.format(
            "AutonomyEngine[running=%s, ticks=%d, layer=%s, action=%s]",
            running.get(),
            ticksProcessed,
            currentLayer,
            currentAction.toString()
        );
    }

    /**
     * Force a context update and re-evaluation.
     * Useful for testing or triggered events.
     *
     * @return The action that was determined
     */
    public ActionResult forceTick() {
        AutonomyContext context = gatherContext();
        ActionResult action = evaluateLayers(context);
        if (action.shouldExecute()) {
            executeAction(action, context);
        }
        return action;
    }

    /**
     * Get the bot player entity.
     *
     * @return ServerPlayerEntity being controlled
     */
    public ServerPlayerEntity getBot() {
        return bot;
    }

    /**
     * Cleanup resources when engine is no longer needed.
     */
    public void cleanup() {
        stop();
        currentAction = ActionResult.noAction();
        currentLayer = "none";
        LOGGER.info("[AutonomyEngine] Cleanup complete");
    }
}