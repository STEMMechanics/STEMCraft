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

package dev.stemcraft.api.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Context for a command execution.
 */
public interface CommandContext {

    /**
     * Get the command associated with this context.
     *
     * @return The command.
     */
    Command getCommand();

    /**
     * Dispatch a command as the original sender.
     *
     * @param label The command label.
     * @param args The command arguments.
     */
    void dispatch(String label, List<String> args);

    /**
     * Get the sender of the command.
     *
     * @return The command sender.
     */
    CommandSender getSender();

    /**
     * Get the sender as a player, or null if not a player.
     *
     * @return The player sender, or null.
     */
    Player asPlayer();

    /**
     * Get the label (alias) used to invoke the command.
     *
     * @return The command label used.
     */
    String getLabelUsed();

    /**
     * Get the command primary label.
     *
     * @return The command label.
     */
    String getLabel();

    /**
     * Get the command arguments.
     *
     * @return The command arguments.
     */
    List<String> args();

    /**
     * Get the raw command arguments.
     *
     * @return The raw command arguments.
     */
    List<String> rawArgs();

    /**
     * Drop the given number of arguments from the start of the argument list.
     *
     * @param count The number of arguments to drop.
     */
    void dropArgs(int count);
    default void dropArg() { dropArgs(1); }

    /**
     * Check if the given flag is present.
     *
     * @param flag The flag to check.
     * @param def The default value if not present.
     * @return True if the flag is present.
     */
    boolean hasFlag(String flag, boolean def);
    default boolean hasFlag(String flag) { return hasFlag(flag, false); }

    /**
     * Get the value of the given option, or the default value if not present.
     *
     * @param option The option to get.
     * @param def The default value if not present.
     * @return The option value, or the default value.
     */
    String getOption(String option, String def);
    default String getOption(String option) { return getOption(option, null); }

    /**
     * Send an info message to the sender.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    void info(String message, Object... placeholders);

    /**
     * Send a warning message to the sender.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    void warn(String message, Object... placeholders);

    /**
     * Send an error message to the sender.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    void error(String message, Object... placeholders);

    /**
     * Send a success message to the sender.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    void success(String message, Object... placeholders);

    /**
     * Return a usage message for this command to the sender.
     *
     */
    @Contract(" -> fail")
    default void returnUsage() {
        returnError(getCommand().getUsage());
    }

    /**
     * Return an info message from the command to the sender.
     */
    @Contract("_,_ -> fail")
    void returnInfo(String message, Object... placeholders);

    /**
     * Return a warning message from the command to the sender.
     */
    @Contract("_,_ -> fail")
    void returnWarn(String message, Object... placeholders);

    /**
     * Return an error message from the command to the sender.
     */
    @Contract("_,_ -> fail")
    void returnError(String message, Object... placeholders);

    /**
     * Return a success message from the command to the sender.
     */
    @Contract("_,_ -> fail")
    void returnSuccess(String message, Object... placeholders);

    /**
     * Check if the sender is the console.
     *
     * @return True if the sender is the console.
     */
    boolean isConsole();

    /**
     * Check if the sender is a player.
     *
     * @return True if the sender is a player.
     */
    boolean isPlayer();

    /**
     * Check if the sender has the given permission.
     *
     * @param permission The permission to check.
     * @return True if the sender has the permission.
     */
    boolean hasPermission(String permission);

    /**
     * Get the number of arguments.
     *
     * @return The number of arguments.
     */
    int numArgs();

    /**
     * Get the argument at the given index, or the default value if not present.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @return The argument at the given index, or the default value.
     */
    String getArg(int index, String def);
    default String getArg(int index) { return getArg(index, null); }

    /**
     * Get the argument at the given index in lower case.
     *
     * @param index The argument index.
     * @return The argument at the given index in lower case.
     */
    default String getArgLower(int index) {
        String arg = getArg(index);
        return arg == null ? null : arg.toLowerCase(Locale.ROOT);
    }

