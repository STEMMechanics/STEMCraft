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

package dev.stemcraft.api.internal;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.plugin.Plugin;

/**
 * Internal holder for the STEMCraftAPI and Plugin instances

 * Warning: This class is for internal use only. Do not use it in your plugins.
 * Using this class may lead to unexpected behavior and is not supported.
 */
public class InstanceHolder {
    private static STEMCraftAPI api;
    private static Plugin plugin;

    public static STEMCraftAPI api() { return api; }
    public static Plugin plugin() { return plugin; }

    public static void set(STEMCraftAPI api, Plugin plugin) {
        InstanceHolder.api = api;
        InstanceHolder.plugin = plugin;
    }
}