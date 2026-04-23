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

/**
 * Temporary startup migration that rewrites known config schema keys from
 * snake_case to kebab-case without touching user-defined IDs.
 */
public final class HyphenatedConfigSchemaMigration {
    private record PathMove(String sourcePath, String targetPath) {}

    private static final List<PathMove> MOVES = List.of(
        new PathMove("whitelist_message", "whitelist-message"),
        new PathMove("web_server", "web-server"),
        new PathMove("webhook_bridge", "webhook-bridge"),
        new PathMove("webhook-bridge.site_webhook_url", "webhook-bridge.site-webhook-url"),
        new PathMove("webhook-bridge.shared_secret", "webhook-bridge.shared-secret"),
        new PathMove("webhook-bridge.server_name", "webhook-bridge.server-name"),
        new PathMove("webhook-bridge.listen_path", "webhook-bridge.listen-path"),
        new PathMove("webhook-bridge.connect_timeout_millis", "webhook-bridge.connect-timeout-millis"),
        new PathMove("webhook-bridge.request_timeout_millis", "webhook-bridge.request-timeout-millis"),
        new PathMove("webhook-bridge.replay_window_seconds", "webhook-bridge.replay-window-seconds"),
        new PathMove("webhook-bridge.debug_logging", "webhook-bridge.debug-logging"),
        new PathMove("webhook-bridge.enforce_account_whitelist", "webhook-bridge.enforce-account-whitelist"),
        new PathMove("webhook-bridge.whitelist_kick_message", "webhook-bridge.whitelist-kick-message"),
        new PathMove("webhook-bridge.drop_stale_sync_managed_events", "webhook-bridge.drop-stale-sync-managed-events"),
        new PathMove("webhook-bridge.drop_sync_managed_events_missing_occurred_at", "webhook-bridge.drop-sync-managed-events-missing-occurred-at"),
        new PathMove("webhook-bridge.allow_status_requests", "webhook-bridge.allow-status-requests"),
        new PathMove("webhook-bridge.allow_player_stats_requests", "webhook-bridge.allow-player-stats-requests"),
        new PathMove("webhook-bridge.allow_remote_commands", "webhook-bridge.allow-remote-commands"),
        new PathMove("webhook-bridge.remote_command_max_output_chars", "webhook-bridge.remote-command-max-output-chars"),
        new PathMove("player_stats", "player-stats"),
        new PathMove("player-stats.autosave_ticks", "player-stats.autosave-ticks"),
        new PathMove("distance_difficulty", "distance-difficulty"),
        new PathMove("player_logs", "player-logs"),
        new PathMove("player-logs.max_days", "player-logs.max-days"),
        new PathMove("player-logs.tps_threshold", "player-logs.tps-threshold"),
        new PathMove("player-logs.memory_threshold", "player-logs.memory-threshold"),
        new PathMove("chat.filter_command", "chat.filter-command"),
        new PathMove("chat.spam_cooldown", "chat.spam-cooldown"),
        new PathMove("chat.spam_message", "chat.spam-message"),
        new PathMove("chat.content_filter", "chat.content-filter"),
        new PathMove("chat.content-filter.timeout_millis", "chat.content-filter.timeout-millis"),
        new PathMove("chat.content-filter.allow_filtered_message", "chat.content-filter.allow-filtered-message"),
        new PathMove("chat.content-filter.blocked_message", "chat.content-filter.blocked-message"),
        new PathMove("chat.content-filter.unavailable_message", "chat.content-filter.unavailable-message"),
        new PathMove("recipes.blast_furnace", "recipes.blast-furnace"),
        new PathMove("recipes.smithing_transform", "recipes.smithing-transform"),
        new PathMove("recipes.smithing_trim", "recipes.smithing-trim"),
        new PathMove("features.deny_spawn_eggs", "features.deny-spawn-eggs"),
        new PathMove("features.drop_player_heads", "features.drop-player-heads"),
        new PathMove("features.no_anvil_repair_cost", "features.no-anvil-repair-cost"),
        new PathMove("features.rebalance_iron_golem", "features.rebalance-iron-golem"),
        new PathMove("features.rebalance-iron-golem.min_drops", "features.rebalance-iron-golem.min-drops"),
        new PathMove("features.rebalance-iron-golem.max_drops", "features.rebalance-iron-golem.max-drops"),
        new PathMove("features.naughty.allowed_commands", "features.naughty.allowed-commands"),
        new PathMove("features.skip_night", "features.skip-night"),
        new PathMove("features.skip-night.random_tick_speed", "features.skip-night.random-tick-speed"),
        new PathMove("features.player_game_messages", "features.player-game-messages"),
        new PathMove("features.player-game-messages.list_entity", "features.player-game-messages.list-entity"),
        new PathMove("features.player-game-messages.list_cause", "features.player-game-messages.list-cause"),
        new PathMove("features.tab.update_ticks", "features.tab.update-ticks"),
        new PathMove("features.tab.ping.good_max", "features.tab.ping.good-max"),
        new PathMove("features.tab.ping.warn_max", "features.tab.ping.warn-max"),
        new PathMove("features.tab.name_format", "features.tab.name-format"),
        new PathMove("features.tab.max_name_len", "features.tab.max-name-len"),
        new PathMove("features.selection_preview", "features.selection-preview"),
        new PathMove("features.selection-preview.update_ticks", "features.selection-preview.update-ticks"),
        new PathMove("features.selection-preview.point_spacing", "features.selection-preview.point-spacing"),
        new PathMove("features.selection-preview.max_points", "features.selection-preview.max-points"),
        new PathMove("features.selection-preview.major_marker_interval", "features.selection-preview.major-marker-interval"),
        new PathMove("features.selection-preview.max_view_distance", "features.selection-preview.max-view-distance"),
        new PathMove("features.selection-preview.flash_material", "features.selection-preview.flash-material")
    );

    private final STEMCraft plugin;
    private final ConfigFile configFile;
    private final List<String> applied = new ArrayList<>();

    public HyphenatedConfigSchemaMigration(STEMCraft plugin, ConfigFile configFile) {
        this.plugin = plugin;
        this.configFile = configFile;
    }

    public boolean apply() {
        File file = new File(plugin.getDataFolder(), configFile.getName());
        YamlConfiguration yaml = load(file);
        if (yaml == null) {
            return false;
        }

        boolean changed = false;

        for (PathMove move : MOVES) {
            changed |= moveNode(yaml, move.sourcePath(), move.targetPath());
        }

        if (changed) {
            save(file, yaml);
            configFile.reload();
            plugin.getLogger().info("Converted config schema keys to kebab-case: " + String.join(", ", applied));
        }

        return changed;
    }

    private YamlConfiguration load(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Could not load config.yml for kebab-case migration: " + exception.getMessage());
            return null;
        }
    }

    private void save(File file, YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save config.yml after kebab-case migration: " + exception.getMessage());
        }
    }

    private boolean moveNode(YamlConfiguration yaml, String sourcePath, String targetPath) {
        if (sourcePath.equals(targetPath)) {
            return false;
        }

        if (yaml.isConfigurationSection(sourcePath)) {
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
                yaml.set(targetPath + "." + leafKey, entry.getValue());
            }

            yaml.set(sourcePath, null);
            applied.add(sourcePath + " -> " + targetPath);
            return true;
        }

        if (!yaml.contains(sourcePath)) {
            return false;
        }

        yaml.set(targetPath, yaml.get(sourcePath));
        yaml.set(sourcePath, null);
        applied.add(sourcePath + " -> " + targetPath);
        return true;
    }
}
