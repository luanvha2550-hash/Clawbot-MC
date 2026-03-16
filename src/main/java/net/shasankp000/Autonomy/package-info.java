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
 *
 * @see net.shasankp000.Autonomy.AutonomyEngine
 * @see net.shasankp000.Autonomy.DecisionLayer
 */
package net.shasankp000.Autonomy;