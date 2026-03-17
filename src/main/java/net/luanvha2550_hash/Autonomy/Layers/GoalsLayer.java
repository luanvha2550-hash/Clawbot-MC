package net.luanvha2550_hash.Autonomy.Layers;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.luanvha2550_hash.Autonomy.ActionResult;
import net.luanvha2550_hash.Autonomy.AutonomyContext;
import net.luanvha2550_hash.Autonomy.AutonomyContext.GoalType;
import net.luanvha2550_hash.Autonomy.DecisionLayer;
import net.luanvha2550_hash.Autonomy.InventorySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Goals Layer - Auto-generated goals based on needs and player commands.
 *
 * <p>This layer has priority 3 (after Combat) and manages long-term objectives
 * for the AI player. Goals are auto-generated based on current needs and
 * can be overridden by player commands.</p>
 *
 * <h3>Goal Types:</h3>
 * <ul>
 *   <li>GATHER_RESOURCE - Collect specific resources (wood, stone, iron, etc.)</li>
 *   <li>BUILD_STRUCTURE - Construct buildings or structures</li>
 *   <li>CRAFT_ITEM - Create specific items or tools</li>
 *   <li>EXPLORE_AREA - Explore new territories</li>
 *   <li>FOLLOW_PLAYER - Follow the owner player</li>
 *   <li>DEFEND_LOCATION - Guard a specific location</li>
 *   <li>FARM_RESOURCE - Farm renewable resources (crops, animals)</li>
 * </ul>
 *
 * <h3>Priority Logic:</h3>
 * <ol>
 *   <li>Continue executing existing plan if in progress</li>
 *   <li>Check cache for similar situation solutions</li>
 *   <li>Analyze current needs and generate goals</li>
 *   <li>Prioritize goals based on urgency and importance</li>
 * </ol>
 */
