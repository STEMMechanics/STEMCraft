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
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.api.util.WorldUtil;
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
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        api.tabComplete().register("world-transition-command-index", (player, args) -> {
            if (args.length < 2) {
                return List.of();
            }

            WorldService.TransitionCommandPhase phase = parseTransitionPhase(args[0]);
            if (phase == null) {
                return List.of();
            }

            return indexedTransitionCommands(args[1], phase).stream()
                .map(indexedCommand -> Integer.toString(indexedCommand.index()))
                .toList();
        });

        command = api.commands().create("world")
                .description("WORLD_DESCRIPTION")
                .permission("stemcraft.command.world")
                .usage("WORLD_COMMAND_USAGE")
                .tabCompletion("create", "", "seed:{int}")
                .tabCompletion("create", "", "{world-generators}")
                .tabCompletion("create", "", "{world-generators}", "seed:{int}")
                .tabCompletion("create", "", "{world-generators}", "{world-generator-options:$2}")
                .tabCompletion("create", "", "{world-generators}", "{world-generator-options:$2}", "seed:{int}")
                .tabCompletion("delete", "{world}")
                .tabCompletion("info")
                .tabCompletion("info", "{world-any}")
                .tabCompletion("displayname", "{world-any}")
                .tabCompletion("displayname", "{world-any}", "clear")
                .tabCompletion("load", "{world-offline}")
                .tabCompletion("unload", "{world}")
                .tabCompletion("list")
                .tabCompletion("duplicate")
                .tabCompletion("listgenerators")
                .tabCompletion("setgenerator", "{world-any}", "{world-generators}")
                .tabCompletion("setgenerator", "{world-any}", "{world-generators}", "{world-generator-options:$2}")
                .tabCompletion("setspawn")
                .tabCompletion("id", "{world}")
                .tabCompletion("joincommands")
                .tabCompletion("joincommands", "{world-any}")
                .tabCompletion("joincommands", "{world-any}", "{int}")
                .tabCompletion("leavecommands")
                .tabCompletion("leavecommands", "{world-any}")
                .tabCompletion("leavecommands", "{world-any}", "{int}")
                .tabCompletion("addjoincommand", "{world-any}")
                .tabCompletion("addleavecommand", "{world-any}")
                .tabCompletion("setjoincommand", "{world-any}", "{world-transition-command-index:join:$1}")
                .tabCompletion("setleavecommand", "{world-any}", "{world-transition-command-index:leave:$1}")
                .tabCompletion("removejoincommand", "{world-any}", "{world-transition-command-index:join:$1}")
                .tabCompletion("removeleavecommand", "{world-any}", "{world-transition-command-index:leave:$1}")
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
        if (ctx.numArgs() == 0) {
            if (ctx.isConsole()) {
                ctx.returnUsage();
            }
            handleSubCommandInfo(ctx);
            return;
        }

        switch(ctx.getArg(0).toLowerCase(Locale.ROOT)) {
            case "create" -> handleSubCommandCreate(ctx);
            case "delete" -> handleSubCommandDelete(ctx);
            case "info" -> handleSubCommandInfo(ctx);
            case "displayname" -> handleSubCommandDisplayName(ctx);
            case "load" -> handleSubCommandLoad(ctx);
            case "unload" -> handleSubCommandUnload(ctx);
            case "list" -> handleSubCommandList(ctx);
            case "duplicate" -> handleSubCommandDuplicate(ctx);
            case "listgenerators" -> handleSubCommandListGenerators(ctx);
            case "setgenerator" -> handleSubCommandSetGenerator(ctx);
            case "setspawn" -> handleSubCommandSetSpawn(ctx);
            case "id" -> handleSubCommandId(ctx);
            case "joincommands" -> handleTransitionCommandList(ctx, WorldService.TransitionCommandPhase.JOIN);
            case "leavecommands" -> handleTransitionCommandList(ctx, WorldService.TransitionCommandPhase.LEAVE);
            case "addjoincommand" -> handleAddTransitionCommand(ctx, WorldService.TransitionCommandPhase.JOIN);
            case "addleavecommand" -> handleAddTransitionCommand(ctx, WorldService.TransitionCommandPhase.LEAVE);
            case "setjoincommand" -> handleSetTransitionCommand(ctx, WorldService.TransitionCommandPhase.JOIN);
            case "setleavecommand" -> handleSetTransitionCommand(ctx, WorldService.TransitionCommandPhase.LEAVE);
            case "removejoincommand" -> handleRemoveTransitionCommand(ctx, WorldService.TransitionCommandPhase.JOIN);
            case "removeleavecommand" -> handleRemoveTransitionCommand(ctx, WorldService.TransitionCommandPhase.LEAVE);
            case "flags" -> {
                String worldArg = ctx.getArg(1, null);
                World explicitWorld = ctx.getArgAsWorld(1);
                World world;
                int flagIndex;

                if(explicitWorld != null) {
                    world = explicitWorld;
                    flagIndex = 2;
                } else {
                    if(ctx.isConsole()) {
                        if (worldArg == null || worldArg.isBlank()) {
                            ctx.returnError("WORLD_COMMAND_CONSOLE_WORLD_REQUIRED");
                        }
                        ctx.returnError("WORLD_NOT_FOUND", "world", worldArg);
                    }
                    world = ctx.asPlayer().getWorld();
                    flagIndex = 1;
                }

                String flag = ctx.getArg(flagIndex, "").toLowerCase(Locale.ROOT);
                ctx.dropArgs(flag.isEmpty() ? flagIndex : flagIndex + 1);
                handleSubCommandFlags(flag, ctx, world);
            }
            default -> {
                String subCommand = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
                String worldArg = ctx.getArg(1, null);
                World explicitWorld = ctx.getArgAsWorld(1);
                World world;

                if(explicitWorld != null) {
                    world = explicitWorld;
                    ctx.dropArgs(2);
                } else {
                    if(ctx.isConsole()) {
                        if (worldArg == null || worldArg.isBlank()) {
                            ctx.returnError("WORLD_COMMAND_CONSOLE_WORLD_REQUIRED");
                        }
                        ctx.returnError("WORLD_NOT_FOUND", "world", worldArg);
                    }
                    world = ctx.asPlayer().getWorld();
                    ctx.dropArgs(1);
                }

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
        String worldArg = ctx.getArg(argIndex, null);
        World world = ctx.getArgAsWorld(argIndex);
        if(world == null) {
            if(ctx.isConsole()) {
                if (worldArg == null || worldArg.isBlank()) {
                    ctx.returnError("WORLD_COMMAND_CONSOLE_WORLD_REQUIRED");
                }
                ctx.returnError("WORLD_NOT_FOUND", "world", worldArg);
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

        String genKey = ctx.getArg(2, "normal");
        String genOpt = ctx.getArg(3, "");
        Long seed = parseSeedOption(ctx);

        if (!worldService.generator().isRegistered(genKey)) {
            if (!worldService.generator().isAvailable(formatExternalGeneratorKey(genKey, genOpt), name)) {
                ctx.returnError("WORLD_INVALID_GENERATOR", "generator", formatGeneratorDetail(genKey, genOpt));
            }
        }

        ctx.info("WORLD_CREATING", "world", name, "generator", formatGeneratorDetail(genKey, genOpt));

        long startedAt = System.nanoTime();
        World world = api.worlds().createWorld(name, genKey, genOpt, seed);
        if (world == null) {
            ctx.returnError("WORLD_FAILED_CREATE_REASON",
                "world", name,
                "reason", worldService.getLastWorldOperationErrorOrDefault(name));
        }

        ctx.returnSuccess("WORLD_CREATED_DURATION", "world", name, "duration", formatElapsed(System.nanoTime() - startedAt));
    }

    private @Nullable Long parseSeedOption(@NotNull CommandContext ctx) {
        String rawSeed = ctx.getOption("seed", "");
        if (rawSeed == null || rawSeed.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(rawSeed.trim());
        } catch (NumberFormatException exception) {
            ctx.returnError("WORLD_INVALID_SEED", "seed", rawSeed);
            return null;
        }
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
                ctx.info("WORLD_EVICTING_PLAYERS", "world", name);
                api.worlds().evictAllPlayers(name);
            }

            ctx.info("WORLD_UNLOADING", "world", name);
            api.tasks().retry(20, () -> api.worlds().unloadWorld(name, false), result -> {
                if (result == TaskService.RetryResult.SUCCESS) {
                    try {
                        api.worlds().deleteWorld(name);
                        ctx.info("WORLD_DELETED", "world", name);
                    } catch (Exception e) {
                        api.messages().error("WORLD_FAILED_DELETE", e, "world", name);
                        ctx.error("WORLD_FAILED_DELETE", "world", name);
                    }
                } else {
                    ctx.error("WORLD_UNLOAD_FAILED", "world", name);
                }
            });
        }
    }

    /**
     * Handle the 'info' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandInfo(CommandContext ctx) {
        String requestedName = ctx.getArg(1, null);
        if ((requestedName == null || requestedName.isBlank()) && ctx.isConsole()) {
            ctx.returnError("WORLD_COMMAND_CONSOLE_WORLD_REQUIRED");
        }

        if (requestedName == null || requestedName.isBlank()) {
            renderLoadedWorldInfo(ctx, ctx.asPlayer().getWorld());
            return;
        }

        World loadedWorld = Bukkit.getWorld(requestedName);
        if (loadedWorld != null) {
            renderLoadedWorldInfo(ctx, loadedWorld);
            return;
        }

        if (!api.worlds().worldExists(requestedName)) {
            ctx.returnError("WORLD_NOT_FOUND", "world", requestedName);
        }

        renderUnloadedWorldInfo(ctx, requestedName);
    }

    /**
     * Set or clear the player-facing display name for a world.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandDisplayName(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "WORLD_COMMAND_USAGE_DISPLAY_NAME");
        String worldName = ctx.getArg(1);
        if (!api.worlds().worldExists(worldName) && Bukkit.getWorld(worldName) == null) {
            ctx.returnError("WORLD_NOT_FOUND", "world", worldName);
            return;
        }

        String requestedName = ctx.getArgsAsString(3, "").trim();
        ConfigSection config = worldService.getConfigSection(worldName);
        if (requestedName.equalsIgnoreCase("clear")) {
            config.set("display-name", null);
            config.save();
            ctx.returnSuccess(
                "WORLD_DISPLAY_NAME_CLEARED",
                "world", worldName,
                "display_name", WorldService.defaultDisplayName(worldName)
            );
            return;
        }

        if (requestedName.isBlank()) {
            ctx.returnError("WORLD_COMMAND_USAGE_DISPLAY_NAME");
            return;
        }

        config.set("display-name", requestedName);
        config.save();
        ctx.returnSuccess("WORLD_DISPLAY_NAME_SET", "world", worldName, "display_name", requestedName);
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

        ctx.info("WORLD_LOADING", "world", name);
        long startedAt = System.nanoTime();
        World world = api.worlds().loadWorld(name);
        if (world == null) {
            ctx.returnError("WORLD_FAILED_LOAD_REASON",
                "world", name,
                "reason", worldService.getLastWorldOperationErrorOrDefault(name));
        } else {
            ctx.returnSuccess("WORLD_LOADED_DURATION", "world", name, "duration", formatElapsed(System.nanoTime() - startedAt));
        }
    }

    public void handleSubCommandSetGenerator(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(3, "WORLD_COMMAND_USAGE_SETGENERATOR");
        String name = ctx.getArg(1);
        if (!api.worlds().worldExists(name)) {
            ctx.returnError("WORLD_NOT_FOUND", "world", name);
        }

        String generatorKey = ctx.getArg(2, "normal");
        String generatorOptions = ctx.getArg(3, "");

        try {
            worldService.setStoredGenerator(name, generatorKey, generatorOptions);
        } catch (IllegalArgumentException exception) {
            ctx.returnError("Invalid generator for world '" + name + "': " + exception.getMessage());
            return;
        }

        ctx.success("Stored generator for world '" + name + "' set to " + formatGeneratorDetail(generatorKey, generatorOptions) + ".");
        if (api.worlds().isWorldLoaded(name)) {
            ctx.warn("World '" + name + "' is currently loaded. Unload and load it again for the new generator to apply to future chunks.");
        } else {
            ctx.info("The new generator will be used next time world '" + name + "' is loaded.");
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

        ctx.info("WORLD_UNLOADING", "world", name);
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

                        Component joinCommands = Component.text("[Join]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/world joincommands " + worldName))
                                .hoverEvent(HoverEvent.showText(Component.text("Edit join commands")));

                        Component leaveCommands = Component.text("[Leave]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/world leavecommands " + worldName))
                                .hoverEvent(HoverEvent.showText(Component.text("Edit leave commands")));

                        Component line = Component.text(worldName, NamedTextColor.YELLOW)
                                .append(Component.text(" - ", NamedTextColor.GRAY))
                                .append(status);

                        if (isPlayer) {
                            line = line.append(Component.text(" "))
                                    .append(joinCommands)
                                    .append(Component.text(" "))
                                    .append(leaveCommands)
                                    .append(Component.text(" "))
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
            api.messages().error("WORLD_FAILED_DUPLICATE", e, "world", src);
            ctx.returnError("WORLD_FAILED_DUPLICATE", "world", src);
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

        StringBuilder sb = new StringBuilder("Available generators:");
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
     * Handle the 'id' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandId(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "WORLD_COMMAND_USAGE_ID");
        World world = ctx.getArgAsWorld(1);
        if (world == null) {
            ctx.returnError("WORLD_NOT_FOUND", "world", ctx.getArg(1));
            return;
        }

        String worldId = world.getUID().toString();
        ctx.info("WORLD_ID_VALUE", "world", world.getName(), "id", worldId);

        Component copy = Component.text(worldId, NamedTextColor.GOLD)
            .clickEvent(ClickEvent.copyToClipboard(worldId))
            .hoverEvent(HoverEvent.showText(Component.text("Copy world UUID to clipboard")));
        ctx.getSender().sendMessage(copy);
    }

    public void handleTransitionCommandList(CommandContext ctx, WorldService.TransitionCommandPhase phase) {
        String worldName = resolveWorldName(ctx, 1);
        int page = ctx.getArgAsInt(2, 1);
        List<Component> lines = buildTransitionCommandLines(worldName, phase, ctx.isPlayer());

        ChatMenuUtil.render(
            ctx.getSender(),
            transitionTitle(worldName, phase),
            "world " + transitionRootLabel(phase) + "commands " + worldName,
            page,
            lines.size(),
            (start, count, isPlayer) -> {
                int end = Math.min(start + count, lines.size());
                if (start >= end) {
                    return List.of();
                }
                return lines.subList(start, end);
            },
            "WORLD_NONE"
        );
    }

    public void handleAddTransitionCommand(CommandContext ctx, WorldService.TransitionCommandPhase phase) {
        ctx.checkArgsSizeAtLeast(3, usageKeyForTransition(phase, "add"));
        String worldName = resolveWorldName(ctx, 1);
        String configuredCommand = ctx.getArgsAsString(2, "").trim();
        if (configuredCommand.isBlank()) {
            ctx.returnUsage();
        }

        List<String> commands = new ArrayList<>(worldService.getWorldTransitionCommands(worldName, phase));
        commands.add(configuredCommand);
        worldService.setWorldTransitionCommands(worldName, phase, commands);
        ctx.returnSuccess("WORLD_TRANSITION_COMMAND_ADDED",
            "type", transitionLabel(phase),
            "world", worldName,
            "command", configuredCommand
        );
    }

    public void handleSetTransitionCommand(CommandContext ctx, WorldService.TransitionCommandPhase phase) {
        ctx.checkArgsSizeAtLeast(4, usageKeyForTransition(phase, "set"));
        String worldName = resolveWorldName(ctx, 1);
        List<String> commands = new ArrayList<>(worldService.getWorldTransitionCommands(worldName, phase));
        if (commands.isEmpty()) {
            ctx.returnError("WORLD_TRANSITION_COMMAND_NONE_SET", "type", transitionLabel(phase), "world", worldName);
        }

        int index = ctx.getArgAsInt(2, 1, 1, commands.size()) - 1;
        String configuredCommand = ctx.getArgsAsString(3, "").trim();
        if (configuredCommand.isBlank()) {
            ctx.returnUsage();
        }

        commands.set(index, configuredCommand);
        worldService.setWorldTransitionCommands(worldName, phase, commands);
        ctx.returnSuccess("WORLD_TRANSITION_COMMAND_UPDATED",
            "type", transitionLabel(phase),
            "world", worldName,
            "index", index + 1,
            "command", configuredCommand
        );
    }

    public void handleRemoveTransitionCommand(CommandContext ctx, WorldService.TransitionCommandPhase phase) {
        ctx.checkArgsSizeAtLeast(3, usageKeyForTransition(phase, "remove"));
        String worldName = resolveWorldName(ctx, 1);
        List<String> commands = new ArrayList<>(worldService.getWorldTransitionCommands(worldName, phase));
        if (commands.isEmpty()) {
            ctx.returnError("WORLD_TRANSITION_COMMAND_NONE_SET", "type", transitionLabel(phase), "world", worldName);
        }

        int index = ctx.getArgAsInt(2, 1, 1, commands.size()) - 1;
        String removed = commands.remove(index);
        worldService.setWorldTransitionCommands(worldName, phase, commands);
        ctx.returnSuccess("WORLD_TRANSITION_COMMAND_REMOVED",
            "type", transitionLabel(phase),
            "world", worldName,
            "index", index + 1,
            "command", removed
        );
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
                return;
            }

            ConfigSection config = worldService.getConfigSection(world);
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
            return;
        }

        ConfigSection config = worldService.getConfigSection(world);
        setting.onCommand(ctx, config, world);
    }

    private String formatGeneratorDetail(String generatorKey, String generatorOptions) {
        String key = (generatorKey == null || generatorKey.isBlank()) ? "normal" : generatorKey.trim();
        String options = generatorOptions == null ? "" : generatorOptions.trim();
        if (options.isBlank()) {
            return key;
        }
        if (!worldService.generator().isRegistered(key)) {
            return key + ":" + options;
        }
        return key + " [" + options + "]";
    }

    private String formatExternalGeneratorKey(String generatorKey, String generatorOptions) {
        String key = (generatorKey == null || generatorKey.isBlank()) ? "normal" : generatorKey.trim();
        String options = generatorOptions == null ? "" : generatorOptions.trim();
        return options.isBlank() || key.contains(":") ? key : key + ":" + options;
    }

    private void renderLoadedWorldInfo(@NotNull CommandContext ctx, @NotNull World world) {
        ConfigSection config = worldService.getConfigSection(world);

        ctx.info("World '" + world.getName() + "':");
        ctx.info(" - Display name: " + describeDisplayName(world.getName(), config));
        ctx.info(" - Status: loaded");
        ctx.info(" - Environment: " + formatEnvironment(world.getEnvironment()));
        ctx.info(" - UUID: " + world.getUID());
        ctx.info(" - Seed: " + world.getSeed());
        ctx.info(" - Generator: " + describeGenerator(world, config));
        ctx.info(" - Difficulty: " + formatEnum(world.getDifficulty().name()));
        ctx.info(" - Players: " + world.getPlayers().size());
        ctx.info(" - Spawn: " + formatLocation(world.getSpawnLocation()));
        ctx.info(" - Time: " + world.getTime() + " ticks");
        ctx.info(" - Weather: " + describeWeather(world));
        ctx.info(" - PVP: " + yesNo(isPvpEnabled(world)));
        ctx.info(" - Height: " + world.getMinHeight() + " to " + world.getMaxHeight());
        ctx.info(" - Border: " + formatBorder(world));
        ctx.info(" - Folder: " + api.worlds().getWorldFolder(world.getName()).toAbsolutePath());
        appendTransitionCommandSummary(ctx, world.getName(), ctx.isPlayer());
        appendConfiguredSettings(ctx, world.getName(), world, config);
    }

    private void renderUnloadedWorldInfo(@NotNull CommandContext ctx, @NotNull String worldName) {
        ConfigSection config = worldService.getExistingConfigSection(worldName);

        ctx.info("World '" + worldName + "':");
        ctx.info(" - Display name: " + describeDisplayName(worldName, config));
        ctx.info(" - Status: unloaded");
        ctx.info(" - Environment: " + formatEnvironment(WorldUtil.resolveEnvironment(worldName)));
        ctx.info(" - Generator: " + describeConfiguredGenerator(config));
        ctx.info(" - Seed: unavailable while unloaded");
        ctx.info(" - Folder: " + api.worlds().getWorldFolder(worldName).toAbsolutePath());

        String lastError = worldService.getLastWorldOperationError(worldName);
        if (lastError != null && !lastError.isBlank()) {
            ctx.info(" - Last load/create error: " + lastError);
        }

        if (config == null) {
            appendTransitionCommandSummary(ctx, worldName, ctx.isPlayer());
            ctx.info(" - Stored settings: none");
            ctx.info(" - Runtime values are unavailable until the world is loaded.");
            return;
        }

        appendTransitionCommandSummary(ctx, worldName, ctx.isPlayer());
        appendConfiguredSettings(ctx, worldName, null, config);
        ctx.info(" - Runtime values are unavailable until the world is loaded.");
    }

    private void appendTransitionCommandSummary(@NotNull CommandContext ctx, @NotNull String worldName, boolean includeActions) {
        appendTransitionCommandSummary(ctx, worldName, WorldService.TransitionCommandPhase.JOIN, includeActions);
        appendTransitionCommandSummary(ctx, worldName, WorldService.TransitionCommandPhase.LEAVE, includeActions);
    }

    private void appendTransitionCommandSummary(
        @NotNull CommandContext ctx,
        @NotNull String worldName,
        @NotNull WorldService.TransitionCommandPhase phase,
        boolean includeActions
    ) {
        List<String> commands = worldService.getWorldTransitionCommands(worldName, phase);
        String prefix = " - " + StringUtil.capitalize(transitionRootLabel(phase)) + " commands: " + commands.size();
        if (!includeActions) {
            ctx.info(prefix);
            return;
        }

        Component line = Component.text(prefix, NamedTextColor.GRAY)
            .append(Component.text(" "))
            .append(action(
                "[Edit]",
                "/world " + transitionRootLabel(phase) + "commands " + worldName,
                "Edit " + transitionLabel(phase) + " commands"
            ));
        ctx.getSender().sendMessage(line);
    }

    private void appendConfiguredSettings(
        @NotNull CommandContext ctx,
        @NotNull String worldName,
        @Nullable World world,
        @NotNull ConfigSection config
    ) {
        List<String> lines = new ArrayList<>();
        for (String key : worldService.getSettingHandlerKeys(null)) {
            WorldBaseSetting setting = worldService.getSettingHandler(key);
            if (setting == null) {
                continue;
            }

            String value;
            if (world != null) {
                value = setting.get(world, config);
            } else {
                value = valueFromConfigSnapshot(worldName, config, key);
            }

            if (value.isBlank() || "unset".equalsIgnoreCase(value)) {
                continue;
            }
            lines.add(key + "=" + value);
        }

        if (lines.isEmpty()) {
            ctx.info(" - Stored settings: none");
            return;
        }

        ctx.info(" - Stored settings:");
        for (String line : lines) {
            ctx.info("   - " + line);
        }
    }

    private @NotNull String valueFromConfigSnapshot(@NotNull String worldName, @NotNull ConfigSection config, @NotNull String key) {
        String baseName = WorldUtil.baseName(worldName);
        ConfigSection baseConfig = worldService.getExistingConfigSection(baseName);
        return switch (key) {
            case "deny-spawn", "gamemode", "tickspeed" ->
                config.getString(key, "unset");
            case "no-damage", "no-hunger", "force-spawn-on-death" -> config.contains(key) ? Boolean.toString(config.getBoolean(key, false)) : "unset";
            case "nether" -> baseConfig == null ? "unset" : baseConfig.getString("nether-world", "unset");
            case "end" -> baseConfig == null ? "unset" : baseConfig.getString("end-world", "unset");
            case "randomspawn" -> readRandomSpawnValue(baseName);
            case "time" -> describeConfiguredTime(config);
            case "weather" -> describeConfiguredWeather(config);
            default -> "unset";
        };
    }

    private @NotNull String readRandomSpawnValue(@NotNull String baseName) {
        ConfigSection root = api.config().load("config.yml");
        if (root == null) {
            return "unset";
        }
        String path = "random-first-spawn.worlds." + baseName + ".enabled";
        return root.contains(path) ? Boolean.toString(root.getBoolean(path, false)) : "unset";
    }

    private @NotNull String describeConfiguredTime(@NotNull ConfigSection config) {
        if (!config.contains("time.set")) {
            return "unset";
        }
        long ticks = config.getLong("time.set", -1L);
        if (ticks < 0L) {
            return "unset";
        }
        return ticks + (config.getBoolean("time.always", false) ? " always" : "");
    }

    private @NotNull String describeConfiguredWeather(@NotNull ConfigSection config) {
        String state = config.getString("weather.state", "unset");
        if (state.isBlank() || "unset".equalsIgnoreCase(state)) {
            return "unset";
        }
        return state + (config.getBoolean("weather.always", false) ? " always" : "");
    }

    private @NotNull String describeGenerator(@NotNull World world, @NotNull ConfigSection config) {
        String configured = describeConfiguredGenerator(config);
        ChunkGenerator generator = world.getGenerator();
        if (generator == null) {
            return "unknown".equals(configured) ? "vanilla/default" : configured + " (vanilla runtime)";
        }

        String runtime = generator.getClass().getSimpleName().isBlank()
            ? generator.getClass().getName()
            : generator.getClass().getSimpleName();
        if ("unknown".equals(configured)) {
            return runtime;
        }
        return configured + " [" + runtime + "]";
    }

    private @NotNull String describeConfiguredGenerator(@Nullable ConfigSection config) {
        if (config == null) {
            return "unknown";
        }

        Object shorthand = config.get("generator");
        if (shorthand instanceof String value) {
            String key = value.trim();
            if (key.isEmpty()) {
                return "unknown";
            }
            return formatGeneratorDetail(key, "");
        }

        ConfigSection generatorSection = config.getSection("generator", false);
        if (generatorSection == null) {
            return "unknown";
        }

        Object rawKey = generatorSection.get("key");
        String key = rawKey instanceof String value ? value.trim() : "";
        if (key.isEmpty()) {
            return "unknown";
        }

        Object rawOptions = generatorSection.get("options");
        String options = rawOptions instanceof String value ? value.trim() : "";
        return formatGeneratorDetail(key, options);
    }

    static @NotNull String describeDisplayName(@NotNull String worldName, @Nullable ConfigSection config) {
        String configured = config == null ? "" : config.getString("display-name", "").trim();
        return configured.isEmpty()
            ? WorldService.defaultDisplayName(worldName) + " (automatic)"
            : configured + " (custom)";
    }

    private @NotNull String describeWeather(@NotNull World world) {
        if (world.isThundering()) {
            return "thunder";
        }
        if (world.hasStorm()) {
            return "rain";
        }
        return "clear";
    }

    private @NotNull String formatLocation(@Nullable org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return "unset";
        }
        return String.format(Locale.ROOT, "%s (%.1f, %.1f, %.1f)",
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ());
    }

    private @NotNull String formatBorder(@NotNull World world) {
        var border = world.getWorldBorder();
        return String.format(Locale.ROOT, "size %.1f at %.1f, %.1f",
            border.getSize(),
            border.getCenter().getX(),
            border.getCenter().getZ());
    }

    private @NotNull String formatEnvironment(@NotNull World.Environment environment) {
        return switch (environment) {
            case NORMAL -> "normal";
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> environment.name().toLowerCase(Locale.ROOT);
        };
    }

    private @NotNull String formatEnum(@NotNull String value) {
        return StringUtil.capitalize(StringUtil.beautify(value));
    }

    private @NotNull String resolveWorldName(@NotNull CommandContext ctx, int argIndex) {
        String requestedName = ctx.getArg(argIndex, null);
        if (requestedName == null || requestedName.isBlank()) {
            if (ctx.isConsole()) {
                ctx.returnError("WORLD_COMMAND_CONSOLE_WORLD_REQUIRED");
            }
            return ctx.asPlayer().getWorld().getName();
        }

        if (!api.worlds().worldExists(requestedName) && Bukkit.getWorld(requestedName) == null) {
            ctx.returnError("WORLD_NOT_FOUND", "world", requestedName);
        }
        return requestedName;
    }

    private @NotNull List<Component> buildTransitionCommandLines(
        @NotNull String worldName,
        @NotNull WorldService.TransitionCommandPhase phase,
        boolean interactive
    ) {
        List<Component> lines = new ArrayList<>();

        Component worldLine = Component.text("World: ", NamedTextColor.GRAY)
            .append(Component.text(worldName, NamedTextColor.YELLOW));
        if (interactive) {
            worldLine = worldLine.append(Component.text(" "))
                .append(action("[Info]", "/world info " + worldName, "Show world details"))
                .append(Component.text(" "))
                .append(action("[Join]", "/world joincommands " + worldName, "Edit join commands"))
                .append(Component.text(" "))
                .append(action("[Leave]", "/world leavecommands " + worldName, "Edit leave commands"));
        }
        lines.add(worldLine);

        Component commandsLine = Component.text("Commands: ", NamedTextColor.GRAY)
            .append(Component.text("default player, use player: or server: prefixes", NamedTextColor.DARK_GRAY));
        if (interactive) {
            commandsLine = commandsLine.append(Component.text(" "))
                .append(suggest(
                    "[Add]",
                    "/world add" + transitionRootLabel(phase) + "command " + worldName + " ",
                    "Add a " + transitionLabel(phase) + " command"
                ));
        }
        lines.add(commandsLine);

        List<IndexedTransitionCommand> commands = indexedTransitionCommands(worldName, phase);
        if (commands.isEmpty()) {
            lines.add(Component.text("None configured.", NamedTextColor.DARK_GRAY));
            return lines;
        }

        for (IndexedTransitionCommand indexedCommand : commands) {
            Component line = Component.text("#" + indexedCommand.index() + " ", NamedTextColor.GRAY)
                .append(Component.text(indexedCommand.command(), NamedTextColor.YELLOW));
            if (interactive) {
                line = line.append(Component.text(" "))
                    .append(suggest(
                        "[Edit]",
                        "/world set" + transitionRootLabel(phase) + "command " + worldName + " " + indexedCommand.index() + " " + indexedCommand.command(),
                        "Edit command #" + indexedCommand.index()
                    ))
                    .append(Component.text(" "))
                    .append(deleteAction(
                        "[Del]",
                        "/world remove" + transitionRootLabel(phase) + "command " + worldName + " " + indexedCommand.index(),
                        "Delete command #" + indexedCommand.index()
                    ));
            }
            lines.add(line);
        }

        return lines;
    }

    private @NotNull List<IndexedTransitionCommand> indexedTransitionCommands(
        @NotNull String worldName,
        @NotNull WorldService.TransitionCommandPhase phase
    ) {
        List<String> commands = worldService.getWorldTransitionCommands(worldName, phase);
        List<IndexedTransitionCommand> indexed = new ArrayList<>(commands.size());
        for (int i = 0; i < commands.size(); i++) {
            indexed.add(new IndexedTransitionCommand(i + 1, commands.get(i)));
        }
        return indexed;
    }

    private @NotNull String transitionTitle(@NotNull String worldName, @NotNull WorldService.TransitionCommandPhase phase) {
        return StringUtil.capitalize(transitionLabel(phase)) + " Commands: " + worldName;
    }

    private @NotNull String transitionRootLabel(@NotNull WorldService.TransitionCommandPhase phase) {
        return switch (phase) {
            case JOIN -> "join";
            case LEAVE -> "leave";
        };
    }

    private @NotNull String transitionLabel(@NotNull WorldService.TransitionCommandPhase phase) {
        return transitionRootLabel(phase);
    }

    private @NotNull String usageKeyForTransition(@NotNull WorldService.TransitionCommandPhase phase, @NotNull String action) {
        return switch (phase) {
            case JOIN -> switch (action) {
                case "add" -> "WORLD_COMMAND_USAGE_ADDJOINCOMMAND";
                case "set" -> "WORLD_COMMAND_USAGE_SETJOINCOMMAND";
                case "remove" -> "WORLD_COMMAND_USAGE_REMOVEJOINCOMMAND";
                default -> "WORLD_COMMAND_USAGE";
            };
            case LEAVE -> switch (action) {
                case "add" -> "WORLD_COMMAND_USAGE_ADDLEAVECOMMAND";
                case "set" -> "WORLD_COMMAND_USAGE_SETLEAVECOMMAND";
                case "remove" -> "WORLD_COMMAND_USAGE_REMOVELEAVECOMMAND";
                default -> "WORLD_COMMAND_USAGE";
            };
        };
    }

    private @Nullable WorldService.TransitionCommandPhase parseTransitionPhase(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "join" -> WorldService.TransitionCommandPhase.JOIN;
            case "leave" -> WorldService.TransitionCommandPhase.LEAVE;
            default -> null;
        };
    }

    private Component action(String label, String command, String hover) {
        return Component.text(label, NamedTextColor.GOLD)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private Component deleteAction(String label, String command, String hover) {
        return Component.text(label, NamedTextColor.RED)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private Component suggest(String label, String command, String hover) {
        return Component.text(label, NamedTextColor.GOLD)
            .clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private @NotNull String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    @SuppressWarnings("deprecation")
    private static boolean isPvpEnabled(@NotNull World world) {
        return world.getPVP();
    }

    private String formatElapsed(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2fs", elapsedNanos / 1_000_000_000.0d);
    }

    private record IndexedTransitionCommand(int index, @NotNull String command) {}
}
