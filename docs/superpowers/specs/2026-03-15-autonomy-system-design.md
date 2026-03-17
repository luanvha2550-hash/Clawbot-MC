# AI-Player Autonomy System - Design Specification

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a fully autonomous AI bot for Minecraft that can think, decide, act, learn, and communicate naturally without constant player intervention.

**Architecture:** Layered decision system with priority-based execution. Survival first, then combat, then auto-objectives, then player commands, then idle behaviors. Uses Q-Learning for combat (instant), knowledge cache with embeddings for learned behaviors (fast), and LLM for new situations (smart). Includes long-term memory, player observation learning, multi-step planning, and natural communication in Portuguese.

**Tech Stack:** Java 21, Fabric API, Carpet Mod, Gemini/OpenAI API (for embeddings and planning), Q-Learning (local), JSON persistence for memory.

---

## 1. Overview

### 1.1 Problem Statement

The current AI-Player bot has multiple decision systems (RLAgent, HybridPlanner, FunctionCaller) that operate in isolation without a unified action loop. The bot "thinks" but doesn't act autonomously. It only responds to threats reactively and requires constant player commands.

### 1.2 Solution

Implement a unified **Autonomy Engine** that:
- Runs a continuous decision loop (100ms ticks)
- Processes priorities in order: Survival → Combat → Auto-Objectives → Player Commands → Idle
- Learns from player observation and caches successful behaviors
- Communicates naturally in Portuguese
- Alerts on important discoveries (diamonds, dangers, structures)

### 1.3 Key Features

| Feature | Description |
|---------|-------------|
| **Survival Layer** | Instant reflexes for health, hunger, environmental dangers |
| **Combat Layer** | Q-Learning based combat (existing RLAgent) |
| **Auto-Objectives** | Self-generated goals based on needs |
| **Long-Term Memory** | Locations, death spots, player relationships, learned skills |
| **Player Observation** | Learns patterns from watching the player |
| **Multi-Step Planning** | Chains actions for complex goals |
| **Natural Communication** | Understands Portuguese, asks clarifications, reports progress |
| **Diamond Alerts** | Immediate notification when diamonds found |

---

## 2. Architecture

### 2.1 System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      AUTONOMY ENGINE v2.0                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     MEMORY SYSTEM                                │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │   │
│  │  │ World Memory │ │ Player Memory│ │ Skill Memory │            │   │
│  │  │ - Locations  │ │ - Players   │ │ - Patterns   │            │   │
│  │  │ - Death Spots│ │ - Trust     │ │ - Success/Fail│           │   │
│  │  │ - Resources  │ │ - Behaviors │ │ - Context    │            │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                          │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   DECISION LAYERS (Priority Order)               │   │
│  │                                                                  │   │
│  │  LAYER 1: SURVIVAL (instant, no LLM)                             │   │
│  │  ├── Health < 20% → Emergency eat/flee                          │   │
│  │  ├── In lava → Emergency exit                                   │   │
│  │  ├── Falling into void → Emergency block placement               │   │
│  │  └── Drowning → Swim to surface                                  │   │
│  │                                                                  │   │
│  │  LAYER 2: COMBAT (RLAgent, instant)                              │   │
│  │  └── Hostile entity detected → Q-Learning action selection       │   │
│  │                                                                  │   │
│  │  LAYER 3: AUTO-OBJECTIVES (cached + LLM)                         │   │
│  │  ├── Need food → Farm/hunt goal                                  │   │
│  │  ├── Need equipment → Craft goal                                 │   │
│  │  ├── Night approaching → Shelter goal                           │   │
│  │  └── Low resources → Gather goal                                 │   │
│  │                                                                  │   │
│  │  LAYER 4: PLAYER COMMANDS (NLP + Planning)                       │   │
│  │  └── "minera diamante" → Multi-step execution                    │   │
│  │                                                                  │   │
│  │  LAYER 5: IDLE (Follow/Observe)                                  │   │
│  │  ├── Follow owner player                                         │   │
│  │  ├── Observe player patterns                                     │   │
│  │  └── Patrol nearby area                                          │   │
│  │                                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                          │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    SUPPORT SYSTEMS                               │   │
│  │                                                                  │   │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐       │   │
│  │  │  Observation   │ │   Planning     │ │    Alerts      │       │   │
│  │  │  System        │ │   System       │ │    System      │       │   │
│  │  └────────────────┘ └────────────────┘ └────────────────┘       │   │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐       │   │
│  │  │  Communication│ │  Pathfinding   │ │  Knowledge     │       │   │
│  │  │  System        │ │  System        │ │  Cache         │       │   │
│  │  └────────────────┘ └────────────────┘ └────────────────┘       │   │
│  │                                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                          │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    ACTION EXECUTOR                                │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │   │
│  │  │ Movement     │ │ Combat       │ │ Interaction  │            │   │
│  │  │ - Walk       │ │ - Attack     │ │ - Mine       │            │   │
│  │  │ - Jump       │ │ - Defend     │ │ - Place      │            │   │
│  │  │ - Sprint     │ │ - Evade      │ │ - Craft      │            │   │
│  │  │ - Follow     │ │ - Combo      │ │ - Interact   │            │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Data Flow

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   World      │────▶│  Context     │────▶│  Priority   │
│   State      │     │  Gathering   │     │  Evaluation │
└──────────────┘     └──────────────┘     └──────────────┘
                                                 │
                                                 ▼
                     ┌──────────────────────────────────────────┐
                     │              DECISION                     │
                     │                                          │
                     │  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
                     │  │Survival │  │ Combat  │  │ Goals   │  │
                     │  │ (100%)  │  │ (100%)  │  │ (Cache) │  │
                     │  └─────────┘  └─────────┘  └─────────┘  │
                     │        │            │            │      │
                     │        └────────────┴────────────┘      │
                     │                     │                   │
                     │                     ▼                   │
                     │            ┌──────────────┐           │
                     │            │    Action     │           │
                     │            │    Executor   │           │
                     │            └──────────────┘           │
                     └───────────────────────────────────────┘
                                          │
                                          ▼
                     ┌──────────────────────────────────────────┐
                     │           EXECUTE & LEARN                 │
                     │                                          │
                     │  ┌─────────────────────────────────────┐ │
                     │  │ 1. Execute action                    │ │
                     │  │ 2. Observe result                    │ │
                     │  │ 3. Record success/failure            │ │
                     │  │ 4. Update memory cache               │ │
                     │  │ 5. Send alert if needed              │ │
                     │  └─────────────────────────────────────┘ │
                     └──────────────────────────────────────────┘
