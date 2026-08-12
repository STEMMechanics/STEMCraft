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

package dev.stemcraft.api.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Thread-safe player-scoped context for plugin-owned teleports.
 */
public final class TeleportContext {
    private static final Map<UUID, Deque<TeleportOptions>> OPTIONS = new ConcurrentHashMap<>();

    private TeleportContext() { }

    public static @NotNull TeleportOptions current(@NotNull UUID playerId) {
        Deque<TeleportOptions> stack = OPTIONS.get(playerId);
        if (stack == null || stack.isEmpty()) {
            return TeleportOptions.DEFAULT;
        }
        return stack.peekLast();
    }

    public static void runWithOptions(@NotNull UUID playerId, @NotNull TeleportOptions options, @NotNull Runnable action) {
        callWithOptions(playerId, options, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T callWithOptions(@NotNull UUID playerId, @NotNull TeleportOptions options, @NotNull Supplier<T> action) {
        Deque<TeleportOptions> stack = OPTIONS.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        stack.addLast(options);
        try {
            return action.get();
        } finally {
            if (!stack.isEmpty()) {
                stack.removeLast();
            }
            if (stack.isEmpty()) {
                OPTIONS.remove(playerId, stack);
            }
        }
    }
}
