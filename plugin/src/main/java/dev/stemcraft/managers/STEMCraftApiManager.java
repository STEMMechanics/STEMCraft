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
import dev.stemcraft.api.services.LocaleService;
import dev.stemcraft.api.services.MessengerService;
import dev.stemcraft.api.services.PlayerLogService;
import dev.stemcraft.api.services.WorldService;
import dev.stemcraft.api.tabcomplete.TabCompleteService;
import dev.stemcraft.commands.STEMCraftCommandImpl;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;

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

    public File dataFolder() { return plugin.getDataFolder(); }

    public PlayerLogService playerLog() {
        return plugin.playerLogService();
    }

    public MessengerService messenger() {
        return plugin.messengerService();
    }

    public LocaleService locale() { return plugin.localeService(); }

    public WorldService worlds() { return plugin.worldService(); }

    public TabCompleteService tabComplete() { return plugin.tabCompleteService(); }
}
