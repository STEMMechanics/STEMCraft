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
                .tabCompletion("load", "{world-offline}")
                .tabCompletion("unload", "{world}")
                .tabCompletion("list")
                .tabCompletion("duplicate")
                .tabCompletion("listgenerators")
                .tabCompletion("setspawn")
                .tabCompletion("id", "{world}")
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
            case "load" -> handleSubCommandLoad(ctx);
            case "unload" -> handleSubCommandUnload(ctx);
            case "list" -> handleSubCommandList(ctx);
            case "duplicate" -> handleSubCommandDuplicate(ctx);
            case "listgenerators" -> handleSubCommandListGenerators(ctx);
            case "setspawn" -> handleSubCommandSetSpawn(ctx);
            case "id" -> handleSubCommandId(ctx);
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

        ctx.info("WORLD_CREATING", "world", name, "generator", formatGeneratorDetail(genKey, genOpt));

        long startedAt = System.nanoTime();
        World world = api.worlds().createWorld(name, genKey, genOpt, seed);
        if (world == null) {
            ctx.returnError("WORLD_FAILED_CREATE_REASON",
                "world", name,
                "reason", worldService.getLastWorldOperationError(name, "unknown error"));
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
                "reason", worldService.getLastWorldOperationError(name, "unknown error"));
        } else {
            ctx.returnSuccess("WORLD_LOADED_DURATION", "world", name, "duration", formatElapsed(System.nanoTime() - startedAt));
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
     * Handle the 'id' sub-command.
     *
     * @param ctx The command context.
     */
    public void handleSubCommandId(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2, "WORLD_COMMAND_USAGE_ID");
        World world = ctx.getArgAsWorld(1);
        if (world == null) {
            ctx.returnError("WORLD_NOT_FOUND", "world", ctx.getArg(1));
        }

        String worldId = world.getUID().toString();
        ctx.info("WORLD_ID_VALUE", "world", world.getName(), "id", worldId);

        Component copy = Component.text(worldId, NamedTextColor.GOLD)
            .clickEvent(ClickEvent.copyToClipboard(worldId))
            .hoverEvent(HoverEvent.showText(Component.text("Copy world UUID to clipboard")));
        ctx.getSender().sendMessage(copy);
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

    private void renderLoadedWorldInfo(@NotNull CommandContext ctx, @NotNull World world) {
        ConfigSection config = worldService.getConfigSection(world);

        ctx.info("World '" + world.getName() + "':");
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
        ctx.info(" - PVP: " + yesNo(world.getPVP()));
        ctx.info(" - Height: " + world.getMinHeight() + " to " + world.getMaxHeight());
        ctx.info(" - Border: " + formatBorder(world));
        ctx.info(" - Folder: " + api.worlds().getWorldFolder(world.getName()).toAbsolutePath());
        appendConfiguredSettings(ctx, world.getName(), world, config);
    }

    private void renderUnloadedWorldInfo(@NotNull CommandContext ctx, @NotNull String worldName) {
        ConfigSection config = worldService.getExistingConfigSection(worldName);

        ctx.info("World '" + worldName + "':");
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
            ctx.info(" - Stored settings: none");
            ctx.info(" - Runtime values are unavailable until the world is loaded.");
            return;
        }

        appendConfiguredSettings(ctx, worldName, null, config);
        ctx.info(" - Runtime values are unavailable until the world is loaded.");
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

            if (value == null || value.isBlank() || "unset".equalsIgnoreCase(value)) {
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
            case "no-damage", "no-hunger" -> config.contains(key) ? Boolean.toString(config.getBoolean(key, false)) : "unset";
            case "force-spawn-on-death" -> config.contains(key) ? Boolean.toString(config.getBoolean(key, false)) : "unset";
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
        if (border == null) {
            return "unknown";
        }
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

    private @NotNull String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String formatElapsed(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2fs", elapsedNanos / 1_000_000_000.0d);
    }
}
