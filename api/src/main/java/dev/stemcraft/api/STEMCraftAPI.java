/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.api;

import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.events.STEMCraftEventHandler;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.services.LocaleService;
import dev.stemcraft.api.services.MessengerService;
import dev.stemcraft.api.services.PlayerLogService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;

public interface STEMCraftAPI extends MessengerService {
    static STEMCraftAPI api() {
        return InstanceHolder.api();
    }

    /**
     * Get the current version of STEMCraft.
     */
    String version();

    /**
     * Register a new event handler.
     */
    <T extends Event> Listener registerEvent(Class<T> event, STEMCraftEventHandler<T> callback, EventPriority priority, boolean ignoreCancelled);

    /**
     * Register a new command.
     */
    STEMCraftCommand registerCommand(String label);

    /**
     * Get the STEMCraft configuration file.
     */
    YamlConfiguration config();

    /**
     * Get the STEMCraft Data Folder
     */
    File dataFolder();

    /**
     * Get the player log service.
     */
    PlayerLogService playerLog();

    /**
     * Get the messenger service.
     */
    MessengerService messenger();

    /**
     * Get the locale service.
     */
    LocaleService locale();

    default <T extends Event> Listener registerEvent(Class<T> event, STEMCraftEventHandler<T> callback) { return registerEvent(event, callback, EventPriority.NORMAL, false); }
}
