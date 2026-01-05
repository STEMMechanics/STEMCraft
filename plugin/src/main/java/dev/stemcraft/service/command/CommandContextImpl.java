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

        this.args = java.util.Collections.unmodifiableList(positional);
        this.flags = java.util.Collections.unmodifiableSet(flagSet);
        this.options = java.util.Collections.unmodifiableMap(optionMap);
    }

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

    @Override
    public String getLabel() { return command.getLabel(); }

    @Override
    public List<String> args() {
        return new ArrayList<>(args);
    }

    /**
     * Returns the original raw argument list as received from Bukkit, before
     * flag and key-value parsing.
     */
    @Override
    public List<String> rawArgs() {
        return rawArgs;
    }

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
     * The flag may be passed in with or without the leading "-".

     * Examples:
     *  - hasFlag("-force")
     *  - hasFlag("force")
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
     *   getOption("course") -> "parkour1"
     */
    @Override
    public String getOption(String key, String def) {
        if (key == null || key.isEmpty()) {
            return def;
        }
        return options.get(key.toLowerCase(Locale.ROOT));
    }

    @Override
    public void info(String message, Object... placeholders) {
        infoImpl(message, false, placeholders);
    }

    @Override
    public void warn(String message, Object... placeholders) {
        warnImpl(message, false, placeholders);
    }

    @Override
    public void error(String message, Object... placeholders) {
        errorImpl(message, false, placeholders);
    }

    @Override
    public void success(String message, Object... placeholders) {
        successImpl(message, false, placeholders);
    }

    @Override
    public void returnInfo(String message, Object... placeholders) {
        infoImpl(message, true, placeholders);
    }

    @Override
    public void returnWarn(String message, Object... placeholders) {
        warnImpl(message, true, placeholders);
    }

    @Override
    public void returnError(String message, Object... placeholders) {
        errorImpl(message, true, placeholders);
    }

    @Override
    public void returnSuccess(String message, Object... placeholders) {
        successImpl(message, true, placeholders);
    }

    @Override
    public Player getSenderAsPlayer() {
        if(sender instanceof Player player) {
            return player;
        }

        return null;
    }

    @Override
    public boolean isConsole() {
        return !(sender instanceof org.bukkit.entity.Player);
    }

    @Override
    public boolean isPlayer() {
        return sender instanceof org.bukkit.entity.Player;
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public int numArgs() {
        return args.size();
    }

    @Override
    public String getArg(int index) { return getArg(index, null); }

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

    @Override
    public String getArgsAsString(int index, String def) {
        // convert 1-based → 0-based
        int start = index - 1;

        if (start < 0 || start >= args.size()) {
            return def;
        }

        return String.join(" ", args.subList(start, args.size()));
    }

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

    @Override
    public double getArgAsDouble(int index, double def, Double min, Double max) {
        double result = def;

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

    @Override
    public Player getArgAsPlayer(int index, CommandSender def) {
        if(def instanceof Player) {
            return getArgAsPlayer(index, (Player)def);
        } else {
            return getArgAsPlayer(index, null);
        }
    }

    public Player getArgAsPlayer(int index, Player def) {
        String playerName = getArg(index, null);
        if(playerName == null) {
            return def;
        }

        return Bukkit.getPlayerExact(playerName);
    }

    @Override
    public OfflinePlayer getArgAsOfflinePlayer(int index, CommandSender def) {
        String playerName = getArg(index, null);
        if(playerName == null) {
            if (def instanceof Player) {
                return (OfflinePlayer) def;
            }
        }

        return Bukkit.getOfflinePlayerIfCached(playerName);
    }

    @Override
    public World getArgAsWorld(int index, World def) {
        String worldName = getArg(index, null);
        if(worldName == null) {
            return def;
        }

        World world = Bukkit.getWorld(worldName);
        return world != null ? world : def;
    }

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

    @Override
    public void checkArgIsPlayer(int index, String error, Object... placeholders) {
        String arg = getArg(index, null);
        if(arg == null || getArgAsPlayer(index, null) == null) {
            String message = error;

            if(message == null || message.isEmpty()) {
                message = "COMMAND_ARGUMENT_INVALID_PLAYER";
            }

            throw new CommandException(message, placeholders == null ? "null" : arg);
        }
    }

    @Override
    public void checkArgIsPlayerIfConsole(int index, String error, Object... placeholders) {
        if(isConsole()) {
            if(error == null || error.isEmpty()) {
                error = "CONSOLE_PLAYER_REQUIRED";
            }

            checkArgIsPlayer(index, error, placeholders);
        }
    }

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

    private void infoImpl(String message, boolean ret, Object... placeholders) {
        command.info(sender, message, placeholders);
        if(ret) {
            throw new CommandException();
        }
    }

    private void warnImpl(String message, boolean ret, Object... placeholders) {
        command.warn(sender, message, placeholders);
        if (ret) {
            throw new CommandException();
        }
    }

    private void errorImpl(String message, boolean ret, Object... placeholders) {
        command.error(sender, message, placeholders);
        if (ret) {
            throw new CommandException();
        }
    }

    private void successImpl(String message, boolean ret, Object... placeholders) {
        command.success(sender, message, placeholders);
        if(ret) {
            throw new CommandException();
        }
    }
}
