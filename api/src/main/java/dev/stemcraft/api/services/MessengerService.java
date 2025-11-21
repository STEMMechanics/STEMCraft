/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.api.services;

import org.bukkit.command.CommandSender;

public interface MessengerService extends STEMCraftService {

    void log(CommandSender sender, String message, Throwable ex, String... placeholders);
    void info(CommandSender sender, String message, Throwable ex, String... placeholders);
    void warn(CommandSender sender, String message, Throwable ex, String... placeholders);
    void error(CommandSender sender, String message, Throwable ex, String... placeholders);
    void success(CommandSender sender, String message, Throwable ex, String... placeholders);

    default void log(String message, String... placeholders) { log(null, message, null, placeholders); }
    default void log(String message, Throwable ex, String... placeholders) { log(null, message, ex, placeholders); }
    default void log(CommandSender sender, String message, String... placeholders) { log(sender, message, null, placeholders); }
    default void info(String message, String... placeholders) { info(null, message, null, placeholders); }
    default void info(String message, Throwable ex, String... placeholders) { info(null, message, ex, placeholders); }
    default void info(CommandSender sender, String message, String... placeholders) { info(sender, message, null, placeholders); }
    default void warn(String message, String... placeholders) { warn(null, message, null, placeholders); }
    default void warn(String message, Throwable ex, String... placeholders) { warn(null, message, ex, placeholders); }
    default void warn(CommandSender sender, String message, String... placeholders) { warn(sender, message, null, placeholders); }
    default void error(String message, String... placeholders) { error(null, message, null, placeholders); }
    default void error(String message, Throwable ex, String... placeholders) { error(null, message, ex, placeholders); }
    default void error(CommandSender sender, String message, String... placeholders) { error(sender, message, null, placeholders); }
    default void success(String message, String... placeholders) { success(null, message, null, placeholders); }
    default void success(String message, Throwable ex, String... placeholders) { success(null, message, ex, placeholders); }
    default void success(CommandSender sender, String message, String... placeholders) { success(sender, message, null, placeholders); }
}