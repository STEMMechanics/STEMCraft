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

package dev.stemcraft.service.command;

import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.command.CommandException;
import dev.stemcraft.api.util.TimeUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of the CommandContext interface.
 */
public class CommandContextImpl implements CommandContext {
    @Getter
    private final Command command;
    @Getter
    private final CommandSender sender;
    @Getter
    final String labelUsed;
    private final List<String> rawArgs;
    private final List<String> args;
    private final java.util.Set<String> flags;
    private final java.util.Map<String, String> options;

    /**
     * Constructor for CommandContextImpl.
     *
     * @param command The command being executed.
     * @param sender The sender of the command.
     * @param labelUsed The label used to invoke the command.
     * @param args The arguments passed to the command.
     */
    public CommandContextImpl(Command command, CommandSender sender, String labelUsed, List<String> args) {
        this.command = command;
        this.sender = sender;
        this.labelUsed = labelUsed.toLowerCase(Locale.ROOT);

        // Preserve original args
        this.rawArgs = List.copyOf(args);

        // Parse into positional args, flags, and key-value options
        java.util.List<String> positional = new java.util.ArrayList<>();
        java.util.Set<String> flagSet = new java.util.HashSet<>();
        java.util.Map<String, String> optionMap = new java.util.HashMap<>();

        for (String arg : args) {
            if (arg == null || arg.isEmpty()) {
                continue;
            }

            // Flag option: starts with "-"
            if (arg.startsWith("-") && arg.length() > 1) {
                flagSet.add(arg.toLowerCase(Locale.ROOT));
                continue;
            }

            // Key-value option: key:value
            int colonIndex = arg.indexOf(':');
            if (colonIndex > 0 && colonIndex < arg.length() - 1) {
                String key = arg.substring(0, colonIndex).toLowerCase(Locale.ROOT);
                String value = arg.substring(colonIndex + 1);
                optionMap.put(key, value);
                continue;
            }

            // Positional argument
            positional.add(arg);
        }

        this.args = positional;
        this.flags = java.util.Collections.unmodifiableSet(flagSet);
        this.options = java.util.Collections.unmodifiableMap(optionMap);
    }

    /**
     * Dispatches another command as the current sender.
     *
     * @param label The command label to dispatch.
     * @param args The arguments to pass to the command.
     * @throws CommandException if the command is not found or to stop execution.
     */
    @Override
    public void dispatch(String label, List<String> args) {
        String cmdLine = label + (args == null || args.isEmpty() ? "" : " " + String.join(" ", args));

        boolean ok = Bukkit.dispatchCommand(sender, cmdLine);
        if (!ok) {
            throw new CommandException("COMMAND_NOT_FOUND", "command", label);
        }

        // If your dispatched command already printed output, you can choose to stop your command flow here.
        // If you want to stop execution consistently:
        throw new CommandException();
    }

    /**
     * Gets the command label.
     *
     * @return The command label.
     */
    @Override
    public String getLabel() { return command.getLabel(); }

    /**
     * Returns the parsed argument list, excluding flags and key-value options.
     *
     * @return List of positional arguments.
     */
    @Override
    public List<String> args() {
        return new ArrayList<>(args);
    }

    /**
     * Returns the original raw argument list as received from Bukkit, before
     * flag and key-value parsing.
     *
     * @return List of raw arguments.
     */
    @Override
    public List<String> rawArgs() {
        return rawArgs;
    }

    /**
     * Drops the specified number of arguments from the start of the argument list.
     *
     * @param count The number of arguments to drop.
     */
    @Override
    public void dropArgs(int count) {
        if (count <= 0 || args.isEmpty()) {
            return;
        }
        int toIndex = Math.min(count, args.size());
        args.subList(0, toIndex).clear();
    }

    /**
     * Checks if a flag is present. Flags are case-insensitive.
     * The flag may be passed in with or without the leading hyphen.

     * Examples:
     *  - hasFlag("-force")
     *  - hasFlag("force").
     *
     *  @param flag The flag to check.
     *  @param def The default value to return if the flag is null or empty.
     *  @return true if the flag is present, false otherwise.
     */
    @Override
    public boolean hasFlag(String flag, boolean def) {
        if (flag == null || flag.isEmpty()) {
            return def;
        }
        String normalized = flag.startsWith("-")
                ? flag.toLowerCase(Locale.ROOT)
                : ("-" + flag.toLowerCase(Locale.ROOT));
        return flags.contains(normalized);
    }

