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

package dev.stemcraft.api.service.command;

import dev.stemcraft.api.command.CommandBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Service for managing commands within the STEMCraft plugin.
 */
public interface CommandService {

    /**
     * Register a new command.
     *
     * @param label The label of the command.
     * @return A CommandBuilder to further configure the command.
     */
    @NotNull CommandBuilder create(@NotNull String label);
}
