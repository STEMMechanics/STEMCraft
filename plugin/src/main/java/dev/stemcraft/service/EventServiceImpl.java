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
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Implementation of the EventService interface for managing event listeners.
 */
public class EventServiceImpl extends BaseService implements EventService {

    /**
     * Constructor for EventServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api    The STEMCraft API instance.
     */
    public EventServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Register an event listener with callback handler.
     *
     * @param <T>              The type of event.
     * @param event            The event class to listen for.
     * @param callback         The callback handler for the event.
     * @param priority         The priority of the event listener.
     * @param ignoreCancelled  Whether to ignore cancelled events.
     * @return The registered listener.
     */
    public <T extends Event> Listener register(Class<T> event, EventHandler<T> callback, EventPriority priority, boolean ignoreCancelled) {
        Listener listener = new Listener() {};

        plugin.getServer().getPluginManager().registerEvent(event, listener, priority, (ignored, rawEvent) -> {
            if(event.isInstance(rawEvent)) {
                T castedEvent = event.cast(rawEvent);
                if (ignoreCancelled && rawEvent instanceof Cancellable c && c.isCancelled()) {
                    return;
                }

                callback.handle(castedEvent);
            }
        }, plugin, ignoreCancelled);

        return listener;
    }
}
