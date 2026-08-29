/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature that customizes the player tab list header, footer, and name format.
 */
public class PlayerTabList extends BaseFeature {
    private static final long DEFAULT_UPDATE_TICKS = 40L;
    private static final String DEFAULT_NAME_FORMAT = "{prefix}{player}{badge-3}";
    private static final int DEFAULT_MAX_NAME_LEN = 48;
    private static final int DEFAULT_GOOD_PING_MAX = 80;
    private static final int DEFAULT_WARN_PING_MAX = 160;
    private static final List<String> DEFAULT_HEADER_LINES = List.of(
        "<gradient:#f59e0b:#ef4444><bold>STEMCraft</bold></gradient>",
        "<gray>Welcome</gray> <yellow>{player}</yellow> <dark_gray>•</dark_gray> <gray>World</gray> <aqua>{world}</aqua>"
    );
    private static final List<String> DEFAULT_FOOTER_LINES = List.of(
        "<gray>Ping</gray> {ping-colour}{ping}ms{/ping-colour} <dark_gray>•</dark_gray> <gray>Online</gray> <green>{online}</green>/<green>{max}</green>",
        "<gray>Tab Name</gray> <white>{player}</white>"
    );

    private final MiniMessage mm = MiniMessage.miniMessage();

    private Chat vaultChat;

    private long updateTicks;
    private List<String> headerLines;
    private List<String> footerLines;
    private String nameFormat;
    private int maxNameLen;
    private int goodMax;
    private int warnMax;

    /**
     * Constructor for PlayerTabList feature.
     *
     * @param api The STEMCraft API instance.
     */
    public PlayerTabList(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Initializes the feature by hooking into Vault, loading configuration, and starting the update task.
     */
    @Override
    public void onEnable() {
        hookVault();
        reload();
        start();
    }

    @Override
    public void onReload() {
        super.onReload();
        hookVault();
        reload();
        start();
    }

    /**
     * Cleans up the feature by stopping the update task.
     */
    private void hookVault() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return;
        var rsp = Bukkit.getServicesManager().getRegistration(Chat.class);
        if (rsp != null) vaultChat = rsp.getProvider();
    }

    /**
     * Stops the update task when the feature is disabled.
     */
    public void reload() {
        ConfigSection section = getConfigSection();
        boolean changed = false;

        changed |= ensureDefault(section, "update_ticks", DEFAULT_UPDATE_TICKS);
        changed |= ensureDefault(section, "header", DEFAULT_HEADER_LINES);
        changed |= ensureDefault(section, "footer", DEFAULT_FOOTER_LINES);
        changed |= ensureDefault(section, "name_format", DEFAULT_NAME_FORMAT);
        changed |= ensureDefault(section, "max_name_len", DEFAULT_MAX_NAME_LEN);
        changed |= ensureDefault(section, "ping.good_max", DEFAULT_GOOD_PING_MAX);
        changed |= ensureDefault(section, "ping.warn_max", DEFAULT_WARN_PING_MAX);

        if (changed) {
            section.save();
        }

        updateTicks = section.getLong("update_ticks", DEFAULT_UPDATE_TICKS);
        headerLines = section.getStringList("header");
        footerLines = section.getStringList("footer");
        nameFormat = section.getString("name_format", DEFAULT_NAME_FORMAT);
        maxNameLen = section.getInt("max_name_len", DEFAULT_MAX_NAME_LEN);

        goodMax = section.getInt("ping.good_max", DEFAULT_GOOD_PING_MAX);
        warnMax = section.getInt("ping.warn_max", DEFAULT_WARN_PING_MAX);
    }

    @Override
    protected List<String> getConfigPathCandidates() {
        List<String> candidates = new ArrayList<>();
        candidates.add("tab");
        candidates.add("features.tab");
        candidates.add("chat.tab");
        candidates.addAll(super.getConfigPathCandidates());
        return candidates;
    }

    /**
     * Starts the repeating task to update the tab list.
     */
    public void start() {
        stop();
        api.tasks().repeating("tab-update", 1L, Math.max(1L, updateTicks), this::tick);
    }

    /**
     * Stops the repeating task that updates the tab list.
     */
    public void stop() {
        api.tasks().cancel("tab-update");
    }