```

---

## 3. Components

### 3.1 AutonomyEngine

**File:** `net.shasankp000.Autonomy.AutonomyEngine`

**Purpose:** Main loop that coordinates all decision layers.

**Responsibilities:**
- Run tick loop every 100ms
- Gather context from world state
- Evaluate layers in priority order
- Execute selected action
- Handle interrupts (survival events)

```java
public class AutonomyEngine {
    private static final int TICK_INTERVAL_MS = 100;

    private final SurvivalLayer survivalLayer;
    private final CombatLayer combatLayer;
    private final GoalsLayer goalsLayer;
    private final CommandLayer commandLayer;
    private final IdleLayer idleLayer;

    private final KnowledgeCache knowledgeCache;
    private final LongTermMemory longTermMemory;
    private final ObservationSystem observationSystem;
    private final AlertSystem alertSystem;

    private void tick() {
        // 1. Gather context
        AutonomyContext context = gatherContext();

        // 2. Evaluate layers in priority order
        // Layer 1: Survival (always first)
        ActionResult result = survivalLayer.evaluate(context);
        if (result.shouldExecute()) {
            executeAction(result);
            return;
        }

        // Layer 2: Combat (if threats)
        if (context.hasHostileThreats()) {
            result = combatLayer.evaluate(context);
            if (result.shouldExecute()) {
                executeAction(result);
                return;
            }
        }

        // Layer 3: Auto-objectives
        if (context.hasAutoGoal()) {
            result = goalsLayer.evaluate(context);
            if (result.shouldExecute()) {
                executeAction(result);
                recordOutcome(context, result);
                return;
            }
        }

        // Layer 4: Player commands
        if (context.hasPendingCommand()) {
            result = commandLayer.evaluate(context);
            if (result.shouldExecute()) {
                executeAction(result);
                return;
            }
        }

        // Layer 5: Idle
        result = idleLayer.evaluate(context);
        executeAction(result);
    }
}
```

### 3.2 SurvivalLayer

**File:** `net.shasankp000.Autonomy.Layers.SurvivalLayer`

**Purpose:** Instant reflexes for survival - no LLM, pure rules.

**Thresholds:**
| Condition | Threshold | Action |
|-----------|-----------|--------|
| Health critical | < 6 (3 hearts) | Emergency eat/flee |
| Health low | < 10 (5 hearts) | Eat food |
| Hunger critical | < 6 (3 bars) | Emergency eat |
| Hunger low | < 10 (5 bars) | Find food |
| In lava | N/A | Exit immediately |
| Falling into void | Y < -60 | Place block below |
| Drowning | Air < 30 | Swim to surface |
| Burning | On fire | Find water |

**Implementation:**
```java
public class SurvivalLayer implements DecisionLayer {

    private static final float HEALTH_CRITICAL = 6.0f;
    private static final float HEALTH_LOW = 10.0f;
    private static final int HUNGER_CRITICAL = 6;
    private static final int HUNGER_LOW = 10;

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // 1. Critical dangers (instant death)
        if (isInLava(context)) {
            return ActionResult.immediate("EXIT_LAVA", () -> exitLava());
        }

        if (isFallingIntoVoid(context)) {
            return ActionResult.immediate("VOID_SAVE", () -> placeBlockBelow());
        }

        if (isDrowning(context)) {
            return ActionResult.immediate("SWIM_UP", () -> swimToSurface());
        }

        // 2. Health critical
        if (context.getBotHealth() < HEALTH_CRITICAL) {
            return createEmergencyHealthAction(context);
        }

        // 3. Hunger critical
        if (context.getBotHunger() < HUNGER_CRITICAL) {
            return createEmergencyHungerAction(context);
        }

        // 4. Environmental hazards
        ActionResult hazard = checkEnvironmentalHazards(context);
        if (hazard != null) return hazard;

        // 5. Low health/hunger (non-critical)
        if (context.getBotHealth() < HEALTH_LOW) {
            return createHealthAction(context);
        }

        if (context.getBotHunger() < HUNGER_LOW) {
            return createHungerAction(context);
        }

        return ActionResult.noAction();
    }
}
```

### 3.3 CombatLayer

**File:** `net.shasankp000.Autonomy.Layers.CombatLayer`

**Purpose:** Wrapper around existing RLAgent for combat decisions.

**Extends:** Existing `net.shasankp000.GameAI.RLAgent`

**New Actions:**
- `BLOCK` - Use shield
- `RETREAT` - Tactical withdrawal
- `COMBO_ATTACK` - Chain attacks
- `KITE` - Hit and run

**Integration:**
```java
public class CombatLayer implements DecisionLayer {

    private final RLAgent rlAgent;
    private final AdvancedCombatAI advancedCombat;

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        if (!context.hasHostileThreats()) {
            return ActionResult.noAction();
        }

        // Get threat assessment from AdvancedCombatAI
        List<TargetInfo> targets = advancedCombat.prioritizeTargets(
            bot, context.getHostileEntities(), context.getOwner()
        );

        // Get action from RLAgent
        State currentState = buildState(context);
        StateActions.Action action = rlAgent.chooseAction(currentState, qTable);

        // Convert to ActionResult
        return convertToActionResult(action, targets);
    }
}
```

### 3.4 GoalsLayer

**File:** `net.shasankp000.Autonomy.Layers.GoalsLayer`

**Purpose:** Manages multi-step goals with LLM planning and caching.

**Goal Types:**
```java
public enum GoalType {
    GATHER_RESOURCE,    // Collect wood, stone, iron, diamonds
    BUILD_STRUCTURE,     // Shelter, house, farm
    CRAFT_ITEM,          // Tools, weapons, armor
    EXPLORE_AREA,        // New caves, structures
    FOLLOW_PLAYER,       // Stay near owner
    DEFEND_LOCATION,     // Guard an area
    FARM_RESOURCE        // Sustainable resource production
}
```

**Goal Generation:**
```java
public class GoalsLayer implements DecisionLayer {

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // 1. If executing a plan, continue
        if (currentPlan != null && currentStepIndex < currentPlan.size()) {
            return executeNextPlanStep(context);
        }

