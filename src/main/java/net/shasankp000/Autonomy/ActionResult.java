package net.shasankp000.Autonomy;

import net.minecraft.util.math.Vec3d;

/**
 * Result of a decision layer evaluation.
 *
 * <p>Contains information about what action to take and how to execute it.</p>
 */
public class ActionResult {

    /**
     * Type of action result
     */
    public enum Type {
        /** No action needed, proceed to next layer */
        NO_ACTION,
        /** Immediate action required (survival) */
        IMMEDIATE,
        /** Normal action (can be queued) */
        NORMAL,
        /** Scheduled action (part of a plan) */
        SCHEDULED,
        /** Action completed successfully */
        COMPLETED
    }

    private final Type type;
    private final String actionId;
    private final String description;
    private final Runnable executor;
    private final boolean success;
    private final long timestamp;

    // Movement target (for MOVE_TO and FOLLOW_OWNER actions)
    private final Vec3d targetPosition;

    private ActionResult(Type type, String actionId, String description,
                         Runnable executor, boolean success, Vec3d targetPosition) {
        this.type = type;
        this.actionId = actionId;
        this.description = description;
        this.executor = executor;
        this.success = success;
        this.timestamp = System.currentTimeMillis();
        this.targetPosition = targetPosition;
    }

    // Backward-compatible constructor
    private ActionResult(Type type, String actionId, String description,
                         Runnable executor, boolean success) {
        this(type, actionId, description, executor, success, null);
    }

    // ========== Factory Methods ==========

    /**
     * No action needed - proceed to next layer.
     */
    public static ActionResult noAction() {
        return new ActionResult(Type.NO_ACTION, null, null, null, false, null);
    }

    /**
     * Immediate action required (survival layer).
     */
    public static ActionResult immediate(String actionId, Runnable executor, String description) {
        return new ActionResult(Type.IMMEDIATE, actionId, description, executor, false, null);
    }

    /**
     * Normal action that can be queued.
     */
    public static ActionResult normal(String actionId, Runnable executor, String description) {
        return new ActionResult(Type.NORMAL, actionId, description, executor, false, null);
    }

    /**
     * Scheduled action (part of a multi-step plan).
     */
    public static ActionResult scheduled(String actionId, String firstAction, String description) {
        return new ActionResult(Type.SCHEDULED, actionId, description, null, false, null);
    }

    /**
     * Action completed successfully.
     */
    public static ActionResult completed(String description) {
        return new ActionResult(Type.COMPLETED, null, description, null, true, null);
    }

    // ========== Convenience Factory Methods ==========

    /**
     * Create an action to move to a position.
     */
    public static ActionResult moveTo(Vec3d position) {
        return new ActionResult(Type.NORMAL, "MOVE_TO",
            String.format("Move to (%.0f, %.0f, %.0f)", position.x, position.y, position.z),
            null, false, position);
    }

    /**
     * Create an action to follow owner.
     */
    public static ActionResult followOwner(Vec3d ownerPosition) {
        return new ActionResult(Type.NORMAL, "FOLLOW_OWNER",
            String.format("Follow owner at (%.0f, %.0f, %.0f)",
                ownerPosition.x, ownerPosition.y, ownerPosition.z),
            null, false, ownerPosition);
    }

    /**
     * Create an action to observe player behavior.
     */
    public static ActionResult observePlayer() {
        return new ActionResult(Type.NORMAL, "OBSERVE_PLAYER", "Observe player behavior pattern", null, false, null);
    }

    // ========== Getters ==========

    /**
     * Should this action be executed?
     */
    public boolean shouldExecute() {
        return type != Type.NO_ACTION && type != Type.COMPLETED;
    }

    /**
     * Was this action successful?
     */
    public boolean wasSuccessful() {
        return success;
    }

    /**
     * Does this action have a movement target?
     */
    public boolean hasMovementTarget() {
        return targetPosition != null;
    }

    public Type getType() { return type; }
    public String getActionId() { return actionId; }
    public String getDescription() { return description; }
    public Runnable getExecutor() { return executor; }
    public long getTimestamp() { return timestamp; }
    public Vec3d getTargetPosition() { return targetPosition; }

    @Override
    public String toString() {
        if (type == Type.NO_ACTION) {
            return "ActionResult[NO_ACTION]";
        }
        return String.format("ActionResult[%s, %s, %s]", type, actionId, description);
    }
}