public class GoalsLayer implements DecisionLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger("goals-layer");

    // Need thresholds
    private static final int FOOD_CRITICAL_THRESHOLD = 5;
    private static final int FOOD_LOW_THRESHOLD = 10;
    private static final int TOOL_MIN_THRESHOLD = 1;
    private static final int RESOURCE_LOW_THRESHOLD = 16;
    private static final int RESOURCE_CRITICAL_THRESHOLD = 8;

    // Goal completion tracking
    private static final int MAX_PLAN_STEPS = 20;
    private static final long GOAL_TIMEOUT_MS = 300000; // 5 minutes

    private final ServerPlayerEntity bot;
    private final Object knowledgeCache;  // Will be KnowledgeCache when implemented

    // Current goal state
    private Goal currentGoal;
    private int currentStepIndex;
    private long goalStartTime;

    // Cached goal results
    private List<Goal> lastGeneratedGoals;
    private long lastGoalGenerationTime;

    public GoalsLayer(ServerPlayerEntity bot) {
        this(bot, null);
    }

    public GoalsLayer(ServerPlayerEntity bot, Object knowledgeCache) {
        this.bot = bot;
        this.knowledgeCache = knowledgeCache;
        this.currentGoal = null;
        this.currentStepIndex = 0;
        this.goalStartTime = 0;
        this.lastGeneratedGoals = new ArrayList<>();
        this.lastGoalGenerationTime = 0;
    }

    @Override
    public String getLayerName() {
        return "Goals";
    }

    @Override
    public int getPriority() {
        return 3; // After Combat, before Command
    }

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // 1. If we have an active goal, continue executing the plan
        if (currentGoal != null && !currentGoal.isCompleted()) {
            ActionResult continueResult = continueGoalExecution(context);
            if (continueResult.shouldExecute()) {
                return continueResult;
            }
            // If current step completed, move to next step
            if (currentGoal.isStepCompleted(currentStepIndex)) {
                currentStepIndex++;
                if (currentStepIndex < currentGoal.getSteps().size()) {
                    return executeCurrentStep(context);
                } else {
                    // Goal completed
                    completeCurrentGoal();
                }
            }
        }

        // 2. Generate new goals based on needs
        List<Goal> goals = analyzeNeeds(context);

        if (goals.isEmpty()) {
            LOGGER.debug("[Goals] No goals generated - all needs satisfied");
            return ActionResult.noAction();
        }

        // 3. Check knowledge cache for similar situations
        Goal cachedGoal = checkCacheForSimilarSituation(context, goals);
        if (cachedGoal != null) {
            LOGGER.info("[Goals] Using cached goal: {}", cachedGoal.getDescription());
            setCurrentGoal(cachedGoal);
            return executeCurrentStep(context);
        }

        // 4. Select highest priority goal
        Goal selectedGoal = goals.get(0);
        LOGGER.info("[Goals] Selected goal: {} (priority: {})",
            selectedGoal.getDescription(), selectedGoal.getPriority());

        setCurrentGoal(selectedGoal);
        return executeCurrentStep(context);
    }

    /**
     * Analyze current needs and generate prioritized goals.
     *
     * @param context Current autonomy context
     * @return List of goals sorted by priority (highest first)
     */
    public List<Goal> analyzeNeeds(AutonomyContext context) {
        List<Goal> goals = new ArrayList<>();
        InventorySnapshot inventory = context.getInventory();

        // Priority 1: Food needs
        Goal foodGoal = generateFoodGoal(context);
        if (foodGoal != null) {
            goals.add(foodGoal);
        }

        // Priority 2: Equipment needs (tools, weapons, armor)
        Goal equipmentGoal = generateEquipmentGoal(context);
        if (equipmentGoal != null) {
            goals.add(equipmentGoal);
        }

        // Priority 3: Resource needs
        Goal resourceGoal = generateResourceGoal(context);
        if (resourceGoal != null) {
            goals.add(resourceGoal);
        }

        // Priority 4: Follow owner if nearby
        Goal followGoal = generateFollowGoal(context);
        if (followGoal != null) {
            goals.add(followGoal);
        }

        // Priority 5: Exploration if idle
        Goal exploreGoal = generateExploreGoal(context);
        if (exploreGoal != null) {
            goals.add(exploreGoal);
        }

        // Priority 6: Farming for sustainable resources
        Goal farmGoal = generateFarmGoal(context);
        if (farmGoal != null) {
            goals.add(farmGoal);
        }

        // Sort by priority (lower number = higher priority)
        goals.sort(Comparator.comparingInt(Goal::getPriority));

        return goals;
    }

    // ========== Goal Generation Methods ==========

    /**
     * Generate a goal for food acquisition.
     */
    private Goal generateFoodGoal(AutonomyContext context) {
        InventorySnapshot inventory = context.getInventory();
        int foodCount = inventory.getFoodCount();
        int hunger = context.getBotHunger();

        // Critical food shortage
        if (foodCount == 0 && hunger < FOOD_LOW_THRESHOLD) {
            return createGatherFoodGoal("critical", hunger);
        }

        // Low food supply
        if (foodCount < 4 && hunger < FOOD_CRITICAL_THRESHOLD) {
            return createGatherFoodGoal("urgent", hunger);
        }

        // General food gathering
        if (foodCount < 8) {
            return createGatherFoodGoal("routine", hunger);
        }

        return null;
    }

    /**
     * Create a goal for gathering food.
     */
    private Goal createGatherFoodGoal(String urgency, int currentHunger) {
        List<GoalStep> steps = new ArrayList<>();

        // Determine best food source
        if (currentHunger < 6) {
            // Emergency: Find nearest food source
            steps.add(new GoalStep("LOCATE_FOOD_SOURCE",
                "Find nearest food source (animals, crops, chests)", 1));
            steps.add(new GoalStep("ACQUIRE_FOOD",
                "Acquire food immediately", 1));
            steps.add(new GoalStep("CONSUME_FOOD",
                "Eat food to restore hunger", 1));
        } else {
            // Normal food gathering
            steps.add(new GoalStep("LOCATE_FOOD_AREA",
                "Find area with food resources", 2));
            steps.add(new GoalStep("GATHER_FOOD",
                "Gather food items", 2));
            steps.add(new GoalStep("STORE_EXCESS",
                "Store excess food in inventory", 3));
        }

        int priority = urgency.equals("critical") ? 1 : (urgency.equals("urgent") ? 2 : 4);

        return new Goal(
            UUID.randomUUID().toString(),
            GoalType.GATHER_RESOURCE,
            "Gather food (" + urgency + ")",
            description -> "Gather food to maintain hunger level. Current: " + currentHunger,
            priority,
            steps
        );
    }

    /**
     * Generate a goal for equipment acquisition.
     */
    private Goal generateEquipmentGoal(AutonomyContext context) {
        InventorySnapshot inventory = context.getInventory();

        // Check for missing essential tools
        if (inventory.getToolCount() < TOOL_MIN_THRESHOLD) {
            return createCraftToolGoal(inventory);
        }

        // Check for weapon needs
        if (inventory.getWeaponCount() < TOOL_MIN_THRESHOLD) {
            return createCraftWeaponGoal(inventory);
        }

        // Check for armor needs
        if (!inventory.hasCompleteArmor()) {
            return createCraftArmorGoal(inventory);
        }

        // Check for tier upgrades
        if (shouldUpgradeEquipment(inventory)) {
            return createUpgradeGoal(inventory);
        }

        return null;
    }

    /**
     * Create a goal for crafting tools.
     */
    private Goal createCraftToolGoal(InventorySnapshot inventory) {
        List<GoalStep> steps = new ArrayList<>();

        // Determine best tool tier available
        int bestTier = determineBestToolTier(inventory);

        if (bestTier == 0) {
            // Need to gather materials first
            steps.add(new GoalStep("GATHER_WOOD",
                "Gather wood for basic tools", 1));
            steps.add(new GoalStep("CRAFT_CRAFTING_TABLE",
                "Place crafting table", 2));
            steps.add(new GoalStep("CRAFT_TOOLS",
                "Craft basic wooden tools", 2));
        } else {
            // Can craft better tools
            steps.add(new GoalStep("GATHER_MATERIALS",
                "Gather " + getTierMaterialName(bestTier) + " for tools", 1));
            steps.add(new GoalStep("CRAFT_TOOLS",
                "Craft " + getTierMaterialName(bestTier) + " tools", 2));
        }

        return new Goal(
            UUID.randomUUID().toString(),
            GoalType.CRAFT_ITEM,
            "Craft essential tools",
            desc -> "Craft tools to improve resource gathering efficiency",
            2,
            steps
        );
    }

    /**
     * Create a goal for crafting weapons.
     */
    private Goal createCraftWeaponGoal(InventorySnapshot inventory) {
        List<GoalStep> steps = new ArrayList<>();

        int tier = determineBestWeaponTier(inventory);

        steps.add(new GoalStep("GATHER_MATERIALS",
            "Gather " + getTierMaterialName(tier) + " for weapon", 1));
        steps.add(new GoalStep("CRAFT_WEAPON",
            "Craft " + getTierMaterialName(tier) + " weapon", 2));

        return new Goal(
            UUID.randomUUID().toString(),
            GoalType.CRAFT_ITEM,
            "Craft weapon for combat",
            desc -> "Craft a weapon to improve combat capabilities",
            2,
            steps
        );
    }

    /**
     * Create a goal for crafting armor.
     */
    private Goal createCraftArmorGoal(InventorySnapshot inventory) {
        List<GoalStep> steps = new ArrayList<>();

        int tier = Math.max(1, inventory.getBestArmorTier() + 1);
        tier = Math.min(tier, determineMaxArmorTier(inventory));

        steps.add(new GoalStep("GATHER_MATERIALS",
            "Gather " + getTierMaterialName(tier) + " for armor", 1));
        steps.add(new GoalStep("CRAFT_ARMOR",
            "Craft " + getTierMaterialName(tier) + " armor pieces", 2));

        return new Goal(
            UUID.randomUUID().toString(),
            GoalType.CRAFT_ITEM,
            "Craft protective armor",
            desc -> "Craft armor to improve survivability",
            3,
            steps
        );
    }

    /**
     * Create a goal for upgrading equipment.
     */
    private Goal createUpgradeGoal(InventorySnapshot inventory) {
        List<GoalStep> steps = new ArrayList<>();

        int nextTier = inventory.getBestWeaponTier() + 1;
        String material = getTierMaterialName(nextTier);

        steps.add(new GoalStep("LOCATE_RESOURCE",
            "Find " + material + " deposits", 1));
        steps.add(new GoalStep("MINE_RESOURCE",
            "Mine " + material + " ore", 2));
        steps.add(new GoalStep("SMELT_RESOURCE",
            "Smelt " + material + " ingots", 2));
        steps.add(new GoalStep("UPGRADE_EQUIPMENT",
            "Craft upgraded equipment", 2));

        return new Goal(
            UUID.randomUUID().toString(),
            GoalType.CRAFT_ITEM,
            "Upgrade equipment tier",
            desc -> "Upgrade equipment to " + material + " tier",
            3,
            steps
        );
    }

    /**
     * Generate a goal for gathering resources.
     */
    private Goal generateResourceGoal(AutonomyContext context) {
        InventorySnapshot inventory = context.getInventory();

        // Check for low essential resources
        if (inventory.getWoodCount() < RESOURCE_LOW_THRESHOLD) {
            return createResourceGoal("wood", RESOURCE_LOW_THRESHOLD - inventory.getWoodCount());
        }

        if (inventory.getStoneCount() < RESOURCE_LOW_THRESHOLD) {
            return createResourceGoal("stone", RESOURCE_LOW_THRESHOLD - inventory.getStoneCount());
        }

        if (inventory.getIronCount() < RESOURCE_CRITICAL_THRESHOLD) {
            return createResourceGoal("iron", RESOURCE_CRITICAL_THRESHOLD - inventory.getIronCount());
        }

        // Check for late-game resources
        if (shouldMineDiamonds(inventory)) {
            return createResourceGoal("diamond", 5);
        }

        return null;
    }

    /**
     * Create a goal for gathering a specific resource.
     */
    private Goal createResourceGoal(String resourceType, int amountNeeded) {
        List<GoalStep> steps = new ArrayList<>();

        steps.add(new GoalStep("LOCATE_" + resourceType.toUpperCase(),
            "Find " + resourceType + " source", 1));
        steps.add(new GoalStep("GATHER_" + resourceType.toUpperCase(),
            "Gather " + amountNeeded + " " + resourceType, 2));
        steps.add(new GoalStep("STORE_" + resourceType.toUpperCase(),
            "Store gathered " + resourceType, 3));

        return new Goal(
            UUID.randomUUID().toString(),
            GoalType.GATHER_RESOURCE,
            "Gather " + resourceType,
            desc -> "Gather " + amountNeeded + " " + resourceType + " for crafting and building",
            3,
            steps
        );
    }

    /**
     * Generate a goal for following the owner.
     */
    private Goal generateFollowGoal(AutonomyContext context) {
        if (!context.hasOwnerNearby()) {
            return null;
        }

        double distance = context.getDistanceToOwner();
        if (distance > 32.0) {
            // Owner is far away, follow
            List<GoalStep> steps = new ArrayList<>();
            steps.add(new GoalStep("NAVIGATE_TO_OWNER",
                "Navigate to owner position", 1));
            steps.add(new GoalStep("MAINTAIN_DISTANCE",
                "Stay within follow distance", 2));

            return new Goal(
                UUID.randomUUID().toString(),
                GoalType.FOLLOW_PLAYER,
                "Follow owner",
                desc -> "Follow the owner who is " + String.format("%.0f", distance) + " blocks away",
                4,
                steps
            );
        }

        return null;
    }

    /**
     * Generate a goal for exploration.
     */
    private Goal generateExploreGoal(AutonomyContext context) {
        // Only explore if idle and needs are met
        if (hasUrgentNeeds(context)) {
            return null;
        }

        String currentBiome = context.getCurrentBiome();

        List<GoalStep> steps = new ArrayList<>();
        steps.add(new GoalStep("CHOOSE_DIRECTION",
            "Choose unexplored direction", 1));
        steps.add(new GoalStep("EXPLORE_AREA",
            "Explore new chunks in " + currentBiome, 2));
        steps.add(new GoalStep("MAP_FEATURES",
            "Map notable features and resources", 3));

        return new Goal(
            UUID.randomUUID().toString(),
            GoalType.EXPLORE_AREA,
            "Explore new territory",
            desc -> "Explore " + currentBiome + " biome for resources and structures",
            5,
            steps
        );
    }

    /**
     * Generate a goal for farming.
     */
    private Goal generateFarmGoal(AutonomyContext context) {
        // Only farm if established and have seeds
        if (hasUrgentNeeds(context)) {
            return null;
        }

        InventorySnapshot inventory = context.getInventory();

        // Check if we have farmable resources
        if (inventory.hasItem("wheat_seeds") || inventory.hasItem("carrot") ||
            inventory.hasItem("potato") || inventory.hasItem("beetroot_seeds")) {

            List<GoalStep> steps = new ArrayList<>();
            steps.add(new GoalStep("LOCATE_FARMLAND",
                "Find or create suitable farmland", 1));
            steps.add(new GoalStep("PLANT_CROPS",
                "Plant available crops", 2));
            steps.add(new GoalStep("MAINTAIN_FARM",
                "Check and maintain farm", 3));
            steps.add(new GoalStep("HARVEST_CROPS",
                "Harvest mature crops", 4));

            return new Goal(
                UUID.randomUUID().toString(),
                GoalType.FARM_RESOURCE,
                "Maintain farm",
                desc -> "Plant and maintain crops for sustainable food",
                5,
                steps
            );
        }

        return null;
    }

    // ========== Goal Execution Methods ==========

    /**
     * Continue executing the current goal.
     */
    private ActionResult continueGoalExecution(AutonomyContext context) {
        if (currentGoal == null || currentGoal.isCompleted()) {
            return ActionResult.noAction();
        }

        // Check for goal timeout
        if (System.currentTimeMillis() - goalStartTime > GOAL_TIMEOUT_MS) {
            LOGGER.warn("[Goals] Goal timed out: {}", currentGoal.getDescription());
            abandonCurrentGoal("timeout");
            return ActionResult.noAction();
        }

        return executeCurrentStep(context);
    }

    /**
     * Execute the current step of the goal.
     */
    private ActionResult executeCurrentStep(AutonomyContext context) {
        if (currentGoal == null) {
            return ActionResult.noAction();
        }

        List<GoalStep> steps = currentGoal.getSteps();
        if (currentStepIndex >= steps.size()) {
            completeCurrentGoal();
            return ActionResult.noAction();
        }

        GoalStep currentStep = steps.get(currentStepIndex);

        LOGGER.debug("[Goals] Executing step {}/{}: {}",
            currentStepIndex + 1, steps.size(), currentStep.getDescription());

        return ActionResult.scheduled(
            currentStep.getActionId(),
            currentGoal.getId(),
            currentStep.getDescription()
        );
    }

    /**
     * Complete the current goal.
     */
    private void completeCurrentGoal() {
        if (currentGoal != null) {
            LOGGER.info("[Goals] Goal completed: {}", currentGoal.getDescription());
            currentGoal.markCompleted();

            // Could store in knowledge cache for learning
            if (knowledgeCache != null) {
                storeGoalInCache(currentGoal);
            }
        }

        currentGoal = null;
        currentStepIndex = 0;
        goalStartTime = 0;
    }

    /**
     * Abandon the current goal.
     */
    public void abandonCurrentGoal(String reason) {
        if (currentGoal != null) {
            LOGGER.info("[Goals] Abandoning goal: {} (reason: {})",
                currentGoal.getDescription(), reason);
        }
        currentGoal = null;
        currentStepIndex = 0;
        goalStartTime = 0;
    }

    /**
     * Set a new current goal.
     */
    public void setCurrentGoal(Goal goal) {
        this.currentGoal = goal;
        this.currentStepIndex = 0;
        this.goalStartTime = System.currentTimeMillis();
        LOGGER.info("[Goals] Starting goal: {} with {} steps",
            goal.getDescription(), goal.getSteps().size());
    }

    /**
     * Set a goal from player command.
     */
    public void setPlayerCommandGoal(Goal goal) {
        // Player commands override auto-generated goals
        abandonCurrentGoal("player_command");
        setCurrentGoal(goal);
        LOGGER.info("[Goals] Set player command goal: {}", goal.getDescription());
    }

    // ========== Cache Methods ==========

    /**
     * Check knowledge cache for similar situations.
     */
    private Goal checkCacheForSimilarSituation(AutonomyContext context, List<Goal> goals) {
        // Placeholder for knowledge cache integration
        // When KnowledgeCache is implemented, this will:
        // 1. Create situation signature from context
        // 2. Query cache for similar situations
        // 3. Return learned goal if found

        if (knowledgeCache == null) {
            return null;
        }

        // TODO: Integrate with KnowledgeCache when implemented
        // Example:
        // String situationSignature = createSituationSignature(context);
        // CachedSolution solution = knowledgeCache.query(situationSignature);
        // if (solution != null && solution.getSuccessRate() > 0.8) {
        //     return solutionToGoal(solution);
        // }

        return null;
    }

    /**
     * Store completed goal in cache for learning.
     */
    private void storeGoalInCache(Goal goal) {
        // Placeholder for knowledge cache integration
        // When KnowledgeCache is implemented, this will:
        // 1. Create solution record from goal execution
        // 2. Store in cache with situation signature
        // 3. Track success rate for future reference

        // TODO: Integrate with KnowledgeCache when implemented
    }

    // ========== Utility Methods ==========

    /**
     * Check if there are urgent needs that should prevent exploration.
     */
    private boolean hasUrgentNeeds(AutonomyContext context) {
        InventorySnapshot inventory = context.getInventory();

        // Low food
        if (inventory.getFoodCount() < 4) {
            return true;
        }

        // Low health
        if (context.getHealthPercent() < 0.5f) {
            return true;
        }

        // Missing tools
        if (inventory.getToolCount() < 1) {
            return true;
        }

        // Hostile entities nearby
        if (!context.getHostileEntities().isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * Check if equipment should be upgraded.
     */
    private boolean shouldUpgradeEquipment(InventorySnapshot inventory) {
        // Upgrade to iron if we have iron but using stone
        if (inventory.getIronCount() > 10 && inventory.getBestWeaponTier() < 2) {
            return true;
        }

        // Upgrade to diamond if we have diamonds but using iron
        if (inventory.getDiamondCount() > 5 && inventory.getBestWeaponTier() < 3) {
            return true;
        }

        return false;
    }

    /**
     * Check if we should mine for diamonds.
     */
    private boolean shouldMineDiamonds(InventorySnapshot inventory) {
        // Only go diamond mining if we have good iron equipment
        if (inventory.getBestWeaponTier() < 2) {
            return false;
        }

        // And no diamonds currently
        return inventory.getDiamondCount() < 5;
    }

    /**
     * Determine the best tool tier we can craft.
     */
    private int determineBestToolTier(InventorySnapshot inventory) {
        if (inventory.getDiamondCount() >= 3) return 3;
        if (inventory.getIronCount() >= 3) return 2;
        if (inventory.getStoneCount() >= 3) return 1;
        if (inventory.getWoodCount() >= 2) return 0;
        return -1; // No materials
    }

    /**
     * Determine the best weapon tier we can craft.
     */
    private int determineBestWeaponTier(InventorySnapshot inventory) {
        if (inventory.getDiamondCount() >= 2) return 3;
        if (inventory.getIronCount() >= 2) return 2;
        if (inventory.getStoneCount() >= 2) return 1;
        if (inventory.getWoodCount() >= 2) return 0;
        return -1;
    }

    /**
     * Determine the maximum armor tier we can craft.
     */
    private int determineMaxArmorTier(InventorySnapshot inventory) {
        if (inventory.getDiamondCount() >= 24) return 3;
        if (inventory.getIronCount() >= 24) return 2;
        // Leather would be tier 1
        return 1;
    }

    /**
     * Get the material name for a tier.
     */
    private String getTierMaterialName(int tier) {
        return switch (tier) {
            case 0 -> "wood";
            case 1 -> "stone";
            case 2 -> "iron";
            case 3 -> "diamond";
            case 4 -> "netherite";
            default -> "unknown";
        };
    }

    /**
     * Get the current goal.
     */
    public Goal getCurrentGoal() {
        return currentGoal;
    }

    /**
     * Get the current step index.
     */
    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    // ========== Inner Classes ==========

    /**
     * Represents a goal for the AI player.
     */
    public static class Goal {
        private final String id;
        private final GoalType type;
        private final String name;
        private final String description;
        private final int priority;
        private final List<GoalStep> steps;
        private boolean completed;
        private long startTime;
        private long completionTime;

        public Goal(String id, GoalType type, String name,
                    java.util.function.Function<String, String> descriptionGenerator,
                    int priority, List<GoalStep> steps) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.priority = priority;
            this.steps = new ArrayList<>(steps);
            this.completed = false;
            this.startTime = System.currentTimeMillis();
            this.completionTime = 0;

            // Generate description
            this.description = descriptionGenerator.apply(name);
        }

        public String getId() { return id; }
        public GoalType getType() { return type; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getPriority() { return priority; }
        public List<GoalStep> getSteps() { return steps; }
        public boolean isCompleted() { return completed; }
        public long getStartTime() { return startTime; }
        public long getCompletionTime() { return completionTime; }

        public void markCompleted() {
            this.completed = true;
            this.completionTime = System.currentTimeMillis();
        }

        public boolean isStepCompleted(int stepIndex) {
            if (stepIndex < 0 || stepIndex >= steps.size()) {
                return false;
            }
            return steps.get(stepIndex).isCompleted();
        }

        public void markStepCompleted(int stepIndex) {
            if (stepIndex >= 0 && stepIndex < steps.size()) {
                steps.get(stepIndex).markCompleted();
            }
        }

        public int getCompletedStepCount() {
            return (int) steps.stream().filter(GoalStep::isCompleted).count();
        }

        public float getProgress() {
            if (steps.isEmpty()) return 1.0f;
            return (float) getCompletedStepCount() / steps.size();
        }

        @Override
        public String toString() {
            return String.format("Goal[%s, %s, priority=%d, progress=%.0f%%]",
                type, name, priority, getProgress() * 100);
        }
    }

    /**
     * Represents a single step in a goal plan.
     */
    public static class GoalStep {
        private final String actionId;
        private final String description;
        private final int order;
        private boolean completed;
        private long startTime;
        private long completionTime;
        private int attempts;

        public GoalStep(String actionId, String description, int order) {
            this.actionId = actionId;
            this.description = description;
            this.order = order;
            this.completed = false;
            this.startTime = 0;
            this.completionTime = 0;
            this.attempts = 0;
        }

        public String getActionId() { return actionId; }
        public String getDescription() { return description; }
        public int getOrder() { return order; }
        public boolean isCompleted() { return completed; }
        public long getStartTime() { return startTime; }
        public long getCompletionTime() { return completionTime; }
        public int getAttempts() { return attempts; }

        public void start() {
            this.startTime = System.currentTimeMillis();
            this.attempts++;
        }

        public void markCompleted() {
            this.completed = true;
            this.completionTime = System.currentTimeMillis();
        }

        public void reset() {
            this.completed = false;
            this.startTime = 0;
            this.completionTime = 0;
        }

        @Override
        public String toString() {
            return String.format("Step[%s, order=%d, completed=%s]",
                actionId, order, completed);
        }
    }
}