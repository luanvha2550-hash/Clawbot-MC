package net.shasankp000.Autonomy.Layers;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.Autonomy.ActionResult;
import net.shasankp000.Autonomy.AutonomyContext;
import net.shasankp000.Autonomy.AutonomyContext.GoalType;
import net.shasankp000.Autonomy.DecisionLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command Layer - Processes player commands from chat in Portuguese.
 *
 * <p>This layer has priority 4 (after Goals) and handles commands sent by players
 * through the chat system. Commands are parsed from Portuguese language input.</p>
 *
 * <h3>Supported Portuguese Commands:</h3>
 * <ul>
 *   <li>"minera/procura [recurso]" - GATHER intent - Mine or gather resources</li>
 *   <li>"ataca/mata [alvo]" - COMBAT intent - Attack or kill entities</li>
 *   <li>"me segue/vem" - FOLLOW intent - Follow the player</li>
 *   <li>"faz/constrói [estrutura]" - BUILD intent - Build structures</li>
 *   <li>"crafta/faz [item]" - CRAFT intent - Craft items</li>
 *   <li>"como tá" / "status" - REPORT intent - Report current status</li>
 *   <li>"para" - STOP intent - Stop current action</li>
 * </ul>
 *
 * <h3>Command Processing:</h3>
 * <ol>
 *   <li>Commands are queued via queueCommand()</li>
 *   <li>evaluate() processes queued commands</li>
 *   <li>Entities are extracted from message (resources, targets, etc.)</li>
 *   <li>Action is generated based on intent</li>
 * </ol>
 */
