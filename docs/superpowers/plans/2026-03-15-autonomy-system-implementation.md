# AI-Player Autonomy System - Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a fully autonomous AI bot for Minecraft that can think, decide, act, learn, and communicate naturally without constant player intervention.

**Architecture:** Layered decision system with priority-based execution. Survival first, then combat, then auto-objectives, then player commands, then idle behaviors. Uses Q-Learning for combat (instant), knowledge cache with embeddings for learned behaviors (fast), and LLM for new situations (smart).

**Tech Stack:** Java 21, Fabric API 1.21.1, Carpet Mod, Gemini/OpenAI API (for embeddings), Q-Learning (local), JSON/Gson for persistence.

---

## File Structure

### New Files to Create

```
src/main/java/net/shasankp000/Autonomy/
├── AutonomyEngine.java           # Main decision loop
├── AutonomyContext.java          # World state snapshot
├── ActionResult.java             # Action execution result
├── DecisionLayer.java            # Interface for all layers
├── InventorySnapshot.java        # Inventory data
├── Layers/
│   ├── SurvivalLayer.java        # Instant survival reflexes
│   ├── CombatLayer.java          # RLAgent wrapper
│   ├── GoalsLayer.java           # Goal management
│   ├── CommandLayer.java         # Player command queue
│   └── IdleLayer.java            # Default behaviors
├── Memory/
│   ├── LongTermMemory.java       # Persistent memory
│   ├── WorldLocation.java        # Location data
│   ├── DeathSpot.java            # Death memory
│   ├── PlayerMemory.java         # Player relationships
│   └── SkillMemory.java          # Learned skills
├── Cache/
│   ├── KnowledgeCache.java       # Behavior cache
│   ├── CachedSituation.java      # Cache entry
│   └── SituationEncoder.java     # Embedding encoder
├── Alert/
│   ├── AlertSystem.java          # Notifications
│   ├── AlertConfig.java          # Alert settings
│   └── AlertType.java            # Alert enum
└── Communication/
    ├── CommunicationSystem.java  # Chat handler
    ├── IntentRecognizer.java     # NLP parsing
    ├── ResponseGenerator.java    # Portuguese responses
    └── CommandQueue.java         # Pending commands
```

### Files to Modify

| File | Changes |
|------|---------|
| `GameAI/BotEventHandler.java` | Integrate AutonomyEngine |
| `Entity/createFakePlayer.java` | Initialize AutonomyEngine on spawn |
| `AIPlayer.java` | Register communication handlers |

---

## Phase 1: Core Components (Tasks 1-4)

### Task 1: Create Package Structure and Interfaces

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/package-info.java`
- Create: `src/main/java/net/shasankp000/Autonomy/DecisionLayer.java`
- Create: `src/main/java/net/shasankp000/Autonomy/ActionResult.java`

- [ ] **Step 1: Create Autonomy package directory structure**

Run: `mkdir -p src/main/java/net/shasankp000/Autonomy/{Layers,Memory,Cache,Alert,Communication}`

- [ ] **Step 2: Create package-info.java**

```java
/**
 * Autonomy System for AI-Player Bot.
 *
 * <p>Layered decision system for autonomous behavior.</p>
 *
 * <h2>Priority Order:</h2>
 * <ol>
 *   <li>Survival - Instant reflexes for health, hunger, environmental dangers</li>
 *   <li>Combat - Q-Learning based combat decisions</li>
 *   <li>Goals - Auto-generated and player-assigned objectives</li>
 *   <li>Commands - Player chat commands</li>
 *   <li>Idle - Follow player, observe, patrol</li>
 * </ol>
 */
package net.shasankp000.Autonomy;
```

- [ ] **Step 3: Create DecisionLayer.java**

```java
package net.shasankp000.Autonomy;

/**
 * Interface for all decision layers.
 */
public interface DecisionLayer {
    ActionResult evaluate(AutonomyContext context);
    String getLayerName();
    int getPriority();
}
```

- [ ] **Step 4: Create ActionResult.java**

```java
package net.shasankp000.Autonomy;

import net.minecraft.util.math.Vec3d;

/**
 * Result of a decision layer evaluation.
 */
