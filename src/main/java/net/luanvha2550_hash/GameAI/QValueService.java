package net.luanvha2550_hash.GameAI;

import net.luanvha2550_hash.Database.QEntry;
import net.luanvha2550_hash.Database.QTable;
import net.luanvha2550_hash.Database.StateActionPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia cálculo e cache de Q-values.
 * Extraído de RLAgent.java para separar responsabilidade de Q-value.
 */
public class QValueService {

    private static final Logger LOGGER = LoggerFactory.getLogger("qvalue-service");
    private static final double ALPHA = 0.1;
    private static final double GAMMA = 0.9;
    private static final long CACHE_EXPIRY_MS = 500;

    private final QTable qTable;
    private final Map<String, Double> maxQValueCache = new ConcurrentHashMap<>();
    private long lastCacheClear = System.currentTimeMillis();

    public QValueService(QTable qTable) {
        this.qTable = qTable;
    }

    /**
     * Calcula Q-value para transição state-action-nextState.
     * Método principal extraído de RLAgent.calculateQValue().
     */
    public double calculateQValue(State initialState, StateActions.Action action, double reward, State nextState) {
        StateActionPair pair = new StateActionPair(initialState, action);
        QEntry existingEntry = qTable.getEntry(pair);
        double oldQValue = (existingEntry != null) ? existingEntry.getQValue() : 0.0;

        clearCacheIfExpired();

        String cacheKey = String.format("%d_%d_%d_%s_%d",
            nextState.getBotX(), nextState.getBotY(), nextState.getBotZ(),
            nextState.getDimensionType(), nextState.getNearbyEntities().size());

        double maxNextQValue;
        Double cachedMax = maxQValueCache.get(cacheKey);
        if (cachedMax != null) {
            maxNextQValue = cachedMax;
        } else {
            int bucketRange = 2;
            maxNextQValue = qTable.getTable().entrySet().stream()
                .filter(e -> {
                    State s = e.getKey().getState();
                    return Math.abs(s.getBotX() - nextState.getBotX()) <= bucketRange &&
                           Math.abs(s.getBotY() - nextState.getBotY()) <= bucketRange &&
                           Math.abs(s.getBotZ() - nextState.getBotZ()) <= bucketRange &&
                           s.getDimensionType().equals(nextState.getDimensionType());
                })
                .filter(e -> State.isStateConsistent(e.getKey().getState(), nextState))
                .mapToDouble(e -> e.getValue().getQValue())
                .max().orElse(0.0);
            maxQValueCache.put(cacheKey, maxNextQValue);
        }

        double newQValue = oldQValue + ALPHA * (reward + GAMMA * maxNextQValue - oldQValue);
        LOGGER.debug("Q-value calculado: {} -> {}", pair, newQValue);
        return newQValue;
    }

    /**
     * Obtém Q-value para par state-action.
     */
    public double getQValue(State state, StateActions.Action action) {
        StateActionPair pair = new StateActionPair(state, action);
        QEntry entry = qTable.getEntry(pair);
        return entry != null ? entry.getQValue() : 0.0;
    }

    /**
     * Atualiza Q-value para par state-action.
     */
    public void updateQValue(State state, StateActions.Action action, double newQValue, State nextState) {
        qTable.addEntry(state, action, newQValue, nextState);
    }

    /**
     * Obtém máximo Q-value para um estado.
     */
    public double getMaxQValue(State state) {
        String cacheKey = String.format("max_%d_%d_%d_%s",
            state.getBotX(), state.getBotY(), state.getBotZ(), state.getDimensionType());

        Double cached = maxQValueCache.get(cacheKey);
        if (cached != null) return cached;

        double maxQ = Double.NEGATIVE_INFINITY;
        for (Map.Entry<StateActionPair, QEntry> entry : qTable.getTable().entrySet()) {
            if (State.isStateConsistent(entry.getKey().getState(), state)) {
                maxQ = Math.max(maxQ, entry.getValue().getQValue());
            }
        }
        double result = maxQ == Double.NEGATIVE_INFINITY ? 0.0 : maxQ;
        maxQValueCache.put(cacheKey, result);
        return result;
    }

    /**
     * Limpa cache se expirado.
     */
    private void clearCacheIfExpired() {
        long now = System.currentTimeMillis();
        if (now - lastCacheClear > CACHE_EXPIRY_MS) {
            maxQValueCache.clear();
            lastCacheClear = now;
            LOGGER.debug("Cache Q-value limpo");
        }
    }

    /**
     * Limpa cache forçadamente.
     */
    public void clearCache() {
        maxQValueCache.clear();
        lastCacheClear = System.currentTimeMillis();
    }
}