        // 2. Generate auto-goals based on needs
        List<Goal> neededGoals = analyzeNeeds(context);

        // 3. Check cache for similar situations
        Optional<String> cachedAction = knowledgeCache.findActionForSituation(
            context.getSituationEmbedding()
        );

        if (cachedAction.isPresent()) {
            return executeCachedAction(cachedAction.get(), context);
        }

        // 4. Use LLM for new situation
        return planWithLLM(context);
    }

    private List<Goal> analyzeNeeds(AutonomyContext context) {
        List<Goal> goals = new ArrayList<>();

        // Food priority
        if (context.getBotHunger() < 14) {
            goals.add(new Goal(GoalType.FARM_RESOURCE, "food", 90));
        }

        // Equipment priority
        if (!context.hasCompleteArmor()) {
            goals.add(new Goal(GoalType.CRAFT_ITEM, "armor", 70));
        }

        if (context.getBestWeaponTier() < 2) {
            goals.add(new Goal(GoalType.CRAFT_ITEM, "better_weapon", 60));
        }

        // Resource gathering
        if (context.getWoodCount() < 10) {
            goals.add(new Goal(GoalType.GATHER_RESOURCE, "wood", 50));
        }

        if (context.getStoneCount() < 10) {
            goals.add(new Goal(GoalType.GATHER_RESOURCE, "stone", 45));
        }

        // Night shelter
        if (context.isNightApproaching() && !context.hasNearbyShelter()) {
            goals.add(new Goal(GoalType.BUILD_STRUCTURE, "shelter", 80));
        }

        return goals;
    }
}
```

### 3.5 Long-Term Memory

**File:** `net.shasankp000.Autonomy.Memory.LongTermMemory`

**Purpose:** Persistent storage of learned information.

**Data Structures:**
```java
public class LongTermMemory {

    // World locations
    private Map<String, WorldLocation> locations;

    // Death memory (avoid these spots)
    private List<DeathSpot> deathSpots;

    // Player relationships
    private Map<UUID, PlayerMemory> playerMemories;

    // Learned skills
    private Map<String, SkillMemory> skills;

    // Observed patterns
    private List<BehaviorPattern> observedPatterns;

    // Persistence
    private Path memoryFile;
}

public class WorldLocation {
    public String id;
    public String name;
    public Vec3d position;
    public LocationType type; // BASE, RESOURCE, DANGER, INTERESTING
    public Map<String, Integer> resources; // diamond: 5, iron: 20
    public long lastVisited;
    public int visitCount;
    public double safetyRating; // 0.0 - 1.0
}

public class DeathSpot {
    public Vec3d position;
    public String cause; // "LAVA", "CREEPER", "FALL"
    public int deathCount;
    public long lastDeathTime;
}

public class PlayerMemory {
    public UUID playerId;
    public String name;
    public int trustLevel; // 0-100
    public List<String> observedBehaviors;
    public List<String> commandsGiven;
    public long lastInteraction;
}

public class SkillMemory {
    public String skillId;
    public String description;
    public int successCount;
    public int failureCount;
    public List<Double> situationEmbedding;
    public String bestAction;
    public double confidenceScore;
}
```

### 3.6 ObservationSystem

**File:** `net.shasankp000.Autonomy.Observation.ObservationSystem`

**Purpose:** Watch and learn from player behavior.

**Implementation:**
```java
public class ObservationSystem {

    private static final int OBSERVATION_BUFFER_SIZE = 300; // 30 seconds at 10 ticks/sec

    private final Queue<PlayerAction> actionBuffer;
    private final PatternDetector patternDetector;
    private final LongTermMemory memory;

    // Called every tick when observing player
    public void recordPlayerAction(ServerPlayerEntity player, AutonomyContext context) {
        PlayerAction action = captureAction(player, context);
        actionBuffer.add(action);

        // Try to detect patterns
        Optional<BehaviorPattern> pattern = patternDetector.detect(actionBuffer);

        if (pattern.isPresent()) {
            // Store learned pattern
            memory.storePattern(pattern.get());

            // Apply to bot behavior
            applyLearnedPattern(pattern.get());
        }
    }

    private PlayerAction captureAction(ServerPlayerEntity player, AutonomyContext context) {
        return PlayerAction.builder()
            .position(player.getPos())
            .rotation(player.getYaw(), player.getPitch())
            .handItem(player.getMainHandStack())
            .targetBlock(player.raycast(5))
            .nearbyEntities(context.getNearbyEntities())
            .timestamp(System.currentTimeMillis())
            .build();
    }
}

public class BehaviorPattern {
    public String patternId;
    public String description;
    public List<ActionStep> steps;
    public String triggerCondition; // "when diamond_found"
    public int observationCount;
    public double confidenceScore;
}
```

### 3.7 Multi-Step Planning

**File:** `net.shasankp000.Autonomy.Planning.MultiStepPlanner`

**Purpose:** Decompose complex goals into executable steps.

**Recipe Knowledge:**
```java
public class RecipeKnowledge {

    private static final Map<String, Recipe> RECIPES = Map.of(
        "diamond_sword", new Recipe(
            List.of("diamond:2", "stick:1"),
            "crafting_table",
            List.of("diamond", "diamond", "stick"),
            "vertical"
        ),
        "iron_pickaxe", new Recipe(
            List.of("iron_ingot:3", "stick:2"),
            "crafting_table",
            List.of("iron", "iron", "iron", "", "stick", "", "", "stick", ""),
            "grid"
        ),
        // ... more recipes
    );

    private static final Map<String, List<String>> DEPENDENCIES = Map.of(
        "diamond_sword", List.of("diamond", "stick"),
        "stick", List.of("planks"),
        "planks", List.of("wood"),
        "iron_ingot", List.of("iron_ore", "furnace", "fuel"),
        "furnace", List.of("cobblestone:8"),
        // ... dependency chains
    );
}
```

**Plan Execution:**
```java
public class MultiStepPlanner {

    public Plan createPlan(String goal, AutonomyContext context) {
        // 1. Check if goal requires crafting
        if (isCraftingGoal(goal)) {
            return createCraftingPlan(goal, context);
        }

        // 2. Check if goal requires gathering
        if (isGatheringGoal(goal)) {
            return createGatheringPlan(goal, context);
        }

        // 3. Check if goal requires building
        if (isBuildingGoal(goal)) {
            return createBuildingPlan(goal, context);
        }

        // 4. Use LLM for complex/unknown goals
        return planWithLLM(goal, context);
    }