public class ActionResult {
    public enum Type { NO_ACTION, IMMEDIATE, NORMAL, SCHEDULED, COMPLETED }

    private final Type type;
    private final String actionId;
    private final String description;
    private final Runnable executor;
    private final boolean success;

    private ActionResult(Type type, String actionId, String description,
                         Runnable executor, boolean success) {
        this.type = type;
        this.actionId = actionId;
        this.description = description;
        this.executor = executor;
        this.success = success;
    }

    public static ActionResult noAction() {
        return new ActionResult(Type.NO_ACTION, null, null, null, false);
    }

    public static ActionResult immediate(String actionId, Runnable executor, String description) {
        return new ActionResult(Type.IMMEDIATE, actionId, description, executor, false);
    }

    public static ActionResult normal(String actionId, Runnable executor, String description) {
        return new ActionResult(Type.NORMAL, actionId, description, executor, false);
    }

    public static ActionResult completed(String description) {
        return new ActionResult(Type.COMPLETED, null, description, null, true);
    }

    public static ActionResult followOwner(Vec3d position) {
        return normal("FOLLOW_OWNER", null,
            String.format("Follow owner at (%.0f, %.0f, %.0f)", position.x, position.y, position.z));
    }

    public boolean shouldExecute() { return type != Type.NO_ACTION && type != Type.COMPLETED; }
    public boolean wasSuccessful() { return success; }
    public Type getType() { return type; }
    public String getActionId() { return actionId; }
    public String getDescription() { return description; }
    public Runnable getExecutor() { return executor; }
}
```

- [ ] **Step 5: Commit Phase 1 interfaces**

```bash
git add src/main/java/net/shasankp000/Autonomy/
git commit -m "feat(autonomy): add core interfaces (DecisionLayer, ActionResult)"
```

---

### Task 2: Create AutonomyContext and InventorySnapshot

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/AutonomyContext.java`
- Create: `src/main/java/net/shasankp000/Autonomy/InventorySnapshot.java`

- [ ] **Step 1: Create AutonomyContext.java**

See full implementation in the spec document (Section 3.14).

- [ ] **Step 2: Create InventorySnapshot.java**

See full implementation in the spec document (Section 3.14).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/shasankp000/Autonomy/AutonomyContext.java
git add src/main/java/net/shasankp000/Autonomy/InventorySnapshot.java
git commit -m "feat(autonomy): add AutonomyContext and InventorySnapshot"
```

---

### Task 3: Create SurvivalLayer

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/Layers/SurvivalLayer.java`
- Create: `src/main/java/net/shasankp000/Autonomy/Layers/package-info.java`

- [ ] **Step 1: Create Layers package**

- [ ] **Step 2: Create SurvivalLayer.java**

See full implementation in the spec document (Task 5).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/shasankp000/Autonomy/Layers/
git commit -m "feat(autonomy): add SurvivalLayer with critical danger handling"
```

---

### Task 4: Create AutonomyEngine

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/AutonomyEngine.java`

- [ ] **Step 1: Create AutonomyEngine.java**

See full implementation in the spec document (Task 6).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/net/shasankp000/Autonomy/AutonomyEngine.java
git commit -m "feat(autonomy): add AutonomyEngine main decision loop"
```

---

## Phase 2: Decision Layers (Tasks 5-7)

### Task 5: Create CombatLayer

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/Layers/CombatLayer.java`

- [ ] **Step 1: Create CombatLayer.java**

See full implementation in the spec document (Task 7).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/net/shasankp000/Autonomy/Layers/CombatLayer.java
git commit -m "feat(autonomy): add CombatLayer with RLAgent integration"
```

---

### Task 6: Create GoalsLayer

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/Layers/GoalsLayer.java`

- [ ] **Step 1: Create GoalsLayer.java**

See full implementation in the spec document (Task 8).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/net/shasankp000/Autonomy/Layers/GoalsLayer.java
git commit -m "feat(autonomy): add GoalsLayer with auto-objective generation"
```

---

### Task 7: Create IdleLayer

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/Layers/IdleLayer.java`

- [ ] **Step 1: Create IdleLayer.java**

See full implementation in the spec document (Task 7).

- [ ] **Step 2: Commit**