    /**
     * Get the argument at the given index in upper case.
     *
     * @param index The argument index.
     * @return The argument at the given index in upper case.
     */
    default String getArgUpper(int index) {
        String arg = getArg(index);
        return arg == null ? null : arg.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the argument at the given index as a boolean, or the default value if not present.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @return The argument at the given index as a boolean, or the default value.
     */
    boolean getArgAsBoolean(int index, boolean def);
    default boolean getArgAsBoolean(int index) { return getArgAsBoolean(index, false); }

    /**
     * Get the argument at the given index as an integer, or the default value if not present
     * Optionally enforce minimum and maximum values.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @param min The minimum value, or null for no minimum.
     * @param max The maximum value, or null for no maximum.
     * @return The argument at the given index as an integer, or the default value.
     */
    int getArgAsInt(int index, int def, Integer min, Integer max);
    default int getArgAsInt(int index) { return getArgAsInt(index, 0, null, null); }
    default int getArgAsInt(int index, int def) { return getArgAsInt(index, def, null, null); }

    /**
     * Get the argument at the given index as a float, or the default value if not present.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @param min The minimum value, or null for no minimum.
     * @param max The maximum value, or null for no maximum.
     * @return The argument at the given index as a float, or the default value.
     */
    float getArgAsFloat(int index, float def, Float min, Float max);
    default float getArgAsFloat(int index) { return getArgAsFloat(index, 0.0f, null, null); }
    default float getArgAsFloat(int index, float def) { return getArgAsFloat(index, def, null, null); }

    /**
     * Get the argument at the given index as a double, or the default value if not present.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @param min The minimum value, or null for no minimum.
     * @param max The maximum value, or null for no maximum.
     * @return The argument at the given index as a double, or the default value.
     */
    double getArgAsDouble(int index, double def, Double min, Double max);
    default double getArgAsDouble(int index) { return getArgAsDouble(index, 0.0d, null, null); }
    default double getArgAsDouble(int index, double def) { return getArgAsDouble(index, def, null, null); }

    /**
     * Get all arguments starting from the given index as a single string, or the default value if none present.
     *
     * @param startingIndex The starting argument index.
     * @param def The default value if none present.
     * @return All arguments starting from the given index as a single string, or the default value.
     */
    String getArgsAsString(int startingIndex, String def);
    default String getArgsAsString(int startingIndex) { return getArgsAsString(startingIndex, ""); }
    default String getArgsAsString() { return getArgsAsString(1, ""); }

    /**
     * Get the argument at the given index as a Player, or the default value if not present.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @return The argument at the given index as a Player, or the default value.
     */
    Player getPlayer(int index, CommandSender def);
    default Player getPlayer(int index) { return getPlayer(index, null); }

    default Player getArgAsPlayerOrSender(int index) {
        Player player = getPlayer(index, null);
        if (player == null && isPlayer()) {
            player = asPlayer();
        }
        return player;
    }

    /**
     * Get the argument at the given index as an OfflinePlayer, or the default value if not present.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @return The argument at the given index as an OfflinePlayer, or the default value.
     */
    OfflinePlayer getArgAsOfflinePlayer(int index, CommandSender def);
    default OfflinePlayer getArgAsOfflinePlayer(int index) { return getArgAsOfflinePlayer(index, null); }

    /**
     * Get the argument at the given index as a Duration, or the default value if not present.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @return The argument at the given index as a Duration, or the default value.
     */
    Duration getArgAsDuration(int index, Duration def);
    default Duration getArgAsDuration(int index) { return getArgAsDuration(index, null); }

    /**
     * Get the argument at the given index as a World, or the default value if not present.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @return The argument at the given index as a World, or the default value.
     */
    World getArgAsWorld(int index, World def);
    default World getArgAsWorld(int index) { return getArgAsWorld(index, null); }

    /**
     * Check that arguments are not empty. Show error if check fails.
     *
     * @param error The error message to show if check fails.
     */
    void checkArgsNotEmpty(String error);
    default void checkArgsNotEmpty() { checkArgsNotEmpty(""); }

    /**
     * Check that the sender is not the console. Show error if check fails.
     *
     * @param error The error message to show if check fails.
     */
    void checkNotConsole(String error);
    default void checkNotConsole() { checkNotConsole(""); }

    /**
     * Check that there are at least the given number of arguments. Show error if check fails.
     *
     * @param size The minimum number of arguments required.
     * @param error The error message to show if check fails.
     */
    void checkArgsSizeAtLeast(int size, String error);
    default void checkArgsSizeAtLeast(int size) { checkArgsSizeAtLeast(size, ""); }

    /**
     * Check that the argument at the given index is an integer. Show error if check fails.
     *
     * @param index The argument index.
     * @param error The error message to show if check fails.
     * @param placeholders The placeholders to replace in the message.
     */
    void checkArgIsInt(int index, String error, Object... placeholders);

    /**
     * Check that the argument at the given index is a float. Show error if check fails.
     *
     * @param index The argument index.
     * @param error The error message to show if check fails.
     * @param placeholders The placeholders to replace in the message.
     */
    void checkArgIsFloat(int index, String error, Object... placeholders);

    /**
     * Check that the argument at the given index is a double. Show error if check fails.
     *
     * @param index The argument index.
     * @param error The error message to show if check fails.
     * @param placeholders The placeholders to replace in the message.
     */
    void checkArgIsDouble(int index, String error, Object... placeholders);

    /**
     * Check that the argument at the given index is a player. Show error if check fails.
     *
     * @param index The argument index.
     * @param error The error message to show if check fails.
     * @param placeholders The placeholders to replace in the message.
     */
    void checkArgIsPlayer(int index, String error, Object... placeholders);
    default void checkArgIsPlayer(int index) { checkArgIsPlayer(index, "", ""); }

    /**
     * Check that the argument at the given index is a player if the sender is console. Show error if check fails.
     *
     * @param index The argument index.
     * @param error The error message to show if check fails.
     * @param placeholders The placeholders to replace in the message.
     */
    void checkArgIsPlayerIfConsole(int index, String error, Object... placeholders);
    default void checkArgIsPlayerIfConsole(int index) { checkArgIsPlayerIfConsole(index, "", ""); }

    /**
     * Check that the argument at the given index is an offline player. Show error if check fails.
     *
     * @param index The argument index.
     * @param error The error message to show if check fails.
     * @param placeholders The placeholders to replace in the message.
     */
    void checkArgIsOfflinePlayer(int index, String error, Object... placeholders);

    /**
     * Check that the argument at the given index is a world. Show error if check fails.
     *
     * @param index The argument index.
     * @param error The error message to show if check fails.
     * @param placeholders The placeholders to replace in the message.
     */
    void checkArgIsWorld(int index, String error, Object... placeholders);
    default void checkArgIsWorld(int index) { checkArgIsWorld(index, "", ""); }

    /**
     * Get the sender's name, or "SERVER" if console.
     *
     * @return The sender's name or "SERVER".
     */
    default String getSenderName() {
        Player player = asPlayer();
        return player == null ? "SERVER" : player.getName();
    }
}