package net.shasankp000.Autonomy.Layers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.shasankp000.Autonomy.ActionResult;
import net.shasankp000.Autonomy.AutonomyContext;
import net.shasankp000.Autonomy.DecisionLayer;
import net.shasankp000.Autonomy.InventorySnapshot;
import net.shasankp000.GameAI.RLAgent;
import net.shasankp000.PlayerUtils.AdvancedCombatAI;
import net.shasankp000.PlayerUtils.AdvancedCombatAI.TargetInfo;
import net.shasankp000.PlayerUtils.AdvancedCombatAI.TargetPriority;
import net.shasankp000.PlayerUtils.AdvancedCombatAI.CombatMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Combat Layer - Handles combat decisions and threat management.
 *
 * <p>This layer has priority 2 (after Survival) and handles all combat-related
 * decisions using the RLAgent for learning and AdvancedCombatAI for tactical analysis.</p>
 *
 * <h3>Priorities (in order):</h3>
 * <ol>
 *   <li>Protect owner from immediate threats</li>
 *   <li>Defend self from hostile entities</li>
 *   <li>Engage or retreat based on threat assessment</li>
 *   <li>Choose optimal combat approach (melee/ranged/retreat)</li>
 * </ol>
 *
 * <h3>Integration Points:</h3>
 * <ul>
 *   <li>{@link AdvancedCombatAI} - Target prioritization and threat assessment</li>
 *   <li>{@link RLAgent} - Q-learning based combat decisions</li>
 * </ul>
 */
