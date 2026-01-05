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

package dev.stemcraft.api.capability;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Capability for sending messages to command senders and logging.
 */
public interface HasMessages {

    /**
     * Log a debug message to the console.
     */
    void debug(String message, Throwable ex, Object... placeholders);
    default void debug(String message, Object... placeholders) { debug(message, null, placeholders); }

    /**
     * Log a message to the sender or console if null.
     */
    void log(CommandSender sender, String message, Throwable ex, Object... placeholders);
    default void log(String message, Object... placeholders) { log(null, message, null, placeholders); }
    default void log(String message, Throwable ex, Object... placeholders) { log(null, message, ex, placeholders); }
    default void log(CommandSender sender, String message, Object... placeholders) { log(sender, message, null, placeholders); }

    /**
     * Send a plain message to the sender or console if null.
     */
    void plain(CommandSender sender, String message, Throwable ex, Object... placeholders);
    default void plain(String message, Object... placeholders) { plain(null, message, null, placeholders); }
    default void plain(String message, Throwable ex, Object... placeholders) { plain(null, message, ex, placeholders); }
    default void plain(CommandSender sender, String message, Object... placeholders) { plain(sender, message, null, placeholders); }

    /**
     * Send an info message to the sender or console if null.
     */
    void info(CommandSender sender, String message, Throwable ex, Object... placeholders);
    default void info(String message, Object... placeholders) { info(null, message, null, placeholders); }
    default void info(String message, Throwable ex, Object... placeholders) { info(null, message, ex, placeholders); }
    default void info(CommandSender sender, String message, Object... placeholders) { info(sender, message, null, placeholders); }

    /**
     * Send a warning message to the sender or console if null.
     */
    void warn(CommandSender sender, String message, Throwable ex, Object... placeholders);
    default void warn(String message, Object... placeholders) { warn(null, message, null, placeholders); }
    default void warn(String message, Throwable ex, Object... placeholders) { warn(null, message, ex, placeholders); }
    default void warn(CommandSender sender, String message, Object... placeholders) { warn(sender, message, null, placeholders); }

    /**
     * Send an error message to the sender or console if null.
     */
    void error(CommandSender sender, String message, Throwable ex, Object... placeholders);
    default void error(String message, Object... placeholders) { error(null, message, null, placeholders); }
    default void error(String message, Throwable ex, Object... placeholders) { error(null, message, ex, placeholders); }
    default void error(CommandSender sender, String message, Object... placeholders) { error(sender, message, null, placeholders); }

    /**
     * Send a success message to the sender or console if null.
     */
    void success(CommandSender sender, String message, Throwable ex, Object... placeholders);
    default void success(String message, Object... placeholders) { success(null, message, null, placeholders); }
    default void success(String message, Throwable ex, Object... placeholders) { success(null, message, ex, placeholders); }
    default void success(CommandSender sender, String message, Object... placeholders) { success(sender, message, null, placeholders); }

    /**
     * Broadcast a message to all players, excluding those in the exclude list.
     */
    void broadcast(String message, List<Player> exclude, Object... placeholders);
    default void broadcast(String message, Player exclude, Object... placeholders) { broadcast(message, exclude != null ? List.of(exclude) : null, placeholders); }
    default void broadcast(String message, Object... placeholders) { broadcast(message, (List<Player>)null, placeholders); }
}
