package net.luanvha2550_hash.ChatUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Cache de respostas do LLM para reduzir chamadas API.
 * Usa hash da mensagem como chave para evitar chamadas repetidas.
 */
public class LLMResponseCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("llm-cache");

    // Cache com TTL de 10 minutos
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    // Cache de respostas
    private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // Estatísticas
    private static int cacheHits = 0;
    private static int cacheMisses = 0;

    /**
     * Classe interna para entradas do cache com timestamp
     */
    private static class CacheEntry {
        final String response;
        final long timestamp;

        CacheEntry(String response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * Gera um hash simples para a mensagem
     */
    private static String generateKey(String message) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(message.toLowerCase().trim().getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // Primeiros 16 chars
        } catch (Exception e) {
            // Fallback para hash simples
            return String.valueOf(message.toLowerCase().trim().hashCode());
        }
    }

    /**
     * Obtém resposta do cache se existir e não estiver expirada
     * @param message Mensagem original
     * @return Resposta cacheada ou null se não existir
     */
    public static String get(String message) {
        String key = generateKey(message);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            cacheMisses++;
            LOGGER.debug("Cache miss para: {}", message.substring(0, Math.min(30, message.length())));
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            cacheMisses++;
            LOGGER.debug("Cache expirado para: {}", message.substring(0, Math.min(30, message.length())));
            return null;
        }

        cacheHits++;
        LOGGER.debug("Cache hit! ({}/{} total)", cacheHits, cacheHits + cacheMisses);
        return entry.response;
    }

    /**
     * Salva resposta no cache
     * @param message Mensagem original
     * @param response Resposta do LLM
     */
    public static void put(String message, String response) {
        String key = generateKey(message);
        cache.put(key, new CacheEntry(response, System.currentTimeMillis()));
        LOGGER.debug("Cache salvo para: {}", message.substring(0, Math.min(30, message.length())));
    }

    /**
     * Limpa todas as entradas do cache
     */
    public static void clear() {
        cache.clear();
        cacheHits = 0;
        cacheMisses = 0;
        LOGGER.info("Cache de LLM limpo");
    }

    /**
     * Remove entradas expiradas
     */
    public static void cleanup() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Obtém estatísticas do cache
     */
    public static String getStats() {
        int total = cacheHits + cacheMisses;
        double hitRate = total > 0 ? (cacheHits * 100.0 / total) : 0;
        return String.format("Cache: %d hits, %d misses, %.1f%% hit rate, %d entries",
            cacheHits, cacheMisses, hitRate, cache.size());
    }
}