package net.luanvha2550_hash.GameAI;

import net.luanvha2550_hash.Database.QEntry;
import net.luanvha2550_hash.Database.QTable;
import net.luanvha2550_hash.Database.StateActionPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Gerencia memória de experiências para aprendizado por reforço.
 * Extraído de RLAgent.java para separar responsabilidade de experiência.
 */
public class ExperienceMemory {

    private static final Logger LOGGER = LoggerFactory.getLogger("experience-memory");
    private static final int MAX_EXPERIENCES = 1000;
    private static final int SAMPLE_SIZE = 32;

    private final PriorityQueue<Experience> experienceMemory;
    private final List<Experience> experienceBuffer;
    private final QTable qTable;

    public ExperienceMemory(QTable qTable) {
        this.experienceMemory = new PriorityQueue<>();
        this.experienceBuffer = new ArrayList<>();
        this.qTable = qTable;
    }

    /**
     * Adiciona experiência ao buffer.
     */
    public void addExperience(Experience experience) {
        experienceBuffer.add(experience);
        if (experienceBuffer.size() >= 10) {
            flushExperienceBuffer();
        }
    }

    /**
     * Move experiências do buffer para memória principal.
     */
    private void flushExperienceBuffer() {
        for (Experience exp : experienceBuffer) {
            boolean isDuplicate = experienceMemory.stream().anyMatch(e -> e.isSimilarTo(exp, 0.95));
            if (!isDuplicate) {
                experienceMemory.offer(exp);
            }
        }
        experienceBuffer.clear();
        while (experienceMemory.size() > MAX_EXPERIENCES) {
            experienceMemory.poll();
        }
    }

    /**
     * Amuestra experiências para aprendizado.
     * 70% prioritárias, 30% aleatórias.
     */
    public List<Experience> sampleExperiences() {
        flushExperienceBuffer();
        if (experienceMemory.isEmpty()) return new ArrayList<>();

        List<Experience> samples = new ArrayList<>();
        int prioritySamples = (int) (SAMPLE_SIZE * 0.7);

        PriorityQueue<Experience> tempQueue = new PriorityQueue<>(experienceMemory);
        for (int i = 0; i < prioritySamples && !tempQueue.isEmpty(); i++) {
            samples.add(tempQueue.poll());
        }

        int randomSamples = SAMPLE_SIZE - samples.size();
        List<Experience> allExperiences = new ArrayList<>(experienceMemory);
        Collections.shuffle(allExperiences);
        for (int i = 0; i < randomSamples && i < allExperiences.size(); i++) {
            Experience randomExp = allExperiences.get(i);
            if (!samples.contains(randomExp)) {
                samples.add(randomExp);
            }
        }
        return samples;
    }

    /**
     * Aprende de uma única experiência.
     */
    public void learnFromExperience(Experience exp, double alpha, double gamma) {
        StateActionPair sap = new StateActionPair(exp.state, exp.action);
        QEntry entry = qTable.getEntry(sap);
        double currentQ = (entry != null) ? entry.getQValue() : 0.0;
        double maxNextQ = getMaxQValue(exp.nextState);
        double newQ = currentQ + alpha * (exp.reward + gamma * maxNextQ - currentQ);
        qTable.addEntry(exp.state, exp.action, newQ, exp.nextState);
        double tdError = Math.abs(exp.reward + gamma * maxNextQ - currentQ);
        exp.updatePriority(tdError);
    }

    /**
     * Aprende de um batch de experiências.
     */
    public void learnFromBatch(double alpha, double gamma) {
        List<Experience> batch = sampleExperiences();
        if (batch.isEmpty()) return;
        LOGGER.info("Aprendendo de {} experiências", batch.size());
        for (Experience exp : batch) {
            learnFromExperience(exp, alpha, gamma);
        }
    }

    private double getMaxQValue(State state) {
        double maxQ = Double.NEGATIVE_INFINITY;
        for (Map.Entry<StateActionPair, QEntry> entry : qTable.getTable().entrySet()) {
            if (State.isStateConsistent(entry.getKey().getState(), state)) {
                maxQ = Math.max(maxQ, entry.getValue().getQValue());
            }
        }
        return maxQ == Double.NEGATIVE_INFINITY ? 0.0 : maxQ;
    }

    /**
     * Retorna métricas de experiência.
     */
    public ExperienceMetrics getExperienceMetrics() {
        flushExperienceBuffer();
        return new ExperienceMetrics(
            experienceMemory.size(),
            experienceBuffer.size(),
            experienceMemory.stream().mapToDouble(Experience::getPriority).average().orElse(0.0),
            experienceMemory.stream().filter(Experience::isRecent).count()
        );
    }

    /**
     * Limpa memória de experiências.
     */
    public void clearExperienceMemory() {
        experienceMemory.clear();
        experienceBuffer.clear();
    }

    /**
     * Métricas de experiência.
     */
    public static class ExperienceMetrics {
        public final int totalExperiences;
        public final int bufferSize;
        public final double avgPriority;
        public final long recentExperiences;

        public ExperienceMetrics(int total, int buffer, double avgPriority, long recent) {
            this.totalExperiences = total;
            this.bufferSize = buffer;
            this.avgPriority = avgPriority;
            this.recentExperiences = recent;
        }

        @Override
        public String toString() {
            return String.format("Experiences: %d (buffer: %d), Avg Priority: %.2f, Recent: %d",
                totalExperiences, bufferSize, avgPriority, recentExperiences);
        }
    }
}
