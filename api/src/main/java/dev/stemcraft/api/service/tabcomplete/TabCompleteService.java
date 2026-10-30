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
 * Service for managing tab completion providers.
 */
public interface TabCompleteService {

    /**
     * Register a tab completion provider with a specific name.
     * Registering an existing name replaces its provider. Core defaults are registered
     * before feature services so services can override or expand them.
     *
     * @param name The name of the tab completion provider.
     * @param callback The callback to provide tab completions.
     * @example Registering a provider
     * {@code
     * api.tabComplete().register("arena", (player, args) -> arenaIds);
     * }
     */
    void register(@NotNull String name, @NotNull TabCompletionProvider callback);

    /**
     * Register a tab completion provider that can inspect the current argument draft.
     *
     * <p>This overload is additive: existing providers using the two-argument callback
     * remain valid and continue to receive only their provider context.</p>
     *
     * @param name The name of the tab completion provider.
     * @param callback The draft-aware callback to provide completions.
     * @example Registering a draft-aware provider
     * {@code
     * api.tabComplete().register("message", (player, args, draft) ->
     *     List.of(draft + "{placeholder}"));
     * }
     */
    default void register(@NotNull String name, @NotNull DraftAwareTabCompletionProvider callback) {
        register(name, (player, args) -> callback.provide(player, args, ""));
    }

    /**
     * Get a list of tab completions for a specific provider name.
     *
     * @param name The name of the tab completion provider.
     * @param player The player requesting tab completions.
     * @param args Provider context supplied by the command's placeholder.
     * @return A list of tab completions.
     */
    @NotNull List<String> getCompletionList(@NotNull String name, @NotNull Player player, @NotNull String... args);

    /**
     * Get completions while supplying the current argument draft separately from provider context.
     *
     * <p>Implementations that do not support draft-aware providers fall back to the legacy
     * lookup and ignore {@code draft}.</p>
     *
     * @param name The name of the tab completion provider.
     * @param player The player requesting the completions.
     * @param args Provider context resolved from completed command arguments.
     * @param draft The current argument draft being completed.
     * @return A list of tab completions.
     */
    default @NotNull List<String> getCompletionList(
        @NotNull String name,
        @NotNull Player player,
        @NotNull String[] args,
        @NotNull String draft
    ) {
        return getCompletionList(name, player, args);
    }
}
