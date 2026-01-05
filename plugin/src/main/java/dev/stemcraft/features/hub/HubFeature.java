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

package dev.stemcraft.features.hub;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.PatternUtil;
import dev.stemcraft.features.BaseFeature;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

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
     *
     */
    public HubFeature(STEMCraftAPI api) {
        super(api);
    }

    /**
     *
     */
    @Override
    public void onEnable() {
        loadConfig();

        api.worlds().setDefaultWorld(hubWorld);
        api.events().register(PlayerJoinEvent.class, event -> {
            if(hubWorld != null) {
                Player player = event.getPlayer();

                if(!api.gatekeeper().isWhitelisted(player)) {
                    return;
                }

                api.tasks().runLater(10L, () -> player.teleport(hubWorld.getSpawnLocation()));
            }
        });

        new HubCommand(this, api).register();
    }

    /**
     * Run defined exit commands from config that match the world name
     */
    public void runExitCommands(Player player) {
        String currentWorldName = player.getWorld().getName();

        for(Pattern pattern : worldExitCommands.keySet()) {
            if(!pattern.matcher(currentWorldName).matches()) {
                continue;
            }

            List<String> commandList = worldExitCommands.get(pattern);
            for(String command : commandList) {
                command = command.replace("{hub-world}", hubWorld.getName());
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
     * Load configuration data
     */
    private void loadConfig() {
        String hubWorldName = getConfigSection().getString("world", "world");

        if (!hubWorldName.isEmpty()) {
            hubWorld = Bukkit.getWorld(hubWorldName);
        }

        if (hubWorld == null) {
            hubWorld = Bukkit.getWorlds().getFirst();
        }

        worldExitCommands.clear();
        ConfigSection sec = getConfigSection().getSection("exit-commands");
        if (sec == null) return;

        for (String worldKey : sec.getKeys(false)) {
            Pattern worldPattern = PatternUtil.globToRegex(worldKey.toLowerCase(Locale.ROOT));
            worldExitCommands.put(worldPattern, sec.getStringList(worldKey));
        }
    }
}