    private Plan createCraftingPlan(String item, AutonomyContext context) {
        Recipe recipe = RecipeKnowledge.getRecipe(item);
        Plan plan = new Plan(goal);

        // Check what we already have
        for (String ingredient : recipe.getIngredients()) {
            if (!context.hasItem(ingredient)) {
                // Need to obtain this ingredient
                Plan subPlan = createPlan(ingredient, context);
                plan.addSubPlan(subPlan);
            }
        }

        // Add crafting step
        plan.addStep(new CraftingStep(recipe));

        return plan;
    }

    private Plan createGatheringPlan(String resource, AutonomyContext context) {
        Plan plan = new Plan("Gather " + resource);

        // Find best location for this resource
        Optional<WorldLocation> location = memory.findBestResourceLocation(resource);

        if (location.isPresent()) {
            plan.addStep(new NavigationStep(location.get().position));
            plan.addStep(new MiningStep(resource, location.get()));
        } else {
            // Unknown resource - use exploration
            plan.addStep(new ExplorationStep(resource));
        }

        return plan;
    }
}

public class Plan {
    private String goal;
    private List<PlanStep> steps;
    private int currentStep;
    private PlanStatus status;

    public PlanStep getNextStep() {
        return steps.get(currentStep);
    }

    public void stepCompleted() {
        currentStep++;
        if (currentStep >= steps.size()) {
            status = PlanStatus.COMPLETED;
        }
    }

    public void stepFailed() {
        status = PlanStatus.FAILED;
        // Trigger replanning
    }
}
```

### 3.8 CommunicationSystem

**File:** `net.shasankp000.Autonomy.Communication.CommunicationSystem`

**Purpose:** Natural language interaction in Portuguese.

**Intent Recognition:**
```java
public class IntentRecognizer {

    private final EmbeddingProvider embeddingProvider;
    private final Map<String, List<String>> intentPatterns;

    public IntentRecognizer(EmbeddingProvider provider) {
        this.embeddingProvider = provider;
        this.intentPatterns = loadPatterns();
    }

    // Portuguese command patterns
    private Map<String, List<String>> loadPatterns() {
        return Map.of(
            "COMBAT", List.of(
                "ataca (.*)", "mata (.*)", "liga pro (.*)",
                "defende", "protege"
            ),
            "FOLLOW", List.of(
                "me segue", "vem aqui", "anda comigo",
                "vem", "segue"
            ),
            "GATHER", List.of(
                "minera (.*)", "pega (.*)", "coleta (.*)",
                "proucura (.*)", "acha (.*)"
            ),
            "BUILD", List.of(
                "faz (.*)", "constrói (.*)", "monta (.*)",
                "cria (.*)"
            ),
            "CRAFT", List.of(
                "crafta (.*)", "faz uma (.*)", "cria (.*)"
            ),
            "REPORT", List.of(
                "como tá (.*)", "qual (.*)", "mostra (.*)",
                "inventário", "vida", "status"
            ),
            "WAIT", List.of(
                "espera", "para", "fica aqui"
            ),
            "HELP", List.of(
                "ajuda", "o que você faz", "comandos"
            )
        );
    }

    public IntentResult recognize(String message) {
        message = message.toLowerCase().trim();

        for (Map.Entry<String, List<String>> entry : intentPatterns.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (message.matches(pattern)) {
                    return new IntentResult(
                        entry.getKey(),
                        extractEntities(message, pattern)
                    );
                }
            }
        }

        return IntentResult.unknown();
    }

    private Map<String, String> extractEntities(String message, String pattern) {
        // Extract resource names, quantities, targets
        Map<String, String> entities = new HashMap<>();

        // Portuguese resource mapping
        Map<String, String> resourceMap = Map.of(
            "diamante", "DIAMOND",
            "ferro", "IRON",
            "madeira", "WOOD",
            "pedra", "STONE",
            "carvão", "COAL",
            "ouro", "GOLD"
        );

        for (Map.Entry<String, String> entry : resourceMap.entrySet()) {
            if (message.contains(entry.getKey())) {
                entities.put("resource", entry.getValue());
            }
        }

        // Extract quantities
        Pattern quantityPattern = Pattern.compile("(\\d+)");
        Matcher matcher = quantityPattern.matcher(message);
        if (matcher.find()) {
            entities.put("quantity", matcher.group(1));
        }

        return entities;
    }
}
```

**Response Generator:**
```java
public class ResponseGenerator {

    public String generateResponse(IntentResult intent, AutonomyContext context) {
        return switch (intent.getType()) {
            case "COMBAT" -> generateCombatResponse(intent, context);
            case "FOLLOW" -> generateFollowResponse(intent);
            case "GATHER" -> generateGatherResponse(intent, context);
            case "BUILD" -> generateBuildResponse(intent);
            case "CRAFT" -> generateCraftResponse(intent, context);
            case "REPORT" -> generateReportResponse(context);
            case "WAIT" -> "Beleza, vou ficar aqui esperando.";
            case "HELP" -> generateHelpResponse();
            default -> "Não entendi. Pode reformular?";
        };
    }

    private String generateGatherResponse(IntentResult intent, AutonomyContext context) {
        String resource = intent.getEntity("resource");

        if (resource == null) {
            return "O que você quer que eu minere?";
        }

        // Check if we know where to find it
        Optional<WorldLocation> location = memory.findBestResourceLocation(resource);

        if (location.isPresent()) {
            return String.format("Beleza! Vou minerar %s. Sei de um lugar em %s.",
                resource, location.get().position);
        }

        return String.format("Vou procurar %s. Qualquer coisa te aviso!", resource);
    }

