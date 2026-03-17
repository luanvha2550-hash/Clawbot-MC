/**
 * Decision layers for the autonomy system.
 *
 * <p>Each layer handles a specific type of decision, processed in priority order:</p>
 * <ol>
 *   <li>SurvivalLayer - Instant reflexes for health, hunger, environmental dangers</li>
 *   <li>CombatLayer - Q-Learning based combat decisions</li>
 *   <li>GoalsLayer - Auto-generated and player-assigned objectives</li>
 *   <li>CommandLayer - Player chat commands</li>
 *   <li>IdleLayer - Follow player, observe, patrol</li>
 * </ol>
 */
package net.luanvha2550_hash.Autonomy.Layers;