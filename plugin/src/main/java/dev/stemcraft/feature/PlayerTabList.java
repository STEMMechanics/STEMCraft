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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Feature that customizes the player tab list header, footer, and name format.
 */
public class PlayerTabList extends BaseFeature {
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
        updateTicks = getConfigSection().getLong("update_ticks", 40L);
        headerLines = getConfigSection().getStringList("header");
        footerLines = getConfigSection().getStringList("footer");
        nameFormat = getConfigSection().getString("name_format", "{prefix}{player}");
        maxNameLen = getConfigSection().getInt("max_name_len", 48);

        goodMax = getConfigSection().getInt("ping.good_max", 80);
        warnMax = getConfigSection().getInt("ping.warn_max", 160);
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
            p.sendPlayerListHeaderAndFooter(mm.deserialize(header), mm.deserialize(footer));

            // tab name: build as legacy (Vault is legacy), then deserialize once
            String nameLine = applyPlaceholders(nameFormat, p, prefix, suffix, online, max);
            nameLine = trimVisible(nameLine, maxNameLen);

            Component tabName = legacyToComponent(nameLine);
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
                .replace("{world}", p.getWorld().getName())
                .replace("{ping}", String.valueOf(ping))
                .replace("{ping-colour}", "<" + pingColour + ">")
                .replace("{/ping-colour}", "</" + pingColour + ">")
                .replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max))
                .replace("{prefix}", prefix == null ? "" : prefix)
                .replace("{suffix}", suffix == null ? "" : suffix);

        // glyph bindings (turn :roles\stemcraft: into the char)
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
    private Component legacyToComponent(String legacy) {
        if (legacy == null || legacy.isBlank()) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
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
    private static String trimVisible(String s, int maxLen) {
        if (s == null) return "";
        if (maxLen <= 0) return s;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}