```bash
git add src/main/java/net/shasankp000/Autonomy/Layers/IdleLayer.java
git commit -m "feat(autonomy): add IdleLayer for follow and observe behavior"
```

---

## Phase 3: Memory System (Tasks 8-10)

### Task 8: Create LongTermMemory

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/Memory/package-info.java`
- Create: `src/main/java/net/shasankp000/Autonomy/Memory/LongTermMemory.java`
- Create: `src/main/java/net/shasankp000/Autonomy/Memory/WorldLocation.java`
- Create: `src/main/java/net/shasankp000/Autonomy/Memory/DeathSpot.java`

- [ ] **Step 1: Create Memory package**

- [ ] **Step 2: Create LongTermMemory.java**

```java
package net.shasankp000.Autonomy.Memory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent memory for the bot.
 * Stores locations, death spots, player relationships, and learned skills.
 */
public class LongTermMemory {
    private static final Logger LOGGER = LoggerFactory.getLogger("long-term-memory");

    private final Map<String, WorldLocation> locations = new ConcurrentHashMap<>();
    private final List<DeathSpot> deathSpots = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, PlayerMemory> playerMemories = new ConcurrentHashMap<>();
    private final Path memoryFile;

    public LongTermMemory(Path memoryFile) {
        this.memoryFile = memoryFile;
    }

    public void saveLocation(WorldLocation location) {
        locations.put(location.id, location);
        LOGGER.debug("Saved location: {} at {}", location.name, location.position);
    }

    public Optional<WorldLocation> findBestResourceLocation(String resource) {
        return locations.values().stream()
            .filter(loc -> loc.resources.containsKey(resource))
            .filter(loc -> loc.resources.get(resource) > 0)
            .max(Comparator.comparingDouble(loc -> loc.safetyRating));
    }

    public void recordDeath(Vec3d position, String cause) {
        deathSpots.add(new DeathSpot(position, cause, System.currentTimeMillis()));
        LOGGER.warn("Recorded death at {} due to {}", position, cause);
    }

    public boolean isDangerousLocation(Vec3d position) {
        return deathSpots.stream()
            .anyMatch(spot -> spot.position.distanceTo(position) < 5.0);
    }

    public void load() throws IOException {
        if (!Files.exists(memoryFile)) return;

        String json = Files.readString(memoryFile);
        MemoryData data = new Gson().fromJson(json, MemoryData.class);

        if (data != null) {
            locations.putAll(data.locations);
            deathSpots.addAll(data.deathSpots);
        }
        LOGGER.info("Loaded {} locations and {} death spots", locations.size(), deathSpots.size());
    }

    public void save() throws IOException {
        MemoryData data = new MemoryData(locations, deathSpots);
        String json = new Gson().toJson(data);
        Files.writeString(memoryFile, json);
        LOGGER.debug("Saved memory to {}", memoryFile);
    }

    private static class MemoryData {
        Map<String, WorldLocation> locations;
        List<DeathSpot> deathSpots;
        MemoryData(Map<String, WorldLocation> locations, List<DeathSpot> deathSpots) {
            this.locations = locations;
            this.deathSpots = deathSpots;
        }
    }
}
```

- [ ] **Step 3: Create WorldLocation.java**

```java
package net.shasankp000.Autonomy.Memory;

import net.minecraft.util.math.Vec3d;
import java.util.Map;

public class WorldLocation {
    public final String id;
    public final String name;
    public final Vec3d position;
    public final LocationType type;
    public final Map<String, Integer> resources;
    public final double safetyRating;
    public final long lastVisited;

    public enum LocationType {
        BASE, RESOURCE, DANGER, INTERESTING, DEATH_SPOT
    }

    public WorldLocation(String id, String name, Vec3d position, LocationType type,
                         Map<String, Integer> resources, double safetyRating) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.type = type;
        this.resources = resources;
        this.safetyRating = safetyRating;
        this.lastVisited = System.currentTimeMillis();
    }
}
```

- [ ] **Step 4: Create DeathSpot.java**

```java
package net.shasankp000.Autonomy.Memory;

import net.minecraft.util.math.Vec3d;

public class DeathSpot {
    public final Vec3d position;
    public final String cause;
    public final long timestamp;
    public int deathCount = 1;

