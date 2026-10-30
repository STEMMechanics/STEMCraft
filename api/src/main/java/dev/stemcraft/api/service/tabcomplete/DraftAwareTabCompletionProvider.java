/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
 * Functional interface for tab-completion providers that need the active argument draft.
 *
 * <p>The {@code args} array contains provider context resolved from completed command
 * arguments. The {@code draft} is the separate argument currently being completed.</p>
 */
@FunctionalInterface
public interface DraftAwareTabCompletionProvider extends TabCompletionProvider {

    /**
     * Provides completions using the player, provider context, and current argument draft.
     *
     * @param player The player requesting tab completions.
     * @param args Provider context resolved from completed command arguments.
     * @param draft The current argument draft being completed.
     * @return A list of tab completions.
     */
    @NotNull List<String> provide(
        @NotNull Player player,
        @NotNull String[] args,
        @NotNull String draft
    );

    /**
     * Preserve the original provider contract when this callback is used as a legacy provider.
     *
     * @param player The player requesting tab completions.
     * @param args Provider context.
     * @return A list of tab completions.
     */
    @Override
    default @NotNull List<String> provide(@NotNull Player player, @NotNull String... args) {
        return provide(player, args, "");
    }
}
