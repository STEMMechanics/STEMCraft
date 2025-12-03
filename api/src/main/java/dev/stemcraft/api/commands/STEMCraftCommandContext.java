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
package dev.stemcraft.api.commands;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

public interface STEMCraftCommandContext {
    STEMCraftCommand getCommand();
    CommandSender getSender();
    Player getSenderAsPlayer();
    String getLabelUsed();
    String getLabel();
    List<String> args();
    boolean isConsole();
    boolean isPlayer();
    boolean hasPermission(String permission);
    String getArg(int index, String def);
    String getArgsAsString(int startingIndex, String def);
    Player getArgAsPlayer(int index, CommandSender def);
    OfflinePlayer getArgAsOfflinePlayer(int index, CommandSender def);
    Duration getArgAsDuration(int index, Duration def);

    default String getArg(int index) { return getArg(index, null); }
    default String getArgsAsString(int startingIndex) { return getArgsAsString(startingIndex, ""); }
    default String getArgsAsString() { return getArgsAsString(1, ""); }
    default Player getArgAsPlayer(int index) { return getArgAsPlayer(index, null); }

    default Duration getArgAsDuration(int index) { return getArgAsDuration(index, null); }
    default OfflinePlayer getArgAsOfflinePlayer(int index) { return getArgAsOfflinePlayer(index, null); }

    default String getSenderName() {
        Player player = getSenderAsPlayer();
        return player == null ? "SERVER" : player.getName();
    }

}
