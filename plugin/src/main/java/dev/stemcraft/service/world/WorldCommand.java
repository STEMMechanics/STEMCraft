package dev.stemcraft.service.world;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.chatmenu.SCChatMenuService;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.service.world.WorldSettingCommand;
import dev.stemcraft.api.service.world.WorldSettingCommandExecutor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.*;

public class WorldCommand {
    private STEMCraftAPI api;
    private WorldServiceImpl worldService;

//    private final Map<String, WorldSettingCommandImpl> subCommandRegistry = new HashMap<>();
//    private final Map<String, WorldSettingCommandImpl> flagCommandRegistry = new HashMap<>();
    @Getter
    private Command command;


    public WorldCommand(STEMCraftAPI api, WorldServiceImpl worldService) {
        this.api = api;
        this.worldService = worldService;
    }

        public WorldSettingCommandImpl tabCompletion(String... completions) {
            tabCompletions.add(completions);
            return this;
        }

        public WorldSettingCommandImpl executor(WorldSettingCommandExecutor executor) {
            this.executor = executor;
            return this;
        }

        public void register() {
            if(isFlag) {
                tabCompletions.forEach(completions -> {
                    String[] out = new String[completions.length + 3];
                    out[0] = "flags";
                    out[1] = "{world}";
                    out[2] = command;
                    System.arraycopy(completions, 0, out, 3, completions.length);
                    WorldCommand.this.command.tabCompletion(out);
                });

                flagCommandRegistry.put(command, this);
            } else {
                tabCompletions.forEach(completions -> {
                    String[] out = new String[completions.length + 2];
                    out[0] = command;
                    out[1] = "{world}";
                    System.arraycopy(completions, 0, out, 2, completions.length);
                    WorldCommand.this.command.tabCompletion(out);
                });

                subCommandRegistry.put(command, this);
        }
    }

