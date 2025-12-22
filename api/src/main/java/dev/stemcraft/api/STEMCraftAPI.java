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
import dev.stemcraft.api.services.*;
import dev.stemcraft.api.services.hologram.HologramService;
import dev.stemcraft.api.services.region.RegionService;
import dev.stemcraft.api.services.task.TaskService;
import dev.stemcraft.api.services.punishment.PunishmentService;
import dev.stemcraft.api.services.tabcomplete.TabCompleteService;
import dev.stemcraft.api.services.web.WebService;
import org.bukkit.configuration.file.FileConfiguration;
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
     * Save the STEMCraft configuration file.
     */
    void saveConfig();

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

    /**
     * Get the world service.
     */
    WorldService worlds();

    /**
     * Get the tab complete service.
     */
    TabCompleteService tabComplete();

    TaskService tasks();

    PunishmentService punishment();

    HologramService holograms();

    ItemService items();

    RegionService regions();

    WebService web();

    GateKeeperService gateKeeper();

    default <T extends Event> void registerEvent(Class<T> event, STEMCraftEventHandler<T> callback) {
        registerEvent(event, callback, EventPriority.NORMAL, false);
    }

    File getCacheDir();
    FileConfiguration getCacheConfig(String fileName);
    void saveCacheConfig(String fileName, FileConfiguration config);

    boolean isInMaintenanceMode();

    File getDataFolder();

    FileConfiguration getConfig(String fileName);
    void saveConfig(String fileName, FileConfiguration config);
}
