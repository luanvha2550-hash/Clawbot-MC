package net.luanvha2550_hash.Autonomy.Alert;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Alert system for notifying players about important events.
 *
 * <p>The AlertSystem provides real-time notifications for various events
 * discovered or encountered by the autonomous bot, including:</p>
 * <ul>
 *   <li>Diamond and rare resource discoveries</li>
 *   <li>Danger warnings (hostiles, environmental hazards)</li>
 *   <li>Structure discoveries</li>
 *   <li>Goal completions</li>
 *   <li>Death notifications</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * AlertSystem alertSystem = new AlertSystem(botPlayer);
 *
 * // Notify about diamond discovery
 * alertSystem.onDiamondFound(position, 3);
 *
 * // Warn about danger
 * alertSystem.onDangerDetected("Creeper", creeperPosition);
 *
 * // Announce structure discovery
 * alertSystem.onStructureFound("Village", villagePosition);
 * }</pre>
 *
 * <h2>Message Format:</h2>
 * <p>Alerts are sent as chat messages with the format:
 * {@code [Bot] <icon> <message>}</p>
 *
 * @see AlertType
 * @see AlertConfig
 */
public class AlertSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger("AlertSystem");

    /**
     * Cooldown between similar alerts (in milliseconds).
     */
    private static final long DEFAULT_COOLDOWN_MS = 5000L;

    /**
     * The bot player entity for sending messages.
     */
    private final ServerPlayerEntity bot;

    /**
     * The server instance.
     */
    private final MinecraftServer server;

    /**
     * Configuration for alert behavior.
     */
    private final AlertConfig config;

    /**
     * Scheduler for delayed message sending.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new AlertSystem for the given bot.
     *
     * @param bot The bot player entity
     */
    public AlertSystem(ServerPlayerEntity bot) {
        this.bot = bot;
        this.server = bot.getServer();
        this.config = AlertConfig.getInstance();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Notifies about diamond discovery.
     *
     * <p>Sends a formatted message with the location and count of diamonds found.</p>
     *
     * @param position The position where diamonds were found
     * @param count Number of diamond ore blocks found
     */
    public void onDiamondFound(Vec3d position, int count) {
        if (!shouldSendAlert(AlertType.DIAMOND_FOUND)) {
            return;
        }

        String message = String.format(
                "%s DIAMANTES! Encontrei %d diamante%s em (%d, %d, %d)",
                AlertType.DIAMOND_FOUND.getIcon(),
                count,
                count > 1 ? "s" : "",
                (int) position.x,
                (int) position.y,
                (int) position.z
        );

        sendAlert(AlertType.DIAMOND_FOUND, message);
        LOGGER.info("Diamond found alert: {} diamonds at {}", count, formatPosition(position));
    }

    /**
     * Notifies about detected danger.
     *
     * <p>Warnings are sent immediately due to their critical priority.</p>
     *
     * @param dangerType Type of danger detected (e.g., "Creeper", "Lava", "Fall")
     * @param position Position where danger was detected
     */
    public void onDangerDetected(String dangerType, Vec3d position) {
        if (!shouldSendAlert(AlertType.DANGER_DETECTED)) {
            return;
        }

        String message = String.format(
                "%s PERIGO! %s detectado em (%d, %d, %d) - Tenha cuidado!",
                AlertType.DANGER_DETECTED.getIcon(),
                dangerType,
                (int) position.x,
                (int) position.y,
                (int) position.z
        );

        sendAlert(AlertType.DANGER_DETECTED, message);
        LOGGER.warn("Danger detected: {} at {}", dangerType, formatPosition(position));
    }

    /**
     * Notifies about structure discovery.
     *
     * @param structureType Type of structure found (e.g., "Village", "Temple", "Fortress")
     * @param position Position of the structure
     */
    public void onStructureFound(String structureType, Vec3d position) {
        if (!shouldSendAlert(AlertType.STRUCTURE_FOUND)) {
            return;
        }

        String message = String.format(
                "%s Estrutura encontrada: %s em (%d, %d, %d)",
                AlertType.STRUCTURE_FOUND.getIcon(),
                structureType,
                (int) position.x,
                (int) position.y,
                (int) position.z
        );

        sendAlert(AlertType.STRUCTURE_FOUND, message);
        LOGGER.info("Structure found: {} at {}", structureType, formatPosition(position));
    }

    /**
     * Notifies about rare resource discovery.
     *
     * @param resourceType Type of resource (e.g., "Ancient Debris", "Emerald")
     * @param position Position where resource was found
     * @param count Quantity found
     */
    public void onRareResourceFound(String resourceType, Vec3d position, int count) {
        if (!shouldSendAlert(AlertType.RARE_RESOURCE)) {
            return;
        }

        String message = String.format(
                "%s Recurso raro encontrado: %dx %s em (%d, %d, %d)",
                AlertType.RARE_RESOURCE.getIcon(),
                count,
                resourceType,
                (int) position.x,
                (int) position.y,
                (int) position.z
        );

        sendAlert(AlertType.RARE_RESOURCE, message);
        LOGGER.info("Rare resource found: {}x {} at {}", count, resourceType, formatPosition(position));
    }

    /**
     * Notifies about goal completion.
     *
     * @param goalDescription Description of completed goal
     * @param position Position where goal was completed (if applicable)
     */
    public void onGoalComplete(String goalDescription, Vec3d position) {
        if (!shouldSendAlert(AlertType.GOAL_COMPLETE)) {
            return;
        }

        String message;
        if (position != null) {
            message = String.format(
                    "%s Objetivo completado: %s em (%d, %d, %d)",
                    AlertType.GOAL_COMPLETE.getIcon(),
                    goalDescription,
                    (int) position.x,
                    (int) position.y,
                    (int) position.z
            );
        } else {
            message = String.format(
                    "%s Objetivo completado: %s",
                    AlertType.GOAL_COMPLETE.getIcon(),
                    goalDescription
            );
        }

        sendAlert(AlertType.GOAL_COMPLETE, message);
        LOGGER.info("Goal completed: {}", goalDescription);
    }

    /**
     * Notifies about player death.
     *
     * <p>This is a critical alert that is always sent immediately.</p>
     *
     * @param position Death location
     * @param cause Cause of death
     */
    public void onPlayerDeath(Vec3d position, String cause) {
        if (!shouldSendAlert(AlertType.PLAYER_DEATH)) {
            return;
        }

        String message = String.format(
                "%s MORRI! Causa: %s em (%d, %d, %d) - Preciso recuperar meus itens!",
                AlertType.PLAYER_DEATH.getIcon(),
                cause,
                (int) position.x,
                (int) position.y,
                (int) position.z
        );

        sendAlert(AlertType.PLAYER_DEATH, message);
        LOGGER.error("Player death: {} at {} - {}", cause, formatPosition(position), cause);
    }

    /**
     * Sends a custom alert message.
     *
     * @param type Alert type
     * @param message Custom message to send
     */
    public void sendCustomAlert(AlertType type, String message) {
        if (!shouldSendAlert(type)) {
            return;
        }

        String formattedMessage = type.getIcon() + " " + message;
        sendAlert(type, formattedMessage);
    }

    // ========== Private Methods ==========

    /**
     * Checks if an alert should be sent based on configuration.
     *
     * @param type The alert type to check
     * @return true if the alert should be sent
     */
    private boolean shouldSendAlert(AlertType type) {
        if (bot == null || server == null) {
            LOGGER.warn("Cannot send alert {}: bot or server is null", type);
            return false;
        }

        return config.isEnabled(type);
    }

    /**
     * Sends an alert message to chat.
     *
     * @param type Alert type (determines urgency)
     * @param message Message to send
     */
    private void sendAlert(AlertType type, String message) {
        if (server == null || bot == null) {
            LOGGER.error("Cannot send alert: server or bot is null");
            return;
        }

        // Execute on server thread for thread safety
        if (type.isImmediate()) {
            // Immediate alerts bypass any delay
            server.execute(() -> broadcastMessage(message));
        } else {
            // Non-immediate alerts with slight delay for readability
            scheduler.schedule(() -> {
                if (server != null) {
                    server.execute(() -> broadcastMessage(message));
                }
            }, 100, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Broadcasts a message to all players.
     *
     * @param message The message to broadcast
     */
    private void broadcastMessage(String message) {
        try {
            // Format as [Bot] message
            String formattedMessage = String.format("[Bot] %s", message);

            // Get the bot's name for the prefix
            String botName = bot.getName().getString();

            // Send to chat using say command
            ServerCommandSource source = bot.getCommandSource();
            server.getCommandManager().executeWithPrefix(
                    source.withSilent(),
                    "/say " + formattedMessage
            );

            LOGGER.debug("Broadcast alert: {}", formattedMessage);

        } catch (Exception e) {
            LOGGER.error("Failed to send alert message: {}", e.getMessage());
        }
    }

    /**
     * Formats a position for logging.
     *
     * @param pos Position to format
     * @return Formatted position string
     */
    private String formatPosition(Vec3d pos) {
        return String.format("(%.0f, %.0f, %.0f)", pos.x, pos.y, pos.z);
    }

    /**
     * Shuts down the scheduler.
     * Call this when the bot is being removed.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("AlertSystem shutdown complete");
    }
}