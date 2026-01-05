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

package dev.stemcraft.service.tabcompletion;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.tabcomplete.TabCompleteService;
import dev.stemcraft.api.service.tabcomplete.TabCompletionProvider;
import dev.stemcraft.api.util.SCPlayer;
import dev.stemcraft.service.BaseService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TabCompleteServiceImpl extends BaseService implements TabCompleteService {
    private final HashMap<String, TabCompletionProvider> tabCompletionPlaceholders = new HashMap<>();

    /**
     * Constructor for TabCompleteServiceImpl.
     */
    public TabCompleteServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the service and registers core tab completions.
     */
    public void onEnable() {
        CoreTabCompletions.registerAll(api);
    }

    /**
     * Register a new tab completion placeholder.
     */
    public void register(String name, TabCompletionProvider callback) {
        tabCompletionPlaceholders.put(name, callback);
    }

    /**
     * Get the completion list for a given placeholder.
     */
    public List<String> getCompletionList(String name, Player player, String... args) {
        if (tabCompletionPlaceholders.containsKey(name)) {
            return tabCompletionPlaceholders.get(name).provide(player, args);
        }

        return new ArrayList<String>();
    }
}
