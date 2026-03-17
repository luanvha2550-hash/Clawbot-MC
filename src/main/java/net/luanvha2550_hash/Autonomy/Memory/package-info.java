/**
 * Memory systems for the AI-Player Autonomy architecture.
 *
 * <p>This package provides persistent memory storage for learned information:</p>
 * <ul>
 *   <li>{@link net.luanvha2550_hash.Autonomy.Memory.LongTermMemory} - Main memory manager with JSON persistence</li>
 *   <li>{@link net.luanvha2550_hash.Autonomy.Memory.WorldLocation} - Named locations with metadata</li>
 *   <li>{@link net.luanvha2550_hash.Autonomy.Memory.DeathSpot} - Death event records</li>
 * </ul>
 *
 * <h2>Memory Types:</h2>
 * <dl>
 *   <dt>Locations</dt>
 *   <dd>Named world positions with type classification (BASE, RESOURCE, DANGER, INTERESTING)</dd>
 *   <dt>Death Spots</dt>
 *   <dd>Records of where and why the bot died for danger avoidance learning</dd>
 *   <dt>Player Memories</dt>
 *   <dd>Relationship data with other players (trust levels, interaction history)</dd>
 * </dl>
 *
 * <h2>Persistence:</h2>
 * <p>All memory data is persisted to JSON files in the game config directory
 * and automatically loaded on mod initialization.</p>
 *
 * @see net.luanvha2550_hash.Autonomy.AutonomyContext
 * @see net.luanvha2550_hash.Autonomy.Layers.SurvivalLayer
 */
package net.luanvha2550_hash.Autonomy.Memory;