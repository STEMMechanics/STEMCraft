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
import dev.stemcraft.service.BaseService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Implementation of the TabCompleteService interface.
 */
public class TabCompleteServiceImpl extends BaseService implements TabCompleteService {
    private final HashMap<String, TabCompletionProvider> tabCompletionPlaceholders = new HashMap<>();

    /**
     * Constructor for TabCompleteServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
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
     *
     * @param name The name of the placeholder.
     * @param callback The callback to provide completions.
     */
    public void register(@NotNull String name, @NotNull TabCompletionProvider callback) {
        tabCompletionPlaceholders.put(name, callback);
    }

    /**
     * Get the completion list for a given placeholder.
     *
     * @param name The name of the placeholder.
     * @param player The player requesting the completions.
     * @param args Additional arguments for the completion provider.
     * @return A list of completion strings.
     */
    public @NotNull List<String> getCompletionList(@NotNull String name, @NotNull Player player, @NotNull String... args) {
        if (tabCompletionPlaceholders.containsKey(name)) {
            return tabCompletionPlaceholders.get(name).provide(player, args);
        }

        return new ArrayList<>();
    }
}
