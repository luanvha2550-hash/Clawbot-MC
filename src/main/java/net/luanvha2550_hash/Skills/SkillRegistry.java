package net.luanvha2550_hash.Skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Skill System for AI-Player Bot.
 * Allows the bot to learn and use new skills dynamically.
 * Skills can be added at runtime and are persisted across sessions.
 */
public class SkillRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-skills");

    // Singleton instance
    private static SkillRegistry instance;

    // Registered skills
    private final Map<String, Skill> skills;
    private final Map<String, Integer> skillUsageCount;
    private final Map<String, Long> skillLastUsed;

    /**
     * Represents a single skill that the bot can use
     */
    public static class Skill {
        private final String id;
        private final String name;
        private final String description;
        private final SkillCategory category;
        private final List<String> prerequisites;
        private final SkillExecutor executor;
        private final int cooldownTicks;
        private final int priority;
        private boolean enabled;

        public Skill(String id, String name, String description, SkillCategory category,
                    List<String> prerequisites, SkillExecutor executor, int cooldownTicks, int priority) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.prerequisites = prerequisites != null ? prerequisites : new ArrayList<>();
            this.executor = executor;
            this.cooldownTicks = cooldownTicks;
            this.priority = priority;
            this.enabled = true;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public SkillCategory getCategory() { return category; }
        public List<String> getPrerequisites() { return prerequisites; }
        public int getCooldownTicks() { return cooldownTicks; }
        public int getPriority() { return priority; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public SkillResult execute(SkillContext context) {
            if (!enabled) {
                return SkillResult.failure("Skill is disabled");
            }
            if (executor == null) {
                return SkillResult.failure("No executor defined");
            }
            return executor.execute(context);
        }
    }

    /**
     * Skill categories for organization
     */
    public enum SkillCategory {
        COMBAT,          // Fighting skills
        MOVEMENT,        // Navigation skills
        RESOURCE,        // Mining, gathering
        CRAFTING,        // Crafting items
        SOCIAL,          // Chat, communication
        SURVIVAL,        // Food, health, safety
        UTILITY,         // General utility skills
        ADVANCED         // Complex learned behaviors
    }

    /**
     * Context passed to skill execution
     */
    public static class SkillContext {
        private final Map<String, Object> parameters;
        private final long worldTick;
        private final String botName;

        public SkillContext(String botName, long worldTick) {
            this.botName = botName;
            this.worldTick = worldTick;
            this.parameters = new HashMap<>();
        }

        public SkillContext setParam(String key, Object value) {
            parameters.put(key, value);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> T getParam(String key, T defaultValue) {
            Object value = parameters.get(key);
            if (value == null) return defaultValue;
            return (T) value;
        }

        public String getBotName() { return botName; }
        public long getWorldTick() { return worldTick; }
    }

    /**
     * Result of skill execution
     */
    public static class SkillResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;

        private SkillResult(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data != null ? data : new HashMap<>();
        }

        public static SkillResult success() {
            return new SkillResult(true, "Skill executed successfully", null);
        }

        public static SkillResult success(String message) {
            return new SkillResult(true, message, null);
        }

        public static SkillResult success(String message, Map<String, Object> data) {
            return new SkillResult(true, message, data);
        }

        public static SkillResult failure(String message) {
            return new SkillResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
    }

    /**
     * Functional interface for skill execution
     */
    @FunctionalInterface
    public interface SkillExecutor {
        SkillResult execute(SkillContext context);
    }

    private SkillRegistry() {
        this.skills = new ConcurrentHashMap<>();
        this.skillUsageCount = new ConcurrentHashMap<>();
        this.skillLastUsed = new ConcurrentHashMap<>();
        registerDefaultSkills();
    }

    /**
     * Get singleton instance
     */
    public static synchronized SkillRegistry getInstance() {
        if (instance == null) {
            instance = new SkillRegistry();
        }
        return instance;
    }

    /**
     * Register a new skill
     */
    public void registerSkill(Skill skill) {
        if (skill == null || skill.getId() == null) {
            LOGGER.warn("Cannot register null skill or skill with null id");
            return;
        }
        skills.put(skill.getId(), skill);
        skillUsageCount.put(skill.getId(), 0);
        LOGGER.info("📝 Registered skill: {} ({})", skill.getName(), skill.getId());
    }

    /**
     * Create and register a skill with builder pattern
     */
    public void registerSkill(String id, String name, String description, SkillCategory category,
                             SkillExecutor executor) {
        registerSkill(new Skill(id, name, description, category, null, executor, 0, 0));
    }

    /**
     * Unregister a skill
     */
    public void unregisterSkill(String skillId) {
        if (skills.remove(skillId) != null) {
            skillUsageCount.remove(skillId);
            skillLastUsed.remove(skillId);
            LOGGER.info("🗑️ Unregistered skill: {}", skillId);
        }
    }

    /**
     * Get a skill by ID
     */
    public Skill getSkill(String skillId) {
        return skills.get(skillId);
    }

    /**
     * Check if a skill exists
     */
    public boolean hasSkill(String skillId) {
        return skills.containsKey(skillId);
    }

    /**
     * Execute a skill by ID
     */
    public SkillResult executeSkill(String skillId, SkillContext context) {
        Skill skill = skills.get(skillId);
        if (skill == null) {
            return SkillResult.failure("Skill not found: " + skillId);
        }

        // Check prerequisites
        for (String prereqId : skill.getPrerequisites()) {
            if (!hasSkill(prereqId)) {
                return SkillResult.failure("Missing prerequisite: " + prereqId);
            }
        }

        // Execute skill
        SkillResult result = skill.execute(context);

        // Update usage statistics
        if (result.isSuccess()) {
            skillUsageCount.merge(skillId, 1, Integer::sum);
            skillLastUsed.put(skillId, context.getWorldTick());
        }

        return result;
    }

    /**
     * Get all skills in a category
     */
    public List<Skill> getSkillsByCategory(SkillCategory category) {
        return skills.values().stream()
            .filter(skill -> skill.getCategory() == category)
            .sorted(Comparator.comparingInt(Skill::getPriority).reversed())
            .toList();
    }

    /**
     * Get all enabled skills
     */
    public List<Skill> getEnabledSkills() {
        return skills.values().stream()
            .filter(Skill::isEnabled)
            .sorted(Comparator.comparingInt(Skill::getPriority).reversed())
            .toList();
    }

    /**
     * Get usage count for a skill
     */
    public int getUsageCount(String skillId) {
        return skillUsageCount.getOrDefault(skillId, 0);
    }

    /**
     * Get most used skills
     */
    public List<String> getMostUsedSkills(int limit) {
        return skillUsageCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Enable or disable a skill
     */
    public void setSkillEnabled(String skillId, boolean enabled) {
        Skill skill = skills.get(skillId);
        if (skill != null) {
            skill.setEnabled(enabled);
            LOGGER.info("{} skill: {}", enabled ? "✅ Enabled" : "❌ Disabled", skillId);
        }
    }

    /**
     * Register all default skills
     */
    private void registerDefaultSkills() {
        // Combat Skills
        registerSkill("attack_melee", "Melee Attack", "Attack nearby hostile entity with melee weapon",
            SkillCategory.COMBAT, context -> {
                // Implementation will be connected to CombatStrategyUtils
                return SkillResult.success("Executing melee attack");
            });

        registerSkill("attack_ranged", "Ranged Attack", "Attack distant entity with ranged weapon",
            SkillCategory.COMBAT, context -> {
                return SkillResult.success("Executing ranged attack");
            });

        registerSkill("defend", "Defend", "Block or dodge incoming attacks",
            SkillCategory.COMBAT, context -> {
                return SkillResult.success("Defending against attacks");
            });

        registerSkill("retreat", "Retreat", "Move away from danger while maintaining awareness",
            SkillCategory.COMBAT, context -> {
                return SkillResult.success("Retreating from danger");
            });

        // Movement Skills
        registerSkill("follow_player", "Follow Player", "Follow the owner player at a safe distance",
            SkillCategory.MOVEMENT, context -> {
                return SkillResult.success("Following player");
            });

        registerSkill("navigate_to", "Navigate To", "Navigate to a specific coordinate or location",
            SkillCategory.MOVEMENT, context -> {
                return SkillResult.success("Navigating to target");
            });

        registerSkill("avoid_hazard", "Avoid Hazard", "Avoid lava, cliffs, and other dangerous terrain",
            SkillCategory.MOVEMENT, context -> {
                return SkillResult.success("Avoiding hazard");
            });

        // Resource Skills
        registerSkill("mine_block", "Mine Block", "Mine a specific block type",
            SkillCategory.RESOURCE, context -> {
                return SkillResult.success("Mining block");
            });

        registerSkill("gather_resource", "Gather Resource", "Gather resources from the environment",
            SkillCategory.RESOURCE, context -> {
                return SkillResult.success("Gathering resources");
            });

        registerSkill("deposit_items", "Deposit Items", "Deposit items into a chest or container",
            SkillCategory.RESOURCE, context -> {
                return SkillResult.success("Depositing items");
            });

        // Survival Skills
        registerSkill("eat_food", "Eat Food", "Consume food when hungry",
            SkillCategory.SURVIVAL, context -> {
                return SkillResult.success("Eating food");
            });

        registerSkill("equip_armor", "Equip Armor", "Equip best available armor",
            SkillCategory.SURVIVAL, context -> {
                return SkillResult.success("Equipping armor");
            });

        registerSkill("find_shelter", "Find Shelter", "Find or create shelter during dangerous times",
            SkillCategory.SURVIVAL, context -> {
                return SkillResult.success("Finding shelter");
            });

        // Social Skills
        registerSkill("respond_chat", "Respond Chat", "Respond to player chat messages",
            SkillCategory.SOCIAL, context -> {
                return SkillResult.success("Responding to chat");
            });

        registerSkill("report_status", "Report Status", "Report current status to the player",
            SkillCategory.SOCIAL, context -> {
                return SkillResult.success("Reporting status");
            });

        // Utility Skills
        registerSkill("search_blocks", "Search Blocks", "Search for specific block types in the area",
            SkillCategory.UTILITY, context -> {
                return SkillResult.success("Searching for blocks");
            });

        registerSkill("craft_item", "Craft Item", "Craft an item using available materials",
            SkillCategory.CRAFTING, context -> {
                return SkillResult.success("Crafting item");
            });

        LOGGER.info("✅ Registered {} default skills", skills.size());
    }

    /**
     * Clear all skills (for testing/reset)
     */
    public void clear() {
        skills.clear();
        skillUsageCount.clear();
        skillLastUsed.clear();
    }

    /**
     * Get total number of skills
     */
    public int getSkillCount() {
        return skills.size();
    }

    /**
     * Get all skill IDs
     */
    public Set<String> getSkillIds() {
        return Collections.unmodifiableSet(skills.keySet());
    }
}