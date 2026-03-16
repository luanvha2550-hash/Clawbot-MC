package net.shasankp000.Autonomy.Layers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.shasankp000.Autonomy.ActionResult;
import net.shasankp000.Autonomy.AutonomyContext;
import net.shasankp000.Autonomy.DecisionLayer;
import net.shasankp000.Autonomy.InventorySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Survival Layer - Instant reflexes for health, hunger, and environmental dangers.
 *
 * <p>This layer has the HIGHEST priority (1) and bypasses all other layers.
 * It handles immediate survival needs without consulting any external systems.</p>
 *
 * <h3>Priorities (in order):</h3>
 * <ol>
 *   <li>Critical dangers (lava, void, drowning)</li>
 *   <li>Health critical (eat, flee)</li>
 *   <li>Hunger critical (eat)</li>
 *   <li>Environmental hazards (creeper, TNT)</li>
 *   <li>Health low (eat)</li>
 *   <li>Hunger low (find food)</li>
 * </ol>
 */
public class SurvivalLayer implements DecisionLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger("survival-layer");

    // Health thresholds
    private static final float HEALTH_CRITICAL = 6.0f;   // 3 hearts
    private static final float HEALTH_LOW = 10.0f;       // 5 hearts

    // Hunger thresholds
    private static final int HUNGER_CRITICAL = 6;        // 3 bars
    private static final int HUNGER_LOW = 10;             // 5 bars

    // Air threshold (drowning)
    private static final int AIR_CRITICAL = 30;           // ~1.5 seconds

    private final ServerPlayerEntity bot;

    public SurvivalLayer(ServerPlayerEntity bot) {
        this.bot = bot;
    }

    @Override
    public String getLayerName() {
        return "Survival";
    }

    @Override
    public int getPriority() {
        return 1; // Highest priority
    }

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // 1. CRITICAL DANGERS - Check immediately
        ActionResult criticalResult = checkCriticalDangers(context);
        if (criticalResult.shouldExecute()) {
            LOGGER.debug("[Survival] Critical danger detected: {}", criticalResult.getDescription());
            return criticalResult;
        }

        // 2. HEALTH CRITICAL - Emergency eating
        if (context.getBotHealth() < HEALTH_CRITICAL) {
            LOGGER.warn("[Survival] Critical health: {}", context.getBotHealth());
            return createEmergencyHealthAction(context);
        }

        // 3. HUNGER CRITICAL - Emergency eating
        if (context.getBotHunger() < HUNGER_CRITICAL) {
            LOGGER.warn("[Survival] Critical hunger: {}", context.getBotHunger());
            return createEmergencyHungerAction(context);
        }

        // 4. ENVIRONMENTAL HAZARDS - Creeper, TNT, etc.
        ActionResult hazardResult = checkEnvironmentalHazards(context);
        if (hazardResult.shouldExecute()) {
            LOGGER.debug("[Survival] Environmental hazard: {}", hazardResult.getDescription());
            return hazardResult;
        }

        // 5. HEALTH LOW - Regular eating
        if (context.getBotHealth() < HEALTH_LOW) {
            Optional<ActionResult> healthAction = tryEatFood(context.getInventory());
            if (healthAction.isPresent()) {
                return healthAction.get();
            }
        }

        // 6. HUNGER LOW - Find food
        if (context.getBotHunger() < HUNGER_LOW) {
            return createFindFoodAction(context);
        }

        // No survival actions needed
        return ActionResult.noAction();
    }

    /**
     * Check for immediate life-threatening dangers.
     */
    private ActionResult checkCriticalDangers(AutonomyContext context) {
        // In lava - get out immediately
        if (context.isInLava()) {
            return ActionResult.immediate("EMERGENCY_EXIT_LAVA",
                () -> exitLava(),
                "Exit lava immediately");
        }

        // Drowning - swim to surface
        if (context.isUnderwater() && context.getBotAir() < AIR_CRITICAL) {
            return ActionResult.immediate("EMERGENCY_SWIM",
                () -> swimToSurface(),
                "Swim to surface");
        }

        // Falling into void
        if (context.isFalling() && context.getBotPosition().y < -50) {
            return ActionResult.immediate("EMERGENCY_VOID",
                () -> placeBlockBelow(),
                "Place block to avoid void");
        }

        // On fire
        if (context.isOnFire()) {
            return ActionResult.immediate("EMERGENCY_FIRE",
                () -> findWaterOrStopDrop(),
                "Find water to extinguish");
        }

        return ActionResult.noAction();
    }

    /**
     * Check for environmental hazards like creepers.
     */
    private ActionResult checkEnvironmentalHazards(AutonomyContext context) {
        // Check for ignited creeper
        for (Entity entity : context.getHostileEntities()) {
            if (entity instanceof CreeperEntity creeper) {
                if (creeper.isIgnited()) {
                    Vec3d creeperPos = entity.getPos();
                    return ActionResult.immediate("EVADE_CREEPER",
                        () -> moveAwayFrom(creeperPos, 10.0),
                        String.format("Evade ignited creeper at (%.0f, %.0f, %.0f)",
                            creeperPos.x, creeperPos.y, creeperPos.z));
                }
            }
        }

        // Add more hazard checks as needed
        return ActionResult.noAction();
    }

    /**
     * Create emergency health action.
     */
    private ActionResult createEmergencyHealthAction(AutonomyContext context) {
        // Try to eat food first
        Optional<ItemStack> foodItem = findFoodItem(context.getInventory());
        if (foodItem.isPresent()) {
            ItemStack food = foodItem.get();
            return ActionResult.immediate("EMERGENCY_EAT",
                () -> consumeItem(food),
                "Emergency eating: " + food.getName().getString());
        }

        // No food - flee to safety
        return ActionResult.immediate("EMERGENCY_FLEE",
            () -> fleeToSafety(context),
            "Flee to safety - no food available");
    }

    /**
     * Create emergency hunger action.
     */
    private ActionResult createEmergencyHungerAction(AutonomyContext context) {
        Optional<ItemStack> foodItem = findFoodItem(context.getInventory());
        if (foodItem.isPresent()) {
            ItemStack food = foodItem.get();
            return ActionResult.immediate("EMERGENCY_EAT",
                () -> consumeItem(food),
                "Emergency eating: " + food.getName().getString());
        }

        // No food - need to find some
        return ActionResult.normal("FIND_FOOD",
            () -> searchForFood(context),
            "Searching for food");
    }

    /**
     * Create action to find food.
     */
    private ActionResult createFindFoodAction(AutonomyContext context) {
        Optional<ItemStack> foodItem = findFoodItem(context.getInventory());
        if (foodItem.isPresent()) {
            ItemStack food = foodItem.get();
            return ActionResult.normal("EAT_FOOD",
                () -> consumeItem(food),
                "Eating: " + food.getName().getString());
        }

        // No food available
        return ActionResult.normal("FIND_FOOD",
            () -> searchForFood(context),
            "Need to find food");
    }

    /**
     * Try to eat food if available.
     */
    private Optional<ActionResult> tryEatFood(InventorySnapshot inventory) {
        Optional<ItemStack> foodItem = findFoodItem(inventory);
        if (foodItem.isPresent()) {
            ItemStack food = foodItem.get();
            return Optional.of(ActionResult.normal("EAT_FOOD",
                () -> consumeItem(food),
                "Eating: " + food.getName().getString()));
        }
        return Optional.empty();
    }

    // ========== Action Executors (placeholders for now) ==========

    private void exitLava() {
        // Movement system would handle this
        LOGGER.info("[Survival] Exiting lava at {}", bot.getPos());
    }

    private void swimToSurface() {
        // Movement system would handle this
        LOGGER.info("[Survival] Swimming to surface");
    }

    private void placeBlockBelow() {
        // Block placement system would handle this
        LOGGER.info("[Survival] Placing block to avoid void");
    }

    private void findWaterOrStopDrop() {
        // Find water or stop drop and roll
        LOGGER.info("[Survival] Finding water to extinguish fire");
    }

    private void moveAwayFrom(Vec3d position, double distance) {
        // Movement system would handle this
        LOGGER.info("[Survival] Moving away from {}", position);
    }

    private void fleeToSafety(AutonomyContext context) {
        // Find a safe location
        LOGGER.info("[Survival] Fleeing to safety");
    }

    private void searchForFood(AutonomyContext context) {
        // Search for food sources
        LOGGER.info("[Survival] Searching for food");
    }

    private void consumeItem(ItemStack item) {
        // Consume item
        LOGGER.info("[Survival] Consuming: {}", item.getName().getString());
    }

    // ========== Utility Methods ==========

    private Optional<ItemStack> findFoodItem(InventorySnapshot inventory) {
        // Check hotbar first, then inventory
        // This would integrate with actual inventory system
        // For now, return empty as placeholder
        return Optional.empty();
    }
}