    /**
     * Returns the value for a key-value option in the form key:value.
     * Keys are case-insensitive.

     * Example:
     *   /cmd something course:parkour1
     *   getOption("course") -> "parkour1".
     *
     *   @param key The option key to retrieve.
     *   @param def The default value to return if the key is null, empty, or not found.
     *   @return The option value, or the default value if not found.
     */
    @Override
    public String getOption(String key, String def) {
        if (key == null || key.isEmpty()) {
            return def;
        }
        return options.getOrDefault(key.toLowerCase(Locale.ROOT), def);
    }

    /**
     * Returns the map of all key-value options.
     *
     * @param message The info message.
     * @param placeholders Placeholders for the message.
     */
    @Override
    public void info(String message, Object... placeholders) {
        infoImpl(message, false, placeholders);
    }

    /**
     * Sends a warning message to the command sender.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    @Override
    public void warn(String message, Object... placeholders) {
        warnImpl(message, false, placeholders);
    }

    /**
     * Sends an error message to the command sender.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    @Override
    public void error(String message, Object... placeholders) {
        errorImpl(message, false, placeholders);
    }

    /**
     * Sends a success message to the command sender.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    @Override
    public void success(String message, Object... placeholders) {
        successImpl(message, false, placeholders);
    }

    /**
     * Sends an info message to the command sender and stops further command execution.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    @Override
    public void returnInfo(String message, Object... placeholders) {
        infoImpl(message, true, placeholders);
    }

    /**
     * Sends a warning message to the command sender and stops further command execution.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    @Override
    public void returnWarn(String message, Object... placeholders) {
        warnImpl(message, true, placeholders);
    }

    /**
     * Sends an error message to the command sender and stops further command execution.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    @Override
    public void returnError(String message, Object... placeholders) {
        errorImpl(message, true, placeholders);
    }

    /**
     * Sends a success message to the command sender and stops further command execution.
     *
     * @param message The message to send to the sender.
     * @param placeholders The placeholders to replace in the message.
     */
    @Override
    public void returnSuccess(String message, Object... placeholders) {
        successImpl(message, true, placeholders);
    }

    /**
     * Gets the command sender as a Player, or null if the sender is not a player.
     *
     * @return The Player instance or null.
     */
    @Override
    public Player asPlayer() {
        if(sender instanceof Player player) {
            return player;
        }

        return null;
    }

    /**
     * Checks if the command sender is the console.
     *
     * @return true if the sender is the console, false otherwise.
     */
    @Override
    public boolean isConsole() {
        return !(sender instanceof org.bukkit.entity.Player);
    }

    /**
     * Checks if the command sender is a player.
     *
     * @return true if the sender is a player, false otherwise.
     */
    @Override
    public boolean isPlayer() {
        return sender instanceof org.bukkit.entity.Player;
    }

    /**
     * Checks if the command sender has the specified permission.
     *
     * @param permission The permission to check.
     * @return true if the sender has the permission, false otherwise.
     */
    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    /**
     * Gets the number of positional arguments.
     *
     * @return The number of arguments.
     */
    @Override
    public int numArgs() {
        return args.size();
    }

    /**
     * Gets the argument at the specified index.
     *
     * @param index The index of the argument to retrieve.
     * @return The argument at the specified index, or null if not found.
     */
    @Override
    public String getArg(int index) { return getArg(index, null); }

    /**
     * Gets the argument at the specified index, or a default value if not found.
     *
     * @param index The index of the argument to retrieve.
     * @param def The default value to return if the argument is not found.
     * @return The argument at the specified index, or the default value.
     */
    @Override
    public String getArg(int index, String def) {
        if (args.isEmpty() || index >= args.size()) {
            return def;
        }

        if (index < 0) {
            index = args.size() + index; // -1 -> last, -2 -> second last
        }

        return args.get(index);
    }

    /**
     * Gets all arguments from the specified index as a single string.
     *
     * @param index The starting index of the arguments to retrieve.
     * @return The concatenated arguments as a single string, or an empty string if not found.
     */
    @Override
    public String getArgsAsString(int index, String def) {
        // convert 1-based → 0-based
        int start = index - 1;

        if (start < 0 || start >= args.size()) {
            return def;
        }

        return String.join(" ", args.subList(start, args.size()));
    }

    /**
     * Gets the argument at the specified index as a float.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @param min The minimum value, or null for no minimum.
     * @param max The maximum value, or null for no maximum.
     * @return The argument as a float.
     */
    @Override
    public float getArgAsFloat(int index, float def, Float min, Float max) {
        float result = def;

        try {
            String arg = getArg(index, null);
            result = (arg != null ? Float.parseFloat(arg) : def);
        } catch(NumberFormatException ex) {
            // ignore
        }

        if(min != null && result < min) {
            result = min;
        }
        if(max != null && result > max) {
            result = max;
        }
        return result;
    }

