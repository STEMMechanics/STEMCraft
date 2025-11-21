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
package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.STEMCraftMessenger;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import dev.stemcraft.api.commands.STEMCraftCommandExecutor;
import dev.stemcraft.api.tabcomplete.STEMCraftTabComplete;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

public class STEMCraftCommandImpl extends STEMCraftMessenger implements STEMCraftCommand, TabCompleter {
    private String label;
    private String description;
    private String usage;
    private List<String> aliases = new ArrayList<>();
    private String permission = "";
    private STEMCraftCommandExecutor executor;
    private List<String[]> tabCompletionList = new ArrayList<>();
    private STEMCraftAPI api;

    public STEMCraftCommandImpl() { }
    public STEMCraftCommandImpl(String label) { this.label = label; }

    protected void onLoad(STEMCraft plugin) { }
    protected void onExecute(STEMCraftAPI api, String label, STEMCraftCommandContext ctx) { }

    protected STEMCraftCommandImpl setLabel(String label) {
        this.label = label;
        return this;
    }

    @Override
    public STEMCraftCommand setAlias(String... aliases) {
        this.aliases = Arrays.asList(aliases);
        return this;
    }

    @Override
    public STEMCraftCommand setDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public STEMCraftCommand setUsage(String usage) {
        this.usage = usage;
        return this;
    }

    @Override
    public STEMCraftCommand addTabCompletion(String... completions) {
        this.tabCompletionList.add(completions);
        return this;
    }

    @Override
    public STEMCraftCommand setExecutor(STEMCraftCommandExecutor executor) {
        this.executor = executor;
        return this;
    }

    public void register(JavaPlugin plugin) {
        PluginCommand pluginCommand = null;

        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);

            pluginCommand = c.newInstance(label, plugin);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (pluginCommand != null) {
            pluginCommand.setTabCompleter(this);

            if (!this.aliases.isEmpty()) {
                pluginCommand.setAliases(aliases);
            }

            pluginCommand.setExecutor((sender, command, label, args) -> {
                STEMCraftCommandContext context = new STEMCraftCommandContextImpl(label, sender, Arrays.stream(args).toList());

                if (!permission.isEmpty() && !sender.hasPermission(permission)) {
                    STEMCraftAPI.api().messenger().error(sender, "COMMAND_NO_PERMISSION");
                    return true;
                }

                if(executor != null) {
                    executor.execute(STEMCraftAPI.api(), label, context);
                } else {
                    onExecute(STEMCraftAPI.api(), label, context);
                }
                return true;
            });

            getCommandMap().register(label, "stemcraft", pluginCommand);
        }
    }

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
        String option = null;
        String value = null;

        TabCompleteValueOption(String option, String value) {
            this.option = option;
            this.value = value;
        }
    }

    private static class TabCompleteArgParser {
        List<String> optionArgsAvailable = new ArrayList<>();
        Map<String, List<String>> valueOptionArgsAvailable = new HashMap<>();
        List<String> optionArgsUsed = new ArrayList<>();
        List<String> valueOptionArgsUsed = new ArrayList<>();
        Integer argIndex = 0;
        String[] args;

        public TabCompleteArgParser(String[] args) {
            this.args = args;
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
            valueOptionArgsAvailable.put(option.option, parseValue(option.value));
        }

        public static List<String> parseValue(String value) {
            List<String> list = new ArrayList<>();

            if (value.startsWith("{") && value.endsWith("}")) {
                String placeholder = value.substring(1, value.length() - 1);
                List<String> placeholderList = STEMCraftTabComplete.getCompletionList(placeholder);
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

                List<String> values = parseValue(tabCompletionItem);
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
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> tabCompletionResults = new ArrayList<>();
        List<String> optionArgsAvailable = new ArrayList<>();
        Map<String, List<String>> valueOptionArgsAvailable = new HashMap<>();
        String[] fullArgs = new String[args.length - 1];

        System.arraycopy(args, 0, fullArgs, 0, args.length - 1);

        // iterate each tab completion list
        tabCompletionList.forEach(list -> {
            Boolean matches = true;
            Integer listIndex = 0;

            // Copy the elements except the last one
            TabCompleteArgParser argParser = new TabCompleteArgParser(fullArgs);

            // iterate each tab completion list item
            for (listIndex = 0; listIndex < list.length; listIndex++) {
                String listItem = list[listIndex];

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

                // list item is a string or placeholder
                Boolean nextMatches = argParser.nextMatches(listItem);
                if (nextMatches == null) {
                    tabCompletionResults.addAll(TabCompleteArgParser.parseValue(listItem));
                    break;
                } else if (nextMatches == false) {
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
        if (args[args.length - 1].length() > 0) {
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
            Iterator<String> iterator = tabCompletionResults.iterator();

            while (iterator.hasNext()) {
                String item = iterator.next();
                if (!item.contains(arg)) {
                    iterator.remove();
                }
            }
        }

        return tabCompletionResults;
    }
}
