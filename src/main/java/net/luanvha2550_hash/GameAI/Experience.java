package net.luanvha2550_hash.GameAI;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;

/**
 * Representa uma experiência de aprendizado do agente.
 *
 * Cada experiência armazena:
 * - Estado em que a ação foi tomada
 * - Ação executada
 * - Recompensa recebida
 * - Próximo estado
 * - Timestamp para priorização
 *
 * Implementa Comparable para ordenação por prioridade na PriorityQueue.
 */
public class Experience implements Comparable<Experience>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Estado e ação
    public final State state;
    public final StateActions.Action action;
    public final double reward;
    public final State nextState;

    // Metadados
    public final long timestamp;
    public final int episodeId;

    // Prioridade calculada (usada para ordenação)
    private double priority;

    // Contador estático para IDs de episódio
    private static int episodeCounter = 0;

    public Experience(State state, StateActions.Action action, double reward,
                      State nextState, int episodeId) {
        this.state = state;
        this.action = action;
        this.reward = reward;
        this.nextState = nextState;
        this.timestamp = System.currentTimeMillis();
        this.episodeId = episodeId;
        this.priority = calculatePriority();
    }

    /**
     * Calcula a prioridade desta experiência.
     *
     * Prioridade baseada em:
     * - Recompensa absoluta (maior = mais importante)
     * - Recência (experiências recentes têm valor)
     * - Surpreendente (diferença entre recompensa esperada e recebida)
     */
    private double calculatePriority() {
        double basePriority = Math.abs(reward);

        // Bônus para recompensas negativas (aprender com erros é importante)
        if (reward < 0) {
            basePriority *= 1.5;
        }

        // Bônus para recompensas muito positivas (descobertas importantes)
        if (reward > 50) {
            basePriority *= 1.3;
        }

        return basePriority;
    }

    /**
     * Atualiza a prioridade baseada em novo conhecimento.
     */
    public void updatePriority(double newPriority) {
        this.priority = newPriority;
    }

    public double getPriority() {
        return priority;
    }

    /**
     * Compara experiências por prioridade (ordem reversa para PriorityQueue).
     */
    @Override
    public int compareTo(Experience other) {
        // Ordem reversa: maior prioridade primeiro
        return Double.compare(other.priority, this.priority);
    }

    /**
     * Verifica se esta experiência é similar a outra.
     * Usado para evitar experiências duplicadas na memória.
     */
    public boolean isSimilarTo(Experience other, double threshold) {
        if (this.action != other.action) {
            return false;
        }

        // Comparar estados usando similaridade
        double stateSimilarity = this.state.calculateSimilarity(other.state);
        return stateSimilarity > threshold;
    }

    /**
     * Retorna idade da experiência em milissegundos.
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Verifica se a experiência é recente (< 5 minutos).
     */
    public boolean isRecent() {
        return getAge() < 5 * 60 * 1000;
    }

    /**
     * Verifica se foi uma experiência de morte.
     */
    public boolean isDeathExperience() {
        return reward <= -100;
    }

    /**
     * Verifica se foi uma descoberta importante.
     */
    public boolean isDiscovery() {
        return reward >= 50;
    }

    @Override
    public String toString() {
        return String.format("Experience[action=%s, reward=%.1f, priority=%.2f, age=%ds]",
            action, reward, priority, getAge() / 1000);
    }

    /**
     * Factory method para criar nova experiência.
     */
    public static Experience create(State state, StateActions.Action action,
                                     double reward, State nextState) {
        return new Experience(state, action, reward, nextState, episodeCounter++);
    }

    /**
     * Incrementa o contador de episódios.
     */
    public static void nextEpisode() {
        episodeCounter++;
    }

    /**
     * Reseta o contador de episódios.
     */
    public static void resetEpisodeCounter() {
        episodeCounter = 0;
    }

    /**
     * Comparator para ordenação por prioridade.
     */
    public static class PriorityComparator implements Comparator<Experience> {
        @Override
        public int compare(Experience e1, Experience e2) {
            return Double.compare(e2.getPriority(), e1.getPriority());
        }
    }

    /**
     * Comparator para ordenação por recência.
     */
    public static class RecencyComparator implements Comparator<Experience> {
        @Override
        public int compare(Experience e1, Experience e2) {
            return Long.compare(e2.timestamp, e1.timestamp);
        }
    }
}
