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

package dev.stemcraft.api.service.message;

import dev.stemcraft.api.message.TokenProcessor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Service for managing messages, including localization, logging, and broadcasting.
 */
public interface MessageService {

    /**
     * Get the token processor for adding/removing global tokens.
     *
     * @return The token processor.
     */
    @NotNull TokenProcessor tokens();

    /**
     * Get the localized text for a key with optional placeholders.
     *
     * @param sender The command sender to get the locale from, or null for default.
     * @param key The key for the locale string.
     * @param placeholders Optional placeholders to fill in the locale string.
     * @return The resolved locale string.
     */
    @NotNull String text(@Nullable CommandSender sender, @NotNull String key, @NotNull Object... placeholders);

    /**
     * Log a debug message to the console.
     *
     * @param message The debug message.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders to fill in the message.
     */
    void debug(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders);
    default void debug(@NotNull String message, @NotNull Object... placeholders) { debug(message, null, placeholders); }

    /**
     * Log a message to the sender or console if null.
     *
     * @param sender The command sender to log the message for, or null for console.
     * @param message The message to log.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders to fill in the message.
     */
    void log(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders);
    default void log(@NotNull String message, @NotNull Object... placeholders) { log(null, message, null, placeholders); }
    default void log(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) { log(null, message, ex, placeholders); }
    default void log(@NotNull CommandSender sender, @NotNull String message, @NotNull Object... placeholders) { log(sender, message, null, placeholders); }

    /**
     * Send a message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to include.
     * @param placeholders Optional placeholders to fill in the message.
     */
    void send(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders);
    default void send(@NotNull String message, @NotNull Object... placeholders) { send(null, message, null, placeholders); }
    default void send(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) { send(null, message, ex, placeholders); }
    default void send(@NotNull CommandSender sender, @NotNull String message, @NotNull Object... placeholders) { send(sender, message, null, placeholders); }

    /**
     * Send an info message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to include.
     * @param placeholders Optional placeholders to fill in the message.
     */
    void info(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders);
    default void info(@NotNull String message, @NotNull Object... placeholders) { info(null, message, null, placeholders); }
    default void info(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) { info(null, message, ex, placeholders); }
    default void info(@NotNull CommandSender sender, @NotNull String message, @NotNull Object... placeholders) { info(sender, message, null, placeholders); }

    /**
     * Send a warning message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to include.
     * @param placeholders Optional placeholders to fill in the message.
     */
    void warn(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders);
    default void warn(@NotNull String message, @NotNull Object... placeholders) { warn(null, message, null, placeholders); }
    default void warn(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) { warn(null, message, ex, placeholders); }
    default void warn(@NotNull CommandSender sender, @NotNull String message, @NotNull Object... placeholders) { warn(sender, message, null, placeholders); }

    /**
     * Send an error message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to include.
     * @param placeholders Optional placeholders to fill in the message.
     */
    void error(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders);
    default void error(@NotNull String message, @NotNull Object... placeholders) { error(null, message, null, placeholders); }
    default void error(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) { error(null, message, ex, placeholders); }
    default void error(@NotNull CommandSender sender, @NotNull String message, @NotNull Object... placeholders) { error(sender, message, null, placeholders); }

    /**
     * Send a success message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to include.
     * @param placeholders Optional placeholders to fill in the message.
     */
    void success(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders);
    default void success(@NotNull String message, @NotNull Object... placeholders) { success(null, message, null, placeholders); }
    default void success(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) { success(null, message, ex, placeholders); }
    default void success(@NotNull CommandSender sender, @NotNull String message, @NotNull Object... placeholders) { success(sender, message, null, placeholders); }

    /**
     * Broadcast a message to all players, excluding those in the exclude list.
     *
     * @param message The message to broadcast.
     * @param exclude A list of players to exclude from receiving the message.
     * @param placeholders Optional placeholders to fill in the message.
     */
    void broadcast(@NotNull String message, @Nullable List<Player> exclude, @NotNull Object... placeholders);
    default void broadcast(@NotNull String message, @Nullable Player exclude, @NotNull Object... placeholders) { broadcast(message, exclude != null ? List.of(exclude) : null, placeholders); }
    default void broadcast(@NotNull String message, @NotNull Object... placeholders) { broadcast(message, (List<Player>)null, placeholders); }
}
