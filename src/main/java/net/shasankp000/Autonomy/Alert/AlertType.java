package net.shasankp000.Autonomy.Alert;

/**
 * Enumeration of alert types for the AI-Player notification system.
 *
 * <p>Each alert type has specific characteristics:</p>
 * <ul>
 *   <li>{@code immediate} - Whether the alert should be sent instantly</li>
 *   <li>{@code icon} - Visual identifier for the alert</li>
 *   <li>{@code priority} - Importance level (1=critical, 4=low)</li>
 * </ul>
 */
public enum AlertType {

    /**
     * Diamond discovery notification.
     * Triggered when the bot finds diamond ore or loot.
     */
    DIAMOND_FOUND(false, "\uD83D\uDC8E", 2),

    /**
     * Danger detection warning.
     * Triggered when hostile entities or environmental hazards are detected.
     */
    DANGER_DETECTED(true, "\u26A0\uFE0F", 1),

    /**
     * Structure discovery notification.
     * Triggered when villages, temples, strongholds, etc. are found.
     */
    STRUCTURE_FOUND(false, "\uD83C\uDFE0", 3),

    /**
     * Rare resource discovery notification.
     * Triggered for valuable resources like ancient debris, emeralds, etc.
     */
    RARE_RESOURCE(false, "\u2728", 2),

    /**
     * Goal completion notification.
     * Triggered when an autonomous goal is successfully completed.
     */
    GOAL_COMPLETE(false, "\u2705", 3),

    /**
     * Player death alert.
     * Triggered when the bot dies, for recovery planning.
     */
    PLAYER_DEATH(true, "\u2620\uFE0F", 1);

    /**
     * Whether this alert requires immediate notification.
     * Critical alerts bypass any cooldown or batching mechanisms.
     */
    private final boolean immediate;

    /**
     * Unicode icon representing this alert type.
     */
    private final String icon;

    /**
     * Priority level (1=highest, 4=lowest).
     */
    private final int priority;

    AlertType(boolean immediate, String icon, int priority) {
        this.immediate = immediate;
        this.icon = icon;
        this.priority = priority;
    }

    /**
     * Checks if this alert type requires immediate notification.
     *
     * @return true if the alert should be sent immediately
     */
    public boolean isImmediate() {
        return immediate;
    }

    /**
     * Gets the unicode icon for this alert type.
     *
     * @return The icon string
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Gets the priority level for this alert type.
     * Lower numbers indicate higher priority.
     *
     * @return Priority from 1 (critical) to 4 (low)
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Checks if this alert type is critical (priority 1).
     *
     * @return true if critical priority
     */
    public boolean isCritical() {
        return priority == 1;
    }

    /**
     * Gets a formatted prefix for chat messages.
     *
     * @return Formatted icon prefix
     */
    public String getPrefix() {
        return icon + " ";
    }

    /**
     * Gets a display name for this alert type.
     *
     * @return Human-readable alert type name
     */
    public String getDisplayName() {
        return name().replace("_", " ");
    }
}