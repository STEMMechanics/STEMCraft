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
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event triggered when a mini-game arena countdown reaches zero.
 */
@Getter
public class ArenaCountdownZeroEvent extends BaseEvent {
    private final MiniGameArena arena;

    public ArenaCountdownZeroEvent(MiniGameArena arena) {
        this.arena = arena;
    }

    public MiniGameArena getArena() {
        return getArena("");
    }

    public MiniGameArena getArena(String namespace) {
        if(namespace == null || namespace.isEmpty()) return arena;
        if(arena.getNamespace().equals(namespace)) return arena;
        return null;
    }
}
