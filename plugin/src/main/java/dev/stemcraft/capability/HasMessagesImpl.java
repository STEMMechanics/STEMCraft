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

package dev.stemcraft.capability;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.capability.HasMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Capability for sending messages to command senders and logging.
 */
public class HasMessagesImpl implements HasMessages {

    /**
     * Log a debug message to the console.
     *
     * @param message The debug message.
     * @param ex An optional exception to log.
     * @param placeholders Placeholders to replace in the message.
     */
    public void debug(String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().debug(message, ex, placeholders);
    }

    /**
     * Log a message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to log.
     * @param ex An optional exception to log.
     * @param placeholders Placeholders to replace in the message.
     */
    public void log(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().log(sender, message, ex, placeholders);
    }

    /**
     * Send a plain message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Placeholders to replace in the message.
     */
    public void send(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().send(sender, message, ex, placeholders);
    }

    /**
     * Send an info message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Placeholders to replace in the message.
     */
    public void info(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().info(sender, message, ex, placeholders);
    }

    /**
     * Send a warning message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Placeholders to replace in the message.
     */
    public void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().warn(sender, message, ex, placeholders);
    }

    /**
     * Send an error message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Placeholders to replace in the message.
     */
    public void error(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().error(sender, message, ex, placeholders);
    }

    /**
     * Send a success message to the sender or console if null.
     *
     * @param sender The command sender to send the message to, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Placeholders to replace in the message.
     */
    public void success(CommandSender sender, String message, Throwable ex, Object... placeholders)  {
        STEMCraftAPI.api().messages().success(sender, message, ex, placeholders);
    }

    /**
     * Broadcast a message to all players, excluding those in the exclude list.
     *
     * @param message The message to broadcast.
     * @param exclude A list of players to exclude from receiving the message.
     * @param placeholders Placeholders to replace in the message.
     */
    public void broadcast(String message, List<Player> exclude, Object... placeholders)  {
        STEMCraftAPI.api().messages().broadcast(message, exclude, placeholders);
    }
}
