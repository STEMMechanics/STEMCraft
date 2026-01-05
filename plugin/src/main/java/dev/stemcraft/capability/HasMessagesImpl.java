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

public class HasMessagesImpl implements HasMessages {
    public void debug(String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().debug(message, ex, placeholders);
    }

    public void log(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().log(sender, message, ex, placeholders);
    }

    public void send(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().send(sender, message, ex, placeholders);
    }

    public void send(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders) {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().send(sender, message, ex, placeholders));
    }

    public void info(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().info(sender, message, ex, placeholders);
    }

    public void info(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders) {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().info(sender, message, ex, placeholders));
    }

    public void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().warn(sender, message, ex, placeholders);
    }

    public void warn(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders) {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().warn(sender, message, ex, placeholders));
    }

    public void error(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().error(sender, message, ex, placeholders);
    }

    public void error(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders) {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().error(sender, message, ex, placeholders));
    }

    public void success(CommandSender sender, String message, Throwable ex, Object... placeholders)  {
        STEMCraftAPI.api().messages().success(sender, message, ex, placeholders);
    }

    public void success(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders)  {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().success(sender, message, ex, placeholders));
    }

    public void broadcast(String message, List<Player> exclude, Object... placeholders)  {
        STEMCraftAPI.api().messages().broadcast(message, exclude, placeholders);
    }
}