    private String generateHelpResponse() {
        return """
            🤖 Comandos disponíveis:

            ⚔️ Combate: "ataca [mob]", "defende"
            👣 Movimento: "me segue", "espera aqui"
            ⛏️ Coleta: "minera [recurso]", "pega [item]"
            🔨 Construção: "faz uma casa", "constrói abrigo"
            📊 Status: "como tá seu inventário?", "vida"
            ❓ Ajuda: "ajuda"

            Fale em português natural! Ex: "minera diamante pra mim"
            """;
    }
}
```

### 3.9 AlertSystem

**File:** `net.shasankp000.Autonomy.Alert.AlertSystem`

**Purpose:** Notify player of important events.

**Alert Types:**
```java
public enum AlertType {
    DIAMOND_FOUND(true, "💎", 1),      // Critical
    DANGER_DETECTED(true, "⚠️", 2),    // High
    STRUCTURE_FOUND(false, "🏰", 3),   // Medium
    RARE_RESOURCE(false, "📦", 3),     // Medium
    GOAL_COMPLETE(false, "✅", 4),     // Low
    PLAYER_DEATH(true, "💀", 1),       // Critical

    // Configurable
    private final boolean immediate;
    private final String icon;
    private final int priority;
}

public class AlertSystem {

    private final ServerPlayerEntity owner;
    private final AlertConfig config;

    // Special handling for diamond detection
    public void onDiamondFound(Vec3d position, int count) {
        if (!config.isAlertEnabled("diamond")) return;

        String message = String.format(
            "💎💎💎 DIAMANTES! Encontrei %d diamante%s em X=%d, Y=%d, Z=%d!",
            count,
            count > 1 ? "s" : "",
            (int) position.x,
            (int) position.y,
            (int) position.z
        );

        sendChatMessage(message);

        // Additional: stop mining, excavate around
        currentPlan.pause();
        excavateAroundDiamonds(position);
    }

    // Danger alerts
    public void onDangerDetected(String dangerType, Vec3d position) {
        String message = String.format(
            "⚠️ Cuidado! %s detectado em X=%d, Y=%d, Z=%d",
            dangerType,
            (int) position.x,
            (int) position.y,
            (int) position.z
        );

        sendChatMessage(message);
    }

    // Structure found
    public void onStructureFound(String structureType, Vec3d position) {
        if (!config.isAlertEnabled("structure")) return;

        String message = String.format(
            "🏰 Encontrei %s! Localização: X=%d, Y=%d, Z=%d",
            structureType,
            (int) position.x,
            (int) position.y,
            (int) position.z
        );

        sendChatMessage(message);

        // Save to memory
        memory.saveLocation(new WorldLocation(
            "structure_" + structureType,
            structureType,
            position,
            LocationType.INTERESTING
        ));
    }

    private void sendChatMessage(String message) {
        owner.sendMessage(Text.literal("[Bot] " + message), false);
    }
}
```

### 3.10 KnowledgeCache

**File:** `net.shasankp000.Autonomy.Cache.KnowledgeCache`

**Purpose:** Cache learned behaviors with embedding similarity search.

**Implementation:**
```java
public class KnowledgeCache {

    private static final double SIMILARITY_THRESHOLD = 0.85;

    private final Map<String, CachedSituation> situations = new ConcurrentHashMap<>();
    private final EmbeddingProvider embeddingProvider;

    /**
     * Find a cached action for a similar situation.
     * Returns empty if no similar situation found or confidence too low.
     */
    public Optional<String> findActionForSituation(List<Double> embedding) {
        CachedSituation best = null;
        double bestSimilarity = 0.0;

        for (CachedSituation cached : situations.values()) {
            double similarity = cosineSimilarity(embedding, cached.embedding);

            if (similarity > bestSimilarity && similarity >= SIMILARITY_THRESHOLD) {
                if (cached.isReliable()) {
                    best = cached;
                    bestSimilarity = similarity;
                }
            }
        }

        return best != null ? Optional.of(best.bestAction) : Optional.empty();
    }

    /**
     * Learn a new situation-action pair.
     */
    public void learnSituation(String description, String action,
                              List<Double> embedding, boolean success) {
        String id = generateId(description);
        CachedSituation existing = situations.get(id);

        if (existing != null) {
            // Update existing
            CachedSituation updated = existing.withUpdatedOutcome(success);
            situations.put(id, updated);
        } else {
            // Create new
            CachedSituation newEntry = new CachedSituation(
                id, description, embedding, action,
                success ? 1 : 0, success ? 0 : 1
            );
            situations.put(id, newEntry);
        }
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

public class CachedSituation {
    public final String id;
    public final String description;
    public final List<Double> embedding;
    public final String bestAction;
    public final int successCount;
    public final int failureCount;
    public final long lastUsed;

    public boolean isReliable() {
        return successCount >= 3 && getSuccessRate() >= 0.7;
    }

    public double getSuccessRate() {
        int total = successCount + failureCount;
        return total == 0 ? 0.0 : (double) successCount / total;
    }
}
```

### 3.11 CommandLayer

**File:** `net.shasankp000.Autonomy.Layers.CommandLayer`

**Purpose:** Process and execute player commands from chat.

**Responsibilities:**
- Queue incoming player commands
- Parse Portuguese natural language
- Convert to executable plans
- Track command completion status

```java
public class CommandLayer implements DecisionLayer {

    private final Queue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private PlayerCommand currentCommand;
    private Plan currentPlan;

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // If executing a command, continue
        if (currentPlan != null && !currentPlan.isComplete()) {
            return executeNextPlanStep(context);
        }

        // Check for new command
        if (currentCommand == null) {
            currentCommand = commandQueue.poll();
            if (currentCommand == null) {
                return ActionResult.noAction();
            }
            currentPlan = createPlanForCommand(currentCommand, context);
        }

        return executeNextPlanStep(context);
    }

    public void queueCommand(PlayerCommand command) {
        commandQueue.add(command);
    }

    private Plan createPlanForCommand(PlayerCommand command, AutonomyContext context) {
        return switch (command.getIntent()) {
            case GATHER -> createGatherPlan(command.getEntity("resource"), context);
            case BUILD -> createBuildPlan(command.getEntity("structure"), context);
            case CRAFT -> createCraftPlan(command.getEntity("item"), context);
            case FOLLOW -> createFollowPlan(context);
            case COMBAT -> createCombatPlan(command.getEntity("target"), context);
            default -> Plan.unknown();
        };
    }
}

public class PlayerCommand {
    private final IntentType intent;
    private final Map<String, String> entities;
    private final UUID playerId;
    private final long timestamp;

    public String getEntity(String key) {
        return entities.get(key);
    }
}
```

### 3.12 IdleLayer

**File:** `net.shasankp000.Autonomy.Layers.IdleLayer`

**Purpose:** Default behavior when nothing else to do.

**Behaviors (in priority order):**
1. Follow owner player if nearby
2. Observe player and learn patterns
3. Patrol nearby area for threats
4. Return to safe location

```java
public class IdleLayer implements DecisionLayer {