    public DeathSpot(Vec3d position, String cause, long timestamp) {
        this.position = position;
        this.cause = cause;
        this.timestamp = timestamp;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/shasankp000/Autonomy/Memory/
git commit -m "feat(autonomy): add LongTermMemory for locations and death spots"
```

---

## Phase 4: Knowledge Cache (Tasks 9-10)

### Task 9: Create KnowledgeCache

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/Cache/KnowledgeCache.java`
- Create: `src/main/java/net/shasankp000/Autonomy/Cache/CachedSituation.java`

See full implementation in the spec document (Section 3.10).

### Task 10: Create SituationEncoder

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/Cache/SituationEncoder.java`

See full implementation in the spec document (Section 3.4).

---

## Phase 5: Alert System (Tasks 11-12)

### Task 11: Create AlertSystem

**Files:**
- Create: `src/main/java/net/shasankp000/Autonomy/Alert/AlertSystem.java`
- Create: `src/main/java/net/shasankp000/Autonomy/Alert/AlertType.java`
- Create: `src/main/java/net/shasankp000/Autonomy/Alert/AlertConfig.java`

See full implementation in the spec document (Section 3.9).

---

## Phase 6: Integration (Tasks 13-15)

### Task 13: Modify BotEventHandler

**Files:**
- Modify: `src/main/java/net/shasankp000/GameAI/BotEventHandler.java`

- [ ] **Step 1: Add AutonomyEngine field**

```java
private AutonomyEngine autonomyEngine;
```

- [ ] **Step 2: Initialize AutonomyEngine in constructor**

```java
public void initializeAutonomy(ServerPlayerEntity bot, KnowledgeCache cache, LongTermMemory memory) {
    AlertSystem alerts = new AlertSystem(bot);
    this.autonomyEngine = new AutonomyEngine(bot, cache, memory, alerts);
    this.autonomyEngine.start();
}
```

- [ ] **Step 3: Replace old tick loop with AutonomyEngine**

- [ ] **Step 4: Commit**

```bash
git add src/main/java/net/shasankp000/GameAI/BotEventHandler.java
git commit -m "feat(autonomy): integrate AutonomyEngine into BotEventHandler"
```

---

### Task 14: Modify createFakePlayer

**Files:**
- Modify: `src/main/java/net/shasankp000/Entity/createFakePlayer.java`

- [ ] **Step 1: Initialize AutonomyEngine on bot spawn**

- [ ] **Step 2: Add shutdown hook for AutonomyEngine**

- [ ] **Step 3: Commit**

---

### Task 15: Create configuration file

**Files:**
- Create: `src/main/resources/assets/ai-player/config/autonomy.json`

- [ ] **Step 1: Create default configuration**

```json
{
  "autonomy": {
    "enabled": true,
    "tickIntervalMs": 100
  },
  "survival": {
    "healthCritical": 6.0,
    "hungerCritical": 6
  },
  "alerts": {
    "diamond": { "enabled": true, "priority": "critical" }
  }
}
```

---

## Phase 7: Testing (Tasks 16-18)

### Task 16: Unit Tests

Create tests for:
- AutonomyContext building
- SurvivalLayer priority logic
- ActionResult factory methods

### Task 17: Integration Tests

Create tests for:
- Full autonomy loop
- Layer priority execution
- Diamond detection alert

### Task 18: Manual Testing

1. Spawn bot in survival world
2. Test survival layer (health, hunger, lava)
3. Test combat layer (zombie attack)
4. Test goals layer (auto-gather resources)
5. Test alert system (diamond detection)

---

## Success Criteria

| Criteria | How to Verify |
|----------|---------------|
| Survival autonomy | Bot survives 10+ minutes without player intervention |
| Combat effectiveness | Bot defeats zombie with < 20% health loss |
| Diamond detection | Bot alerts within 5 seconds of finding diamond |
| Command understanding | Bot executes "minera diamante" correctly |

---

## Execution Notes

1. **Build after each phase**: Run `./gradlew build` to verify compilation
2. **Test incrementally**: Test each layer independently before full integration
3. **Log extensively**: Use LOGGER.debug for debugging
4. **Handle errors gracefully**: Wrap all executors in try-catch

---

**Plan complete. Ready to execute?**