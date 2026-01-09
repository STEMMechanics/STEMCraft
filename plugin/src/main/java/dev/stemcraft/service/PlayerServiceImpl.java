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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.player.PlayerService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Implementation of the PlayerService for logging player actions.
 */
public class PlayerServiceImpl extends BaseService implements PlayerService {
    private final List<Player> hiddenPlayers = new ArrayList<>();

    /**
     * Constructor for PlayerServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public PlayerServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Hides a player from all other players.
     *
     * @param player The player to hide.
     */
    @Override
    public void hide(@NotNull Player player) {
        if(hiddenPlayers.contains(player)) return;

        hiddenPlayers.add(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.hidePlayer(plugin, player);
        }
    }

    /**
     * Shows a hidden player to all other players.
     *
     * @param player The player to show.
     */
    @Override
    public void show(@NotNull Player player) {
        if(!hiddenPlayers.contains(player)) return;

        hiddenPlayers.remove(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.showPlayer(plugin, player);
        }
    }
}
