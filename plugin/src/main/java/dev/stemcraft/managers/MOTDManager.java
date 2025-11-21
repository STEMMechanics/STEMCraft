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
import dev.stemcraft.api.services.MOTDService;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class MOTDManager implements MOTDService, Listener {
    private final STEMCraft plugin;

    private final Map<String, String> motds = new LinkedHashMap<>();
    private String motdTitle = "§6§lSTEMCraft";
    private String motdText = "";

    public MOTDManager(STEMCraft plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        motdTitle = plugin.config().getString("motd.title", motdTitle);
        motdText = plugin.config().getString("motd.text", motdText);

        plugin.registerEvent(ServerListPingEvent.class, event -> {
            event.setMotd(motdTitle + "\n" + current());
        });
    }

    public void set(String key, String motd) {
        if (motd == null) {
            clear(key);
            return;
        }

        // remove first so re-put moves it to the end (last = most recent)
        motds.remove(key);
        motds.put(key, motd);
    }

    public void clear(String key) {
        motds.remove(key);
    }

    public String get(String key) {
        if(key == null) {
            return motdText;
        }

        return motds.getOrDefault(key, null);
    }

    public String current() {
        if (motds.isEmpty()) {
            return motdText;
        }

        String last = null;
        for (String value : motds.values()) {
            last = value; // iteration order = insertion order, last = most recent
        }
        return last;
    }
}
