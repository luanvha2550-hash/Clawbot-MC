/**
 * Alert system for the AI-Player Autonomy architecture.
 *
 * <p>This package provides real-time notifications for important events:</p>
 * <ul>
 *   <li>{@link net.shasankp000.Autonomy.Alert.AlertType} - Enumeration of alert categories with priority</li>
 *   <li>{@link net.shasankp000.Autonomy.Alert.AlertConfig} - Configuration for alert behavior</li>
 *   <li>{@link net.shasankp000.Autonomy.Alert.AlertSystem} - Main alert dispatcher</li>
 * </ul>
 *
 * <h2>Alert Types:</h2>
 * <dl>
 *   <dt>DIAMOND_FOUND</dt>
 *   <dd>Notification when valuable resources like diamonds are discovered</dd>
 *   <dt>DANGER_DETECTED</dt>
 *   <dd>Warning about hostile entities or environmental hazards</dd>
 *   <dt>STRUCTURE_FOUND</dt>
 *   <dd>Discovery of generated structures (villages, temples, etc.)</dd>
 *   <dt>RARE_RESOURCE</dt>
 *   <dd>Location of rare or valuable resources</dd>
 *   <dt>GOAL_COMPLETE</dt>
 *   <dd>Notification when an autonomous goal is achieved</dd>
 *   <dt>PLAYER_DEATH</dt>
 *   <dd>Alert when the bot dies (for recovery planning)</dd>
 * </dl>
 *
 * <h2>Priority Levels:</h2>
 * <ul>
 *   <li>1 - Critical (immediate attention required)</li>
 *   <li>2 - High (important events)</li>
 *   <li>3 - Normal (standard notifications)</li>
 *   <li>4 - Low (informational)</li>
 * </ul>
 *
 * <h2>Configuration:</h2>
 * <p>Alert preferences can be customized via JSON configuration:
 * <pre>{@code
 * {
 *   "alerts": {
 *     "DIAMOND_FOUND": { "enabled": true, "priority": 2 },
 *     "DANGER_DETECTED": { "enabled": true, "priority": 1 }
 *   }
 * }
 * }</pre>
 *
 * @see net.shasankp000.Autonomy.Alert.AlertType
 * @see net.shasankp000.Autonomy.Alert.AlertSystem
 */
package net.shasankp000.Autonomy.Alert;