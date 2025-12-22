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
package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.STEMCraftMessenger;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.events.STEMCraftEventHandler;
import dev.stemcraft.api.services.*;
import dev.stemcraft.api.services.hologram.HologramService;
import dev.stemcraft.api.services.region.RegionService;
import dev.stemcraft.api.services.task.TaskService;
import dev.stemcraft.api.services.punishment.PunishmentService;
import dev.stemcraft.api.services.tabcomplete.TabCompleteService;
import dev.stemcraft.api.services.web.WebService;
import dev.stemcraft.commands.STEMCraftCommandImpl;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;

public class STEMCraftApiManager extends STEMCraftMessenger implements STEMCraftAPI {
    private static STEMCraft plugin;

    public STEMCraftApiManager(STEMCraft plugin) {
        STEMCraftApiManager.plugin = plugin;
    }

    public String version() {
        return STEMCraft.getVersion();
    }

    public <T extends Event> Listener registerEvent(Class<T> event, STEMCraftEventHandler<T> callback, EventPriority priority, boolean ignoreCancelled) {
        return plugin.registerEvent(event, callback, priority, ignoreCancelled);
    }

    public STEMCraftCommand registerCommand(String label) {
        return new STEMCraftCommandImpl(label);
    }

    public YamlConfiguration config() {
        return plugin.config();
    }
    public void saveConfig() { plugin.saveConfig(); }

    public File dataFolder() { return plugin.getDataFolder(); }
    public File getCacheDir() { return plugin.cacheDir(); }
    public FileConfiguration getCacheConfig(String fileName) { return plugin.getCacheConfig(fileName); }
    public void saveCacheConfig(String fileName, FileConfiguration config) { plugin.saveCacheConfig(fileName, config); }

    public PlayerLogService playerLog() {
        return plugin.playerLogService();
    }

    public MessengerService messenger() {
        return plugin.messengerService();
    }

    public LocaleService locale() { return plugin.localeService(); }

    public WorldService worlds() { return plugin.worldService(); }

    public TabCompleteService tabComplete() { return plugin.tabCompleteService(); }

    public TaskService tasks() { return plugin.taskService(); }

    public PunishmentService punishment() { return plugin.punishmentService(); }

    public HologramService holograms() { return plugin.hologramService(); }

    public ItemService items() { return plugin.itemService(); }

    public RegionService regions() { return plugin.regionService(); }

    public WebService web() { return plugin.webService(); }

    public boolean isInMaintenanceMode() { return plugin.isInMaintenanceMode(); }

    public File getDataFolder() { return plugin.getDataFolder(); }

    public FileConfiguration getConfig(String fileName) { return plugin.getConfig(fileName); }

    public void saveConfig(String fileName, FileConfiguration config) { plugin.saveConfig(fileName, config); }

    public GateKeeperService gateKeeper() { return plugin.gateKeeperService(); }
}