    /**
     * Gets the argument at the specified index as a double.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @param min The minimum value, or null for no minimum.
     * @param max The maximum value, or null for no maximum.
     * @return The argument as a double.
     */
    @Override
    public double getArgAsDouble(int index, double def, Double min, Double max) {
        double result;

        try {
            String arg = getArg(index, null);
            result = (arg != null ? Double.parseDouble(arg) : def);
        } catch(NumberFormatException ex) {
            return def;
        }

        if(min != null && result < min) {
            result = min;
        }
        if(max != null && result > max) {
            result = max;
        }
        return result;
    }

    /**
     * Gets the argument at the specified index as an integer.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @param min The minimum value, or null for no minimum.
     * @param max The maximum value, or null for no maximum.
     * @return The argument as an integer.
     */
    @Override
    public int getArgAsInt(int index, int def, Integer min, Integer max) {
        int result = def;

        try {
            String arg = getArg(index, null);
            result = (arg != null ? Integer.parseInt(arg) : def);
        } catch(NumberFormatException ex) {
            // ignore
        }

        if(min != null && result < min) {
            result = min;
        }
        if(max != null && result > max) {
            result = max;
        }
        return result;
    }

    /**
     * Gets the argument at the specified index as a boolean.
     *
     * @param index The argument index.
     * @param def The default value if not present.
     * @return The argument as a boolean.
     */
    @Override
    public boolean getArgAsBoolean(int index, boolean def) {
        String arg = getArg(index, null);
        if(arg == null) {
            return def;
        }

        return arg.equalsIgnoreCase("true") || arg.equalsIgnoreCase("yes") || arg.equalsIgnoreCase("1");
    }

    /**
     * Gets the argument at the specified index as a Player.
     *
     * @param index The argument index.
     * @param def The default CommandSender if the arg is not present.
     * @return The argument as a Player, the default Player if the arg is not present,
     * or null if the arg value is not a recognized player.
     */
    @Override
    public Player getPlayer(int index, CommandSender def) {
        if(def instanceof Player) {
            return getPlayer(index, (Player)def);
        } else {
            return getPlayer(index, null);
        }
    }

    public Player getPlayer(int index, Player def) {
        String playerName = getArg(index, null);
        if(playerName == null) {
            return def;
        }

        return Bukkit.getPlayerExact(playerName);
    }

    /**
     * Gets the argument at the specified index as an OfflinePlayer.
     *
     * @param index The argument index.
     * @param def The default CommandSender if not present.
     * @return The argument as an OfflinePlayer.
     */
    @Override
    public OfflinePlayer getArgAsOfflinePlayer(int index, CommandSender def) {
        String playerName = getArg(index, null);
        if(playerName == null) {
            if (def instanceof Player) {
                return (OfflinePlayer) def;
            }

            return null;
        }

        return Bukkit.getOfflinePlayerIfCached(playerName);
    }

    /**
     * Gets the argument at the specified index as a World.
     *
     * @param index The argument index.
     * @param def The default World if not present.
     * @return The argument as a World.
     */
    @Override
    public World getArgAsWorld(int index, World def) {
        String worldName = getArg(index, null);
        if(worldName == null) {
            return def;
        }

        World world = Bukkit.getWorld(worldName);
        return world != null ? world : def;
    }

    /**
     * Gets the argument at the specified index as a Duration.
     *
     * @param index The argument index.
     * @param def The default Duration if not present.
     * @return The argument as a Duration.
     */
    @Override
    public Duration getArgAsDuration(int index, Duration def) {
        String durationStr = getArg(index, null);
        if(durationStr == null) {
            return def;
        }

        try {
            long secs = TimeUtil.parseDuration(durationStr);
            return Duration.ofSeconds(secs);
        }  catch(Exception ignored) {
            return null;
        }
    }