public class CommandLayer implements DecisionLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger("command-layer");

    // Command queue - thread-safe for cross-thread access
    private final Queue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();

    // Current command being processed
    private PlayerCommand currentCommand;
    private long commandStartTime;

    // Reference to bot (for status reporting)
    private final ServerPlayerEntity bot;

    // Portuguese command patterns
    private static final Map<String, CommandIntent> INTENT_PATTERNS = new HashMap<>();

    static {
        // Gather/Mine commands
        INTENT_PATTERNS.put("minera", CommandIntent.GATHER);
        INTENT_PATTERNS.put("minerar", CommandIntent.GATHER);
        INTENT_PATTERNS.put("procura", CommandIntent.GATHER);
        INTENT_PATTERNS.put("proucura", CommandIntent.GATHER);
        INTENT_PATTERNS.put("coleta", CommandIntent.GATHER);
        INTENT_PATTERNS.put("coletar", CommandIntent.GATHER);
        INTENT_PATTERNS.put("pega", CommandIntent.GATHER);
        INTENT_PATTERNS.put("pegue", CommandIntent.GATHER);
        INTENT_PATTERNS.put("pegue", CommandIntent.GATHER);
        INTENT_PATTERNS.put("busca", CommandIntent.GATHER);
        INTENT_PATTERNS.put("buscar", CommandIntent.GATHER);
        INTENT_PATTERNS.put("vá buscar", CommandIntent.GATHER);
        INTENT_PATTERNS.put("pegar", CommandIntent.GATHER);
        INTENT_PATTERNS.put("obter", CommandIntent.GATHER);
        INTENT_PATTERNS.put("consiga", CommandIntent.GATHER);

        // Combat commands
        INTENT_PATTERNS.put("ataca", CommandIntent.COMBAT);
        INTENT_PATTERNS.put("mata", CommandIntent.COMBAT);
        INTENT_PATTERNS.put("elimina", CommandIntent.COMBAT);
        INTENT_PATTERNS.put("combate", CommandIntent.COMBAT);

        // Follow commands
        INTENT_PATTERNS.put("segue", CommandIntent.FOLLOW);
        INTENT_PATTERNS.put("me segue", CommandIntent.FOLLOW);
        INTENT_PATTERNS.put("vem", CommandIntent.FOLLOW);
        INTENT_PATTERNS.put("acompanha", CommandIntent.FOLLOW);

        // Build commands
        INTENT_PATTERNS.put("faz", CommandIntent.BUILD);
        INTENT_PATTERNS.put("constrói", CommandIntent.BUILD);
        INTENT_PATTERNS.put("constroi", CommandIntent.BUILD); // Without accent
        INTENT_PATTERNS.put("construir", CommandIntent.BUILD);

        // Craft commands
        INTENT_PATTERNS.put("crafta", CommandIntent.CRAFT);
        INTENT_PATTERNS.put("cria", CommandIntent.CRAFT);
        INTENT_PATTERNS.put("faz", CommandIntent.CRAFT); // Context-dependent

        // Status/Report commands
        INTENT_PATTERNS.put("como tá", CommandIntent.REPORT);
        INTENT_PATTERNS.put("como ta", CommandIntent.REPORT); // Without accent
        INTENT_PATTERNS.put("status", CommandIntent.REPORT);
        INTENT_PATTERNS.put("relata", CommandIntent.REPORT);
        INTENT_PATTERNS.put("informa", CommandIntent.REPORT);

        // Stop commands
        INTENT_PATTERNS.put("para", CommandIntent.STOP);
        INTENT_PATTERNS.put("parar", CommandIntent.STOP);
        INTENT_PATTERNS.put("para ai", CommandIntent.STOP);
        INTENT_PATTERNS.put("pára", CommandIntent.STOP); // With accent
        INTENT_PATTERNS.put("espera", CommandIntent.STOP);
    }

    // Resource entity patterns (Minecraft resources in Portuguese)
    private static final Set<String> RESOURCE_ENTITIES = Set.of(
        // Basic resources
        "diamante", "diamond",
        "ferro", "iron",
        "ouro", "gold",
        "carvão", "coal", "carvao",
        "pedra", "stone",
        "madeira", "wood",
        "terra", "dirt",
        "areia", "sand",
        "graveto", "stick",
        // Tools
        "picareta", "pickaxe",
        "machado", "axe",
        "pa", "shovel",
        "espada", "sword",
        // Food
        "comida", "food",
        "carne", "meat",
        "pão", "bread", "pao",
        "maçã", "apple", "maca",
        // Minerals
        "redstone",
        "lapis",
        "esmeralda", "emerald",
        "netherite"
    );

    // Target entity patterns (Minecraft mobs in Portuguese)
    private static final Set<String> TARGET_ENTITIES = Set.of(
        // Hostile mobs
        "zumbi", "zombie",
        "esqueleto", "skeleton",
        "aranha", "spider",
        "creeper",
        "enderman",
        "bruxa", "witch",
        "slime",
        "phantom",
        "pillager",
        "vindicador", "vindicator",
        // Passive mobs
        "vaca", "cow",
        "porco", "pig",
        "ovelha", "sheep",
        "galinha", "chicken",
        "cavalo", "horse",
        "lobo", "wolf",
        "gato", "cat"
    );

    // Structure patterns (Minecraft structures in Portuguese)
    private static final Set<String> STRUCTURE_ENTITIES = Set.of(
        "casa", "house",
        "cabana", "hut",
        "torre", "tower",
        "ponte", "bridge",
        "muro", "wall",
        "fazenda", "farm",
        "refúgio", "shelter", "refugio",
        "base"
    );

    // Item patterns (Minecraft items in Portuguese)
    private static final Set<String> ITEM_ENTITIES = Set.of(
        // Tools
        "picareta", "pickaxe",
        "machado", "axe",
        "pa", "shovel",
        "foice", "hoe",
        // Weapons
        "espada", "sword",
        "arco", "bow",
        "flecha", "arrow",
        // Armor
        "capacete", "helmet",
        "peitoral", "chestplate",
        "calça", "leggings", "calca",
        "bota", "boots",
        "armadura", "armor",
        // Misc
        "tocha", "torch",
        "fornalha", "furnace",
        "bancada", "crafting table"
    );

    /**
     * Command intent types.
     */
    public enum CommandIntent {
        GATHER,     // Gather resources (minera, procura)
        COMBAT,     // Attack/kill entities (ataca, mata)
        FOLLOW,     // Follow player (me segue, vem)
        BUILD,      // Build structures (faz, constrói)
        CRAFT,      // Craft items (crafta, cria)
        REPORT,     // Report status (como tá, status)
        STOP,       // Stop current action (para)
        UNKNOWN     // Unrecognized command
    }

    /**
     * Creates a new CommandLayer.
     *
     * @param bot The AI player entity
     */
    public CommandLayer(ServerPlayerEntity bot) {
        this.bot = bot;
        this.currentCommand = null;
        this.commandStartTime = 0;
    }

    @Override
    public String getLayerName() {
        return "Command";
    }

    @Override
    public int getPriority() {
        return 4; // After Goals (3), before Idle (5)
    }

    @Override
    public ActionResult evaluate(AutonomyContext context) {
        // 1. If processing a command, continue it
        if (currentCommand != null && !currentCommand.isCompleted()) {
            return processCurrentCommand(context);
        }

        // 2. Check for pending commands in queue
        PlayerCommand nextCommand = commandQueue.poll();
        if (nextCommand == null) {
            LOGGER.debug("[Command] No pending commands");
            return ActionResult.noAction();
        }

        // 3. Start processing new command
        LOGGER.info("[Command] Processing command: '{}' (intent: {})",
            nextCommand.getMessage(), nextCommand.getIntent());
        currentCommand = nextCommand;
        commandStartTime = System.currentTimeMillis();
        currentCommand.start();

        return processCurrentCommand(context);
    }

    /**
     * Process the current command based on its intent.
     */
    private ActionResult processCurrentCommand(AutonomyContext context) {
        if (currentCommand == null) {
            return ActionResult.noAction();
        }

        CommandIntent intent = currentCommand.getIntent();

        switch (intent) {
            case GATHER:
                return processGatherCommand(context);
            case COMBAT:
                return processCombatCommand(context);
            case FOLLOW:
                return processFollowCommand(context);
            case BUILD:
                return processBuildCommand(context);
            case CRAFT:
                return processCraftCommand(context);
            case REPORT:
                return processReportCommand(context);
            case STOP:
                return processStopCommand(context);
            default:
                LOGGER.warn("[Command] Unknown intent: {}", intent);
                completeCurrentCommand();
                return ActionResult.noAction();
        }
    }

    // ========== Command Processing Methods ==========

    /**
     * Process a gather/mining command.
     */
    private ActionResult processGatherCommand(AutonomyContext context) {
        String resource = currentCommand.getEntity("resource");
        if (resource == null) {
            resource = "recurso desconhecido";
        }

        LOGGER.info("[Command] Gathering resource: {}", resource);

        // Create scheduled action for gathering
        ActionResult result = ActionResult.scheduled(
            "GATHER_" + resource.toUpperCase(),
            "gather_" + resource,
            "Gather " + resource + " (commanded by player)"
        );

        completeCurrentCommand();
        return result;
    }

    /**
     * Process a combat/attack command.
     */
    private ActionResult processCombatCommand(AutonomyContext context) {
        String target = currentCommand.getEntity("target");
        if (target == null) {
            target = "alvo desconhecido";
        }

        LOGGER.info("[Command] Attacking target: {}", target);

        ActionResult result = ActionResult.scheduled(
            "ATTACK_" + target.toUpperCase(),
            "attack_" + target,
            "Attack " + target + " (commanded by player)"
        );

        completeCurrentCommand();
        return result;
    }

    /**
     * Process a follow command.
     */
    private ActionResult processFollowCommand(AutonomyContext context) {
        if (!context.hasOwnerNearby()) {
            LOGGER.warn("[Command] Cannot follow - owner not nearby");
            completeCurrentCommand();
            return ActionResult.noAction();
        }

        LOGGER.info("[Command] Following player");
        completeCurrentCommand();

        return ActionResult.followOwner(context.getOwnerPosition());
    }

    /**
     * Process a build command.
     */
    private ActionResult processBuildCommand(AutonomyContext context) {
        String structure = currentCommand.getEntity("structure");
        if (structure == null) {
            structure = "estrutura desconhecida";
        }

        LOGGER.info("[Command] Building structure: {}", structure);

        ActionResult result = ActionResult.scheduled(
            "BUILD_" + structure.toUpperCase(),
            "build_" + structure,
            "Build " + structure + " (commanded by player)"
        );

        completeCurrentCommand();
        return result;
    }

    /**
     * Process a craft command.
     */
    private ActionResult processCraftCommand(AutonomyContext context) {
        String item = currentCommand.getEntity("item");
        if (item == null) {
            item = "item desconhecido";
        }

        LOGGER.info("[Command] Crafting item: {}", item);

        ActionResult result = ActionResult.scheduled(
            "CRAFT_" + item.toUpperCase(),
            "craft_" + item,
            "Craft " + item + " (commanded by player)"
        );

        completeCurrentCommand();
        return result;
    }

    /**
     * Process a report/status command.
     */
    private ActionResult processReportCommand(AutonomyContext context) {
        LOGGER.info("[Command] Generating status report");

        String report = generateStatusReport(context);
        currentCommand.setReport(report);

        // Log the report
        LOGGER.info("[Command] Status report: {}", report);

        completeCurrentCommand();

        // Return completed action with report
        return ActionResult.completed(report);
    }

    /**
     * Process a stop command.
     */
    private ActionResult processStopCommand(AutonomyContext context) {
        LOGGER.info("[Command] Stopping current action");
        completeCurrentCommand();

        // Return normal action to signal stop
        return ActionResult.normal("STOP", null, "Stop current action");
    }

    // ========== Command Queue Methods ==========

    /**
     * Queue a new command from player chat.
     *
     * @param message The raw message from chat
     * @param intent The detected intent (optional, will be parsed if null)
     */
    public void queueCommand(String message, String intent) {
        PlayerCommand command = parseCommand(message, intent);
        if (command != null && command.getIntent() != CommandIntent.UNKNOWN) {
            commandQueue.offer(command);
            LOGGER.info("[Command] Queued command: '{}' with intent: {}",
                message, command.getIntent());
        } else {
            LOGGER.warn("[Command] Could not parse command: '{}'", message);
        }
    }

    /**
     * Queue a new command with pre-parsed intent.
     *
     * @param message The raw message from chat
     * @param intent The command intent
     */
    public void queueCommand(String message, CommandIntent intent) {
        PlayerCommand command = new PlayerCommand(message, intent);
        command.setEntities(parseEntities(message));
        commandQueue.offer(command);
        LOGGER.info("[Command] Queued command: '{}' with intent: {}",
            message, intent);
    }

    /**
     * Parse a command message and extract intent.
     *
     * @param message The raw message from chat
     * @param intentHint Optional intent hint (may be null)
     * @return Parsed PlayerCommand or null if unrecognized
     */
    public PlayerCommand parseCommand(String message, String intentHint) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        String normalizedMessage = normalizeMessage(message);
        CommandIntent intent;

        // Use provided intent hint if valid
        if (intentHint != null) {
            try {
                intent = CommandIntent.valueOf(intentHint.toUpperCase());
            } catch (IllegalArgumentException e) {
                intent = detectIntent(normalizedMessage);
            }
        } else {
            intent = detectIntent(normalizedMessage);
        }

        PlayerCommand command = new PlayerCommand(message, intent);
        command.setEntities(parseEntities(message));

        return command;
    }

    /**
     * Detect the intent from a normalized message.
     *
     * @param message The normalized message
     * @return Detected CommandIntent
     */
    private CommandIntent detectIntent(String message) {
        String lowerMessage = message.toLowerCase(Locale.ROOT);

        // Check for exact matches first
        for (Map.Entry<String, CommandIntent> entry : INTENT_PATTERNS.entrySet()) {
            if (lowerMessage.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Check for word boundary matches
        String[] words = lowerMessage.split("\\s+");
        for (String word : words) {
            if (INTENT_PATTERNS.containsKey(word)) {
                return INTENT_PATTERNS.get(word);
            }
        }

        return CommandIntent.UNKNOWN;
    }

    /**
     * Parse entities from a command message.
     *
     * @param message The raw message from chat
     * @return Map of entity type to entity value
     */
    public Map<String, String> parseEntities(String message) {
        Map<String, String> entities = new HashMap<>();
        String lowerMessage = message.toLowerCase(Locale.ROOT);

        // Extract resource entities
        for (String resource : RESOURCE_ENTITIES) {
            if (lowerMessage.contains(resource)) {
                entities.put("resource", resource);
                break;
            }
        }

        // Extract target entities
        for (String target : TARGET_ENTITIES) {
            if (lowerMessage.contains(target)) {
                entities.put("target", target);
                break;
            }
        }

        // Extract structure entities
        for (String structure : STRUCTURE_ENTITIES) {
            if (lowerMessage.contains(structure)) {
                entities.put("structure", structure);
                break;
            }
        }

        // Extract item entities
        for (String item : ITEM_ENTITIES) {
            if (lowerMessage.contains(item)) {
                entities.put("item", item);
                break;
            }
        }

        return entities;
    }

    /**
     * Normalize a message for processing.
     *
     * @param message The raw message
     * @return Normalized message
     */
    private String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }

        // Remove extra whitespace
        String normalized = message.trim().toLowerCase(Locale.ROOT);

        // Remove accents for easier matching
        normalized = normalized
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ã", "a")
            .replace("õ", "o")
            .replace("ç", "c");

        return normalized;
    }

    /**
     * Generate a status report for the bot.
     *
     * @param context Current autonomy context
     * @return Status report string
     */
    private String generateStatusReport(AutonomyContext context) {
        StringBuilder report = new StringBuilder();

        // Health status
        float healthPercent = context.getHealthPercent() * 100;
        report.append(String.format("Vida: %.0f%% (%.0f/%.0f)",
            healthPercent, context.getBotHealth(), context.getBotMaxHealth()));

        // Hunger status
        report.append(String.format(", Fome: %d/20", context.getBotHunger()));

        // Position
        report.append(String.format(", Posição: (%.0f, %.0f, %.0f)",
            context.getBotPosition().x,
            context.getBotPosition().y,
            context.getBotPosition().z));

        // Biome
        report.append(", Bioma: ").append(context.getCurrentBiome());

        // Threats
        if (context.hasHostileThreats()) {
            report.append(String.format(", Ameaças: %d", context.getHostileEntities().size()));
        }

        // Danger
        if (context.hasImmediateDanger()) {
            report.append(", PERIGO!");
        }

        // Current goal
        if (context.hasAutoGoal()) {
            report.append(", Objetivo: ").append(context.getCurrentGoalType());
        }

        return report.toString();
    }

    /**
     * Complete the current command.
     */
    private void completeCurrentCommand() {
        if (currentCommand != null) {
            currentCommand.complete();
            LOGGER.debug("[Command] Completed command: '{}'", currentCommand.getMessage());
        }
        currentCommand = null;
        commandStartTime = 0;
    }

    /**
     * Clear all pending commands.
     */
    public void clearCommands() {
        commandQueue.clear();
        currentCommand = null;
        commandStartTime = 0;
        LOGGER.info("[Command] Cleared all commands");
    }

    /**
     * Get the number of pending commands.
     *
     * @return Number of commands in queue
     */
    public int getPendingCommandCount() {
        return commandQueue.size();
    }

    /**
     * Check if there is a command being processed.
     *
     * @return true if currently processing a command
     */
    public boolean isProcessingCommand() {
        return currentCommand != null && !currentCommand.isCompleted();
    }

    /**
     * Get the current command being processed.
     *
     * @return Current PlayerCommand or null
     */
    public PlayerCommand getCurrentCommand() {
        return currentCommand;
    }

    // ========== Inner Classes ==========

    /**
     * Represents a command from a player.
     *
     * <p>Contains the original message, parsed intent, extracted entities,
     * and execution status.</p>
     */
    public static class PlayerCommand {

        private final String message;
        private final CommandIntent intent;
        private final long timestamp;
        private Map<String, String> entities;
        private String report;
        private boolean completed;
        private long completionTime;
        private long startTime;

        /**
         * Create a new player command.
         *
         * @param message The raw message from chat
         * @param intent The parsed intent
         */
        public PlayerCommand(String message, CommandIntent intent) {
            this.message = message;
            this.intent = intent;
            this.timestamp = System.currentTimeMillis();
            this.entities = new HashMap<>();
            this.completed = false;
            this.completionTime = 0;
            this.startTime = 0;
        }

        /**
         * Get the raw message.
         *
         * @return The raw message from chat
         */
        public String getMessage() {
            return message;
        }

        /**
         * Get the command intent.
         *
         * @return The parsed CommandIntent
         */
        public CommandIntent getIntent() {
            return intent;
        }

        /**
         * Get the timestamp when the command was received.
         *
         * @return Timestamp in milliseconds
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * Get an entity by type.
         *
         * @param entityType The entity type (resource, target, structure, item)
         * @return The entity value or null if not found
         */
        public String getEntity(String entityType) {
            return entities.get(entityType);
        }

        /**
         * Get all entities.
         *
         * @return Map of entity type to entity value
         */
        public Map<String, String> getEntities() {
            return Collections.unmodifiableMap(entities);
        }

        /**
         * Set the entities map.
         *
         * @param entities Map of entity type to entity value
         */
        public void setEntities(Map<String, String> entities) {
            this.entities = new HashMap<>(entities);
        }

        /**
         * Get the status report (for REPORT commands).
         *
         * @return The status report or null
         */
        public String getReport() {
            return report;
        }

        /**
         * Set the status report.
         *
         * @param report The status report
         */
        public void setReport(String report) {
            this.report = report;
        }

        /**
         * Check if the command is completed.
         *
         * @return true if completed
         */
        public boolean isCompleted() {
            return completed;
        }

        /**
         * Start executing the command.
         */
        public void start() {
            this.startTime = System.currentTimeMillis();
        }

        /**
         * Mark the command as completed.
         */
        public void complete() {
            this.completed = true;
            this.completionTime = System.currentTimeMillis();
        }

        /**
         * Get the execution duration in milliseconds.
         *
         * @return Duration or 0 if not started/completed
         */
        public long getDuration() {
            if (startTime == 0 || completionTime == 0) {
                return 0;
            }
            return completionTime - startTime;
        }

        @Override
        public String toString() {
            return String.format("PlayerCommand[message='%s', intent=%s, entities=%s]",
                message, intent, entities);
        }
    }
}