public class CombatLayer implements DecisionLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger("combat-layer");

    // Distance thresholds
    private static final double MELEE_RANGE = 3.0;
    private static final double CLOSE_RANGE = 8.0;
    private static final double DETECTION_RANGE = 16.0;

    // Health thresholds for retreat
    private static final float RETREAT_HEALTH_THRESHOLD = 0.25f;  // 25% health

    private final ServerPlayerEntity bot;
    private final AdvancedCombatAI combatAI;
    private final RLAgent rlAgent;

    // Combat state tracking
    private Entity currentTarget;
    private int ticksSinceLastAttack;
    private CombatMode combatMode;

    public CombatLayer(ServerPlayerEntity bot) {
        this.bot = bot;
        this.combatAI = new AdvancedCombatAI();
        this.rlAgent = new RLAgent();
        this.combatMode = CombatMode.BALANCED;
        this.ticksSinceLastAttack = 0;
    }

    @Override
    public String getLayerName() {
        return "Combat";
    }

    @Override
    public int getPriority() {
        return 2; // Second highest priority after Survival
    }

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // Check if there are any hostile entities
        List<Entity> hostileEntities = context.getHostileEntities();
        if (hostileEntities.isEmpty()) {
            // No threats - clear combat state
            clearCombatState();
            return ActionResult.noAction();
        }

        // Check for survival layer override (retreat if critical health)
        if (shouldRetreatForSurvival(context)) {
            LOGGER.info("[Combat] Retreating due to critical health");
            return createRetreatAction(context, hostileEntities);
        }

        // Prioritize targets using AdvancedCombatAI
        PlayerEntity ownerToProtect = getOwnerToProtect(context);
        List<TargetInfo> prioritizedTargets = combatAI.prioritizeTargets(bot, hostileEntities, ownerToProtect);

        if (prioritizedTargets.isEmpty()) {
            clearCombatState();
            return ActionResult.noAction();
        }

        // Get the highest priority target
        TargetInfo primaryTarget = prioritizedTargets.get(0);

        // Update combat state
        currentTarget = primaryTarget.entity;
        combatAI.setTarget(primaryTarget.entity);

        // Determine best action based on context
        ActionResult action = determineCombatAction(context, prioritizedTargets, primaryTarget);

        LOGGER.debug("[Combat] Primary target: {} (priority: {}, distance: {:.1f})",
            primaryTarget.entityType, primaryTarget.priority, primaryTarget.distance);

        return action;
    }

    /**
     * Determine the best combat action based on current context.
     */
    private ActionResult determineCombatAction(AutonomyContext context,
                                                List<TargetInfo> targets,
                                                TargetInfo primaryTarget) {
        // Get weapon information
        ItemStack mainHandItem = bot.getMainHandStack();
        ItemStack offHandItem = bot.getOffHandStack();

        // Check if we have a shield
        boolean hasShield = hasShield(offHandItem);

        // Calculate combat advantage
        boolean hasAdvantage = calculateCombatAdvantage(context, targets);

        // Check for retreat conditions
        float healthPercent = context.getHealthPercent();
        int enemyCount = targets.size();

        if (combatAI.shouldRetreat(healthPercent, enemyCount, hasAdvantage)) {
            LOGGER.info("[Combat] Retreat recommended: health={:.0f}%, enemies={}", healthPercent * 100, enemyCount);
            combatMode = CombatMode.RETREAT;
            return createRetreatAction(context, context.getHostileEntities());
        }

        // Handle immediate threats targeting owner
        if (primaryTarget.priority == TargetPriority.IMMEDIATE_THREAT && primaryTarget.isTargetingPlayer) {
            LOGGER.info("[Combat] Protecting owner from threat: {}", primaryTarget.entityType);
            return createDefendOwnerAction(primaryTarget, context);
        }

        // Determine combat mode based on threat level
        if (primaryTarget.priority == TargetPriority.HIGH_THREAT) {
            combatMode = CombatMode.AGGRESSIVE;
        } else if (hasAdvantage) {
            combatMode = CombatMode.BALANCED;
        } else {
            combatMode = CombatMode.DEFENSIVE;
        }

        // Determine action based on distance and equipment
        String bestAction = combatAI.determineBestAction(bot, targets, mainHandItem, offHandItem);

        return createCombatAction(bestAction, primaryTarget, context, hasShield);
    }

    /**
     * Create appropriate combat action based on the recommended action string.
     */
    private ActionResult createCombatAction(String actionRecommendation,
                                            TargetInfo target,
                                            AutonomyContext context,
                                            boolean hasShield) {
        // Parse the action recommendation
        if (actionRecommendation.startsWith("MELEE_ATTACK")) {
            return createMeleeAction(target, hasShield);
        } else if (actionRecommendation.startsWith("RANGED_ATTACK")) {
            return createRangedAction(target);
        } else if (actionRecommendation.startsWith("BLOCK_AND_COUNTER")) {
            return createBlockAndCounterAction(target);
        } else if (actionRecommendation.startsWith("APPROACH_AND_ATTACK")) {
            return createApproachAction(target);
        } else if (actionRecommendation.startsWith("POSITION_AND_ATTACK")) {
            return createPositionAction(target);
        } else if (actionRecommendation.startsWith("APPROACH")) {
            return createApproachAction(target);
        }

        // Default: engage target
        return createMeleeAction(target, hasShield);
    }

    // ========== Action Factories ==========

    /**
     * Create a melee attack action.
     */
    private ActionResult createMeleeAction(TargetInfo target, boolean hasShield) {
        String description = String.format("Melee attack on %s at distance %.1f",
            target.entityType, target.distance);

        if (hasShield && target.threatLevel > 0.7) {
            description += " (with shield ready)";
        }

        return ActionResult.normal("MELEE_ATTACK",
            () -> executeMeleeAttack(target.entity),
            description);
    }

    /**
     * Create a ranged attack action.
     */
    private ActionResult createRangedAction(TargetInfo target) {
        String description = String.format("Ranged attack on %s at distance %.1f",
            target.entityType, target.distance);

        return ActionResult.normal("RANGED_ATTACK",
            () -> executeRangedAttack(target.entity),
            description);
    }

    /**
     * Create a block and counter action.
     */
    private ActionResult createBlockAndCounterAction(TargetInfo target) {
        return ActionResult.normal("BLOCK_COUNTER",
            () -> executeBlockAndCounter(target.entity),
            String.format("Block and counter against %s", target.entityType));
    }

    /**
     * Create an approach action.
     */
    private ActionResult createApproachAction(TargetInfo target) {
        Vec3d targetPos = target.entity.getPos();
        String description = String.format("Approach %s at (%.0f, %.0f, %.0f)",
            target.entityType, targetPos.x, targetPos.y, targetPos.z);

        return ActionResult.normal("APPROACH_TARGET",
            () -> executeApproach(target.entity),
            description);
    }

    /**
     * Create a position and attack action.
     */
    private ActionResult createPositionAction(TargetInfo target) {
        return ActionResult.normal("POSITION_ATTACK",
            () -> executePositionAndAttack(target.entity),
            String.format("Position for optimal attack on %s", target.entityType));
    }

    /**
     * Create a retreat action.
     */
    private ActionResult createRetreatAction(AutonomyContext context, List<Entity> enemies) {
        Vec3d retreatDirection = combatAI.getRetreatDirection(bot, enemies);

        if (retreatDirection == null) {
            retreatDirection = new Vec3d(0, 0, -1);  // Default retreat backward
        }

        // Make effectively final for lambda
        final Vec3d finalRetreatDirection = retreatDirection;
        Vec3d retreatPos = bot.getPos().add(finalRetreatDirection.multiply(10));

        String description = String.format("Retreat from %d enemies toward (%.0f, %.0f, %.0f)",
            enemies.size(), retreatPos.x, retreatPos.y, retreatPos.z);

        return ActionResult.normal("RETREAT",
            () -> executeRetreat(finalRetreatDirection, enemies),
            description);
    }

    /**
     * Create a defend owner action.
     */
    private ActionResult createDefendOwnerAction(TargetInfo target, AutonomyContext context) {
        LOGGER.info("[Combat] Defending owner from {}", target.entityType);

        // Prioritize attacking the threat
        return ActionResult.normal("DEFEND_OWNER",
            () -> executeDefendOwner(target.entity),
            String.format("Defend owner from %s", target.entityType));
    }

    // ========== Action Executors ==========

    private void executeMeleeAttack(Entity target) {
        LOGGER.info("[Combat] Executing melee attack on {}", target.getType().getName().getString());
        // Combat execution would be handled by the combat system
        ticksSinceLastAttack = 0;
    }

    private void executeRangedAttack(Entity target) {
        LOGGER.info("[Combat] Executing ranged attack on {}", target.getType().getName().getString());
        // Ranged combat execution would be handled by the combat system
    }

    private void executeBlockAndCounter(Entity target) {
        LOGGER.info("[Combat] Blocking and preparing counter against {}", target.getType().getName().getString());
        // Block and counter logic
    }

    private void executeApproach(Entity target) {
        LOGGER.info("[Combat] Approaching target: {}", target.getType().getName().getString());
        // Movement system handles approach
    }

    private void executePositionAndAttack(Entity target) {
        LOGGER.info("[Combat] Positioning for optimal attack on {}", target.getType().getName().getString());
        // Combined positioning and attack
    }

    private void executeRetreat(Vec3d direction, List<Entity> enemies) {
        LOGGER.info("[Combat] Retreating from {} enemies", enemies.size());
        combatMode = CombatMode.RETREAT;
        // Movement system handles retreat
    }

    private void executeDefendOwner(Entity threat) {
        LOGGER.info("[Combat] Defending owner from threat: {}", threat.getType().getName().getString());
        combatMode = CombatMode.DEFENSIVE;
        // Prioritize threat elimination
    }

    // ========== Utility Methods ==========

    /**
     * Check if the bot should retreat for survival reasons.
     */
    private boolean shouldRetreatForSurvival(AutonomyContext context) {
        // Retreat if health is critical
        if (context.isHealthCritical()) {
            return true;
        }

        // Retreat if outnumbered and low health
        float healthPercent = context.getHealthPercent();
        int enemyCount = context.getHostileEntities().size();

        if (healthPercent < 0.4f && enemyCount >= 3) {
            return true;
        }

        return false;
    }

    /**
     * Get the owner player if nearby for protection logic.
     */
    private PlayerEntity getOwnerToProtect(AutonomyContext context) {
        if (!context.hasOwnerNearby()) {
            return null;
        }

        // Find owner in world
        Vec3d ownerPos = context.getOwnerPosition();
        if (ownerPos == null) {
            return null;
        }

        // Would need to get actual player reference from world
        // This is a placeholder - actual implementation depends on how owner is tracked
        return null;
    }

    /**
     * Check if offhand has a shield.
     */
    private boolean hasShield(ItemStack offHandItem) {
        if (offHandItem == null || offHandItem.isEmpty()) {
            return false;
        }
        return offHandItem.getItem().toString().contains("shield");
    }

    /**
     * Calculate combat advantage based on current situation.
     */
    private boolean calculateCombatAdvantage(AutonomyContext context, List<TargetInfo> targets) {
        int enemyCount = targets.size();
        float healthPercent = context.getHealthPercent();

        // Advantage conditions
        boolean hasGoodHealth = healthPercent > 0.7f;
        boolean hasFewEnemies = enemyCount <= 2;
        boolean hasWeapon = !bot.getMainHandStack().isEmpty();
        boolean hasHighGround = false;  // Would need terrain analysis

        // Check inventory for combat items
        InventorySnapshot inventory = context.getInventory();
        boolean hasCombatItems = hasCombatEquipment(inventory);

        // Simple advantage calculation
        int advantageScore = 0;
        if (hasGoodHealth) advantageScore += 2;
        if (hasFewEnemies) advantageScore += 1;
        if (hasWeapon) advantageScore += 1;
        if (hasCombatItems) advantageScore += 1;

        return advantageScore >= 3;
    }

    /**
     * Check if inventory has combat equipment.
     */
    private boolean hasCombatEquipment(InventorySnapshot inventory) {
        // Check weapon count (includes swords, axes, bows)
        if (inventory.getWeaponCount() > 0) {
            return true;
        }
        // Check for shield specifically
        if (inventory.hasItem("shield")) {
            return true;
        }
        // Check best weapon tier
        return inventory.getBestWeaponTier() > 0;
    }

    /**
     * Clear combat state when no threats present.
     */
    private void clearCombatState() {
        currentTarget = null;
        combatAI.clearTarget();
        combatMode = CombatMode.PASSIVE;
    }

    /**
     * Get current combat mode.
     */
    public CombatMode getCombatMode() {
        return combatMode;
    }

    /**
     * Set combat mode.
     */
    public void setCombatMode(CombatMode mode) {
        this.combatMode = mode;
        combatAI.setCombatMode(mode);
    }

    /**
     * Get current target.
     */
    public Entity getCurrentTarget() {
        return currentTarget;
    }
}