    public void onEnable() {
        api.commands().create("world")
                .description("WORLD_DESCRIPTION")
                .permission("stemcraft.command.world")
                .tabCompletion("create", "", "{world-generators}")
                .tabCompletion("delete", "{world}")
                .tabCompletion("load", "{world-offline}")
                .tabCompletion("list")
                .tabCompletion("duplicate")
                .tabCompletion("listgenerators")
                .tabCompletion("flags")
                .tabCompletion("weather", "{world}")
                .tabCompletion("weather", "{world}", "clear")
                .tabCompletion("weather", "{world}", "rain")
                .tabCompletion("weather", "{world}", "thunder")
                .tabCompletion("weather", "{world}", "clear", "always")
                .tabCompletion("weather", "{world}", "rain", "always")
                .tabCompletion("weather", "{world}", "thunder", "always")
                .tabCompletion("time", "{world}")
                .tabCompletion("time", "{world}", "day")
                .tabCompletion("time", "{world}", "noon")
                .tabCompletion("time", "{world}", "night")
                .tabCompletion("time", "{world}", "midnight")
                .tabCompletion("time", "{world}", "day", "always")
                .tabCompletion("time", "{world}", "noon", "always")
                .tabCompletion("time", "{world}", "night", "always")
                .tabCompletion("time", "{world}", "midnight", "always")
                .tabCompletion("flags", "{world}")
                .tabCompletion("flags", "{world}", "no-damage", "true")
                .tabCompletion("flags", "{world}", "no-damage", "false")
                .tabCompletion("flags", "{world}", "no-hunger", "true")
                .tabCompletion("flags", "{world}", "no-hunger", "false")
                .tabCompletion("flags", "{world}", "force-spawn-on-death", "true")
                .tabCompletion("flags", "{world}", "force-spawn-on-death", "false")
                .tabCompletion("gamemode", "{world}", "{gamemode}")
                .tabCompletion("gamemode", "{world}", "unset")
                .tabCompletion("flags", "{world}", "no-mob-spawn", "unset")
                .tabCompletion("flags", "{world}", "no-mob-spawn", "all")
                .tabCompletion("flags", "{world}", "no-mob-spawn", "mobs")
                .tabCompletion("flags", "{world}", "no-mob-spawn", "animals")
                .tabCompletion("flags", "{world}", "tickspeed", "{int}")
                .tabCompletion("flags", "{world}", "tickspeed", "reset")
                .tabCompletion("setspawn")
                .usage("WORLD_COMMAND_USAGE")
                .executor((api, cmd, ctx) -> {
                    var sender = ctx.getSender();
                    var args = ctx.args();

                    if (args.isEmpty()) {
                        api.info(sender, cmd.getUsage());
                        return;
                    }

                    String sub = args.getFirst().toLowerCase(Locale.ROOT);

                    switch (sub) {
                        case "time" -> {
                            if (args.size() < 2) {
                                api.info(sender, "WORLD_COMMAND_USAGE_TIME");
                                return;
                            }

                            String worldName = args.get(1);
                            World world = Bukkit.getWorld(worldName);
                            if (world == null) {
                                api.info(sender, "WORLD_NOT_FOUND", "world", worldName);
                                return;
                            }

                            // /world time <world>  -> show current
                            if (args.size() == 2) {
                                long current = world.getTime();
                                boolean always = plugin.config().getBoolean("worlds." + worldName + ".time.always", false);
                                api.info(sender, "WORLD_TIME_STATUS",
                                        "world", worldName,
                                        "time", String.valueOf(current),
                                        "always", String.valueOf(always));
                                return;
                            }

                            String value = args.get(2).toLowerCase(Locale.ROOT);

                            // /world time <world> unset
                            if ("unset".equals(value)) {
                                plugin.config().set("worlds." + worldName + ".time.value", null);
                                plugin.config().set("worlds." + worldName + ".time.always", null);
                                plugin.configSave();
                                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                                api.info(sender, "WORLD_TIME_UNSET", "world", worldName);
                                return;
                            }

                            long time;
                            switch (value) {
                                case "day" -> time = 1000L;
                                case "noon" -> time = 6000L;
                                case "night" -> time = 13000L;
                                case "midnight" -> time = 18000L;
                                default -> {
                                    try {
                                        time = Long.parseLong(value);
                                    } catch (NumberFormatException ex) {
                                        api.info(sender, "WORLD_INVALID_TIME");
                                        return;
                                    }
                                }
                            }

                            boolean always = args.size() >= 4 && "always".equalsIgnoreCase(args.get(3));

                            world.setTime(time);
                            if (always) {
                                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                            }

                            plugin.config().set("worlds." + worldName + ".time.value", time);
                            plugin.config().set("worlds." + worldName + ".time.always", always);
                            plugin.configSave();

                            String state = String.valueOf(time);
                            if (always) {
                                state += " (fixed)";
                            }

                            api.info(sender, "WORLD_SET_TIME",
                                    "world", worldName,
                                    "state", state);
                        }
                        case "record" -> {
                            if (!(sender instanceof Player player)) {
                                api.info(sender, "COMMAND_PLAYER_ONLY");
                                return;
                            }
                            World world = player.getWorld();

                            if (args.size() < 2) {
                                api.info(sender, cmd.getUsage());
                                return;
                            }

                            String action = args.get(1).toLowerCase(Locale.ROOT);
                            switch (action) {
                                case "start" -> {
                                    captureStart(world);
                                    api.info(player, "WORLD_COMMAND_START_RECORD");
                                }
                                case "stop" -> {
                                    captureStop(world);
                                    api.info(player, "WORLD_COMMAND_STOP_RECORD");
                                }
                                case "rollback" -> {
                                    captureRollback(world);
                                    api.info(player, "WORLD_COMMAND_ROLLBACK");
                                }
                                case "reset" -> {
                                    captureReset(world);
                                    api.info(player, "WORLD_COMMAND_RESET");
                                }
                                default -> api.info(sender, cmd.getUsage());
                            }
                        }
                        case "create" -> {
                            ctx.checkArgsSizeAtLeast(2, "WORLD_COMMAND_USAGE_CREATE");
                            String name = ctx.getArg(2);

                            if (isWorldLoaded(name) || worldExists(name)) {
                                ctx.returnError("WORLD_ALREADY_EXISTS", "world", name);
                            }

                            String genKey = ctx.getArg(3);
                            String genOpt = ctx.getArg(4);
                            createWorld(name, genKey, genOpt);

                            ctx.returnInfo("WORLD_CREATED", "world", name);
                        }
                        case "delete" -> {
                            if (args.size() < 2) {
                                api.info(sender, "WORLD_COMMAND_USAGE_DELETE");
                                return;
                            }
                            String name = args.get(1);
                            World world = Bukkit.getWorld(name);

                            if (world != null && world.equals(defaultWorld)) {
                                api.info(sender, "WORLD_DELETE_DEFAULT_DENY", "world", name);
                                return;
                            }

                            if (!worldExists(name) || world == null) {
                                api.info(sender, "WORLD_NOT_FOUND", "world", name);
                                return;
                            }

                            if (isWorldLoaded(name)) {
                                if (!world.getPlayers().isEmpty()) {
                                    api.info(sender, "WORLD_EVICTING_PLAYERS", "world", name);
                                    evictAllPlayers(name);
                                }

                                api.info(sender, "WORLD_UNLOADING", "world", name);
                                api.tasks().retry(20, () -> unloadWorld(name, false), result -> {
                                    if (result == TaskService.RetryResult.SUCCESS) {
                                        try {
                                            deleteWorld(name);
                                            api.info(sender, "WORLD_DELETED", "world", name);
                                        } catch (IOException e) {
                                            api.info(sender, "WORLD_FAILED_DELETE", e, "world", name);
                                        }
                                    } else {
                                        api.info(sender, "WORLD_UNLOAD_FAILED", "world", name);
                                    }
                                });
                            }
                        }
                        case "load" -> {
                            if (args.size() < 2) {
                                api.info(sender, "WORLD_COMMAND_USAGE_LOAD");
                                return;
                            }
                            String name = args.get(1);
                            if (!worldExists(name)) {
                                api.info(sender, "WORLD_NOT_FOUND", "world", name);
                                return;
                            }

                            api.info(sender, "WORLD_LOADING", "world", name);
                            World world = loadWorld(name);
                            if (world == null) {
                                api.info(sender, "WORLD_FAILED_LOAD", "world", name);
                            } else {
                                api.info(sender, "WORLD_LOADED", "world", name);
                            }
                        }
                        case "duplicate" -> {
                            if (args.size() < 3) {
                                api.info(sender, "WORLD_COMMAND_USAGE_DUPLICATE");
                                return;
                            }

                            String src = args.get(1);
                            String dst = args.get(2);

                            try {
                                duplicateWorld(src, dst);
                                api.info(sender, "WORLD_DUPLICATED", "source", src, "target", dst);
                            } catch (IOException e) {
                                api.info(sender, "WORLD_FAILED_DUPLICATE", e, "world", src);
                            }
                        }
                        case "list" -> {
                            List<String> worlds = listWorlds();

                            SCChatMenuService.render(
                                    ctx.getSender(),
                                    "Worlds",
                                    "world list",
                                    ctx.getArgAsInt(2, 1),
                                    worlds.size(),
                                    (start, count, isPlayer) -> {
                                        List<Component> lines = new ArrayList<>();

                                        int end = Math.min(start + count, worlds.size());
                                        for (int i = start; i < end; i++) {
                                            String worldName = worlds.get(i);
                                            boolean loaded = isWorldLoaded(worldName);

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
                        case "listgenerators" -> {
                            if (registry.isEmpty()) {
                                api.info(sender, "WORLD_NO_GENERATORS");
                                return;
                            }

                            StringBuilder sb = new StringBuilder("Registered generators:");
                            for (String key : registry.keySet()) {
                                sb.append("\n - ").append(key);
                            }

                            api.info(sender, sb.toString());
                        }
                        case "flags" -> {
                            // 0 = flags
                            // 1 = world (optional)
                            // 2 = flag-name
                            // 3+ = flag-args

                            ctx.dropArg();  // drop "flags"

                            World world = ctx.getArgAsWorld(1);
                            if(world == null) {
                                if(ctx.isConsole()) {
                                    ctx.returnError("A world must be specified when using this command from console.");
                                } else {
                                    world = ctx.getSenderAsPlayer().getWorld();
                                }
                            } else {
                                ctx.dropArg(); // drop world arg
                            }

                            String flagName = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
                            ctx.dropArg();

                            if(flagName.isEmpty()) {
                                ctx.returnUsage();
                            } else {
                                WorldBaseSetting setting = worldService.getSetting(flagName, WorldService.SettingCommandMode.FLAG);
                                if(setting == null) {
                                    ctx.returnError("Unknown flag setting '" + flagName + "'.");
                                    return;
                                }

                                ConfigSection config = worldService.getConfigSection(world).getSection(flagName);
                                setting.onCommand(ctx, config, world);
                                return;
                            }




                            if (args.size() < 2 && !(sender instanceof Player)) {
                                api.info(sender, "WORLD_COMMAND_USAGE_FLAGS");
                                return;
                            }

                            World world;
                            if (args.size() >= 2) {
                                String worldArg = args.get(1);
                                if ("here".equalsIgnoreCase(worldArg) && sender instanceof Player player) {
                                    world = player.getWorld();
                                } else {
                                    world = Bukkit.getWorld(worldArg);
                                }
                            } else {
                                world = ((Player) sender).getWorld();
                            }

                            if (world == null) {
                                api.info(sender, "WORLD_NOT_FOUND", args.get(1));
                                return;
                            }

                            if (args.size() == 1 || args.size() == 2) {
                                Boolean daylight = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
                                Boolean weatherCycle = world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE);
                                Boolean mobSpawning = world.getGameRuleValue(GameRule.DO_MOB_SPAWNING);
                                Integer tickSpeed = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
                                long time = world.getTime();
                                String weather = world.hasStorm() ? (world.isThundering() ? "thunder" : "rain") : "clear";

                                StringBuilder sb = new StringBuilder("Flags for world ").append(world.getName()).append(":");
                                sb.append("\n - time: ").append(time);
                                sb.append("\n - weather: ").append(weather);
                                sb.append("\n - frozen: ").append(Boolean.FALSE.equals(daylight) && Boolean.FALSE.equals(weatherCycle));
                                String mobMode = plugin.config().getString("worlds." + world.getName() + ".no-mob-spawn", "unset");
                                sb.append("\n - no-mob-spawn: ").append(mobMode);
                                sb.append("\n - tickspeed: ").append(tickSpeed == null ? "default" : tickSpeed);
                                sb.append("\n - no-damage: ").append(plugin.config().getBoolean("worlds." + world.getName() + ".flags.no-damage", false));
                                sb.append("\n - no-hunger: ").append(plugin.config().getBoolean("worlds." + world.getName() + ".flags.no-hunger", false));
                                sb.append("\n - force-spawn-on-death: ").append(plugin.config().getBoolean("worlds." + world.getName() + ".flags.force-spawn-on-death", false));

                                api.info(sender, sb.toString());
                                return;
                            }

                            if (args.size() < 4) {
                                api.info(sender, "WORLD_COMMAND_USAGE_FLAGS");
                                return;
                            }

                            String flag = args.get(2).toLowerCase(Locale.ROOT);
                            String value = args.get(3);

                            switch (flag) {
                                case "time" -> {
                                    long time;
                                    switch (value.toLowerCase(Locale.ROOT)) {
                                        case "day" -> time = 1000L;
                                        case "noon" -> time = 6000L;
                                        case "night" -> time = 13000L;
                                        case "midnight" -> time = 18000L;
                                        default -> {
                                            try {
                                                time = Long.parseLong(value);
                                            } catch (NumberFormatException ex) {
                                                api.info(sender, "WORLD_INVALID_TIME");
                                                return;
                                            }
                                        }
                                    }
                                    world.setTime(time);
                                    api.info(sender, "WORLD_SET_TIME", "world", world.getName(), "status", value);
                                }
                                case "frozen" -> {
                                    boolean frozen = Boolean.parseBoolean(value);
                                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !frozen);
                                    world.setGameRule(GameRule.DO_WEATHER_CYCLE, !frozen);
                                    api.info(sender, "WORLD_SET_FROZEN", "world", world.getName(), "status", frozen);
                                }
                                case "weather" -> {
                                    switch (value.toLowerCase(Locale.ROOT)) {
                                        case "clear" -> {
                                            world.setStorm(false);
                                            world.setThundering(false);
                                        }
                                        case "rain" -> {
                                            world.setStorm(true);
                                            world.setThundering(false);
                                        }
                                        case "thunder" -> {
                                            world.setStorm(true);
                                            world.setThundering(true);
                                        }
                                        default -> {
                                            api.info(sender, "WORLD_INVALID_WEATHER");
                                            return;
                                        }
                                    }
                                    api.info(sender, "WORLD_SET_WEATHER", "world", world.getName(), "state", value.toLowerCase(Locale.ROOT));
                                }
                                case "no-mob-spawn" -> {
                                    String mode = value.toLowerCase(Locale.ROOT);

                                    // /world flags <world> mobspawning unset
                                    if ("unset".equals(mode)) {
                                        plugin.config().set("worlds." + world.getName() + ".no-mob-spawn", null);
                                        plugin.configSave();
                                        // return control to vanilla game rules
                                        world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
                                        api.info(sender, "WORLD_SET_MOB_SPAWNING", "world", world.getName(), "state", "unset");
                                        return;
                                    }

                                    // Normalize: anything unknown becomes "animals"
                                    if (!"all".equals(mode) && !"mobs".equals(mode) && !"animals".equals(mode)) {
                                        mode = "animals";
                                    }

                                    // Persist
                                    plugin.config().set("worlds." + world.getName() + ".no-mob-spawn", mode);
                                    plugin.configSave();

                                    // Reduce load where possible
                                    if ("all".equals(mode)) {
                                        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                                    } else {
                                        world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
                                    }

                                    purgeDeniedEntities(world, mode);

                                    api.info(sender, "WORLD_SET_MOB_SPAWNING", "world", world.getName(), "state", mode);
                                }
                                case "tickspeed" -> {
                                    if (value.equalsIgnoreCase("reset")) {
                                        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3); // default
                                        api.info(sender, "WORLD_RESET_TICKSPEED", "world", world.getName());
                                        return;
                                    }

                                    int speed;
                                    try {
                                        speed = Integer.parseInt(value);
                                    } catch (NumberFormatException ex) {
                                        api.error(sender, "WORLD_INVALID_TICKSPEED");
                                        return;
                                    }

                                    world.setGameRule(GameRule.RANDOM_TICK_SPEED, speed);
                                    api.info(sender, "WORLD_SET_TICKSPEED", "world", world.getName(), "speed", String.valueOf(speed));
                                }
                                case "no-damage" -> {
                                    boolean enabled = Boolean.parseBoolean(value);
                                    plugin.config().set("worlds." + world.getName() + ".flags.no-damage", enabled);
                                    plugin.configSave();
                                    api.info(sender, "WORLD_SET_NO_DAMAGE",
                                            "world", world.getName(),
                                            "state", String.valueOf(enabled));

                                    world.getPlayers().forEach(p -> {
                                        p.setHealth(20);
                                    });
                                }
                                case "no-hunger" -> {
                                    boolean enabled = Boolean.parseBoolean(value);
                                    plugin.config().set("worlds." + world.getName() + ".flags.no-hunger", enabled);
                                    plugin.configSave();
                                    api.info(sender, "WORLD_SET_NO_HUNGER",
                                            "world", world.getName(),
                                            "state", String.valueOf(enabled));

                                    world.getPlayers().forEach(p -> {
                                        p.setFoodLevel(20);
                                    });
                                }
                                case "force-spawn-on-death" -> {
                                    boolean enabled = Boolean.parseBoolean(value);
                                    plugin.config().set("worlds." + world.getName() + ".flags.force-spawn-on-death", enabled);
                                    plugin.configSave();
                                    api.info(sender, "WORLD_SET_FORCE_SPAWN_ON_DEATH",
                                            "world", world.getName(),
                                            "state", String.valueOf(enabled));
                                }
                                default -> api.info(sender, "WORLD_INVALID_FLAG");
                            }
                        }
                        case "gamemode" -> {
                            if (args.size() < 2) {
                                api.info(sender, "WORLD_COMMAND_USAGE_GAMEMODE");
                                return;
                            }

                            String worldName = args.get(1);
                            World world = Bukkit.getWorld(worldName);
                            if (world == null) {
                                api.info(sender, "WORLD_NOT_FOUND", "world", worldName);
                                return;
                            }

                            if (args.size() > 2) {
                                String value = args.get(2);

                                // /world gamemode <world> unset
                                if ("unset".equalsIgnoreCase(value)) {
                                    setWorldGameMode(worldName, null);
                                    api.info(sender, "WORLD_GAMEMODE_UNSET", "world", worldName);
                                    return;
                                }

                                // /world gamemode <world> <gamemode>
                                GameMode mode;
                                try {
                                    mode = GameMode.valueOf(value.toUpperCase(Locale.ROOT));
                                } catch (IllegalArgumentException ex) {
                                    api.info(sender, "WORLD_INVALID_GAMEMODE", "gamemode", value);
                                    return;
                                }

                                setWorldGameMode(worldName, mode);
                                api.info(sender, "WORLD_SET_GAMEMODE", "world", worldName, "gamemode", mode.name().toLowerCase(Locale.ROOT));

                                world.getPlayers().forEach(p -> {
                                    p.setGameMode(mode);
                                    api.info(p, "WORLD_GAMEMODE_CHANGED_BY_WORLD_SETTING", "world", worldName, "gamemode", mode.name().toLowerCase(Locale.ROOT));
                                });
                            } else {
                                GameMode gameMode = getWorldGameMode(worldName);
                                String gameModeString = "unset";

                                if (gameMode != null) {
                                    gameModeString = gameMode.name().toLowerCase(Locale.ROOT);
                                }

                                api.info(sender, "WORLD_GET_GAMEMODE", "world", worldName, "gamemode", gameModeString);
                            }
                        }
                        case "setspawn" -> {
                            ctx.checkNotConsole();

                            Player player = ctx.getSenderAsPlayer();
                            World world = player.getWorld();

                            world.setSpawnLocation(player.getLocation());
                            ctx.returnInfo("WORLD_SPAWN_UPDATED",
                                    "world", world.getName());
                        }
                        default -> {
                            handleUnknownSubCommand(cmd, ctx);
                        }
                    }
                })
                .register(plugin);
    }

    public void onDisable() {
        // Nothing to clean up
    }

    /**
     * Register a sub-command handler
     */
    public void addSubCommand(String subCommand, WorldBaseSettingImpl handler) {
        subCommandRegistry.put(subCommand.toLowerCase(Locale.ROOT), handler);
    }

    /**
     * Register a flag-command handler
     */
    public WorldSettingCommand addFlagCommand(String flagCommand) {
        return new WorldSettingCommandImpl(flagCommand.toLowerCase(Locale.ROOT), true);
    }

    /**
     * Register tab completions for the world command
     */
    public void addTabCompletion(String flag, String... completions) {
        command.tabCompletion(completions);
    }


    /**
     * Handle unknown sub-commands by delegating to registered handlers
     */
    private void handleUnknownSubCommand(Command cmd, CommandContext ctx) {
        String subCommand = ctx.getArg(1, "").toLowerCase(Locale.ROOT);

        WorldBaseSettingImpl handler = subCommandRegistry.get(subCommand);
        if (handler != null) {
            handler.onCommand(cmd, ctx);
        } else {
            ctx.returnUsage();
        }
    }
}