    /**
     * Checks if the number of arguments is at least the specified size.
     *
     * @param size The minimum number of arguments required.
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgsSizeAtLeast(int size, String error) {
        if(args.size() < size) {
            String message = error;

            if(message == null || message.isEmpty()) {
                if(this.getCommand().getUsage().isEmpty()) {
                    message = "COMMAND_MISSING_ARGUMENTS";
                } else {
                    message = "Usage: " + this.getCommand().getUsage();
                }
            }

            throw new CommandException(message);
        }
    }

    /**
     * Checks if the argument at the specified index is an integer.
     *
     * @param index The index of the argument to check.
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgIsInt(int index, String error, Object... placeholders) {
        String arg = getArg(index, null);
        if(arg == null) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID";
            }

            throw new CommandException(message, "argument", placeholders);
        }

        try {
            Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID";
            }

            throw new CommandException(message, placeholders);
        }
    }

    /**
     * Checks if the argument at the specified index is an integer.
     *
     * @param index The index of the argument to check.
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgIsFloat(int index, String error, Object... placeholders) {
        String arg = getArg(index, null);
        if(arg == null) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID";
            }

            throw new CommandException(message, placeholders);
        }

        try {
            Float.parseFloat(arg);
        } catch (NumberFormatException e) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID";
            }

            throw new CommandException(message, placeholders);
        }
    }

    /**
     * Checks if the argument at the specified index is a double.
     *
     * @param index The index of the argument to check.
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgIsDouble(int index, String error, Object... placeholders) {
        String arg = getArg(index, null);
        if(arg == null) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID";
            }

            throw new CommandException(message, placeholders);
        }

        try {
            Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID";
            }

            throw new CommandException(message, placeholders);
        }
    }

    /**
     * Checks that the command sender is not the console.
     *
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkNotConsole(String error) {
        if(isConsole()) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_COMMAND_PLAYER_ONLY";
            }

            throw new CommandException(message);
        }
    }

    /**
     * Checks that there is at least one argument provided.
     *
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgsNotEmpty(String error) {
        if(args().isEmpty()) {
            String message = error;

            if(message == null || message.isEmpty()) {
                if(this.getCommand().getUsage().isEmpty()) {
                    message = "COMMAND_MISSING_ARGUMENTS";
                } else {
                    message = "Usage: " + this.getCommand().getUsage();
                }
            }

            throw new CommandException(message);
        }
    }

    /**
     * Checks if the argument at the specified index is a valid Player.
     *
     * @param index The index of the argument to check.
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgIsPlayer(int index, String error, Object... placeholders) {
        String arg = getArg(index, null);
        if(arg == null || getPlayer(index, null) == null) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID_PLAYER";
            }

            throw new CommandException(message, placeholders == null ? "null" : arg);
        }
    }

    /**
     * Checks if the command sender is the console, and if so, ensures the argument at the specified index is a valid Player.
     *
     * @param index The index of the argument to check.
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgIsPlayerIfConsole(int index, String error, Object... placeholders) {
        if(isConsole()) {
            if(error == null || error.isEmpty()) {
                error = "CONSOLE_PLAYER_REQUIRED";
            }

            checkArgIsPlayer(index, error, placeholders);
        }
    }

    /**
     * Checks if the argument at the specified index is a valid OfflinePlayer.
     *
     * @param index The index of the argument to check.
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgIsOfflinePlayer(int index, String error, Object... placeholders) {
        String arg = getArg(index, null);
        if(arg == null || getArgAsOfflinePlayer(index, null) == null) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID_OFFLINE_PLAYER";
            }

            throw new CommandException(message, placeholders == null ? "null" : arg);
        }
    }

    /**
     * Checks if the argument at the specified index is a valid World.
     *
     * @param index The index of the argument to check.
     * @param error The error message to throw if the check fails.
     */
    @Override
    public void checkArgIsWorld(int index, String error, Object... placeholders) {
        String arg = getArg(index, null);
        if(arg == null || getArgAsWorld(index, null) == null) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID_WORLD";
            }

            throw new CommandException(message, placeholders == null ? "null" : arg);
        }
    }

    /**
     * Sends an info message to the command sender.
     *
     * @param message The message to send to the sender.
     * @param ret Whether to stop further command execution.
     * @param placeholders Placeholders for the message.
     */
    private void infoImpl(String message, boolean ret, Object... placeholders) {
        command.info(sender, message, placeholders);
        if(ret) {
            throw new CommandException();
        }
    }

    /**
     * Sends a warning message to the command sender.
     *
     * @param message The message to send to the sender.
     * @param ret Whether to stop further command execution.
     * @param placeholders Placeholders for the message.
     */
    private void warnImpl(String message, boolean ret, Object... placeholders) {
        command.warn(sender, message, placeholders);
        if (ret) {
            throw new CommandException();
        }
    }

    /**
     * Sends an error message to the command sender.
     *
     * @param message The message to send to the sender.
     * @param ret Whether to stop further command execution.
     * @param placeholders Placeholders for the message.
     */
    private void errorImpl(String message, boolean ret, Object... placeholders) {
        command.error(sender, message, placeholders);
        if (ret) {
            throw new CommandException();
        }
    }

    /**
     * Sends a success message to the command sender.
     *
     * @param message The message to send to the sender.
     * @param ret Whether to stop further command execution.
     * @param placeholders Placeholders for the message.
     */
    private void successImpl(String message, boolean ret, Object... placeholders) {
        command.success(sender, message, placeholders);
        if(ret) {
            throw new CommandException();
        }
    }
}
