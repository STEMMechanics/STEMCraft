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

package dev.stemcraft.api.event.minigame;

import dev.stemcraft.api.event.BaseEvent;
import dev.stemcraft.api.minigame.MiniGameArena;
import lombok.Getter;
import org.bukkit.entity.Player;

/**
 * Event triggered when a player leaves a mini-game arena.
 */
public class ArenaPlayerLeaveEvent extends BaseEvent {

    private final MiniGameArena arena;
    @Getter
    private final Player player;

    /**
     * Constructs a new ArenaPlayerLeaveEvent.
     *
     * @param player The player who left the arena.
     * @param arena  The mini-game arena that the player left.
     */
    public ArenaPlayerLeaveEvent(Player player, MiniGameArena arena) {
        this.arena = arena;
        this.player = player;
    }

    /**
     * Retrieves the arena associated with this event.
     * If no namespace is provided, returns the default arena.
     *
     * @param filterNamespace The namespace to filter by.
     * @return The mini-game arena.
     */
    public MiniGameArena getArena(String filterNamespace) {
        if(filterNamespace == null || filterNamespace.isEmpty()) return arena;
        if(arena.getNamespace().equals(filterNamespace)) return arena;
        return null;
    }

    public MiniGameArena getArena() {
        return getArena("");
    }
}
