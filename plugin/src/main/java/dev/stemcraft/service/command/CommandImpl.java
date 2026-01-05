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

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.*;
import dev.stemcraft.capability.HasMessagesImpl;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

public class CommandImpl extends HasMessagesImpl implements Command, TabCompleter {

    @Getter
    private final String label;

    @Getter
    private String description;

    @Getter
    private String usage;

    @Getter
    private final List<String> aliases = new ArrayList<>();

    @Getter
    private String permission = "";

    @Getter
    private final CommandExecutor executor;

    @Getter
    private final List<String[]> tabCompletions = new ArrayList<>();

    /**
     * The underlying Bukkit command instance after registration.
     * Null until register(...) is called.
     */
    private PluginCommand pluginCommand;

    /**
     * Constructor for CommandBuilderImpl.
     */
    public CommandImpl(STEMCraftAPI api, String label, String description, String usage, List<String> aliases, String permission, CommandExecutor executor, List<String[]> tabCompletions) {
        this.label = label;
        this.description = description;
        this.usage = usage;
        this.aliases.addAll(aliases);
        this.permission = permission;
        this.executor = executor;
        this.tabCompletions.addAll(tabCompletions);
    }

    /**
     * Register the command on the server
     */
    public void register(JavaPlugin plugin) {
        PluginCommand pluginCommand = null;

        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);