    private static final double FOLLOW_DISTANCE = 5.0;
    private static final double PATROL_RADIUS = 16.0;

    private Vec3d patrolCenter;
    private long lastObservationTick;

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // 1. Follow player if exists
        if (context.hasOwnerNearby()) {
            double distance = context.getDistanceToOwner();
            if (distance > FOLLOW_DISTANCE) {
                return ActionResult.followOwner(context.getOwnerPosition());
            }
        }

        // 2. Observe player patterns (every 20 ticks)
        if (shouldObserve(context.getCurrentTick())) {
            return ActionResult.observePlayer(context);
        }

        // 3. Patrol if player not nearby
        if (!context.hasOwnerNearby()) {
            return patrolOrReturn(context);
        }

        return ActionResult.noAction();
    }

    private ActionResult patrolOrReturn(AutonomyContext context) {
        if (patrolCenter == null) {
            patrolCenter = context.getBotPosition();
        }

        // Return to safe location if too far
        if (context.getDistanceFrom(patrolCenter) > PATROL_RADIUS) {
            return ActionResult.moveTo(patrolCenter);
        }

        // Random patrol
        return ActionResult.patrol(patrolCenter, PATROL_RADIUS);
    }

    private boolean shouldObserve(long currentTick) {
        return currentTick - lastObservationTick >= 20;
    }
}
```

### 3.13 Core Interfaces

**DecisionLayer Interface:**
```java
public interface DecisionLayer {
    /**
     * Evaluate the context and determine what action to take.
     * @param context Current world state
     * @return The action to execute, or noAction() if this layer has nothing to do
     */
    ActionResult evaluate(AutonomyContext context);
}
```

**ActionResult Class:**
```java
public class ActionResult {
    public enum Type { IMMEDIATE, NORMAL, SCHEDULED, NO_ACTION, COMPLETED }

    private final Type type;
    private final String actionId;
    private final String description;
    private final Runnable executor;
    private final boolean success;

    // Factory methods
    public static ActionResult noAction() {
        return new ActionResult(Type.NO_ACTION, null, null, null, false);
    }

    public static ActionResult immediate(String actionId, Runnable executor, String desc) {
        return new ActionResult(Type.IMMEDIATE, actionId, desc, executor, false);
    }

    public static ActionResult normal(String actionId, Runnable executor, String desc) {
        return new ActionResult(Type.NORMAL, actionId, desc, executor, false);
    }

    public static ActionResult scheduled(String actionId, String firstAction, String desc) {
        return new ActionResult(Type.SCHEDULED, actionId, desc, null, false);
    }

    public static ActionResult completed(String description) {
        return new ActionResult(Type.COMPLETED, null, description, null, true);
    }

    public static ActionResult followOwner(Vec3d ownerPosition) {
        return new ActionResult(Type.NORMAL, "FOLLOW",
            "Following owner at " + ownerPosition,
            () -> navigateTo(ownerPosition), false);
    }

    public static ActionResult observePlayer(AutonomyContext context) {
        return new ActionResult(Type.NORMAL, "OBSERVE",
            "Observing player behavior",
            () -> recordPlayerAction(context), false);
    }

    public boolean shouldExecute() {
        return type != Type.NO_ACTION && type != Type.COMPLETED;
    }

    public boolean wasSuccessful() {
        return success;
    }
}
```

### 3.14 AutonomyContext

**File:** `net.shasankp000.Autonomy.AutonomyContext`

**Purpose:** Snapshot of the current world state for decision making.

```java
public class AutonomyContext {
    // Bot state
    private Vec3d botPosition;
    private Vec3d botVelocity;
    private float botHealth;
    private int botHunger;
    private int botAir;
    private float botYaw, botPitch;

    // Environment
    private List<Entity> nearbyEntities;
    private List<Entity> hostileEntities;
    private Map<String, Integer> nearbyBlocks; // block type -> count
    private String currentBiome;
    private boolean isDaytime;
    private long worldTick;

    // Inventory
    private InventorySnapshot inventory;

    // Player (owner)
    private Vec3d ownerPosition;
    private UUID ownerUUID;
    private double distanceToOwner;

    // Computed
    private List<Double> situationEmbedding;
    private String situationId;

    // State flags
    private boolean hasImmediateDanger;
    private boolean hasHostileThreats;
    private boolean hasPendingCommand;
    private boolean hasAutoGoal;

    // Getters
    public float getBotHealth() { return botHealth; }
    public int getBotHunger() { return botHunger; }
    public Vec3d getBotPosition() { return botPosition; }
    public List<Entity> getHostileEntities() { return hostileEntities; }
    public boolean hasHostileThreats() { return !hostileEntities.isEmpty(); }
    public boolean hasImmediateDanger() { return hasImmediateDanger; }
    public boolean hasPendingCommand() { return hasPendingCommand; }
    public boolean hasOwnerNearby() { return ownerPosition != null; }

    public double getDistanceToOwner() { return distanceToOwner; }
    public Vec3d getOwnerPosition() { return ownerPosition; }

    public List<Double> getSituationEmbedding() { return situationEmbedding; }
    public String getSituationDescription() {
        return String.format("Health: %.0f/20, Hunger: %d/20, Hostiles: %d, Biome: %s",
            botHealth, botHunger, hostileEntities.size(), currentBiome);
    }

    // Builder pattern for construction
    public static class Builder {
        private AutonomyContext context = new AutonomyContext();

        public Builder botPosition(Vec3d pos) { context.botPosition = pos; return this; }
        public Builder botHealth(float health) { context.botHealth = health; return this; }
        public Builder botHunger(int hunger) { context.botHunger = hunger; return this; }
        public Builder hostileEntities(List<Entity> entities) {
            context.hostileEntities = entities;
            context.hasHostileThreats = !entities.isEmpty();
            return this;
        }
        public Builder ownerPosition(Vec3d pos) { context.ownerPosition = pos; return this; }

        public AutonomyContext build() { return context; }
    }
}

