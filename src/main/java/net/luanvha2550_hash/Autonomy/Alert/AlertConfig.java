package net.luanvha2550_hash.Autonomy.Alert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration for alert system behavior.
 *
 * <p>Manages per-alert-type settings including:</p>
 * <ul>
 *   <li>Enable/disable individual alert types</li>
 *   <li>Custom priority overrides</li>
 *   <li>Persistence to JSON configuration file</li>
 * </ul>
 *
 * <h2>Configuration File:</h2>
 * <p>Stored at: {@code config/ai-player/alert_config.json}</p>
 * <pre>{@code
 * {
 *   "alerts": {
 *     "DIAMOND_FOUND": { "enabled": true, "priority": 2 },
 *     "DANGER_DETECTED": { "enabled": true, "priority": 1 },
 *     "STRUCTURE_FOUND": { "enabled": true, "priority": 3 },
 *     "RARE_RESOURCE": { "enabled": true, "priority": 2 },
 *     "GOAL_COMPLETE": { "enabled": true, "priority": 3 },
 *     "PLAYER_DEATH": { "enabled": true, "priority": 1 }
 *   }
 * }
 * }</pre>
 */
public class AlertConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("AlertConfig");

    /**
     * Singleton instance.
     */
    private static volatile AlertConfig instance;

    /**
     * Configuration file name.
     */
    private static final String CONFIG_FILE = "alert_config.json";

    /**
     * Storage path for configuration.
     */
    private final Path storagePath;

    /**
     * Alert type settings.
     */
    private final EnumMap<AlertType, AlertSettings> alertSettings;

    /**
     * Settings for a single alert type.
     */
    public static class AlertSettings {
        private boolean enabled;
        private int priority;

        public AlertSettings() {
            this.enabled = true;
            this.priority = -1; // -1 means use default
        }

        public AlertSettings(boolean enabled, int priority) {
            this.enabled = enabled;
            this.priority = priority;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        /**
         * Gets the effective priority, using default if not set.
         *
         * @param defaultPriority The default priority to use
         * @return Effective priority value
         */
        public int getEffectivePriority(int defaultPriority) {
            return priority > 0 ? priority : defaultPriority;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", enabled);
            obj.addProperty("priority", priority);
            return obj;
        }

        public static AlertSettings fromJson(JsonObject obj) {
            AlertSettings settings = new AlertSettings();
            if (obj.has("enabled")) {
                settings.enabled = obj.get("enabled").getAsBoolean();
            }
            if (obj.has("priority")) {
                settings.priority = obj.get("priority").getAsInt();
            }
            return settings;
        }
    }

    /**
     * Private constructor for singleton pattern.
     */
    private AlertConfig() {
        this.alertSettings = new EnumMap<>(AlertType.class);
        this.storagePath = FabricLoader.getInstance().getConfigDir()
                .resolve("ai-player")
                .resolve(CONFIG_FILE);

        // Initialize with defaults
        for (AlertType type : AlertType.values()) {
            alertSettings.put(type, new AlertSettings(true, -1));
        }

        // Load existing configuration
        load();
    }

    /**
     * Gets the singleton instance.
     *
     * @return The AlertConfig instance
     */
    public static AlertConfig getInstance() {
        if (instance == null) {
            synchronized (AlertConfig.class) {
                if (instance == null) {
                    instance = new AlertConfig();
                }
            }
        }
        return instance;
    }

    /**
     * Checks if an alert type is enabled.
     *
     * @param type The alert type to check
     * @return true if enabled
     */
    public boolean isEnabled(AlertType type) {
        AlertSettings settings = alertSettings.get(type);
        return settings != null && settings.isEnabled();
    }

    /**
     * Sets whether an alert type is enabled.
     *
     * @param type The alert type
     * @param enabled Whether it should be enabled
     */
    public void setEnabled(AlertType type, boolean enabled) {
        AlertSettings settings = alertSettings.get(type);
        if (settings != null) {
            settings.setEnabled(enabled);
            LOGGER.debug("Alert {} enabled: {}", type, enabled);
        }
    }

    /**
     * Gets the priority for an alert type.
     *
     * @param type The alert type
     * @return Effective priority (uses default if not configured)
     */
    public int getPriority(AlertType type) {
        AlertSettings settings = alertSettings.get(type);
        if (settings != null) {
            return settings.getEffectivePriority(type.getPriority());
        }
        return type.getPriority();
    }

    /**
     * Sets a custom priority for an alert type.
     *
     * @param type The alert type
     * @param priority Custom priority (set to -1 to use default)
     */
    public void setPriority(AlertType type, int priority) {
        AlertSettings settings = alertSettings.get(type);
        if (settings != null) {
            settings.setPriority(priority);
            LOGGER.debug("Alert {} priority set to: {}", type, priority);
        }
    }

    /**
     * Gets all alert settings.
     *
     * @return Unmodifiable map of alert settings
     */
    public Map<AlertType, AlertSettings> getAllSettings() {
        return Collections.unmodifiableMap(alertSettings);
    }

    /**
     * Resets all settings to defaults.
     */
    public void resetToDefaults() {
        for (AlertType type : AlertType.values()) {
            alertSettings.put(type, new AlertSettings(true, -1));
        }
        LOGGER.info("Alert config reset to defaults");
    }

    /**
     * Loads configuration from disk.
     */
    public void load() {
        File file = storagePath.toFile();
        if (!file.exists()) {
            LOGGER.info("No alert config file found, using defaults");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(reader, JsonObject.class);

            if (root == null || !root.has("alerts")) {
                LOGGER.warn("Invalid alert config format");
                return;
            }

            JsonObject alertsObj = root.getAsJsonObject("alerts");
            for (AlertType type : AlertType.values()) {
                String key = type.name();
                if (alertsObj.has(key)) {
                    try {
                        AlertSettings settings = AlertSettings.fromJson(alertsObj.getAsJsonObject(key));
                        alertSettings.put(type, settings);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load settings for {}: {}", key, e.getMessage());
                    }
                }
            }

            LOGGER.info("Loaded alert configuration from {}", storagePath);

        } catch (IOException e) {
            LOGGER.error("Failed to load alert config: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error parsing alert config: {}", e.getMessage());
        }
    }

    /**
     * Saves configuration to disk.
     */
    public void save() {
        JsonObject root = new JsonObject();
        JsonObject alertsObj = new JsonObject();

        for (Map.Entry<AlertType, AlertSettings> entry : alertSettings.entrySet()) {
            alertsObj.add(entry.getKey().name(), entry.getValue().toJson());
        }
        root.add("alerts", alertsObj);

        try {
            File file = storagePath.toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(root, writer);
            }

            LOGGER.debug("Saved alert configuration to {}", storagePath);

        } catch (IOException e) {
            LOGGER.error("Failed to save alert config: {}", e.getMessage());
        }
    }

    /**
     * Creates a summary of current configuration.
     *
     * @return Configuration summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder("Alert Configuration:\n");
        for (AlertType type : AlertType.values()) {
            AlertSettings settings = alertSettings.get(type);
            sb.append(String.format("  %s: enabled=%s, priority=%d\n",
                    type.name(),
                    settings.isEnabled(),
                    settings.getEffectivePriority(type.getPriority())));
        }
        return sb.toString().trim();
    }
}