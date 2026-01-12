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

package dev.stemcraft.service.world;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

public class WorldCommand {
    private final STEMCraftAPI api;
    private final WorldServiceImpl worldService;

    @Getter
    private Command command;

    /**
     * Constructor.
     *
     * @param api STEMCraft API instance.
     * @param worldService World service implementation.
     */
    public WorldCommand(STEMCraftAPI api, WorldServiceImpl worldService) {
        this.api = api;
        this.worldService = worldService;
    }

    /**
     * Enable the world command.
     */
    public void onEnable() {
        command = api.commands().create("world")
                .description("WORLD_DESCRIPTION")
                .permission("stemcraft.command.world")
                .usage("WORLD_COMMAND_USAGE")
                .tabCompletion("create", "", "{world-generators}")
                .tabCompletion("delete", "{world}")
                .tabCompletion("load", "{world-offline}")
                .tabCompletion("unload", "{world}")
                .tabCompletion("list")
                .tabCompletion("duplicate")
                .tabCompletion("listgenerators")
                .tabCompletion("setspawn")
                .executor(this::onCommand)
                .register(STEMCraft.getPlugin());
    }

    /**
     * Clean up on plugin disable.
     */
    @SuppressWarnings("EmptyMethod")
    public void onDisable() {
        // Nothing to clean up
    }

    /**
     * The command handler for a world command.
     *
     * @param unused The STEMCraft API instance.
     * @param cmd The command being executed.
     * @param ctx The command context.
     */
    public void onCommand(STEMCraftAPI unused, Command cmd, CommandContext ctx) {
        if(ctx.numArgs() == 0) { ctx.returnUsage(); }

        switch(ctx.getArg(0).toLowerCase(Locale.ROOT)) {
            case "create" -> handleSubCommandCreate(ctx);
            case "delete" -> handleSubCommandDelete(ctx);
            case "load" -> handleSubCommandLoad(ctx);
            case "unload" -> handleSubCommandUnload(ctx);
            case "list" -> handleSubCommandList(ctx);
            case "duplicate" -> handleSubCommandDuplicate(ctx);
            case "listgenerators" -> handleSubCommandListGenerators(ctx);
            case "setspawn" -> handleSubCommandSetSpawn(ctx);
            case "flags" -> {
                String flag = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
                World world = getWorldFromArg(ctx, 1);
                ctx.dropArgs(2);
                handleSubCommandFlags(flag, ctx, world);
            }
            default -> {
                String subCommand = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
                World world = getWorldFromArg(ctx, 1);
                ctx.dropArgs(world != null ? 2 : 1);
                handleUnknownSubCommand(subCommand, ctx, world);
            }
        }
    }

    /**
     * Get the world from the command argument, or the sender's world if not specified.
     *
     * @param ctx The command context.
     * @param argIndex The argument index to check for the world name.
     * @return The world instance.
     */
    public World getWorldFromArg(CommandContext ctx, int argIndex) {
        World world = ctx.getArgAsWorld(argIndex);
        if(world == null) {
            if(ctx.isConsole()) {
                ctx.returnError("WORLD_COMMAND_CONSOLE_WORLD_REQUIRED");
            } else {
                world = ctx.asPlayer().getWorld();
            }
        }

        return world;
    }

