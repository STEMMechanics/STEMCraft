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

package dev.stemcraft.api.service.tabcomplete;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Functional interface for providing tab completions for commands.
 *
 * <p>Use {@link DraftAwareTabCompletionProvider} when the provider also needs the
 * argument currently being completed.</p>
 */
@FunctionalInterface
public interface TabCompletionProvider {

    /**
     * Provides a list of tab completions based on the player and current arguments.
     *
     * @param player The player requesting tab completions.
     * @param args Provider context supplied by the command's placeholder.
     * @return A list of tab completions.
     */
    @NotNull List<String> provide(@NotNull Player player, @NotNull String... args);
}
