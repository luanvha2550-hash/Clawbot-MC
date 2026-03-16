/**
 * Knowledge Cache for learned behaviors in the AI-Player autonomy system.
 *
 * <p>The KnowledgeCache stores learned behaviors with embedding similarity search.
 * When the bot encounters a situation, it checks if a similar situation was seen
 * before and reuses the learned action.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link net.shasankp000.Autonomy.Cache.KnowledgeCache} - Thread-safe cache for situation-action mappings</li>
 *   <li>{@link net.shasankp000.Autonomy.Cache.CachedSituation} - Represents a learned situation with success/failure tracking</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * KnowledgeCache cache = KnowledgeCache.getInstance();
 *
 * // Find similar cached situation
 * Optional<String> action = cache.findActionForSituation(embedding);
 *
 * // Learn from experience
 * cache.learnSituation(description, action, embedding, success);
 * }</pre>
 *
 * @see net.shasankp000.Autonomy.Cache.KnowledgeCache
 * @see net.shasankp000.Autonomy.Cache.CachedSituation
 * @see net.shasankp000.AIProviders.EmbeddingProvider
 */
package net.shasankp000.Autonomy.Cache;