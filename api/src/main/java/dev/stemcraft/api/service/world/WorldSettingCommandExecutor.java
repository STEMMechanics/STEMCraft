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

package dev.stemcraft.api.service.world;

import dev.stemcraft.api.command.CommandContext;
import org.bukkit.World;

/**
 * Functional interface for executing world setting commands.
 */
@FunctionalInterface
public interface WorldSettingCommandExecutor {

    /**
     * Executes the command with the given flag, context, and world.
     *
     * @param flag    The command flag.
     * @param context The command context.
     * @param world   The world associated with the command.
     */
    void execute(String flag, CommandContext context, World world);
}