public class InventorySnapshot {
    private Map<String, Integer> items = new HashMap<>();
    private int woodCount;
    private int stoneCount;
    private int ironCount;
    private int diamondCount;
    private int foodCount;
    private int bestWeaponTier; // 0=wood, 1=stone, 2=iron, 3=diamond
    private int bestArmorTier;
    private boolean hasCompleteArmor;

    public int getWoodCount() { return woodCount; }
    public int getStoneCount() { return stoneCount; }
    public int getIronCount() { return ironCount; }
    public int getDiamondCount() { return diamondCount; }
    public int getBestWeaponTier() { return bestWeaponTier; }
    public boolean hasCompleteArmor() { return hasCompleteArmor; }
}
```

---

## 4. File Structure

### 4.1 New Files

```
src/main/java/net/shasankp000/
├── Autonomy/
│   ├── AutonomyEngine.java           # Main loop
│   ├── AutonomyContext.java          # World state snapshot
│   ├── ActionResult.java             # Action execution result
│   ├── DecisionLayer.java            # Interface for layers
│   ├── Layers/
│   │   ├── SurvivalLayer.java        # Survival reflexes
│   │   ├── CombatLayer.java          # Combat wrapper
│   │   ├── GoalsLayer.java           # Goal management
│   │   ├── CommandLayer.java         # Player commands
│   │   └── IdleLayer.java            # Idle behaviors
│   ├── Memory/
│   │   ├── LongTermMemory.java       # Persistent memory
│   │   ├── WorldLocation.java        # Location data
│   │   ├── DeathSpot.java            # Death memory
│   │   ├── PlayerMemory.java         # Player relationships
│   │   └── SkillMemory.java          # Learned skills
│   ├── Observation/
│   │   ├── ObservationSystem.java     # Player observation
│   │   ├── PlayerAction.java         # Action record
│   │   ├── BehaviorPattern.java      # Learned pattern
│   │   └── PatternDetector.java      # Pattern recognition
│   ├── Planning/
│   │   ├── MultiStepPlanner.java     # Plan creation
│   │   ├── Plan.java                # Plan structure
│   │   ├── PlanStep.java             # Single step
│   │   ├── RecipeKnowledge.java      # Minecraft recipes
│   │   └── DependencyGraph.java      # Item dependencies
│   ├── Communication/
│   │   ├── CommunicationSystem.java  # Chat handler
│   │   ├── IntentRecognizer.java     # NLP
│   │   ├── ResponseGenerator.java    # Responses
│   │   └── CommandQueue.java         # Pending commands
│   ├── Alert/
│   │   ├── AlertSystem.java          # Notifications
│   │   ├── AlertConfig.java          # Alert settings
│   │   └── AlertType.java            # Alert types
│   └── Cache/
│       ├── KnowledgeCache.java       # Behavior cache
│       ├── CachedSituation.java      # Cache entry
│       └── SituationEncoder.java     # Embedding encoder
```

### 4.2 Modified Files

| File | Changes |
|------|---------|
| `BotEventHandler.java` | Integrate AutonomyEngine, remove old decision loop |
| `RLAgent.java` | Extend with new actions (BLOCK, RETREAT, COMBO) |
| `createFakePlayer.java` | Add AutonomyEngine initialization |
| `AIPlayer.java` | Register communication handlers |

---

## 4.3 Existing Code Mapping

This section maps existing code to the new architecture:

| Existing File | New Component | Action |
|---------------|---------------|--------|
| `BotEventHandler.java` | `CombatLayer` | Extract combat logic, wrap in CombatLayer |
| `AutoFaceEntity.java` | `AutonomyEngine` | Replace tick loop with AutonomyEngine |
| `RLAgent.java` | Extended in `CombatLayer` | Add BLOCK, RETREAT, COMBO actions |
| `HybridPlanner.java` | `MultiStepPlanner` | Integrate planning logic |
| `FunctionCallerV2.java` | `GoalsLayer` | Use for LLM-based planning |
| `AdvancedCombatAI.java` | `CombatLayer` | Use for threat prioritization |
| `PlayerTracker.java` | `IdleLayer` | Use for following player |
| `SkillRegistry.java` | `GoalsLayer` | Use skills as action primitives |
| `DecisionResolver.java` | `IntentRecognizer` | Replace with new NLP system |
| `State.java` | `AutonomyContext` | Migrate state representation |
| `StateActions.java` | `ActionResult` | Migrate action types |

**Key Integration Points:**

```java
// 1. AutonomyEngine replaces AutoFaceEntity loop
// OLD:
// AutoFaceEntity.scheduleAtFixedRate(() -> detectAndReact(), 0, 33, MILLISECONDS);

// NEW:
AutonomyEngine engine = new AutonomyEngine(bot, cache, memory);
engine.start(); // Runs tick every 100ms

// 2. CombatLayer wraps RLAgent
// OLD:
// RLAgent.chooseAction(state, qTable);

// NEW:
CombatLayer combatLayer = new CombatLayer(bot, rlAgent, advancedCombat);
ActionResult action = combatLayer.evaluate(context);

// 3. GoalsLayer uses FunctionCallerV2 for complex actions
// OLD:
// FunctionCallerV2.callFunction(functionName, params);

// NEW:
// GoalsLayer.planWithLLM() internally uses FunctionCallerV2
```

**Code Preservation:**

The following existing code should be preserved and extended:

| Code | Preservation Strategy |
|------|----------------------|
| `RLAgent` Q-Learning | Keep intact, extend actions enum |
| `State` representation | Migrate fields to AutonomyContext |
| `HybridPlanner` A* search | Keep for complex planning |
| `AdvancedCombatAI` threat detection | Keep and integrate |
| `PlayerTracker` following logic | Migrate to IdleLayer |

**Code Removal:**

| Code | Reason |
|------|--------|
| `AutoFaceEntity` tick loop | Replaced by AutonomyEngine |
| `BotEventHandler.detectAndReact()` | Split into SurvivalLayer/CombatLayer |

---

## 5. Integration

### 5.1 Initialization

```java
// In AIPlayer.java or createFakePlayer.java

