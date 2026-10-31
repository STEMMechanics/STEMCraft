package dev.stemcraft.config.migration;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.config.ConfigFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Temporary startup migration for legacy service config paths.
 * Remove this class after old configs have been migrated in the field.
 */
public final class LegacyServiceConfigMigration {
    private final STEMCraft plugin;
    private final ConfigFile configFile;
    private final List<String> applied = new ArrayList<>();

    public LegacyServiceConfigMigration(STEMCraft plugin, ConfigFile configFile) {
        this.plugin = plugin;
        this.configFile = configFile;
    }

    /**
     * Applies the legacy config migration once at startup.
     */
    public void apply() {
        File file = new File(plugin.getDataFolder(), configFile.getName());
        YamlConfiguration yaml = load(file);
        if (yaml == null) {
            return;
        }

        boolean changed = false;

        changed |= mergeSection(yaml, "motd-services", "motd", UnaryOperator.identity());
        changed |= mergeSection(yaml, "recipe-services", "recipes", UnaryOperator.identity());
        changed |= mergeSection(yaml, "world-services", "worlds", UnaryOperator.identity());
        changed |= mergeSection(yaml, "chat-services", "chat", LegacyServiceConfigMigration::normalizeLegacyChatKey);
        changed |= migrateLegacyChatBridgeEvents(yaml);

        changed |= mergeSection(yaml, "auditing", "player_logs", LegacyServiceConfigMigration::normalizeLegacyAuditKey);
        changed |= mergeSection(yaml, "player_log", "player_logs", LegacyServiceConfigMigration::normalizeLegacyAuditKey);
        changed |= mergeSection(yaml, "player-log", "player_logs", LegacyServiceConfigMigration::normalizeLegacyAuditKey);

        changed |= mergeSection(yaml, "selection_preview", "features.selection_preview", UnaryOperator.identity());
        changed |= mergeSection(yaml, "selection-preview", "features.selection_preview", UnaryOperator.identity());

        if (changed) {
            save(file, yaml);
            configFile.reload();
            plugin.getLogger().info("Migrated legacy config paths: " + String.join(", ", applied));
        }

    }

    private YamlConfiguration load(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Could not load config.yml for legacy config migration: " + exception.getMessage());
            return null;
        }
    }

    private void save(File file, YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save config.yml after legacy config migration: " + exception.getMessage());
        }
    }

    private boolean mergeSection(YamlConfiguration yaml, String sourcePath, String targetPath, UnaryOperator<String> keyNormalizer) {
        if (!yaml.isConfigurationSection(sourcePath)) {
            return false;
        }

        ConfigurationSection sourceSection = yaml.getConfigurationSection(sourcePath);
        if (sourceSection == null) {
            return false;
        }

        Map<String, Object> values = sourceSection.getValues(true);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String leafKey = entry.getKey();
            if (sourceSection.isConfigurationSection(leafKey)) {
                continue;
            }

            String normalizedKey = keyNormalizer.apply(leafKey);
            if (normalizedKey == null || normalizedKey.isBlank()) {
                continue;
            }

            yaml.set(targetPath + "." + normalizedKey, entry.getValue());
        }

        yaml.set(sourcePath, null);
        applied.add(sourcePath + " -> " + targetPath);
        return true;
    }

    private static String normalizeLegacyChatKey(String key) {
        key = stripLeadingDots(key);
        if (key.startsWith("chat.")) {
            key = key.substring("chat.".length());
        }
        return key;
    }

    private static String normalizeLegacyAuditKey(String key) {
        key = stripLeadingDots(key);
        if(key.equalsIgnoreCase("max-days")) {
            return "max_days";
        }
        return key;
    }

    private boolean migrateLegacyChatBridgeEvents(YamlConfiguration yaml) {
        String sourcePath = yaml.isConfigurationSection("chat.discord-bridge")
            ? "chat.discord-bridge"
            : yaml.isConfigurationSection("chat.discord_bridge")
                ? "chat.discord_bridge"
                : null;
        if (sourcePath == null) {
            return false;
        }

        ConfigurationSection source = yaml.getConfigurationSection(sourcePath);
        if (source == null || !hasConfiguredOutboundEndpoint(source)) {
            return false;
        }

        String targetPath = "web-server.event-subscriptions";
        boolean targetAlreadyConfigured = yaml.isConfigurationSection(targetPath)
            && hasConfiguredOutboundEndpoint(yaml.getConfigurationSection(targetPath));
        if (targetAlreadyConfigured) {
            return false;
        }

        boolean changed = false;
        for (Map.Entry<String, Object> entry : source.getValues(true).entrySet()) {
            String key = entry.getKey();
            if (source.isConfigurationSection(key) || isInboundChatBridgeKey(key) || "enabled".equals(key)) {
                continue;
            }

            String normalizedKey = normalizeLegacyEventSubscriptionKey(key);
            if (normalizedKey == null || normalizedKey.isBlank()) {
                continue;
            }
            yaml.set(targetPath + "." + normalizedKey, entry.getValue());
            yaml.set(sourcePath + "." + key, null);
            changed = true;
        }

        if (changed) {
            yaml.set(targetPath + ".enabled", true);
            applied.add(sourcePath + " outbound settings -> " + targetPath);
        }
        return changed;
    }

    private static boolean hasConfiguredOutboundEndpoint(ConfigurationSection section) {
        if (section == null) {
            return false;
        }
        String url = firstConfiguredString(section, "url", "");
        String token = firstConfiguredString(section, "bearer-token", "bearer_token");
        return !url.isBlank() || !token.isBlank();
    }

    private static String firstConfiguredString(ConfigurationSection section, String primary, String fallback) {
        String value = section.getString(primary, "");
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return section.getString(fallback, "").trim();
    }

    private static boolean isInboundChatBridgeKey(String key) {
        return key.equals("inbound-enabled")
            || key.equals("inbound_enabled")
            || key.equals("inbound-format")
            || key.equals("inbound_format")
            || key.startsWith("inbound-enabled.")
            || key.startsWith("inbound_enabled.")
            || key.startsWith("inbound-format.")
            || key.startsWith("inbound_format.");
    }

    private static String normalizeLegacyEventSubscriptionKey(String key) {
        String normalized = stripLeadingDots(key);
        normalized = normalized.replace("bearer_token", "bearer-token")
            .replace("server_id", "server-id")
            .replace("queue_size", "queue-size")
            .replace("max_retries", "max-retries")
            .replace("redacted_commands", "redacted-commands");
        return normalized;
    }

    private static String stripLeadingDots(String key) {
        while (key.startsWith(".")) {
            key = key.substring(1);
        }
        return key;
    }
}
