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

package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.capability.HasMessagesImpl;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.command.CommandException;
import dev.stemcraft.api.command.CommandExecutor;
import dev.stemcraft.service.command.CommandContextImpl;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

public class STEMCraftCommandImpl extends HasMessagesImpl implements Command, TabCompleter {
    @Getter
    private String label;
    @Getter
    private String description;
    @Getter
    private String usage;
    private List<String> aliases = new ArrayList<>();
    @Getter
    private String permission = "";
    private CommandExecutor executor;
    private final List<String[]> tabCompletionList = new ArrayList<>();
    private STEMCraftAPI api;
    private PluginCommand pluginCommand = null;

    public STEMCraftCommandImpl() { }
    public STEMCraftCommandImpl(String label) { this.label = label; }

    public void onLoad(STEMCraft plugin) { }
    protected void onExecute(STEMCraftAPI api, Command cmd, CommandContext ctx) { }

    protected STEMCraftCommandImpl setLabel(String label) {
        this.label = label;
        return this;
    }

    @Override
    public Command setAlias(String... aliases) {
        this.aliases = Arrays.asList(aliases);
        if(this.pluginCommand != null) { this.pluginCommand.setAliases(this.aliases); }
        return this;
    }

    @Override
    public Command setDescription(@NonNull String description) {
        this.description = STEMCraft.getPlugin().locale().get(description);
        if(this.pluginCommand != null) { this.pluginCommand.description(this.description); }
        return this;
    }

    @Override
    public Command setUsage(@NonNull String usage) {
        this.usage = usage;
        if(this.pluginCommand != null) { this.pluginCommand.usage(this.usage); }
        return this;
    }

    @Override
    public Command setPermission(@NonNull String permission) {
        this.permission = permission;
        if(this.pluginCommand != null) { this.pluginCommand.permission(this.permission); }
        return this;
    }

    @Override
    public Command addTabCompletion(String... completions) {
        this.tabCompletionList.add(completions);
        return this;
    }

    @Override
    public Command setExecutor(CommandExecutor executor) {
        this.executor = executor;
        return this;
    }

    public void register(JavaPlugin plugin) {
        if(pluginCommand != null) {
            if(!unregister()) {
                error("COMMAND_FAIL_UNREGISTER", "label", label);
            }
        }

        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);

            pluginCommand = c.newInstance(label, plugin);
        } catch (Exception e) {
            STEMCraftAPI.api().messages().error("STEMCRAFT_ERROR_PLUGIN_CLASS", e, "error", e.getMessage());
        }

        if (pluginCommand != null) {
            pluginCommand.setTabCompleter(this);

            if (!this.aliases.isEmpty()) {
                pluginCommand.setAliases(aliases);
            }

            if (this.description != null && !this.description.isEmpty()) {
                pluginCommand.description(this.description);
            }

            if (this.usage != null && !this.usage.isEmpty()) {
                pluginCommand.usage(this.usage);
            }

            if (this.permission != null && !this.permission.isEmpty()) {
                pluginCommand.permission(this.permission);
            }

            pluginCommand.executor((sender, command, label, args) -> {
                CommandContext context = new CommandContextImpl(this, sender, label, Arrays.stream(args).toList());

                if (!permission.isEmpty() && !sender.hasPermission(permission)) {
                    STEMCraftAPI.api().messages().error(sender, "COMMAND_NO_PERMISSION");
                    return true;
                }

                try {
                    if (executor != null) {
                        executor.execute(STEMCraftAPI.api(), this, context);
                    } else {
                        onExecute(STEMCraftAPI.api(), this, context);
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
            debug("COMMAND_LOADED", "label", label);
        }
    }

    public boolean unregister() {
        if(pluginCommand == null) { return true; }

        CommandMap map = getCommandMap();
        if (map == null) return false;

        try {
            // Remove from command map itself:
            Field knownCommandsField = map.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, org.bukkit.command.Command> known = (Map<String, org.bukkit.command.Command>) knownCommandsField.get(map);

            // remove by its primary label
            known.remove(this.label);

            // remove by namespaced label ("stemcraft:fly")
            known.remove("stemcraft:" + this.label.toLowerCase(Locale.ROOT));

            // remove aliases
            if (!aliases.isEmpty()) {
                for (String alias : aliases) {
                    known.remove(alias.toLowerCase(Locale.ROOT));
                    known.remove("stemcraft:" + alias.toLowerCase(Locale.ROOT));
                }
            }

            if (pluginCommand != null) {
                pluginCommand.unregister(map);
                pluginCommand = null;
            }

            return true;

        } catch (Exception e) {
            error("COMMAND_FAIL_UNREGISTER", e, "label", label);
            return false;
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


}
