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

package dev.stemcraft.api.service.event;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Service for managing event handlers within the STEMCraft plugin.
 */
public interface EventService {

    /**
     * Register a new event handler.
     *
     * @param <T> The type of event to handle.
     * @param event The class of the event to handle.
     * @param callback The callback to invoke when the event is fired.
     * @param priority The priority of the event handler.
     * @param ignoreCancelled Whether to ignore cancelled events.
     * @return The registered listener.
     */
    <T extends Event> Listener register(Class<T> event, EventHandler<T> callback, EventPriority priority, boolean ignoreCancelled);
    default <T extends Event> Listener register(Class<T> event, EventHandler<T> callback) { return register(event, callback, EventPriority.NORMAL, false); }
}
