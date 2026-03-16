package net.shasankp000.Autonomy;

/**
 * Interface for all decision layers in the autonomy system.
 *
 * <p>Each layer evaluates the current context and returns an action to execute.
 * Layers are processed in priority order: Survival > Combat > Goals > Commands > Idle.</p>
 *
 * <p>Implementation should be stateless where possible, with any state
 * managed through the AutonomyContext.</p>
 */
public interface DecisionLayer {

    /**
     * Evaluate the current context and determine what action to take.
     *
     * @param context Current world state snapshot
     * @return The action to execute, or noAction() if this layer has nothing to do
     */
    ActionResult evaluate(AutonomyContext context);

    /**
     * Get the name of this layer for logging and debugging.
     *
     * @return Layer name (e.g., "Survival", "Combat", "Goals")
     */
    String getLayerName();

    /**
     * Get the priority of this layer. Lower numbers = higher priority.
     *
     * @return Priority number (1 = highest)
     */
    int getPriority();
}