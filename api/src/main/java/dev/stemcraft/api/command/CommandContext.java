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
package dev.stemcraft.api.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

public interface CommandContext {

    /**
     * Get the command associated with this context
     */
    Command getCommand();

    /**
     * Dispatch a command as the original sender
     */
    void dispatch(String label, List<String> args);

    /**
     * Get the sender of the command
     */
    CommandSender getSender();

    /**
     * Get the sender as a player, or null if not a player
     */
    Player getSenderAsPlayer();

    /**
     * Get the label used to invoke the command
     */
    String getLabelUsed();

    /**
     * Get the command arguments
     */
    String getLabel();

    /**
     * Get the command arguments
     */
    List<String> args();

    /**
     * Get the raw command arguments
     */
    List<String> rawArgs();

    /**
     * Drop the given number of arguments from the start of the argument list
     */
    void dropArgs(int count);
    default void dropArg() { dropArgs(1); }

    /**
     * Check if the given flag is present
     */
    boolean hasFlag(String flag, boolean def);
    default boolean hasFlag(String flag) { return hasFlag(flag, false); }

    /**
     * Get the value of the given option, or the default value if not present
     */
    String getOption(String option, String def);
    default String getOption(String option) { return getOption(option, null); }

    /**
     * Send a info message to the sender
     */
    void info(String message, Object... placeholders);

    /**
     * Send a warning message to the sender
     */
    void warn(String message, Object... placeholders);

    /**
     * Send an error message to the sender
     */
    void error(String message, Object... placeholders);

    /**
     * Send a success message to the sender
     */
    void success(String message, Object... placeholders);

    /**
     * Return usage information from the command
     */
    @Contract(" -> fail")
    default void returnUsage() {
        returnError(getCommand().getUsage());
    }

    /**
     * Return an info message from the command
     */
    @Contract("_,_ -> fail")
    void returnInfo(String message, Object... placeholders);

    /**
     * Return a warning message from the command
     */
    @Contract("_,_ -> fail")
    void returnWarn(String message, Object... placeholders);

    /**
     * Return an error message from the command
     */
    @Contract("_,_ -> fail")
    void returnError(String message, Object... placeholders);

    /**
     * Return a success message from the command
     */
    @Contract("_,_ -> fail")
    void returnSuccess(String message, Object... placeholders);

    /**
     * Check if the sender is the console
     */
    boolean isConsole();

    /**
     * Check if the sender is a player
     */
    boolean isPlayer();

    /**
     * Check if the sender has the given permission
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean hasPermission(String permission);

    /**
     * Get the number of arguments
     */
    int numArgs();

    /**
     * Get the argument at the given index, or the default value if not present
     */
    String getArg(int index, String def);
    default String getArg(int index) { return getArg(index, null); }

    /**
     * Get the argument at the given index in lower case
     */
    default String getArgLower(int index) {
        String arg = getArg(index);
        return arg == null ? null : arg.toLowerCase(Locale.ROOT);
    }