    /**
     * Handle the 'create' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandCreate(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "WORLD_COMMAND_USAGE_CREATE");
        String name = ctx.getArg(1);

        if (api.worlds().isWorldLoaded(name) || api.worlds().worldExists(name)) {
            ctx.returnError("WORLD_ALREADY_EXISTS", "world", name);
        }

        String genKey = ctx.getArg(2, "default");
        String genOpt = ctx.getArg(3, "");

        api.worlds().createWorld(name, genKey, genOpt);

        ctx.returnInfo("WORLD_CREATED", "world", name);
    }

    /**
     * Handle the 'delete' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandDelete(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "WORLD_COMMAND_USAGE_DELETE");
        String name = ctx.getArg(1);

        World world = Bukkit.getWorld(name);

        if (world != null && world.equals(api.worlds().getDefaultWorld())) {
            ctx.returnError("WORLD_DELETE_DEFAULT_DENY", "world", name);
        }

        if (!api.worlds().worldExists(name) || world == null) {
            ctx.returnError("WORLD_NOT_FOUND", "world", name);
        }

        if (api.worlds().isWorldLoaded(name)) {
            if (!world.getPlayers().isEmpty()) {
                ctx.returnInfo("WORLD_EVICTING_PLAYERS", "world", name);
                api.worlds().evictAllPlayers(name);
            }

            ctx.returnInfo("WORLD_UNLOADING", "world", name);
            api.tasks().retry(20, () -> api.worlds().unloadWorld(name, false), result -> {
                if (result == TaskService.RetryResult.SUCCESS) {
                    try {
                        api.worlds().deleteWorld(name);
                        ctx.returnInfo("WORLD_DELETED", "world", name);
                    } catch (Exception e) {
                        ctx.returnError("WORLD_FAILED_DELETE", e, "world", name);
                    }
                } else {
                    ctx.returnError("WORLD_UNLOAD_FAILED", "world", name);
                }
            });
        }
    }

    /**
     * Handle the 'load' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandLoad(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "WORLD_COMMAND_USAGE_LOAD");
        String name = ctx.getArg(1);
        if (!api.worlds().worldExists(name)) {
            ctx.returnError("WORLD_NOT_FOUND", "world", name);
        }

        ctx.returnInfo("WORLD_LOADING", "world", name);
        World world = api.worlds().loadWorld(name);
        if (world == null) {
            ctx.returnError("WORLD_FAILED_LOAD", "world", name);
        } else {
            ctx.returnInfo("WORLD_LOADED", "world", name);
        }
    }

    /**
     * Handle the 'unload' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandUnload(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "WORLD_COMMAND_USAGE_UNLOAD");
        String name = ctx.getArg(1);

        if (!api.worlds().isWorldLoaded(name)) {
            ctx.returnError("WORLD_NOT_LOADED", "world", name);
        }

        ctx.returnInfo("WORLD_UNLOADING", "world", name);
        boolean success = api.worlds().unloadWorld(name, true);
        if (success) {
            ctx.returnInfo("WORLD_UNLOADED", "world", name);
        } else {
            ctx.returnError("WORLD_UNLOAD_FAILED", "world", name);
        }
    }

    /**
     * Handle the 'list' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandList(CommandContext ctx) {
        List<String> worlds = api.worlds().listWorlds();

        ChatMenuUtil.render(
                ctx.getSender(),
                "Worlds",
                "world list",
                ctx.getArgAsInt(1, 1),
                worlds.size(),
                (start, count, isPlayer) -> {
                    List<Component> lines = new ArrayList<>();

                    int end = Math.min(start + count, worlds.size());
                    for (int i = start; i < end; i++) {
                        String worldName = worlds.get(i);
                        boolean loaded = api.worlds().isWorldLoaded(worldName);

                        NamedTextColor statusColor = loaded ? NamedTextColor.GREEN : NamedTextColor.RED;

                        Component status = Component.text(loaded ? "loaded" : "unloaded", statusColor);

                        Component loadToggle = loaded
                                ? Component.text("[Unload]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/world unload " + worldName))
                                .hoverEvent(HoverEvent.showText(Component.text("Unload this world")))
                                : Component.text("[Load]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/world load " + worldName))
                                .hoverEvent(HoverEvent.showText(Component.text("Load this world")));

                        Component delete = Component.text("[Del]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/world delete " + worldName))
                                .hoverEvent(HoverEvent.showText(Component.text("Delete this world")));

                        Component line = Component.text(worldName, NamedTextColor.YELLOW)
                                .append(Component.text(" - ", NamedTextColor.GRAY))
                                .append(status);

                        if (isPlayer) {
                            line = line.append(Component.text(" "))
                                    .append(loadToggle)
                                    .append(Component.text(" "))
                                    .append(delete);
                        }

                        lines.add(line);
                    }

                    return lines;
                },
                "WORLD_NONE"
        );
    }

    /**
     * Handle the 'duplicate' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandDuplicate(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "WORLD_COMMAND_USAGE_DUPLICATE");

        String src = ctx.getArg(1);
        String dst = ctx.getArg(2);

        try {
            api.worlds().duplicateWorld(src, dst);
            ctx.returnInfo("WORLD_DUPLICATED", "source", src, "target", dst);
        } catch (Exception e) {
            ctx.returnError("WORLD_FAILED_DUPLICATE", e, "world", src);
        }
    }

    /**
     * Handle the 'listgenerators' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandListGenerators(CommandContext ctx) {
        if (worldService.generator().list().isEmpty()) {
            ctx.returnInfo("WORLD_NO_GENERATORS");
        }

        StringBuilder sb = new StringBuilder("Registered generators:");
        for (String key : worldService.generator().list()) {
            sb.append("\n - ").append(key);
        }

        ctx.returnInfo(sb.toString());
    }

    /**
     * Handle the 'setspawn' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandSetSpawn(CommandContext ctx) {
        ctx.checkNotConsole();

        Player player = ctx.asPlayer();
        World world = player.getWorld();

        world.setSpawnLocation(player.getLocation());
        ctx.returnInfo("WORLD_SPAWN_UPDATED",
                "world", world.getName());
    }

    /**
     * Handle the 'flags' sub-command.
     *
     * @param flag The flag being modified.
     * @param ctx The command context.
     * @param world The world to apply the flag command to.
     */
    public void handleSubCommandFlags(String flag, CommandContext ctx, World world) {
        if(flag.isEmpty()) {
            ctx.info("WORLD_FLAGS_HEADER");
            for(String key : worldService.getSettingHandlerKeys(WorldService.SettingCommandMode.FLAG)) {
                ctx.info("WORLD_FLAGS_ENTRY", "flag", key, "value", worldService.getSetting(world, key));
            }
        } else {
            WorldBaseSetting setting = worldService.getSettingHandler(flag, WorldService.SettingCommandMode.FLAG);
            if(setting == null) {
                ctx.returnError("WORLD_COMMAND_UNKNOWN_FLAG", "flag", flag);
            }

            ConfigSection config = worldService.getConfigSection(world).getSection(flag);
            setting.onCommand(ctx, config, world);
        }
    }

    /**
     * Handle unknown sub-commands by delegating to registered handlers.
     *
     * @param subCommand The sub-command being executed.
     * @param ctx The command context.
     * @param world The world to apply the command to.
     */
    private void handleUnknownSubCommand(String subCommand, CommandContext ctx, World world) {
        WorldBaseSetting setting = worldService.getSettingHandler(subCommand, WorldService.SettingCommandMode.SUBCOMMAND);
        if(setting == null) {
            ctx.returnError("WORLD_COMMAND_UNKNOWN_SUBCOMMAND", "command", subCommand);
        }

        ConfigSection config = worldService.getConfigSection(world).getSection(subCommand);
        setting.onCommand(ctx, config, world);
    }
}