public void initializeBot(ServerPlayerEntity bot) {
    // Initialize providers
    EmbeddingProvider embeddingProvider = EmbeddingProviderFactory.create();

    // Initialize memory
    LongTermMemory memory = new LongTermMemory(memoryPath);
    memory.load();

    // Initialize systems
    KnowledgeCache cache = new KnowledgeCache(embeddingProvider);
    ObservationSystem observation = new ObservationSystem(memory);
    AlertSystem alerts = new AlertSystem(owner, alertConfig);

    // Initialize autonomy engine
    AutonomyEngine engine = new AutonomyEngine(bot, cache, memory, observation, alerts);

    // Start autonomy
    engine.start();
}
```

### 5.2 Main Loop Integration

```java
// Replace existing AutoFaceEntity loop with AutonomyEngine

// OLD: AutoFaceEntity.scheduleAtFixedRate(...)
// NEW: autonomyEngine.start()

// The AutonomyEngine handles:
// - Entity detection (was AutoFaceEntity)
// - Combat decisions (was BotEventHandler)
// - Goal execution (new)
// - Communication handling (new)
```

### 5.3 Chat Command Integration

```java
// In ChatHandler.java

public void onPlayerMessage(ServerPlayerEntity player, String message) {
    // Recognize intent
    IntentResult intent = intentRecognizer.recognize(message);

    if (intent.getType() != IntentType.UNKNOWN) {
        // Queue command for autonomy engine
        autonomyEngine.queueCommand(intent);

        // Generate response
        String response = responseGenerator.generateResponse(intent, context);
        sendChat(response);
    }
}
```

---

## 6. Configuration

### 6.1 Config File

`config/ai-player/autonomy.json`:
```json
{
  "autonomy": {
    "enabled": true,
    "tickIntervalMs": 100,
    "maxActionsPerTick": 3
  },
  "survival": {
    "healthCritical": 6.0,
    "healthLow": 10.0,
    "hungerCritical": 6,
    "hungerLow": 10
  },
  "combat": {
    "enabled": true,
    "maxTargetRange": 32.0,
    "retreatHealthThreshold": 20.0
  },
  "goals": {
    "autoGenerate": true,
    "maxConcurrentGoals": 3
  },
  "alerts": {
    "diamond": { "enabled": true, "priority": "critical" },
    "danger": { "enabled": true, "priority": "high" },
    "structure": { "enabled": true, "priority": "medium" },
    "death": { "enabled": true, "priority": "critical" }
  },
  "communication": {
    "language": "pt-BR",
    "prefix": "[Bot]",
    "reportProgress": true
  },
  "memory": {
    "persistPath": "./ai-player-memory/",
    "maxLocations": 1000,
    "maxDeathSpots": 100
  }
}
```

---

## 7. Testing Strategy

### 7.1 Unit Tests

| Component | Test Focus |
|-----------|------------|
| `SurvivalLayer` | Priority ordering, threshold triggers |
| `CombatLayer` | Q-Learning action selection |
| `GoalsLayer` | Goal generation, caching |
| `KnowledgeCache` | Embedding similarity, persistence |
| `IntentRecognizer` | Portuguese command parsing |

### 7.2 Integration Tests

| Test | Scenario |
|------|----------|
| Survival Priority | Bot in lava + hostile mob → chooses lava escape |
| Goal Interruption | Bot mining + health drops → stops, eats, resumes |
| Diamond Alert | Bot finds diamond → pauses, excavates, notifies |
| Command Override | Auto-goal + player command → command takes priority |

### 7.3 Manual Testing

1. Spawn bot in survival world
2. Wait for night → bot should seek/make shelter
3. Attack bot with zombie → bot should fight back
4. Say "minera diamante" → bot should mine at Y=-59
5. Watch for diamond alert notification

---

## 8. Implementation Order

### Phase 1: Core Autonomy
1. `AutonomyContext.java` - Context snapshot (first, engine depends on it)
2. `ActionResult.java` - Action result and factory methods
3. `DecisionLayer.java` - Interface for all decision layers
4. `AutonomyEngine.java` - Main loop and coordination

### Phase 2: Survival & Idle
5. `SurvivalLayer.java` - Survival reflexes (highest priority)
6. `IdleLayer.java` - Default behaviors (lowest priority)

### Phase 3: Combat Integration
7. `CombatLayer.java` - RLAgent wrapper
8. Extend `RLAgent.java` - Add BLOCK, RETREAT, COMBO actions

### Phase 4: Goals & Memory
9. `GoalsLayer.java` - Goal management
10. `KnowledgeCache.java` - Behavior caching
11. `SituationEncoder.java` - Embedding encoder
12. `LongTermMemory.java` - Persistent memory

### Phase 5: Commands
13. `CommandLayer.java` - Player command queue
14. `IntentRecognizer.java` - NLP parsing
15. `ResponseGenerator.java` - Portuguese responses

### Phase 6: Observation & Learning
16. `ObservationSystem.java` - Player watching
17. `PatternDetector.java` - Pattern recognition

### Phase 7: Planning
18. `MultiStepPlanner.java` - Plan creation
19. `RecipeKnowledge.java` - Minecraft recipes
20. `Plan.java` - Plan data structure
21. `PlanStep.java` - Individual step

### Phase 8: Communication
22. `CommunicationSystem.java` - Chat handling
23. `CommandQueue.java` - Command management

### Phase 9: Alerts
24. `AlertSystem.java` - Notifications
25. `AlertConfig.java` - Alert settings
26. Diamond detection hook in mining logic

### Phase 10: Integration
27. Modify `BotEventHandler.java` - Remove old loop, integrate AutonomyEngine
28. Modify `createFakePlayer.java` - Initialize AutonomyEngine
29. Create `autonomy.json` - Configuration file
30. Full integration testing

---

## 9. Success Criteria

| Criteria | Measurement |
|----------|-------------|
| Survival autonomy | Bot survives 10+ minutes without player intervention |
| Combat effectiveness | Bot defeats zombie/skeleton with < 20% health loss |
| Diamond detection | Bot alerts within 5 seconds of finding diamond |
| Command understanding | Bot correctly executes 90%+ of Portuguese commands |
| Memory persistence | Bot remembers locations across server restarts |
| Learning from player | Bot applies observed pattern after 3 demonstrations |
| Multi-step planning | Bot completes "faz espada diamante" without help |

---

## 10. Future Enhancements

| Enhancement | Description |
|-------------|-------------|
| Building patterns | Learn complex building from player |
| Farming automation | Auto-create and maintain farms |
| Redstone basics | Simple redstone contraptions |
| Nether navigation | Navigate nether safely |
| Team coordination | Multiple bots working together |
| Voice commands | Speech-to-text integration |