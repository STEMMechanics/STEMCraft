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

package dev.stemcraft;

import dev.stemcraft.api.service.command.CommandService;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.capability.HasMessagesImpl;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.gatekeeper.GatekeeperService;
import dev.stemcraft.api.service.hologram.HologramService;
import dev.stemcraft.api.service.item.ItemService;
import dev.stemcraft.api.service.locale.LocaleService;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.service.player.PlayerLogService;
import dev.stemcraft.api.service.region.RegionService;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.api.service.punishment.PunishmentService;
import dev.stemcraft.api.service.tabcomplete.TabCompleteService;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.service.world.WorldService;

import java.io.File;

public class STEMCraftAPIImpl extends HasMessagesImpl implements STEMCraftAPI {
    private static STEMCraft plugin;

    public STEMCraftAPIImpl(STEMCraft plugin) {
        STEMCraftAPIImpl.plugin = plugin;
    }

    /**
     * Get the current version of STEMCraft.
     */
    public String getVersion() {
        return STEMCraft.getVersion();
    }

    /**
     * Get the data folder for STEMCraft.
     */
    public File getDataFolder() { return plugin.getDataFolder(); }

    /**
     * Check if the server is in maintenance mode.
     */
    public boolean isMaintenanceMode() { return plugin.isMaintenanceMode(); }

    /**
     * Get the command service.
     */
    public CommandService commands() {
        return plugin.commands();
    }

    /**
     * Get the STEMCraft configuration file.
     */
    public ConfigService config() {
        return plugin.config();
    }

    /**
     * Get the event service.
     */
    public EventService events() { return plugin.events(); }

//    public YamlConfiguration config() {
//        return plugin.config();
//    }
//    public void saveConfig() { plugin.saveConfig(); }
//
//    public File dataFolder() { return plugin.getDataFolder(); }
//    public File getCacheDir() { return plugin.cacheDir(); }
//    public FileConfiguration getCacheConfig(String fileName) { return plugin.getCacheConfig(fileName); }
//    public void saveCacheConfig(String fileName, FileConfiguration config) { plugin.saveCacheConfig(fileName, config); }

    public PlayerLogService playerLog() {
        return plugin.playerLogService();
    }

    public MessageService messages() {
        return plugin.messengerService();
    }

    public LocaleService locales() { return plugin.locale(); }

    public WorldService worlds() { return plugin.worldService(); }

    public TabCompleteService tabComplete() { return plugin.tabCompleteService(); }

    public TaskService tasks() { return plugin.taskService(); }

    public PunishmentService punishments() { return plugin.punishmentService(); }

    public HologramService holograms() { return plugin.hologramService(); }

    public ItemService items() { return plugin.items(); }

    /**
     * Get the region service.
     */
    public RegionService regions() { return plugin.regions(); }

    public WebService web() { return plugin.webService(); }

//    public FileConfiguration getConfig(String fileName) { return plugin.getConfig(fileName); }
//
//    public void saveConfig(String fileName, FileConfiguration config) { plugin.saveConfig(fileName, config); }

    public GatekeeperService gatekeeper() { return plugin.gateKeeperService(); }
}
