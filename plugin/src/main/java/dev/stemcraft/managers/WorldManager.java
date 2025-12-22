package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.factories.ChunkGeneratorFactory;
import dev.stemcraft.api.services.WorldService;
import dev.stemcraft.api.services.task.TaskService;
import dev.stemcraft.api.utils.SCPlayer;
import dev.stemcraft.api.utils.chatmenu.SCChatMenu;
import dev.stemcraft.api.utils.SCString;
import dev.stemcraft.events.WorldDeleteEvent;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventPriority;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager implements WorldService {
    private final STEMCraft plugin;
    private final Map<String, ChunkGeneratorFactory> registry = new ConcurrentHashMap<>();

    private final List<World> recordActive = new ArrayList<>();
    private final Map<World,RecordedWorldState> recordState = new HashMap<>();

    @Getter
    @Setter
    private World defaultWorld;

    public WorldManager(STEMCraft plugin) {
        this.plugin = plugin;
        this.defaultWorld = Bukkit.getWorlds().getFirst();
    }

    public void onEnable() {
        // Load worlds
        ConfigurationSection worldsSection = plugin.config().getConfigurationSection("worlds");
        Set<String> configuredWorlds = new HashSet<>();
        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                if(worldExists(worldName)) {
                    configuredWorlds.add(worldName);
                } else {
                    plugin.warn("WORLD_CONFIG_WORLD_NOT_EXIST", "world", worldName);
                }
            }
        }

        List<String> discoveredWorlds = listWorlds();
        for (String worldName : discoveredWorlds) {
            World existing = Bukkit.getWorld(worldName);
            if (existing != null) {
                plugin.log("WORLD_CONFIG_LOADED_BY_SERVER", "world", worldName);
                applyWorldSettings(existing);
                if (existing.getEnvironment() == World.Environment.NORMAL) {
                    ensureDimensionWorlds(existing.getName());
                }
            } else {
                if (configuredWorlds.contains(worldName)) {
                    boolean load = worldsSection.getBoolean(worldName + ".load", false);
                    if (load) {
                        World world = loadWorld(worldName);
                        if (world != null) {
                            applyWorldSettings(world);
                            if (world.getEnvironment() == World.Environment.NORMAL) {
                                ensureDimensionWorlds(world.getName());
                            }
                        }
                        plugin.log("WORLD_CONFIG_LOADED", "world", worldName);
                    }
                } else {
                    plugin.log("WORLD_CONFIG_UNLOADED", "world", worldName);
                }
            }
        }
        // Portal routing: ensure each overworld uses its own _nether and _the_end companions.
        plugin.registerEvent(PlayerPortalEvent.class, event -> {
            var cause = event.getCause();
            if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                    && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;

            Location from = event.getFrom();
            World fromWorld = from.getWorld();
            if (fromWorld == null) return;

            String base = baseWorldName(fromWorld.getName());
            if (base == null || base.isEmpty()) return;

            String targetName;
            if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
                if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                    targetName = base + "_nether";
                } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
                    targetName = base;
                } else {
                    return;
                }
            } else {
                // END_PORTAL
                if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                    targetName = base + "_the_end";
                } else if (fromWorld.getEnvironment() == World.Environment.THE_END) {
                    targetName = base;
                } else {
                    return;
                }
            }

            World targetWorld = Bukkit.getWorld(targetName);
            if (targetWorld == null) {
                // load if it exists on disk/config
                if (worldExists(targetName) || Files.isDirectory(worldRoot(targetName))) {
                    targetWorld = loadWorld(targetName);
                }
            }
            if (targetWorld == null) return;

            Location to = from.clone();
            to.setWorld(targetWorld);

            // Nether coordinate scaling
            if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
                if (fromWorld.getEnvironment() == World.Environment.NORMAL
                        && targetWorld.getEnvironment() == World.Environment.NETHER) {
                    to.setX(from.getX() / 8.0);
                    to.setZ(from.getZ() / 8.0);
                } else if (fromWorld.getEnvironment() == World.Environment.NETHER
                        && targetWorld.getEnvironment() == World.Environment.NORMAL) {
                    to.setX(from.getX() * 8.0);
                    to.setZ(from.getZ() * 8.0);
                }
            }

            event.setTo(to);
        }, EventPriority.HIGHEST, true);

        plugin.tabCompleteService().register("world-offline", (player, args) -> {
            List<String> suggestions = new ArrayList<>();
            for (String worldName : listWorlds()) {
                if (isWorldLoaded(worldName)) continue; // only offline
                suggestions.add(worldName);
            }

            return suggestions;
        });

        plugin.tabCompleteService().register("world-generators", (player, args) -> new ArrayList<>(registry.keySet()));

        plugin.registerCommand("world")
                .setDescription("WORLD_DESCRIPTION")
                .setPermission("stemcraft.command.world")
                .addTabCompletion("record")
                .addTabCompletion("create", "", "{world-generators}")
                .addTabCompletion("delete", "{world}")
                .addTabCompletion("load", "{world-offline}")
                .addTabCompletion("list")
                .addTabCompletion("duplicate")
                .addTabCompletion("listgenerators")
                .addTabCompletion("flags")
                .addTabCompletion("weather", "{world}")
                .addTabCompletion("weather", "{world}", "clear")
                .addTabCompletion("weather", "{world}", "rain")
                .addTabCompletion("weather", "{world}", "thunder")
                .addTabCompletion("weather", "{world}", "clear", "always")
                .addTabCompletion("weather", "{world}", "rain", "always")
                .addTabCompletion("weather", "{world}", "thunder", "always")
                .addTabCompletion("time", "{world}")
                .addTabCompletion("time", "{world}", "day")
                .addTabCompletion("time", "{world}", "noon")
                .addTabCompletion("time", "{world}", "night")
                .addTabCompletion("time", "{world}", "midnight")
                .addTabCompletion("time", "{world}", "day", "always")
                .addTabCompletion("time", "{world}", "noon", "always")
                .addTabCompletion("time", "{world}", "night", "always")
                .addTabCompletion("time", "{world}", "midnight", "always")
                .addTabCompletion("flags", "{world}")
                .addTabCompletion("flags", "{world}", "no-damage", "true")
                .addTabCompletion("flags", "{world}", "no-damage", "false")
                .addTabCompletion("flags", "{world}", "no-hunger", "true")
                .addTabCompletion("flags", "{world}", "no-hunger", "false")
                .addTabCompletion("flags", "{world}", "force-spawn-on-death", "true")
                .addTabCompletion("flags", "{world}", "force-spawn-on-death", "false")
                .addTabCompletion("gamemode", "{world}", "{gamemode}")
                .addTabCompletion("gamemode", "{world}", "unset")
                .addTabCompletion("flags", "{world}", "no-mob-spawn", "unset")
                .addTabCompletion("flags", "{world}", "no-mob-spawn", "all")
                .addTabCompletion("flags", "{world}", "no-mob-spawn", "mobs")
                .addTabCompletion("flags", "{world}", "no-mob-spawn", "animals")
                .addTabCompletion("flags", "{world}", "tickspeed", "{int}")
                .addTabCompletion("flags", "{world}", "tickspeed", "reset")
                .addTabCompletion("setspawn")
                .setUsage("WORLD_COMMAND_USAGE")
                .setExecutor((api, cmd, ctx) -> {
                    var sender = ctx.getSender();
                    var args = ctx.args();

                    if (args.isEmpty()) {
                        api.info(sender, cmd.getUsage());
                        return;
                    }

                    String sub = args.getFirst().toLowerCase(Locale.ROOT);

                    switch (sub) {
                        case "weather" -> {
                            if (args.size() < 2) {
                                api.info(sender, "WORLD_COMMAND_USAGE_WEATHER");
                                return;
                            }

                            String worldName = args.get(1);
                            World world = Bukkit.getWorld(worldName);
                            if (world == null) {
                                api.info(sender, "WORLD_NOT_FOUND", "world", worldName);
                                return;
                            }

                            // /world weather <world>  -> show current
                            if (args.size() == 2) {
                                String current;
                                if (!world.hasStorm() && !world.isThundering()) {
                                    current = "clear";
                                } else if (world.hasStorm() && !world.isThundering()) {
                                    current = "rain";
                                } else {
                                    current = "thunder";
                                }

                                boolean always = plugin.config().getBoolean("worlds." + worldName + ".weather.always", false);
                                api.info(sender, "WORLD_WEATHER_STATUS",
                                        "world", worldName,
                                        "weather", current,
                                        "always", String.valueOf(always));
                                return;
                            }

                            String type = args.get(2).toLowerCase(Locale.ROOT);

                            // /world weather <world> unset
                            if ("unset".equals(type)) {
                                plugin.config().set("worlds." + worldName + ".weather.state", null);
                                plugin.config().set("worlds." + worldName + ".weather.always", null);
                                plugin.configSave();
                                // return weather cycle control to the game rules
                                world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
                                api.info(sender, "WORLD_WEATHER_UNSET", "world", worldName);
                                return;
                            }

                            boolean always = args.size() >= 4 && "always".equalsIgnoreCase(args.get(3));

                            switch (type) {
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

                            if (always) {
                                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                            }

                            plugin.config().set("worlds." + worldName + ".weather.state", type);
                            plugin.config().set("worlds." + worldName + ".weather.always", always);
                            plugin.configSave();

                            api.info(sender, "WORLD_SET_WEATHER",
                                    "world", worldName,
                                    "state", type,
                                    "always", String.valueOf(always));
                        }
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
                            if(always) {
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

                            if(world != null && world.equals(defaultWorld)) {
                                api.info(sender, "WORLD_DELETE_DEFAULT_DENY", "world", name);
                                return;
                            }

                            if(!worldExists(name) || world == null) {
                                api.info(sender, "WORLD_NOT_FOUND", "world", name);
                                return;
                            }

                            if (isWorldLoaded(name)) {
                                if(!world.getPlayers().isEmpty()) {
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

                            SCChatMenu.render(
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

                                            if(isPlayer) {
                                                line = line.append(Component.text(" "))
                                                        .append(loadToggle)
                                                        .append(Component.text(" "))
                                                        .append(delete);
                                            }

                                            lines.add(line);
                                        }

                                        return lines;                                    },
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
                                    if(value.equalsIgnoreCase("reset")) {
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

                            if(args.size() > 2) {
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

                                if(gameMode != null) {
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
                        default -> api.info(sender, cmd.getUsage());
                    }
                })
                .register(plugin);

        // Active mob-spawn filtering per world (mobspawning flag)
        plugin.registerEvent(CreatureSpawnEvent.class, event -> {
            Location loc = event.getLocation();
            World world = loc.getWorld();
            if (world == null) return;

            String mode = plugin.config().getString("worlds." + world.getName() + ".no-mob-spawn", "unset");
            if (mode == null) mode = "unset";
            mode = mode.toLowerCase(Locale.ROOT);

            if ("unset".equals(mode)) return;

            // Normalize unknown values to "animals"
            if (!"all".equals(mode) && !"mobs".equals(mode) && !"animals".equals(mode)) {
                mode = "all";
            }

            // animals: allow non-hostiles, block hostiles
            if ("animals".equals(mode)) {
                if (!(event.getEntity() instanceof org.bukkit.entity.Monster)) {
                    event.setCancelled(true);
                }
            }

            // mobs: allow hostiles (Monster), block non-hostiles
            if ("mobs".equals(mode)) {
                if (event.getEntity() instanceof Monster) {
                    event.setCancelled(true);
                }
                return;
            }

            // none: block all creature spawns
            if ("all".equals(mode)) {
                event.setCancelled(true);
                return;
            }
        });

        // World State Recording
        plugin.registerEvent(BlockBreakEvent.class, event -> {
            capture(event.getBlock());
        });

        plugin.registerEvent(BlockPlaceEvent.class, event -> {
            World world = event.getBlock().getWorld();
            if (!isCapturing(world)) return;

            if (event instanceof BlockMultiPlaceEvent multi) {
                for (BlockState replaced : multi.getReplacedBlockStates()) {
                    capture(replaced);
                }
            } else {
                capture(event.getBlockReplacedState());
            }

            // Crude fix for Aikar's hopper patch on Paper
            Block placed = event.getBlock();
            if (placed.getType() == Material.HOPPER) {
                Block above = placed.getRelative(BlockFace.UP);
                BlockState aboveState = above.getState();
                if (aboveState instanceof Container || aboveState instanceof Campfire) {
                    // This captures the real contents before any hopper tick
                    capture(aboveState);
                }
            }
        });

        plugin.registerEvent(BlockBurnEvent.class, event -> {
            capture(event.getBlock());
        });

        plugin.registerEvent(BlockIgniteEvent.class, event -> {
            capture(event.getBlock());
        });

        plugin.registerEvent(BlockExplodeEvent.class, event -> {
            for (Block block : event.blockList()) {
                capture(block);
            }
        });

        plugin.registerEvent(EntityExplodeEvent.class, event -> {
            for (Block block : event.blockList()) {
                capture(block);
            }
        });

        plugin.registerEvent(BlockFromToEvent.class, event -> {
            capture(event.getToBlock());
        });

        plugin.registerEvent(BlockFadeEvent.class, event -> {
            capture(event.getBlock());
        });

        plugin.registerEvent(BlockFormEvent.class, event -> {
            // Snow, ice, etc
            capture(event.getBlock());
        });

        plugin.registerEvent(BlockSpreadEvent.class, event -> {
            capture(event.getBlock());
        });

        plugin.registerEvent(LeavesDecayEvent.class, event -> {
            capture(event.getBlock());
        });

        plugin.registerEvent(StructureGrowEvent.class, event -> {
            // Trees etc
            for (org.bukkit.block.BlockState state : event.getBlocks()) {
                capture(state.getBlock());
            }
        });

        plugin.registerEvent(EntityChangeBlockEvent.class, event -> {
            if (event.getEntityType() == EntityType.ENDERMAN
                    || event.getEntityType() == EntityType.FALLING_BLOCK
                    || event.getEntityType() == EntityType.SILVERFISH) {
                capture(event.getBlock());
            }
        });

        plugin.registerEvent(PlayerInteractEvent.class, event -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            Block block = event.getClickedBlock();
            if (block == null) return;

            BlockData data = block.getBlockData();

            // Doors, trapdoors, fence gates
            if (data instanceof Openable || data instanceof Campfire) {
                capture(block);
            }
        });

        plugin.registerEvent(PlayerBucketEmptyEvent.class, event -> {
            capture(event.getBlockClicked().getRelative(event.getBlockFace()));
        });

        plugin.registerEvent(ItemSpawnEvent.class, event -> {
            World world = event.getLocation().getWorld();
            if (!isCapturing(world)) return;

            capture(event.getEntity());
        });

        plugin.registerEvent(EntitySpawnEvent.class, event -> {
            World world = event.getLocation().getWorld();
            if (!isCapturing(world)) return;

            if (isTemporaryEntity(event.getEntityType())) {
                capture(event.getEntity());
            }
        });

        plugin.registerEvent(EntityPlaceEvent.class, event -> {
            World world = event.getEntity().getWorld();
            if (!isCapturing(world)) return;

            if (isTemporaryEntity(event.getEntityType())) {
                capture(event.getEntity());
            }
        });

        plugin.registerEvent(SpongeAbsorbEvent.class, event -> {
            if (!isCapturing(event.getBlock().getWorld())) return;

            World world = event.getBlock().getWorld();

            for (BlockState pending : event.getBlocks()) {
                // World is still in previous state here, so this is the *water* snapshot
                Block liveBlock = world.getBlockAt(pending.getX(), pending.getY(), pending.getZ());
                capture(liveBlock.getState());
            }
        });

        plugin.registerEvent(InventoryOpenEvent.class, event -> {
            if (!isCapturing(event.getPlayer().getWorld())) return;

            // snapshot the top inventory's container if it is block-based (chest, barrel, etc)
            recordInventoryContainer(event.getView().getTopInventory());
        });

        plugin.registerEvent(InventoryMoveItemEvent.class, event -> {
            // hopper world
            if (!isCapturing(event.getSource().getLocation().getWorld())) return;

            // record source container (if block-backed)
            recordInventoryContainer(event.getSource());
            // record destination container (if block-backed)
            recordInventoryContainer(event.getDestination());
        });

        plugin.registerEvent(InventoryClickEvent.class, event -> {
            if (!isCapturing(event.getWhoClicked().getWorld())) return;

            // top inventory is the container UI (chest, barrel, etc)
            recordInventoryContainer(event.getView().getTopInventory());
        });

        plugin.registerEvent(InventoryDragEvent.class, event -> {
            if (!isCapturing(event.getWhoClicked().getWorld())) return;

            recordInventoryContainer(event.getView().getTopInventory());
        });

        plugin.registerEvent(BlockCookEvent.class, event -> {
            if (!isCapturing(event.getBlock().getWorld())) return;

            // First cook tick after recording starts will snapshot this campfire/furnace
            capture(event.getBlock().getState());
        });

        plugin.registerEvent(BlockPistonExtendEvent.class, event -> {
            World world = event.getBlock().getWorld();
            if (!isCapturing(world)) return;

            // Record the piston base before it changes state
            capture(event.getBlock());

            // Record all blocks that are about to be moved by the piston
            for (Block moved : event.getBlocks()) {
                capture(moved);
            }

            // Record the block in front where the piston head will appear
            Block front = event.getBlock().getRelative(event.getDirection(), event.getBlocks().size() + 1);
            capture(front);
        });

        plugin.registerEvent(BlockPistonRetractEvent.class, event -> {
            World world = event.getBlock().getWorld();
            if (!isCapturing(world)) return;

            // Record the piston base before it retracts
            capture(event.getBlock());

            // Record all blocks that are about to be moved back by the piston (sticky)
            for (Block moved : event.getBlocks()) {
                capture(moved);
            }

            // Record the block directly in front of the piston where the head will disappear from
            Block front = event.getBlock().getRelative(event.getDirection(), 1);
            capture(front);
        });

        plugin.registerEvent(PlayerTeleportEvent.class, event -> {
            Player player = event.getPlayer();
            Location to = event.getTo();

            World world = to.getWorld();
            if (world == null) return;

            GameMode mode = getWorldGameMode(world);
            if (mode != null) player.setGameMode(mode);
        });

        // Respawn rules:
        // - Default: let Minecraft handle bed/anchor respawns.
        // - If no bed/anchor and Minecraft would respawn the player in a different world,
        //   respawn them at the death world's spawn.
        // - Optional per-world override: force respawn at world spawn even if bed/anchor exists.
        plugin.registerEvent(PlayerRespawnEvent.class, event -> {
            Player player = event.getPlayer();

            World deathWorld = player.getWorld();
            if (deathWorld == null) return;

            boolean forceWorldSpawn = plugin.config().getBoolean(
                    "worlds." + deathWorld.getName() + ".flags.force-spawn-on-death",
                    false
            );

            // If configured, always force the death world's spawn (even if bed/anchor exists)
            if (forceWorldSpawn) {
                Location spawn = deathWorld.getSpawnLocation();
                event.setRespawnLocation(spawn);
                return;
            }

            // Vanilla respawn uses the player's bed across *any* world. In a multi-world server
            // this can send players to an unrelated world. Keep respawns inside this world's set.
            Location target = event.getRespawnLocation();
            World targetWorld = (target == null ? null : target.getWorld());

            String base = baseWorldName(deathWorld.getName());
            if (base == null || base.isEmpty()) return;

            String netherName = base + "_nether";
            String endName = base + "_the_end";

            boolean ok = false;
            if (targetWorld != null) {
                String tn = targetWorld.getName();
                ok = tn.equalsIgnoreCase(base)
                        || tn.equalsIgnoreCase(netherName)
                        || tn.equalsIgnoreCase(endName)
                        || tn.equalsIgnoreCase(base + "_end");
            }

            if (!ok) {
                World overworld = Bukkit.getWorld(base);
                if (overworld != null) {
                    event.setRespawnLocation(overworld.getSpawnLocation());
                } else {
                    // fallback: death world spawn
                    event.setRespawnLocation(deathWorld.getSpawnLocation());
                }
            }
        }, EventPriority.HIGHEST, true);

        plugin.registerEvent(EntityDamageEvent.class, event -> {
            World world = event.getEntity().getWorld();
            if (isWorldNoDamage(world)) {
                event.setCancelled(true);
            }
        });

        plugin.registerEvent(FoodLevelChangeEvent.class, event -> {
            World world = event.getEntity().getWorld();
            if (isWorldNoHunger(world)) {
                event.setCancelled(true);
            }
        });

        plugin.registerEvent(WorldLoadEvent.class, event -> {
            plugin.config().set("worlds." + event.getWorld().getName() + ".load", true);
        }, EventPriority.MONITOR, false);

        plugin.registerEvent(WorldUnloadEvent.class, event -> {
            plugin.config().set("worlds." + event.getWorld().getName() + ".load", null);
        }, EventPriority.MONITOR, false);

        plugin.registerEvent(WorldDeleteEvent.class, event -> {
            plugin.config().set("worlds." + event.getWorldName() + ".load", null);
            plugin.saveConfig();
        }, EventPriority.MONITOR, false);

    }

    // -------- status
    @Override public boolean isWorldLoaded(String name) { return Bukkit.getWorld(name) != null; }
    @Override public boolean worldExists(String name)   { return listWorlds().contains(name); }

    // -------- load / unload
    @Override public World loadWorld(String name) { return ensure(name, null); }
    @Override public boolean unloadWorld(String name, boolean save) {
        World w = Bukkit.getWorld(name);
        if (w == null) return false;

        boolean result = Bukkit.unloadWorld(w, save);
        if (result) {
            plugin.config().set("worlds." + name + ".load", false);
            plugin.saveConfig();
        }

        return result;
    }

    // -------- create
    @Override public World createWorld(String name, String key, String option) { return ensure(name, generatorFor(key, option)); }


    private static String baseWorldName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether")) return name.substring(0, name.length() - "_nether".length());
        if (lower.endsWith("_the_end")) return name.substring(0, name.length() - "_the_end".length());
        if (lower.endsWith("_end")) return name.substring(0, name.length() - "_end".length());
        return name;
    }

    private void ensureDimensionWorlds(String baseName) {
        if (baseName == null || baseName.isEmpty()) return;

        String lower = baseName.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether") || lower.endsWith("_the_end") || lower.endsWith("_end")) return;

        // Vanilla naming for End is _the_end.
        String netherName = baseName + "_nether";
        String endName = baseName + "_the_end";

        if (worldExists(netherName) || Files.isDirectory(worldRoot(netherName))) {
            ensure(netherName, null);
        }
        if (worldExists(endName) || Files.isDirectory(worldRoot(endName))) {
            ensure(endName, null);
        }
    }

    private World.Environment resolveEnv(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether")) return World.Environment.NETHER;
        if (lower.endsWith("_the_end")) return World.Environment.THE_END;
        if (lower.endsWith("_end")) return World.Environment.THE_END;
        return World.Environment.NORMAL;
    }

    private World ensure(String name, ChunkGenerator gen) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;

        World.Environment env = resolveEnv(name);

        WorldCreator wc = new WorldCreator(name).environment(env);
        if (gen != null) wc.generator(gen);

        World world = wc.createWorld();

        if (world != null) {
            plugin.config().set("worlds." + name + ".load", true);
            plugin.configSave();
            applyWorldSettings(world);
            if (world.getEnvironment() == World.Environment.NORMAL) {
                ensureDimensionWorlds(world.getName());
            }
        }

        return world;
    }

    private ChunkGenerator generatorFor(String key, String cfg) {
        if (key == null || key.isEmpty()) return null;
        ChunkGeneratorFactory f = registry.get(key.toLowerCase(Locale.ROOT));
        if (f == null) throw new IllegalArgumentException("Unknown generator key: " + key);
        return f.create(cfg);
    }

    // -------- fs ops (must be unloaded)
    @Override public void deleteWorld(String name) throws IOException {
        requireUnloaded(name);
        Path root = worldRoot(name);
        if (!Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }

        // Remove from config
        ConfigurationSection worldsSection = plugin.config().getConfigurationSection("worlds");
        if (worldsSection != null && worldsSection.contains(name)) {
            worldsSection.set(name, null); // delete the whole section for this world
            plugin.saveConfig();
        }

        Bukkit.getPluginManager().callEvent(new WorldDeleteEvent(name));
    }

    @Override public void renameWorld(String oldName, String newName) throws IOException {
        requireUnloaded(oldName); requireUnloaded(newName);
        Files.move(worldRoot(oldName), worldRoot(newName), StandardCopyOption.ATOMIC_MOVE);
    }

    @Override public void duplicateWorld(String src, String dst) throws IOException {
        requireUnloaded(src); requireUnloaded(dst);
        Path s = worldRoot(src), d = worldRoot(dst);
        if (!Files.exists(s)) throw new IOException("Source world not found: " + src);
        try (var stream = Files.walk(s)) {
            stream.forEach(p -> {
                Path rel = s.relativize(p);
                String rs = rel.toString().replace('\\', '/');
                if (rs.endsWith("uid.dat") || rs.endsWith("session.lock")) return;

                Path out = d.resolve(rel);
                try {
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(out);
                    } else {
                        Files.copy(p, out, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e); // cleaner than RuntimeException
                }
            });
        }
    }

    // -------- discovery
    @Override public List<String> listWorlds() {
        Set<String> names = new LinkedHashSet<>();

        // 1) Loaded worlds
        for (World w : Bukkit.getWorlds()) {
            names.add(w.getName());
        }

        // 2) World folders on disk
        Path container = plugin.getServer().getWorldContainer().toPath();
        try (var ds = Files.newDirectoryStream(container)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) continue;

                boolean isWorld =
                        Files.exists(p.resolve("level.dat")) ||
                                Files.isDirectory(p.resolve("region")) ||
                                Files.isDirectory(p.resolve("playerdata")) ||
                                Files.isDirectory(p.resolve("data")) ||
                                Files.isDirectory(p.resolve("DIM-1").resolve("region")) ||
                                Files.isDirectory(p.resolve("DIM1").resolve("region"));

                if (isWorld) {
                    names.add(p.getFileName().toString());
                }
            }
        } catch (IOException ignored) {}

        List<String> out = new ArrayList<>(names);
        Collections.sort(out);
        return out;
    }

    @Override public Path getWorldFolder(String name) { return worldRoot(name); }

    // -------- registry
    @Override public void registerGenerator(String key, ChunkGeneratorFactory factory) {
        registry.put(key.toLowerCase(Locale.ROOT), factory);
        plugin.log("WORLD_REGISTERED_CHUNK_GENERATOR", "key", key);
    }
    // -------- helpers
    private void requireUnloaded(String name) throws IOException {
        if (isWorldLoaded(name)) throw new IOException("World is loaded: " + name);
    }
    private Path worldRoot(String name) { return plugin.getServer().getWorldContainer().toPath().resolve(name); }

    // -------- world settings helpers
    private void applyWorldSettings(World world) {
        applyWorldWeather(world);
        applyWorldTime(world);
    }

    private void applyWorldWeather(World world) {
        String base = "worlds." + world.getName() + ".weather";
        String state = plugin.config().getString(base + ".state");
        boolean always = plugin.config().getBoolean(base + ".always", false);

        if (state == null || state.isEmpty()) return;

        switch (state.toLowerCase(Locale.ROOT)) {
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
        }

        if (always) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        }
    }

    private void applyWorldTime(World world) {
        String base = "worlds." + world.getName() + ".time";
        long time = plugin.config().getLong(base + ".value", -1L);
        boolean always = plugin.config().getBoolean(base + ".always", false);

        if (time >= 0) {
            world.setTime(time);
        }

        if (always) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }
    }

    private boolean isWorldNoDamage(World world) {
        return plugin.config().getBoolean("worlds." + world.getName() + ".flags.no-damage", false);
    }

    private boolean isWorldNoHunger(World world) {
        return plugin.config().getBoolean("worlds." + world.getName() + ".flags.no-hunger", false);
    }

    // -------- gamemode helpers
    public GameMode getWorldGameMode(World world) {
        return getWorldGameMode(world.getName());
    }

    public GameMode getWorldGameMode(String name) {
        ConfigurationSection worldsSection = plugin.config().getConfigurationSection("worlds");
        if (worldsSection == null) return null;

        String raw = worldsSection.getString(name + ".gamemode");
        if (raw == null || raw.isEmpty()) return null;

        try {
            return GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.warn("WORLD_INVALID_GAMEMODE_CONFIG", "world", name, "gamemode", raw);
            return null;
        }
    }

    public void setWorldGameMode(World world, GameMode mode) {
        setWorldGameMode(world.getName(), mode);
    }

    public void setWorldGameMode(String name, GameMode mode) {
        if (mode == null) {
            plugin.config().set("worlds." + name + ".gamemode", null);
        } else {
            plugin.config().set("worlds." + name + ".gamemode", mode.name());
        }
        plugin.configSave();
    }

    static class RecordedWorldState {
        final Map<String,RecordedBlockState> blockStateMap = new HashMap<String, RecordedBlockState>();
        final Set<UUID> spawnedEntities = new HashSet<>();

        void recordEntity(Entity e) {
            spawnedEntities.add(e.getUniqueId());
        }

        public void recordBlock(BlockState state) {
            String locString = state.getX() + "," + state.getY() + "," + state.getZ();
            if (!blockStateMap.containsKey(locString)) {
                blockStateMap.put(locString, new RecordedBlockState(state));
            }
        }

        void save(ConfigurationSection section) {
            ConfigurationSection blocksSection = section.createSection("blocks");
            for (Map.Entry<String, RecordedBlockState> entry : blockStateMap.entrySet()) {
                ConfigurationSection bs = blocksSection.createSection(entry.getKey());
                entry.getValue().save(bs);
            }
        }

        static RecordedWorldState load(ConfigurationSection section) {
            RecordedWorldState worldState = new RecordedWorldState();
            ConfigurationSection blocksSection = section.getConfigurationSection("blocks");
            if (blocksSection != null) {
                for (String key : blocksSection.getKeys(false)) {
                    ConfigurationSection bs = blocksSection.getConfigurationSection(key);
                    if (bs == null) continue;
                    RecordedBlockState rbs = RecordedBlockState.load(bs);
                    worldState.blockStateMap.put(key, rbs);
                }
            }
            return worldState;
        }

        public void rollback(World world) {
            // remove tracked entities
            for (UUID id : spawnedEntities) {
                Entity e = world.getEntity(id);
                if (e != null && !e.isDead()) {
                    e.remove();
                }
            }
            spawnedEntities.clear();

            // rollback blocks
            Iterator<Map.Entry<String, RecordedBlockState>> it = blockStateMap.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, RecordedBlockState> entry = it.next();

                String[] locParts = entry.getKey().split(",");
                if (locParts.length == 3) {
                    int x = Integer.parseInt(locParts[0]);
                    int y = Integer.parseInt(locParts[1]);
                    int z = Integer.parseInt(locParts[2]);

                    int cx = x >> 4;
                    int cz = z >> 4;

                    // Load chunk if needed
                    if (!world.isChunkLoaded(cx, cz)) {
                        world.getChunkAt(cx, cz); // loads the chunk
                    }

                    Location loc = new Location(world, x, y, z);
                    entry.getValue().restore(loc, false);
                }

                it.remove(); // clears as we go
            }
        }
    }

    static class RecordedBlockState {
        final Material type;
        final String data;
        ItemStack[] inventoryContents;

        RecordedBlockState(Block block) {
            type = block.getType();
            data = block.getBlockData().getAsString();
            if (block.getState() instanceof Container container) {
                this.inventoryContents = container.getInventory().getContents();
            }
        }

        RecordedBlockState(Material type, String data, ItemStack[] inventoryContents) {
            this.type = type;
            this.data = data;
            this.inventoryContents = inventoryContents;
        }

        RecordedBlockState(BlockState state) {
            type = state.getType();
            data = state.getBlockData().getAsString();

            if (state instanceof Container container) {
                ItemStack[] contents = container.getInventory().getContents();
                inventoryContents = Arrays.stream(contents)
                        .map(item -> item == null ? null : item.clone())
                        .toArray(ItemStack[]::new);
            } else if (state instanceof Campfire campfire) {
                int size = campfire.getSize();
                inventoryContents = new ItemStack[size];
                for (int i = 0; i < size; i++) {
                    ItemStack item = campfire.getItem(i);
                    inventoryContents[i] = (item == null ? null : item.clone());
                }
            }
        }

        void save(ConfigurationSection section) {
            section.set("type", type.name());
            section.set("data", data);
            if (inventoryContents != null) {
                section.set("inventory", Arrays.asList(inventoryContents)); // ItemStack is serialisable
            }
        }

        static RecordedBlockState load(ConfigurationSection section) {
            String typeName = section.getString("type");
            Material type = typeName != null ? Material.matchMaterial(typeName) : Material.AIR;
            String data = section.getString("data", "minecraft:air");

            List<?> list = section.getList("inventory");
            ItemStack[] inventory = null;
            if (list != null) {
                inventory = list.stream()
                        .map(o -> (ItemStack) o)
                        .toArray(ItemStack[]::new);
            }
            return new RecordedBlockState(type, data, inventory);
        }

        public void restore(Location location, boolean applyPhysics) {
            Block block = location.getBlock();

            // Just restore what we recorded
            block.setType(this.type, applyPhysics);
            BlockData data = Bukkit.createBlockData(this.data);
            block.setBlockData(data, applyPhysics);

            if (inventoryContents != null) {
                org.bukkit.block.BlockState state = block.getState();
                if (state instanceof org.bukkit.block.Container container) {
                    int invSize = container.getInventory().getSize();
                    ItemStack[] toApply = new ItemStack[invSize];
                    int copyLen = Math.min(invSize, inventoryContents.length);
                    System.arraycopy(inventoryContents, 0, toApply, 0, copyLen);

                    container.getInventory().clear();
                    container.getInventory().setContents(toApply);
                } else if (state instanceof Campfire campfire) {
                    int size = campfire.getSize();
                    for (int i = 0; i < size; i++) {
                        ItemStack item = (i < inventoryContents.length ? inventoryContents[i] : null);
                        campfire.setItem(i, item == null ? null : item.clone());
                    }
                    campfire.update(true, applyPhysics);
                }
            }
        }
    }

    @Override
    public boolean isCapturing(World world) {
        return recordActive.contains(world);
    }

    @Override
    public void captureStart(World world) {
        if(!isCapturing(world)) {
            recordActive.add(world);
            recordState.putIfAbsent(world, new RecordedWorldState());
        }
    }

    @Override
    public void captureStop(World world) {
        recordActive.remove(world);
    }

    @Override
    public void captureRollback(World world) {
        if(isCapturing(world)) {
            recordState.get(world).rollback(world);
        }
    }

    @Override
    public void captureReset(World world) {
        if(isCapturing(world)) {
            recordState.put(world, new RecordedWorldState());
        }
    }

    @Override
    public void capture(BlockState state) {
        World world = state.getWorld();
        if (!isCapturing(world)) return;

        RecordedWorldState worldState = recordState.get(world);

        // Always record the original state at this position
        worldState.recordBlock(state);

        Material type = state.getType();
        Block block = state.getBlock();
        BlockData data = state.getBlockData();

        // Doors (two vertical blocks)
        if (isDoor(type) && data instanceof org.bukkit.block.data.type.Door door) {
            Block other = (door.getHalf() == org.bukkit.block.data.type.Door.Half.TOP)
                    ? block.getRelative(org.bukkit.block.BlockFace.DOWN)
                    : block.getRelative(org.bukkit.block.BlockFace.UP);
            worldState.recordBlock(other.getState()); // snapshot partner's *old* state
        }

        // Beds (two horizontal blocks)
        if (isBed(type) && data instanceof org.bukkit.block.data.type.Bed bed) {
            Block other = (bed.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD)
                    ? block.getRelative(bed.getFacing().getOppositeFace())
                    : block.getRelative(bed.getFacing());
            worldState.recordBlock(other.getState());
        }

        // Chests (double chest – record any neighbouring chest halves too)
        if (isChest(type)) {
            for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[] {
                    org.bukkit.block.BlockFace.NORTH,
                    org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST,
                    org.bukkit.block.BlockFace.WEST
            }) {
                Block other = block.getRelative(face);
                if (!isChest(other.getType())) continue;

                worldState.recordBlock(other.getState());
            }
        }
    }

    @Override
    public void capture(Entity entity) {
        World world = entity.getWorld();
        if (!isCapturing(world)) return;

        recordState.get(world).recordEntity(entity);
    }

    private boolean isTemporaryEntity(EntityType type) {
        String name = type.name();

        // Minecarts
        if (name.contains("MINECART")) return true;

        // Boats and rafts
        if (name.contains("BOAT") || name.contains("RAFT")) return true;

        // Projectiles, drops, misc
        return switch (type) {
            case ARROW, SPECTRAL_ARROW, TRIDENT,
                 FALLING_BLOCK, TNT,
                 EXPERIENCE_ORB -> true;
            default -> false;
        };
    }

    private static boolean isDoor(Material type) {
        return type != null && type.name().endsWith("_DOOR");
    }

    private static boolean isBed(Material type) {
        return type != null && type.name().endsWith("_BED");
    }

    private static boolean isChest(Material type) {
        return type != null && type.name().endsWith("CHEST");
    }

    private void recordInventoryContainer(org.bukkit.inventory.Inventory inv) {
        InventoryHolder holder = inv.getHolder();

        Block block = inv.getLocation().getBlock();
        String type = block.getType().toString();
        String loc = block.getX() + "," + block.getY() + "," + block.getZ();

        // Prefer the real container inventory for logging, not the event snapshot
        String items;
        BlockState blockState = block.getState();
        if (blockState instanceof Container container) {
            SCString.toString(container.getInventory());
        } else {
            SCString.toString(inv);
        }

        // Single chest / barrel / etc
        if (holder instanceof BlockState state) {
            capture(state); // snapshots once per location
            return;
        }

        // Double chest
        if (holder instanceof org.bukkit.block.DoubleChest dc) {
            InventoryHolder left = dc.getLeftSide();
            InventoryHolder right = dc.getRightSide();

            if (left instanceof BlockState ls) {
                capture(ls);
            }
            if (right instanceof BlockState rs) {
                capture(rs);
            }
        }
    }

    public void evictAllPlayers(World world) {
        World firstWorld = Bukkit.getWorlds().getFirst();

        if (world.equals(firstWorld)) {
            throw new IllegalStateException("Cannot evict players from the main world");
        }

        world.getPlayers().forEach(player -> {
            plugin.messengerService().info(player, "WORLD_EVICTED", "world", world.getName());
            SCPlayer.teleport(player, defaultWorld.getSpawnLocation());
        });
    }

    private int purgeDeniedEntities(World world, String noMobSpawnMode) {
        if (world == null || noMobSpawnMode == null) return 0;

        String mode = noMobSpawnMode.toLowerCase(Locale.ROOT);
        int removed = 0;

        for (LivingEntity ent : world.getLivingEntities()) {
            // never touch players or armor stands
            if (ent instanceof Player) continue;
            if (ent instanceof ArmorStand) continue;

            boolean deny = false;

            // all = deny everything living (except players/armor stands)
            if ("all".equals(mode)) deny = true;

            // mobs = deny hostiles (Monsters)
            if ("mobs".equals(mode)) deny = (ent instanceof Monster);

            // animals = deny passive/utility life
            if ("animals".equals(mode)) {
                deny = (ent instanceof Animals)
                        || (ent instanceof WaterMob)
                        || (ent instanceof Ambient)
                        || (ent instanceof AbstractVillager);
            }

            if (deny) {
                ent.remove();
                removed++;
            }
        }

        return removed;
    }
}
