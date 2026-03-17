package net.luanvha2550_hash.PlayerUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Advanced combat AI utilities for intelligent combat behavior.
 * Extends CombatStrategyUtils with:
 * - Target prioritization based on threat and distance
 * - Combo attack sequences
 * - Dodging and positioning logic
 * - Weapon-specific combat tactics
 * - Team awareness (protecting the player)
 */
public class AdvancedCombatAI {
    private static final Logger LOGGER = LoggerFactory.getLogger("advanced-combat-ai");

    // Combat state
    private Entity currentTarget = null;
    private Entity priorityTarget = null;  // Target targeting the player
    private CombatMode currentMode = CombatMode.PASSIVE;
    private long lastAttackTick = 0;
    private int comboCount = 0;
    private Vec3d lastTargetPosition = null;

    /**
     * Combat modes for the bot
     */
    public enum CombatMode {
        PASSIVE,         // Not fighting
        DEFENSIVE,       // Protecting self/player, only counter-attack
        BALANCED,        // Standard combat behavior
        AGGRESSIVE,      // Prioritize damage over defense
        RETREAT          // Escaping from combat
    }

    /**
     * Target priority levels
     */
    public enum TargetPriority {
        IMMEDIATE_THREAT,    // Entity actively attacking the player
        HIGH_THREAT,         // Dangerous entity nearby (Creeper, etc.)
        MEDIUM_THREAT,       // Standard hostile mob
        LOW_THREAT,          // Minor threat, can be ignored temporarily
        IGNORE               // Not a threat
    }

    /**
     * Combat result after an action
     */
    public static class CombatActionResult {
        public final boolean success;
        public final double damageDealt;
        public final double damageTaken;
        public final String actionTaken;
        public final String recommendation;

        public CombatActionResult(boolean success, double damageDealt, double damageTaken,
                                 String actionTaken, String recommendation) {
            this.success = success;
            this.damageDealt = damageDealt;
            this.damageTaken = damageTaken;
            this.actionTaken = actionTaken;
            this.recommendation = recommendation;
        }
    }

    /**
     * Target information with priority
     */
    public static class TargetInfo {
        public final Entity entity;
        public final TargetPriority priority;
        public final double distance;
        public final double threatLevel;
        public final boolean isTargetingPlayer;
        public final String entityType;

        public TargetInfo(Entity entity, TargetPriority priority, double distance,
                         double threatLevel, boolean isTargetingPlayer, String entityType) {
            this.entity = entity;
            this.priority = priority;
            this.distance = distance;
            this.threatLevel = threatLevel;
            this.isTargetingPlayer = isTargetingPlayer;
            this.entityType = entityType;
        }
    }

