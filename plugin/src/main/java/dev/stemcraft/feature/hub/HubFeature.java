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

package dev.stemcraft.feature.hub;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.PatternUtil;
import dev.stemcraft.feature.BaseFeature;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Hub feature that manages the server's hub world and related functionalities.
 */
public class HubFeature extends BaseFeature {
    @Getter
    private World hubWorld;
    private final Map<Pattern, List<String>> worldExitCommands = new HashMap<>();

    /**
     * Constructor for the HubFeature.
     */
    public HubFeature(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is enabled.
     */
    @Override
    public void onEnable() {
        loadConfig();

        if (hubWorld != null) {
            api.worlds().setDefaultWorld(hubWorld);
        }

        api.events().register(WorldLoadEvent.class, event -> {
            if (hubWorld != null) {
                return;
            }

            hubWorld = resolveHubWorld();
            if (hubWorld != null) {
                api.worlds().setDefaultWorld(hubWorld);
            }
        });

        api.events().register(PlayerJoinEvent.class, event -> {
            if(hubWorld != null) {
                Player player = event.getPlayer();

                api.tasks().runLater(10L, () -> player.teleport(hubWorld.getSpawnLocation()));
            }
        });

        new HubCommand(this, api).register();
    }

    /**
     * Run defined exit commands from config that match the world name.
     *
     * @param player The player exiting the world.
     */
    public void runExitCommands(Player player) {
        String currentWorldName = player.getWorld().getName();
        String currentWorldKey = currentWorldName.toLowerCase(Locale.ROOT);
        World resolvedHubWorld = hubWorld != null ? hubWorld : api.worlds().getDefaultWorld();
        String hubWorldName = resolvedHubWorld == null ? currentWorldName : resolvedHubWorld.getName();

        for(Map.Entry<Pattern, List<String>> entry : worldExitCommands.entrySet()) {
            Pattern pattern = entry.getKey();
            if(!pattern.matcher(currentWorldKey).matches()) {
                continue;
            }

            List<String> commandList = entry.getValue();
            for(String command : commandList) {
                command = command.replace("{hub-world}", hubWorldName);
                command = command.replace("{world}", currentWorldName);
                command = command.replace("{player}", player.getName());

                if(command.startsWith("p:")) {
                    Bukkit.dispatchCommand(player, command.substring(3));
                } else {
                    ConsoleCommandSender console = Bukkit.getConsoleSender();
                    Bukkit.dispatchCommand(console, command);
                }
            }
        }
    }

    /**
     * Load configuration data.
     */
    private void loadConfig() {
        hubWorld = resolveHubWorld();

        worldExitCommands.clear();
        ConfigSection sec = getConfigSection().getSection("exit-commands");
        if (sec == null) return;

        for (String worldKey : sec.getKeys(false)) {
            Pattern worldPattern = PatternUtil.globToRegex(worldKey.toLowerCase(Locale.ROOT));
            List<String> commands = loadExitCommands(sec, worldKey);
            if (!commands.isEmpty()) {
                worldExitCommands.put(worldPattern, commands);
            }
        }
    }

    private @NotNull List<String> loadExitCommands(@NotNull ConfigSection root, @NotNull String worldKey) {
        if (root.isSection(worldKey)) {
            ConfigSection entry = root.getSection(worldKey, false);
            if (entry == null) {
                return List.of();
            }

            boolean asPlayer = entry.getBoolean("as-player", false);
            List<String> commands = new ArrayList<>();

            String command = entry.getString("command");
            if (!command.isBlank()) {
                commands.add(asPlayer ? "p:" + command : command);
            }

            for (String item : entry.getStringList("commands")) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                commands.add(asPlayer ? "p:" + item : item);
            }

            return commands;
        }

        Object raw = root.get(worldKey);
        if (raw instanceof String command && !command.isBlank()) {
            return List.of(command);
        }

        List<String> commands = new ArrayList<>();
        for (String item : root.getStringList(worldKey)) {
            if (item == null || item.isBlank()) {
                continue;
            }
            commands.add(item);
        }
        return commands;
    }

    private World resolveHubWorld() {
        String hubWorldName = getConfigSection().getString("world", "world");
        if (!hubWorldName.isEmpty()) {
            World configuredWorld = Bukkit.getWorld(hubWorldName);
            if (configuredWorld != null) {
                return configuredWorld;
            }
        }

        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }
}