    /**
     * Updates the tab list header, footer, and player names for all online players.
     */
    private void tick() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        for (Player p : Bukkit.getOnlinePlayers()) {
            String prefix = getVaultPrefix(p);
            String suffix = getVaultSuffix(p);

            String header = joinLines(headerLines);
            String footer = joinLines(footerLines);

            header = applyPlaceholders(header, p, prefix, suffix, online, max);
            footer = applyPlaceholders(footer, p, prefix, suffix, online, max);

            // header/footer: MiniMessage
            p.sendPlayerListHeaderAndFooter(
                mm.deserialize(normalizeMiniMessage(header)),
                mm.deserialize(normalizeMiniMessage(footer))
            );

            // Config uses MiniMessage. Vault placeholders are converted from legacy before insertion.
            String nameLine = applyPlaceholders(nameFormat, p, prefix, suffix, online, max);
            Component tabName = trimVisible(mm.deserialize(normalizeMiniMessage(nameLine)), maxNameLen);
            p.playerListName(tabName);
        }
    }

    /**
     * Applies placeholders to a string for a given player.
     *
     * @param s The input string with placeholders.
     * @param p The player.
     * @param prefix The player's prefix.
     * @param suffix The player's suffix.
     * @param online The number of online players.
     * @param max The maximum number of players.
     * @return The string with placeholders replaced.
     */
    private String applyPlaceholders(String s, Player p, String prefix, String suffix, int online, int max) {
        if (s == null) return "";

        String pingColour;
        int ping = p.getPing();
        if (ping <= goodMax) {
            pingColour = "green";
        } else if (ping <= warnMax) {
            pingColour = "yellow";
        } else {
            pingColour = "red";
        }

        String out = s
                .replace("{player}", p.getName())
                .replace("{world}", api.worlds().getDisplayName(p.getWorld()))
                .replace("{world-raw}", p.getWorld().getName())
                .replace("{ping}", String.valueOf(ping))
                .replace("{ping-colour}", "<" + pingColour + ">")
                .replace("{/ping-colour}", "</" + pingColour + ">")
                .replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max))
                .replace("{prefix}", legacyToMiniMessage(prefix))
                .replace("{suffix}", legacyToMiniMessage(suffix));

        out = STEMCraft.getPlugin().entitlements().applyBadgePlaceholders(p.getUniqueId(), out);

        // glyph bindings (turn :roles/stemcraft: into the char)
        out = api.messages().tokens().apply(out);

        return out;
    }

    /**
     * Retrieves the Vault prefix for a player.
     *
     * @param p The player.
     * @return The formatted prefix string.
     */
    private String getVaultPrefix(Player p) {
        if (vaultChat == null) return "";
        String raw = vaultChat.getPlayerPrefix(p);
        return raw == null ? "" : raw.replace('&', '§');
    }

    /**
     * Retrieves the Vault suffix for a player, replacing color codes.
     *
     * @param p The player.
     * @return The formatted suffix.
     */
    private String getVaultSuffix(Player p) {
        if (vaultChat == null) return "";
        String raw = vaultChat.getPlayerSuffix(p);
        return raw == null ? "" : raw.replace('&', '§');
    }

    /**
     * Converts a legacy formatted string into a Component.
     *
     * @param legacy The legacy formatted string.
     * @return The corresponding Component.
     */
    private String legacyToMiniMessage(String legacy) {
        if (legacy == null || legacy.isBlank()) return "";
        return mm.serialize(LegacyComponentSerializer.legacySection().deserialize(legacy));
    }

    /**
     * Joins a list of lines into a single string with newline separators.
     *
     * @param lines The list of lines.
     * @return The joined string.
     */
    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        return String.join("\n", lines);
    }

    /**
     * Trims a string to a maximum visible length.
     *
     * @param s The input string.
     * @param maxLen The maximum visible length.
     * @return The trimmed string.
     */
    static Component trimVisible(Component component, int maxLen) {
        if (maxLen <= 0 || PlainTextComponentSerializer.plainText().serialize(component).length() <= maxLen) {
            return component;
        }
        String legacy = LegacyComponentSerializer.legacySection().serialize(component);
        StringBuilder trimmed = new StringBuilder();
        int visible = 0;
        for (int index = 0; index < legacy.length() && visible < maxLen; index++) {
            char current = legacy.charAt(index);
            trimmed.append(current);
            if (current == '§' && index + 1 < legacy.length()) {
                trimmed.append(legacy.charAt(++index));
            } else {
                visible++;
            }
        }
        return LegacyComponentSerializer.legacySection().deserialize(trimmed.toString());
    }

    static String normalizeMiniMessage(String input) {
        if (input == null) return "";
        return input
            .replace("<dark_grey>", "<dark_gray>")
            .replace("</dark_grey>", "</dark_gray>")
            .replace("<grey>", "<gray>")
            .replace("</grey>", "</gray>");
    }

    private boolean ensureDefault(ConfigSection section, String path, Object value) {
        if (section.contains(path)) {
            return false;
        }
        section.set(path, value);
        return true;
    }
}