    /**
     * Analyze all nearby entities and prioritize targets
     */
    public List<TargetInfo> prioritizeTargets(ServerPlayerEntity bot, List<Entity> nearbyEntities,
                                               PlayerEntity playerToProtect) {
        List<TargetInfo> targets = new ArrayList<>();

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof LivingEntity) || entity == bot) continue;

            TargetInfo info = analyzeEntity(bot, entity, playerToProtect);
            if (info.priority != TargetPriority.IGNORE) {
                targets.add(info);
            }
        }

        // Sort by priority (IMMEDIATE_THREAT first), then by distance
        targets.sort((a, b) -> {
            int priorityCompare = a.priority.compareTo(b.priority);
            if (priorityCompare != 0) return priorityCompare;
            return Double.compare(a.distance, b.distance);
        });

        return targets;
    }

    /**
     * Analyze a single entity for threat assessment
     */
    private TargetInfo analyzeEntity(ServerPlayerEntity bot, Entity entity, PlayerEntity playerToProtect) {
        double distance = bot.squaredDistanceTo(entity);
        boolean isTargetingPlayer = isTargetingPlayer(entity, playerToProtect);
        String entityType = entity.getType().getName().getString();
        double threatLevel = calculateThreatLevel(entity, distance);

        TargetPriority priority;
        if (isTargetingPlayer) {
            priority = TargetPriority.IMMEDIATE_THREAT;
        } else if (threatLevel > 0.8) {
            priority = TargetPriority.HIGH_THREAT;
        } else if (threatLevel > 0.4) {
            priority = TargetPriority.MEDIUM_THREAT;
        } else if (threatLevel > 0.1) {
            priority = TargetPriority.LOW_THREAT;
        } else {
            priority = TargetPriority.IGNORE;
        }

        return new TargetInfo(entity, priority, Math.sqrt(distance), threatLevel, isTargetingPlayer, entityType);
    }

    /**
     * Calculate threat level of an entity (0.0 to 1.0)
     */
    private double calculateThreatLevel(Entity entity, double squaredDistance) {
        double baseThreat = 0.0;

        // High-threat entities
        if (entity instanceof CreeperEntity) {
            baseThreat = 0.95;  // Very dangerous
        } else if (entity instanceof WitherSkeletonEntity) {
            baseThreat = 0.85;
        } else if (entity instanceof EndermanEntity) {
            // Enderman is only dangerous when angry
            EndermanEntity enderman = (EndermanEntity) entity;
            baseThreat = enderman.isAngry() ? 0.75 : 0.1;
        } else if (entity instanceof BlazeEntity) {
            baseThreat = 0.80;
        } else if (entity instanceof GhastEntity) {
            baseThreat = 0.70;
        } else if (entity instanceof SkeletonEntity || entity instanceof StrayEntity) {
            baseThreat = 0.65;
        } else if (entity instanceof SpiderEntity || entity instanceof CaveSpiderEntity) {
            baseThreat = 0.55;
        } else if (entity instanceof ZombieEntity || entity instanceof HuskEntity) {
            baseThreat = 0.45;
        } else if (entity instanceof EndermiteEntity || entity instanceof SilverfishEntity) {
            baseThreat = 0.35;
        } else if (entity instanceof SlimeEntity || entity instanceof MagmaCubeEntity) {
            baseThreat = 0.30;
        } else if (entity instanceof PiglinEntity || entity instanceof PiglinBruteEntity) {
            baseThreat = 0.60;
        } else if (entity instanceof PillagerEntity || entity instanceof VindicatorEntity) {
            baseThreat = 0.70;
        } else if (entity instanceof RavagerEntity) {
            baseThreat = 0.90;
        } else if (entity instanceof VexEntity) {
            baseThreat = 0.75;
        } else if (entity instanceof WitchEntity) {
            baseThreat = 0.65;
        } else if (entity instanceof WardenEntity) {
            baseThreat = 1.0;  // Maximum threat
        } else {
            // Check if it's a hostile mob
            if (entity instanceof MobEntity) {
                MobEntity mob = (MobEntity) entity;
                if (mob.isAttacking()) {
                    baseThreat = 0.50;
                } else {
                    baseThreat = 0.30;
                }
            }
        }

        // Adjust threat based on distance (closer = more threatening)
        double distance = Math.sqrt(squaredDistance);
        double distanceModifier;
        if (distance < 3) {
            distanceModifier = 1.2;  // Very close, increase threat
        } else if (distance < 6) {
            distanceModifier = 1.0;
        } else if (distance < 12) {
            distanceModifier = 0.8;
        } else if (distance < 24) {
            distanceModifier = 0.5;
        } else {
            distanceModifier = 0.2;
        }

        return Math.min(1.0, baseThreat * distanceModifier);
    }

    /**
     * Check if an entity is targeting the player
     */
    private boolean isTargetingPlayer(Entity entity, PlayerEntity player) {
        if (entity instanceof MobEntity) {
            MobEntity mob = (MobEntity) entity;
            LivingEntity target = mob.getTarget();
            return target == player;
        }
        return false;
    }

    /**
     * Determine the best combat action
     */
    public String determineBestAction(ServerPlayerEntity bot, List<TargetInfo> targets,
                                      ItemStack mainHandItem, ItemStack offHandItem) {
        if (targets.isEmpty()) {
            return "NO_ACTION: No threats detected";
        }

        TargetInfo primaryTarget = targets.get(0);  // Highest priority target

        // Check if we have weapons
        boolean hasMeleeWeapon = isMeleeWeapon(mainHandItem);
        boolean hasRangedWeapon = isRangedWeapon(mainHandItem);
        boolean hasShield = offHandItem.getItem() == Items.SHIELD;

        // Check distance
        if (primaryTarget.distance < 3) {
            // Close combat
            if (primaryTarget.priority == TargetPriority.IMMEDIATE_THREAT) {
                if (hasShield) {
                    return "BLOCK_AND_COUNTER: Block with shield, then attack";
                }
                return "MELEE_ATTACK: Attack immediately with melee weapon";
            }
            return "MELEE_ATTACK: Standard melee attack";
        }

        if (primaryTarget.distance < 8) {
            // Medium range
            if (hasRangedWeapon) {
                return "RANGED_ATTACK: Use ranged weapon from medium distance";
            }
            if (hasMeleeWeapon) {
                if (primaryTarget.priority == TargetPriority.IMMEDIATE_THREAT) {
                    return "APPROACH_AND_ATTACK: Close distance and attack";
                }
                return "POSITION_AND_ATTACK: Get into optimal melee range";
            }
        }

        if (primaryTarget.distance < 16) {
            // Long range
            if (hasRangedWeapon) {
                return "RANGED_ATTACK: Shoot from long distance";
            }
            return "APPROACH: Move closer to target";
        }

        // Very far
        return "APPROACH: Target is too far, move closer";
    }

    /**
     * Check if item is a melee weapon
     */
    private boolean isMeleeWeapon(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        return item.getItem() == Items.DIAMOND_SWORD ||
               item.getItem() == Items.IRON_SWORD ||
               item.getItem() == Items.NETHERITE_SWORD ||
               item.getItem() == Items.STONE_SWORD ||
               item.getItem() == Items.WOODEN_SWORD ||
               item.getItem() == Items.GOLDEN_SWORD ||
               item.getItem() == Items.DIAMOND_AXE ||
               item.getItem() == Items.IRON_AXE ||
               item.getItem() == Items.NETHERITE_AXE ||
               item.getItem() == Items.STONE_AXE ||
               item.getItem() == Items.WOODEN_AXE ||
               item.getItem() == Items.GOLDEN_AXE;
    }

    /**
     * Check if item is a ranged weapon
     */
    private boolean isRangedWeapon(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        return item.getItem() == Items.BOW ||
               item.getItem() == Items.CROSSBOW ||
               item.getItem() == Items.TRIDENT;
    }

    /**
     * Get combo recommendation based on current state
     */
    public String getComboRecommendation(int comboCount, Entity target) {
        if (comboCount >= 3) {
            return "CRITICAL_STRIKE: High damage attack opportunity";
        }
        if (comboCount >= 2) {
            return "FINISHING_BLOW: Strong attack to finish target";
        }
        if (comboCount >= 1) {
            return "FOLLOW_UP: Continue combo with quick attack";
        }
        return "INITIATE: Start combat with opening attack";
    }

    /**
     * Set combat mode
     */
    public void setCombatMode(CombatMode mode) {
        this.currentMode = mode;
        LOGGER.info("Combat mode set to: {}", mode);
    }

    /**
     * Get current target
     */
    public Entity getCurrentTarget() {
        return currentTarget;
    }

    /**
     * Set current target
     */
    public void setTarget(Entity target) {
        this.currentTarget = target;
        if (target != null) {
            this.lastTargetPosition = target.getPos();
        }
    }

    /**
     * Clear target
     */
    public void clearTarget() {
        this.currentTarget = null;
        this.priorityTarget = null;
        this.lastTargetPosition = null;
        this.comboCount = 0;
    }

    /**
     * Check if should retreat
     */
    public boolean shouldRetreat(double healthPercent, int enemyCount, boolean hasAdvantage) {
        if (healthPercent < 0.2) return true;  // Low health
        if (enemyCount >= 4 && !hasAdvantage) return true;  // Outnumbered
        return false;
    }

    /**
     * Get recommended retreat direction
     */
    public Vec3d getRetreatDirection(ServerPlayerEntity bot, List<Entity> enemies) {
        Vec3d botPos = bot.getPos();

        // Calculate average direction away from all enemies
        double avgEnemyX = 0, avgEnemyZ = 0;
        int count = 0;

        for (Entity enemy : enemies) {
            if (enemy instanceof LivingEntity) {
                avgEnemyX += enemy.getX();
                avgEnemyZ += enemy.getZ();
                count++;
            }
        }

        if (count == 0) return null;

        avgEnemyX /= count;
        avgEnemyZ /= count;

        // Direction away from average enemy position
        double dx = botPos.x - avgEnemyX;
        double dz = botPos.z - avgEnemyZ;
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length == 0) return new Vec3d(0, 0, -1);  // Default retreat direction

        return new Vec3d(dx / length, 0, dz / length);
    }
}