            pluginCommand = c.newInstance(label, plugin);
            this.pluginCommand = pluginCommand;
        } catch (Exception e) {
            STEMCraftAPI.api().messages().error("STEMCRAFT_ERROR_PLUGIN_CLASS", e, "error", e.getMessage());
        }

        if (pluginCommand != null) {
            pluginCommand.setTabCompleter(this);
            applyToBukkitCommand(pluginCommand);

            pluginCommand.executor((sender, command, label, args) -> {
                CommandContext context = new CommandContextImpl(this, sender, label, Arrays.stream(args).toList());

                if (!permission.isEmpty() && !sender.hasPermission(permission)) {
                    STEMCraftAPI.api().messages().error(sender, "COMMAND_NO_PERMISSION");
                    return true;
                }

                try {
                    if (executor != null) {
                        executor.execute(STEMCraftAPI.api(), this, context);
                    }
                } catch(CommandException ex) {
                    String msg = ex.getMessage();
                    if(msg != null && !msg.isEmpty()) {
                        STEMCraftAPI.api().messages().error(sender, ex.getMessage());
                    }
                }

                return true;
            });

            getCommandMap().register(label, "stemcraft", pluginCommand);
        }
    }

    /**
     * Apply the current in-memory metadata to the Bukkit command instance.
     */
    private void applyToBukkitCommand(@NotNull PluginCommand cmd) {
        // Aliases
        cmd.setAliases(this.aliases);

        // Description / usage
        if (this.description != null) {
            cmd.description(this.description);
        }
        if (this.usage != null) {
            cmd.usage(this.usage);
        }

        // Permission (empty string effectively means "no permission")
        if (this.permission == null || this.permission.isEmpty()) {
            cmd.permission(null);
        } else {
            cmd.permission(this.permission);
        }
    }

    /**
     * Update the permission required to run this command. Use empty string to clear.
     */
    public void setPermission(@NotNull String permission) {
        this.permission = permission;
        if (this.pluginCommand != null) {
            applyToBukkitCommand(this.pluginCommand);
        }
    }

    /**
     * Replace aliases.
     */
    public void setAliases(@NotNull String... aliases) {
        this.aliases.clear();
        this.aliases.addAll(Arrays.asList(aliases));
        if (this.pluginCommand != null) {
            applyToBukkitCommand(this.pluginCommand);
        }
    }

    /**
     * Update the usage string.
     */
    public void setUsage(@NotNull String usage) {
        this.usage = usage;
        if (this.pluginCommand != null) {
            applyToBukkitCommand(this.pluginCommand);
        }
    }

    /**
     * Update the description.
     */
    public void setDescription(@NotNull String description) {
        this.description = description;
        if (this.pluginCommand != null) {
            applyToBukkitCommand(this.pluginCommand);
        }
    }

    /**
     * Add a tab completion pattern (your existing format: String[] items).
     */
    public void addTabCompletion(@NotNull String... completions) {
        this.tabCompletions.add(completions);
    }

    /**
     * Get the server CommandMap
     */
    private CommandMap getCommandMap() {
        // Paper has Bukkit.getCommandMap()
        try {
            return Bukkit.getCommandMap();
        } catch (NoSuchMethodError ignored) { }

        // Spigot: reflect CraftServer.commandMap
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot get CommandMap", e);
        }
    }

    private static class TabCompleteValueOption {
        String option;
        String value;

        TabCompleteValueOption(String option, String value) {
            this.option = option;
            this.value = value;
        }
    }

    private static class TabCompleteArgParser {
        final List<String> optionArgsAvailable = new ArrayList<>();
        final Map<String, List<String>> valueOptionArgsAvailable = new HashMap<>();
        final List<String> optionArgsUsed = new ArrayList<>();
        final List<String> valueOptionArgsUsed = new ArrayList<>();
        Integer argIndex = 0;
        final String[] args;
        final Player player;

        public TabCompleteArgParser(String[] args, Player player) {
            this.args = args;
            this.player = player;
        }

        public static String getStringAsOption(String arg) {
            if (arg.startsWith("-")) {
                return arg.toLowerCase();
            }

            return null;
        }

        public void addOption(String option) {
            optionArgsAvailable.add(option);
        }

        public static TabCompleteValueOption getStringAsValueOption(String arg) {
            if (arg.matches("^[a-zA-Z0-9-_]:.*")) {
                String option = arg.substring(0, arg.indexOf(':')).toLowerCase();
                String value = arg.substring(arg.indexOf(':') + 1);

                return new TabCompleteValueOption(option, value);
            }

            return null;
        }

        public void addValueOption(TabCompleteValueOption option) {
            valueOptionArgsAvailable.put(option.option, parseValue(option.value, player));
        }

        public static List<String> parseValue(String value, Player player) {
            List<String> list = new ArrayList<>();

            if (value.startsWith("{") && value.endsWith("}")) {
                String inner = value.substring(1, value.length() - 1); // remove { }
                String[] parts = inner.split(":");

                String placeholder = parts[0];
                String[] args = new String[Math.max(0, parts.length - 1)];

                if (parts.length > 1) {
                    System.arraycopy(parts, 1, args, 0, parts.length - 1);
                }

                List<String> placeholderList = STEMCraftAPI.api()
                        .tabComplete()
                        .getCompletionList(placeholder, player, args);

                list.addAll(placeholderList);
            } else {
                list.add(value);
            }

            return list;
        }


        public Boolean hasRemainingArgs() {
            return argIndex < args.length - 1;
        }

        public void next() {
            nextMatches(null);
        }

        public Boolean nextMatches(String tabCompletionItem) {
            for (; argIndex < args.length; argIndex++) {
                String arg = args[argIndex];

                String option = getStringAsOption(arg);
                if (option != null) {
                    optionArgsUsed.add(option);
                    optionArgsAvailable.remove(option);
                    continue;
                }

                TabCompleteValueOption valueOption = getStringAsValueOption(arg);
                if (valueOption != null) {
                    valueOptionArgsUsed.add(valueOption.option);
                    valueOptionArgsAvailable.remove(valueOption.option);
                    continue;
                }

                if (tabCompletionItem == null) {
                    argIndex++;
                    return true;
                }

                List<String> values = parseValue(tabCompletionItem, player);
                if (values.contains(arg)) {
                    argIndex++;
                    return true;
                }

                return false;
            }

            // To get here we are out of args to parse
            return null;
        }

        public void processRemainingArgs() {
            while (hasRemainingArgs()) {
                next();
            }
        }
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command cmd, @NotNull String label, String[] args) {
        Player player = (sender instanceof Player p) ? p : null;
        List<String> tabCompletionResults = new ArrayList<>();
        List<String> optionArgsAvailable = new ArrayList<>();
        Map<String, List<String>> valueOptionArgsAvailable = new HashMap<>();
        String[] fullArgs = new String[args.length - 1];

        System.arraycopy(args, 0, fullArgs, 0, args.length - 1);

        // iterate each tab completion list
        tabCompletions.forEach(list -> {
            boolean matches = true;
            int listIndex;

            // Copy the elements except the last one
            TabCompleteArgParser argParser = new TabCompleteArgParser(fullArgs, player);

            // iterate each tab completion list item
            for (listIndex = 0; listIndex < list.length; listIndex++) {
                String listItem = list[listIndex];

                if(listItem != null) {
                    // list item is an option
                    String option = TabCompleteArgParser.getStringAsOption(listItem);
                    if (option != null) {
                        argParser.addOption(option);
                        continue;
                    }

                    // list item is a value option
                    TabCompleteValueOption valueOption = TabCompleteArgParser.getStringAsValueOption(listItem);
                    if (valueOption != null) {
                        argParser.addValueOption(valueOption);
                        continue;
                    }
                }

                // list item is a string or placeholder
                Boolean nextMatches = argParser.nextMatches(listItem);
                if (nextMatches == null) {
                    if(listItem != null) {
                        tabCompletionResults.addAll(TabCompleteArgParser.parseValue(listItem, player));
                    }

                    break;
                } else if (!nextMatches) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                // parse remaining arg items
                argParser.processRemainingArgs();

                optionArgsAvailable.addAll(argParser.optionArgsAvailable);
                valueOptionArgsAvailable.putAll(argParser.valueOptionArgsAvailable);
            }
        });

        // remove non-matching items from the results based on what the player has already entered
        if (!args[args.length - 1].isEmpty()) {
            String arg = args[args.length - 1];

            // if the player has only a dash in the arg, only show dash arguments
            if (arg.equals("-")) {
                return optionArgsAvailable;
            }

            // if the player has written the start of a option arg
            if (arg.contains(":")) {
                // if the option arg is available
                String key = arg.substring(0, arg.indexOf(":"));
                if (valueOptionArgsAvailable.containsKey(key)) {
                    tabCompletionResults.clear();
                    String prefix = key + ":";
                    for (String item : valueOptionArgsAvailable.get(key)) {
                        tabCompletionResults.add(prefix + item);
                    }
                }
            }

            // remove items in tabCompletionResults that do not contain the current arg text

            tabCompletionResults.removeIf(item -> !item.contains(arg));
        }

        return tabCompletionResults;
    }
}