    /**
     * Get the argument at the given index in upper case
     */
    default String getArgUpper(int index) {
        String arg = getArg(index);
        return arg == null ? null : arg.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the argument at the given index as a boolean, or the default value if not present
     */
    boolean getArgAsBoolean(int index, boolean def);
    default boolean getArgAsBoolean(int index) { return getArgAsBoolean(index, false); }

    /**
     * Get the argument at the given index as an integer, or the default value if not present
     * Optionally enforce minimum and maximum values
     */
    int getArgAsInt(int index, int def, Integer min, Integer max);
    default int getArgAsInt(int index) { return getArgAsInt(index, 0, null, null); }
    default int getArgAsInt(int index, int def) { return getArgAsInt(index, def, null, null); }

    /**
     * Get the argument at the given index as a float, or the default value if not present
     */
    float getArgAsFloat(int index, float def, Float min, Float max);
    default float getArgAsFloat(int index) { return getArgAsFloat(index, 0.0f, null, null); }
    default float getArgAsFloat(int index, float def) { return getArgAsFloat(index, def, null, null); }

    /**
     * Get the argument at the given index as a double, or the default value if not present
     */
    double getArgAsDouble(int index, double def, Double min, Double max);
    default double getArgAsDouble(int index) { return getArgAsDouble(index, 0.0d, null, null); }
    default double getArgAsDouble(int index, double def) { return getArgAsDouble(index, def, null, null); }

    /**
     * Get all arguments starting from the given index as a single string, or the default value if none present
     */
    String getArgsAsString(int startingIndex, String def);
    default String getArgsAsString(int startingIndex) { return getArgsAsString(startingIndex, ""); }
    default String getArgsAsString() { return getArgsAsString(1, ""); }

    /**
     * Get the argument at the given index as a Player, or the default value if not present
     */
    Player getArgAsPlayer(int index, CommandSender def);
    default Player getArgAsPlayer(int index) { return getArgAsPlayer(index, null); }

    default Player getArgAsPlayerOrSender(int index) {
        Player player = getArgAsPlayer(index, null);
        if (player == null && isPlayer()) {
            player = getSenderAsPlayer();
        }
        return player;
    }

    /**
     * Get the argument at the given index as an OfflinePlayer, or the default value if not present
     */
    OfflinePlayer getArgAsOfflinePlayer(int index, CommandSender def);
    default OfflinePlayer getArgAsOfflinePlayer(int index) { return getArgAsOfflinePlayer(index, null); }

    /**
     * Get the argument at the given index as a Duration, or the default value if not present
     */
    Duration getArgAsDuration(int index, Duration def);
    default Duration getArgAsDuration(int index) { return getArgAsDuration(index, null); }

    /**
     * Get the argument at the given index as a World, or the default value if not present
     */
    World getArgAsWorld(int index, World def);
    default World getArgAsWorld(int index) { return getArgAsWorld(index, null); }

    /**
     * Check that arguments are not empty. Show error if check fails.
     */
    void checkArgsNotEmpty(String error);
    default void checkArgsNotEmpty() { checkArgsNotEmpty(""); }

    /**
     * Check that the sender is not the console. Show error if check fails.
     */
    void checkNotConsole(String error);
    default void checkNotConsole() { checkNotConsole(""); }

    /**
     * Check that there are at least the given number of arguments. Show error if check fails.
     */
    void checkArgsSizeAtLeast(int size, String error);
    default void checkArgsSizeAtLeast(int size) { checkArgsSizeAtLeast(size, ""); }

    /**
     * Check that the argument at the given index is an integer. Show error if check fails.
     */
    void checkArgIsInt(int index, String error, Object... placeholders);

    /**
     * Check that the argument at the given index is an float. Show error if check fails.
     */
    void checkArgIsFloat(int index, String error, Object... placeholders);

    /**
     * Check that the argument at the given index is an double. Show error if check fails.
     */
    void checkArgIsDouble(int index, String error, Object... placeholders);

    /**
     * Check that the argument at the given index is an player. Show error if check fails.
     */
    void checkArgIsPlayer(int index, String error, Object... placeholders);
    default void checkArgIsPlayer(int index) { checkArgIsPlayer(index, "", ""); }

    /**
     * Check that the argument at the given index is an player if the sender is console. Show error if check fails.
     */
    void checkArgIsPlayerIfConsole(int index, String error, Object... placeholders);
    default void checkArgIsPlayerIfConsole(int index) { checkArgIsPlayerIfConsole(index, "", ""); }

    /**
     * Check that the argument at the given index is an offline player. Show error if check fails.
     */
    void checkArgIsOfflinePlayer(int index, String error, Object... placeholders);

    /**
     * Check that the argument at the given index is an world. Show error if check fails.
     */
    void checkArgIsWorld(int index, String error, Object... placeholders);
    default void checkArgIsWorld(int index) { checkArgIsWorld(index, "", ""); }

    /**
     * Get the sender's name, or "SERVER" if console
     */
    default String getSenderName() {
        Player player = getSenderAsPlayer();
        return player == null ? "SERVER" : player.getName();
    }
}
