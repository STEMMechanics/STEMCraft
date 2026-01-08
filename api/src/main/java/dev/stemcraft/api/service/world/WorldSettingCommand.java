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

/**
 * Interface for defining world setting commands.
 */
public interface WorldSettingCommand {

    /**
     * Add tab completions for this command.
     *
     * @param completions The tab completions to add.
     * @return The current WorldSettingCommand instance.
     */
    WorldSettingCommand tabCompletion(String... completions);

    /**
     * Set the executor for this command.
     *
     * @param executor The command executor.
     * @return The current WorldSettingCommand instance.
     */
    WorldSettingCommand executor(WorldSettingCommandExecutor executor);

    /**
     * Register this command.
     */
    void register();
}