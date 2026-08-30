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

package dev.stemcraft.feature;

import dev.stemcraft.api.service.playerreset.*;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.protection.ProtectionRequest;
import dev.stemcraft.api.service.protection.ProtectionType;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.TeleportContext;
import dev.stemcraft.api.util.TeleportOptions;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.WorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.time.Duration;
import java.util.List;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Feature that provides various teleportation utilities such as /tpall, /tphere, /back, /warp, /setwarp, /delwarp, /spawn, /tpworld, /top, /jump, and /thru.
 */
public class TeleportUtils extends BaseFeature {
    private static final long DAMAGE_PROTECTION_MILLIS = 10_000L;

    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, WorldLastLocation>> worldLastLocations = new ConcurrentHashMap<>();
    private final Map<String, Location> warps = new ConcurrentHashMap<>();

    private record WorldLastLocation(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            long updatedAt
    ) { }

    /**
     * Constructor.
     *
     * @param api The STEMCraft API instance.
     */
    public TeleportUtils(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is being enabled.
     */
    @Override
    public void onEnable() {
        loadWarpsFromConfig();
        ensureBackLocationStorage();
        loadBackLocationsFromStorage();
        loadWorldLastLocationsFromStorage();
        api.playerResets().register(new PlayerResetHandler() {
            public @NotNull String id() { return "player-locations"; }
            public @NotNull Set<PlayerResetScope> scopes() { return Set.of(PlayerResetScope.GAMEPLAY, PlayerResetScope.COMPLETE); }
            public int priority() { return 180; }
            public @NotNull PlayerResetPreview preview(@NotNull PlayerResetContext context) {
                int count = backLocations.containsKey(context.playerUuid()) ? 1 : 0;
                count += worldLastLocations.getOrDefault(context.playerUuid(), Map.of()).size();
                return new PlayerResetPreview("Back and per-world last locations", count);
            }
            public void reset(@NotNull PlayerResetContext context) {
                UUID uuid = context.playerUuid(); backLocations.remove(uuid); worldLastLocations.remove(uuid);
            }
        });

        // Track previous locations for /back
        api.events().register(PlayerTeleportEvent.class, event -> {
            if (isMovementAdjustment(event) || isNoOpTeleport(event.getFrom(), event.getTo())) {
                return;
            }
            Player player = event.getPlayer();
            TeleportOptions options = TeleportContext.current(player.getUniqueId());
            if (options.grantDamageProtection()) {
                api.protections().request(
                    player,
                    Duration.ofMillis(DAMAGE_PROTECTION_MILLIS),
                    new ProtectionRequest(ProtectionType.TELEPORT_DAMAGE, "teleport-utils", event.getFrom(), event.getTo())
                );
            }
            Location from = event.getFrom();
            if (options.updateBackLocation()) {
                setBackLocation(player.getUniqueId(), from);
            }
            if (options.updateWorldLastLocation()) {
                setWorldLastLocation(player.getUniqueId(), from);
            }

            Location to = event.getTo();
            if (options.updateWorldLastLocation()) {
                setWorldLastLocation(player.getUniqueId(), to);
            }
        });

        api.events().register(PlayerTeleportEvent.class, event -> {
            Location to = event.getTo();
            if (to == null) {
                return;
            }
            if (isMovementAdjustment(event) || isNoOpTeleport(event.getFrom(), to)) {
                return;
            }
            if (!TeleportContext.current(event.getPlayer().getUniqueId()).logToConsole()) {
                return;
            }

            STEMCraft.getPlugin().getLogger().info(
                "[teleport] " + event.getPlayer().getName()
                    + " " + formatLocation(event.getFrom())
                    + " -> " + formatLocation(to)
            );
        }, EventPriority.MONITOR, true);

        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            api.protections().request(
                player,
                Duration.ofMillis(DAMAGE_PROTECTION_MILLIS),
                new ProtectionRequest(ProtectionType.TELEPORT_DAMAGE, "player-join", null, player.getLocation())
            );
            setWorldLastLocation(player.getUniqueId(), player.getLocation());
        });
        api.events().register(PlayerQuitEvent.class, event -> {
            Player player = event.getPlayer();
            api.protections().clear(player, ProtectionType.TELEPORT_DAMAGE);
            setWorldLastLocation(player.getUniqueId(), player.getLocation());
        });

        // Run configured commands when a player teleports to a different world
        api.events().register(PlayerTeleportEvent.class, event -> {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (isMovementAdjustment(event) || isNoOpTeleport(from, to)) return;
            if (from.getWorld() == null || to.getWorld() == null) return;
            if (from.getWorld().equals(to.getWorld())) return;

            Player player = event.getPlayer();
            for (String raw : getWorldChangeCommands()) {
                dispatchWorldChangeCommand(player, from, to, raw);
            }
        }, EventPriority.MONITOR, true);

        api.events().register(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            Location loc = player.getLocation();
            setBackLocation(player.getUniqueId(), loc);
        });

        // /tpall
        api.commands().create("tpall")
                .usage("TPALL_USAGE")
                .permission("stemcraft.command.tpall")
                .description("TPALL_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    ctx.checkNotConsole();
                    Player sender = ctx.asPlayer();
                    Location targetLocation = sender.getLocation();
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        if (target.equals(sender)) continue;
                        PlayerUtil.teleport(target, targetLocation);
                        cmd.info(target, "TPALL_TARGET_NOTIFY", "player", sender.getName());
                    }

                    ctx.returnInfo("TPALL_SUCCESS", "count", (Bukkit.getOnlinePlayers().size() - 1));
                })
                .register(STEMCraft.getPlugin());
        
        // /tphere <player>
        api.commands().create("tphere")
                .usage("TPHERE_USAGE")
                .permission("stemcraft.command.tphere")
                .description("TPHERE_DESCRIPTION")
                .tabCompletion("{player}")
                .executor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player sender)) {
                        cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                        return;
                    }

                    if (ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    Player target = ctx.getPlayer(0);
                    if (target == null) {
                        cmd.error("PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                        return;
                    }

                    setBackLocation(target.getUniqueId(), target.getLocation());
                    target.teleport(sender.getLocation());
                    cmd.info(ctx.getSender(), "TPHERE_SUCCESS", "player", target.getName());
                })
                .register(STEMCraft.getPlugin());

        api.commands().create("tpspawn")
                .usage("TPSPAWN_USAGE")
                .permission("stemcraft.command.tpspawn")
                .description("TPSPAWN_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    if (ctx.isConsole() && ctx.args().size() < 2) {
                        ctx.returnError("CONSOLE_PLAYER_REQUIRED");
                        return;
                    }

                    Player target;
                    World world;

                    if (ctx.args().isEmpty()) {
                        if (!(ctx.getSender() instanceof Player sender)) {
                            ctx.returnError("COMMAND_PLAYER_ONLY");
                            return;
                        }

                        target = sender;
                        world = sender.getWorld();
                    } else {
                        String worldName = ctx.getArg(0);
                        World requestedWorld = Bukkit.getWorld(worldName);
                        if (requestedWorld == null) {
                            ctx.returnError("WORLD_NOT_FOUND", "world", worldName);
                            return;
                        }

                        target = ctx.isConsole()
                                ? ctx.getPlayer(1)
                                : ctx.getPlayer(1, ctx.getSender());
                        if (target == null) {
                            ctx.returnError("PLAYER_NOT_FOUND", "player", ctx.getArg(1));
                            return;
                        }

                        world = requestedWorld;
                    }

                    setBackLocation(target.getUniqueId(), target.getLocation());
                    PlayerUtil.teleport(target, world.getSpawnLocation());
                    cmd.info(ctx.getSender(), "TPSPAWN_SUCCESS", "player", target.getName(), "world", world.getName());
                })
                .register(STEMCraft.getPlugin());

        // /back [player]
        api.commands().create("back")
                .usage("BACK_USAGE")
                .permission("stemcraft.command.back")
                .description("BACK_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    Player target;

                    if (ctx.args().isEmpty()) {
                        if (!(ctx.getSender() instanceof Player sender)) {
                            cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                            return;
                        }
                        target = sender;
                    } else {
                        OfflinePlayer off = ctx.getArgAsOfflinePlayer(0);
                        if (off == null || !off.isOnline()) {
                            cmd.error("PLAYER_NOT_FOUND", "player", ctx.args().getFirst());
                            return;
                        }
                        target = off.getPlayer();
                    }

                    if (target == null) {
                        cmd.error("PLAYER_NOT_FOUND", "player", ctx.args().isEmpty() ? "" : ctx.args().getFirst());
                        return;
                    }

                    UUID uuid = target.getUniqueId();
                    Location back = backLocations.get(uuid);
                    if (back == null) {
                        cmd.info(ctx.getSender(), "BACK_NO_LOCATION", "player", target.getName());
                        return;
                    }

                    backLocations.remove(uuid);
                    saveBackLocation(uuid, null);

                    target.teleport(back);
                    cmd.info(ctx.getSender(), "BACK_SUCCESS", "player", target.getName());
                })
                .register(STEMCraft.getPlugin());

        // /warp <name>
        api.commands().create("warp")
                .usage("WARP_USAGE")
                .permission("stemcraft.command.warp")
                .description("WARP_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                        return;
                    }

                    if (ctx.args().isEmpty()) {
                        if (warps.isEmpty()) {
                            cmd.info(player, "WARP_LIST_EMPTY");
                            return;
                        }
                        cmd.info(player, "WARP_LIST", "warps", String.join(", ", warps.keySet()));
                        return;
                    }

                    String name = ctx.getArg(0).toLowerCase(Locale.ROOT);
                    Location loc = warps.get(name);
                    if (loc == null) {
                        cmd.error(player, "WARP_NOT_FOUND", "warp", name);
                        return;
                    }

                    setBackLocation(player.getUniqueId(), player.getLocation());
                    player.teleport(loc);
                    cmd.info(player, "WARP_SUCCESS", "warp", name);
                })
                .register(STEMCraft.getPlugin());

        // /setwarp <name>
        api.commands().create("setwarp")
                .usage("SETWARP_USAGE")
                .permission("stemcraft.command.setwarp")
                .description("SETWARP_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                        return;
                    }

                    if (ctx.args().isEmpty()) {
                        cmd.error(player, cmd.getUsage());
                        return;
                    }

                    String name = ctx.getArg(0).toLowerCase(Locale.ROOT);
                    Location loc = player.getLocation();

                    warps.put(name, loc);
                    saveWarpToConfig(name, loc);

                    cmd.info(player, "SETWARP_SUCCESS", "warp", name);
                })
                .register(STEMCraft.getPlugin());

        // /delwarp <name>
        api.commands().create("delwarp")
                .usage("DELWARP_USAGE")
                .permission("stemcraft.command.delwarp")
                .description("DELWARP_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    String name = ctx.getArg(0).toLowerCase(Locale.ROOT);
                    if (!warps.containsKey(name)) {
                        cmd.error(ctx.getSender(), "WARP_NOT_FOUND", "warp", name);
                        return;
                    }

                    warps.remove(name);
                    deleteWarpFromConfig(name);

                    cmd.info(ctx.getSender(), "DELWARP_SUCCESS", "warp", name);
                })
                .register(STEMCraft.getPlugin());

        // /spawn <world> [player]
        api.commands().create("spawn")
                .usage("SPAWN_USAGE")
                .permission("stemcraft.command.spawn")
                .description("SPAWN_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        if (!(ctx.getSender() instanceof Player sender)) {
                            cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                            return;
                        }

                        World world = sender.getWorld();
                        teleportToWorldSpawn(cmd, sender, world, sender);
                        return;
                    }

                    String worldName = ctx.getArg(0);
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        cmd.error(ctx.getSender(), "WORLD_NOT_FOUND", "world", worldName);
                        return;
                    }

                    Player target;
                    if (ctx.args().size() >= 2) {
                        OfflinePlayer off = ctx.getArgAsOfflinePlayer(1);
                        if (off == null || !off.isOnline()) {
                            cmd.error("PLAYER_NOT_FOUND", "player", ctx.getArg(1));
                            return;
                        }
                        target = off.getPlayer();
                    } else {
                        if (!(ctx.getSender() instanceof Player sender)) {
                            cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                            return;
                        }
                        target = sender;
                    }

                    teleportToWorldSpawn(cmd, ctx.getSender(), world, target);
                })
                .register(STEMCraft.getPlugin());

        // /tpworld <world>
        api.commands().create("tpworld")
                .usage("TPWORLD_USAGE")
                .permission("stemcraft.command.tpworld")
                .description("TPWORLD_DESCRIPTION")
                .tabCompletion("{world}", "{player}")
                .executor((plugin, cmd, ctx) -> {
                    ctx.checkArgsSizeAtLeast(1);

                    if(ctx.isConsole() && ctx.args().size() < 2) {
                        ctx.returnError("CONSOLE_PLAYER_REQUIRED");
                    }

                    String worldName = ctx.getArg(0);
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        ctx.returnError("WORLD_NOT_FOUND", "world", worldName);
                    }

                    Player targetPlayer = ctx.getPlayer(1, ctx.getSender());
                    if (targetPlayer == null) {
                        ctx.returnError("PLAYER_NOT_FOUND", "player", ctx.getArg(1));
                    }

                    Location destination = getWorldLastLocation(targetPlayer.getUniqueId(), world.getName());
                    if (destination == null) {
                        destination = world.getSpawnLocation();
                        PlayerUtil.teleport(targetPlayer, destination);
                        ctx.returnInfo("TPWORLD_SUCCESS_SPAWN", "world", worldName);
                        return;
                    }

                    PlayerUtil.teleport(targetPlayer, destination);
                    ctx.returnInfo("TPWORLD_SUCCESS_LAST", "world", worldName);
                })
                .register(STEMCraft.getPlugin());

        // /tpworldspawn <world> [player]
        api.commands().create("tpworldspawn")
                .usage("TPWORLDSPAWN_USAGE")
                .permission("stemcraft.command.tpworldspawn")
                .description("TPWORLDSPAWN_DESCRIPTION")
                .tabCompletion("{world}", "{player}")
                .executor((plugin, cmd, ctx) -> {
                    ctx.checkArgsSizeAtLeast(1);

                    if (ctx.isConsole() && ctx.args().size() < 2) {
                        ctx.returnError("CONSOLE_PLAYER_REQUIRED");
                    }

                    String worldName = ctx.getArg(0);
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        ctx.returnError("WORLD_NOT_FOUND", "world", worldName);
                    }

                    Player targetPlayer = ctx.getPlayer(1, ctx.getSender());
                    if (targetPlayer == null) {
                        ctx.returnError("PLAYER_NOT_FOUND", "player", ctx.getArg(1));
                    }

                    PlayerUtil.teleport(targetPlayer, world.getSpawnLocation());
                    ctx.returnInfo("TPWORLDSPAWN_SUCCESS", "world", worldName);
                })
                .register(STEMCraft.getPlugin());

        // /tpworldlast <world-base> [player]
        api.commands().create("tpworldlast")
                .usage("TPWORLDLAST_USAGE")
                .permission("stemcraft.command.tpworldlast")
                .description("TPWORLDLAST_DESCRIPTION")
                .tabCompletion("{world}", "{player}")
                .executor((plugin, cmd, ctx) -> {
                    ctx.checkArgsSizeAtLeast(1);

                    if (ctx.isConsole() && ctx.args().size() < 2) {
                        ctx.returnError("CONSOLE_PLAYER_REQUIRED");
                    }

                    String worldBase = WorldUtil.baseName(ctx.getArg(0));
                    Player targetPlayer = ctx.getPlayer(1, ctx.getSender());
                    if (targetPlayer == null) {
                        ctx.returnError("PLAYER_NOT_FOUND", "player", ctx.getArg(1));
                    }

                    Location destination = getLastLocationInWorldSet(targetPlayer.getUniqueId(), worldBase);
                    if (destination == null || destination.getWorld() == null) {
                        World fallbackWorld = Bukkit.getWorld(worldBase);
                        if (fallbackWorld == null) {
                            fallbackWorld = api.worlds().loadWorld(worldBase);
                        }
                        if (fallbackWorld == null) {
                            ctx.returnError("WORLD_NOT_FOUND", "world", worldBase);
                        }

                        PlayerUtil.teleport(targetPlayer, fallbackWorld.getSpawnLocation());
                        ctx.returnInfo("TPWORLDLAST_SUCCESS_SPAWN", "world", fallbackWorld.getName());
                        return;
                    }

                    PlayerUtil.teleport(targetPlayer, destination);
                    ctx.returnInfo("TPWORLDLAST_SUCCESS_LAST", "world", destination.getWorld().getName());
                })
                .register(STEMCraft.getPlugin());

        // /top
        api.commands().create("top")
                .usage("TOP_USAGE")
                .permission("stemcraft.command.top")
                .description("TOP_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                        return;
                    }

                    World world = player.getWorld();
                    Location base = player.getLocation();

                    int x = base.getBlockX();
                    int z = base.getBlockZ();
                    int y = world.getHighestBlockYAt(x, z);

                    Location dest = new Location(world, x + 0.5, y + 1, z + 0.5);

                    setBackLocation(player.getUniqueId(), player.getLocation());
                    player.teleport(dest);

                    cmd.info(player, "TOP_SUCCESS");
                })
                .register(STEMCraft.getPlugin());


// /jump
        api.commands().create("jump")
                .usage("JUMP_USAGE")
                .permission("stemcraft.command.jump")
                .description("JUMP_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                        return;
                    }

                    int maxDistance = 120; // adjustable
                    Block block = player.getTargetBlockExact(maxDistance);
                    Location target = block != null
                            ? block.getLocation()
                            : null;

                    if (target == null) {
                        cmd.error(player, "JUMP_NO_TARGET");
                        return;
                    }

                    Location dest = target.clone().add(0.5, 1, 0.5);

                    setBackLocation(player.getUniqueId(), player.getLocation());
                    player.teleport(dest);

                    cmd.info(player, "JUMP_SUCCESS");
                })
                .register(STEMCraft.getPlugin());


// /thru
        api.commands().create("thru")
                .usage("THRU_USAGE")
                .permission("stemcraft.command.thru")
                .description("THRU_DESCRIPTION")
                .executor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                        return;
                    }

                    int maxDistance = 40; // reasonable for walls
                    Location eye = player.getEyeLocation();
                    org.bukkit.util.Vector dir = eye.getDirection().normalize();

                    Location check = eye.clone();
                    Location lastSafe = null;

                    for (int i = 0; i < maxDistance; i++) {
                        check.add(dir);

                        if (check.getBlock().isEmpty()) lastSafe = check.clone();
                        else if (!check.getBlock().isEmpty() && lastSafe != null) {
                            // Found wall and exit air block
                            Location dest = lastSafe.clone().add(0, 0, 0);
                            dest.setPitch(player.getPitch());
                            dest.setYaw(player.getYaw());

                            setBackLocation(player.getUniqueId(), player.getLocation());
                            player.teleport(dest);

                            cmd.info(player, "THRU_SUCCESS");
                            return;
                        }
                    }

                    cmd.error(player, "THRU_FAILED");
                })
                .register(STEMCraft.getPlugin());
    }

    private boolean isNoOpTeleport(Location from, Location to) {
        return from != null && to != null && from.equals(to);
    }

    private boolean isMovementAdjustment(PlayerTeleportEvent event) {
        return event.getCause() == PlayerTeleportEvent.TeleportCause.DISMOUNT;
    }
    /**
     * Load warps from configuration.
     */
    private void loadWarpsFromConfig() {
        if (!getRootConfigSection().isSection("warps")) {
            return;
        }

        ConfigSection section = getRootConfigSection().getSection("warps");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value.isEmpty()) continue;

            Location loc = LocationUtil.deserialize(value);
            warps.put(key.toLowerCase(Locale.ROOT), loc);
        }
    }

    /**
     * Save a warp to configuration.
     *
     * @param name The name of the warp.
     * @param loc The location of the warp.
     */
    private void saveWarpToConfig(String name, Location loc) {
        String value = LocationUtil.serialize(loc, true, true);
        getRootConfigSection().set("warps." + name, value);
        getRootConfigSection().save();
    }

    /**
     * Delete a warp from configuration.
     *
     * @param name The name of the warp to delete.
     */
    private void deleteWarpFromConfig(String name) {
        getRootConfigSection().set("warps." + name, null);
        getRootConfigSection().save();
    }

    private void ensureBackLocationStorage() {
        int version = api.database().migrationVersion("teleport-utils");
        if (version < 1) {
            boolean created = api.database().execute(
                    "CREATE TABLE IF NOT EXISTS player_last_locations (" +
                    "uuid TEXT PRIMARY KEY," +
                    "location TEXT NOT NULL," +
                    "updated_at INTEGER NOT NULL" +
                    ");"
            );
            if (!created) {
                api.messages().error("Failed to create player_last_locations table.");
                return;
            }

            migrateBackLocationsFromConfig();
            api.database().setMigrationVersion("teleport-utils", 1);
            version = 1;
        }

        if (version < 2) {
            boolean created = api.database().execute(
                    "CREATE TABLE IF NOT EXISTS player_world_last_locations (" +
                    "uuid TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "x REAL NOT NULL," +
                    "y REAL NOT NULL," +
                    "z REAL NOT NULL," +
                    "yaw REAL NOT NULL," +
                    "pitch REAL NOT NULL," +
                    "updated_at INTEGER NOT NULL," +
                    "PRIMARY KEY(uuid, world)" +
                    ");"
            );
            if (!created) {
                api.messages().error("Failed to create player_world_last_locations table.");
                return;
            }

            api.database().setMigrationVersion("teleport-utils", 2);
        }
    }

    private void migrateBackLocationsFromConfig() {
        ConfigSection section = getRootConfigSection().getSection("last-locations", false);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value.isEmpty()) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(key);
                saveBackLocation(uuid, LocationUtil.deserialize(value));
            } catch (IllegalArgumentException ignored) {
                // ignored
            }
        }

        getRootConfigSection().set("last-locations", null);
        getRootConfigSection().save();
    }

    /**
     * Load back locations from database.
     */
    private void loadBackLocationsFromStorage() {
        backLocations.clear();

        api.database().queryEach(
            "SELECT uuid, location FROM player_last_locations",
            null,
            rs -> {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String serialized = rs.getString("location");
                    Location location = LocationUtil.deserialize(serialized);
                    if (location != null) {
                        backLocations.put(uuid, location);
                    }
                } catch (IllegalArgumentException ignored) {
                    // ignored
                }
            }
        );
    }

    /**
     * Load per-world last locations from database.
     */
    private void loadWorldLastLocationsFromStorage() {
        worldLastLocations.clear();

        api.database().queryEach(
                "SELECT uuid, world, x, y, z, yaw, pitch, updated_at FROM player_world_last_locations",
                null,
                rs -> {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String world = rs.getString("world");
                        if (world == null || world.isBlank()) {
                            return;
                        }

                        WorldLastLocation record = new WorldLastLocation(
                                world.toLowerCase(Locale.ROOT),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                (float) rs.getDouble("yaw"),
                                (float) rs.getDouble("pitch"),
                                rs.getLong("updated_at")
                        );

                        worldLastLocations
                                .computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                                .put(record.worldName(), record);
                    } catch (IllegalArgumentException ignored) {
                        // ignored
                    }
                }
        );
    }

    /**
     * Save a back location to database.
     *
     * @param uuid The UUID of the player.
     * @param loc The location to save.
     */
    private void saveBackLocation(UUID uuid, Location loc) {
        if (loc == null) {
            api.database().update(
                "DELETE FROM player_last_locations WHERE uuid = ?",
                ps -> ps.setString(1, uuid.toString())
            );
        } else {
            String value = LocationUtil.serialize(loc, true, true);
            api.database().update(
                "INSERT INTO player_last_locations (uuid, location, updated_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET location = excluded.location, updated_at = excluded.updated_at",
                ps -> {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, value);
                    ps.setLong(3, System.currentTimeMillis());
                }
            );
        }
    }

    /**
     * Set the back location for a player.
     *
     * @param uuid The UUID of the player.
     * @param loc The location to set as back location.
     */
    private void setBackLocation(UUID uuid, Location loc) {
        if (loc == null) return;
        Location clone = loc.clone();
        backLocations.put(uuid, clone);
        saveBackLocation(uuid, clone);
    }

    /**
     * Set the last known location for a specific world.
     *
     * @param uuid Player UUID.
     * @param loc Current location.
     */
    private void setWorldLastLocation(UUID uuid, Location loc) {
        if (uuid == null || loc == null || loc.getWorld() == null) {
            return;
        }

        String worldName = loc.getWorld().getName().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        WorldLastLocation record = new WorldLastLocation(
                worldName,
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                now
        );

        worldLastLocations
                .computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                .put(worldName, record);

        api.database().update(
                "INSERT INTO player_world_last_locations (uuid, world, x, y, z, yaw, pitch, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid, world) DO UPDATE SET x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch, updated_at = excluded.updated_at",
                ps -> {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, worldName);
                    ps.setDouble(3, record.x());
                    ps.setDouble(4, record.y());
                    ps.setDouble(5, record.z());
                    ps.setDouble(6, record.yaw());
                    ps.setDouble(7, record.pitch());
                    ps.setLong(8, record.updatedAt());
                }
        );
    }

    /**
     * Get a player's last location in a specific world.
     *
     * @param uuid Player UUID.
     * @param worldName World name.
     * @return Last known location in the world, or null.
     */
    private Location getWorldLastLocation(UUID uuid, String worldName) {
        if (uuid == null || worldName == null || worldName.isBlank()) {
            return null;
        }

        Map<String, WorldLastLocation> byWorld = worldLastLocations.get(uuid);
        if (byWorld == null) {
            return null;
        }

        WorldLastLocation record = byWorld.get(worldName.toLowerCase(Locale.ROOT));
        return record == null ? null : toLocation(record);
    }

    public Location resolveWorldDestination(UUID uuid, World world) {
        if (world == null) {
            return null;
        }

        Location destination = getWorldLastLocation(uuid, world.getName());
        if (destination != null) {
            return destination;
        }

        return world.getSpawnLocation();
    }

    /**
     * Get a player's most recent location across a world-set (overworld/nether/end).
     *
     * @param uuid Player UUID.
     * @param worldBase Base world name.
     * @return Most recently visited location in that world-set, or null.
     */
    private Location getLastLocationInWorldSet(UUID uuid, String worldBase) {
        if (uuid == null || worldBase == null || worldBase.isBlank()) {
            return null;
        }

        Map<String, WorldLastLocation> byWorld = worldLastLocations.get(uuid);
        if (byWorld == null || byWorld.isEmpty()) {
            return null;
        }

        String base = WorldUtil.baseName(worldBase).toLowerCase(Locale.ROOT);
        String[] candidates = new String[]{base, base + "_nether", base + "_the_end"};

        WorldLastLocation best = null;
        for (String worldName : candidates) {
            WorldLastLocation record = byWorld.get(worldName);
            if (record == null) {
                continue;
            }

            if (best == null || record.updatedAt() > best.updatedAt()) {
                best = record;
            }
        }

        return best == null ? null : toLocation(best);
    }

    private Location toLocation(WorldLastLocation record) {
        if (record == null) {
            return null;
        }

        World world = Bukkit.getWorld(record.worldName());
        if (world == null) {
            world = api.worlds().loadWorld(record.worldName());
        }
        if (world == null) {
            return null;
        }

        return new Location(world, record.x(), record.y(), record.z(), record.yaw(), record.pitch());
    }

    /**
     * Get the list of commands to run on world change.
     *
     * @return List of commands.
     */
    private List<String> getWorldChangeCommands() {
        return getRootConfigSection().getStringList("teleport-commands");
    }

    /**
     * Apply standard placeholders to a string.
     *
     * @param player The player involved in the teleport.
     * @param from The original location.
     * @param to The destination location.
     * @param input The input string with placeholders.
     * @return The string with placeholders replaced.
     */
    private String applyStandardPlaceholders(Player player, Location from, Location to, String input) {
        if (input == null) return "";
        String out = input;

        String fromWorld = (from != null && from.getWorld() != null) ? from.getWorld().getName() : "";
        String toWorld = (to != null && to.getWorld() != null) ? to.getWorld().getName() : "";

        out = out.replace("{player}", player.getName());
        out = out.replace("{uuid}", player.getUniqueId().toString());
        out = out.replace("{from_world}", fromWorld).replace("{from-world}", fromWorld);
        out = out.replace("{to_world}", toWorld).replace("{to-world}", toWorld);

        if (from != null) {
            out = out.replace("{from_x}", String.valueOf(from.getX())).replace("{from-x}", String.valueOf(from.getX()));
            out = out.replace("{from_y}", String.valueOf(from.getY())).replace("{from-y}", String.valueOf(from.getY()));
            out = out.replace("{from_z}", String.valueOf(from.getZ())).replace("{from-z}", String.valueOf(from.getZ()));
        }
        if (to != null) {
            out = out.replace("{to_x}", String.valueOf(to.getX())).replace("{to-x}", String.valueOf(to.getX()));
            out = out.replace("{to_y}", String.valueOf(to.getY())).replace("{to-y}", String.valueOf(to.getY()));
            out = out.replace("{to_z}", String.valueOf(to.getZ())).replace("{to-z}", String.valueOf(to.getZ()));
        }

        return out;
    }

    /**
     * Dispatch a world change command.
     *
     * @param player The player involved in the teleport.
     * @param from The original location.
     * @param to The destination location.
     * @param raw The raw command string.
     */
    private void dispatchWorldChangeCommand(Player player, Location from, Location to, String raw) {
        if (raw == null) return;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return;

        String cmdLine = applyStandardPlaceholders(player, from, to, trimmed);

        // Prefix behaviour:
        // - player:<command>  => execute as player
        // - otherwise         => execute as console
        if (cmdLine.regionMatches(true, 0, "player:", 0, "player:".length())) {
            String asPlayer = cmdLine.substring("player:".length()).trim();
            if (!asPlayer.isEmpty()) {
                Bukkit.dispatchCommand(player, asPlayer);
            }
            return;
        }

        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        Bukkit.dispatchCommand(console, cmdLine);
    }

    /**
     * Teleport a player to a world's spawn location.
     *
     * @param cmd The command instance.
     * @param sender The command sender.
     * @param world The target world.
     * @param target The target player.
     */
    private void teleportToWorldSpawn(Command cmd,
                                      CommandSender sender,
                                      World world,
                                      Player target) {
        if (target == null || world == null) return;

        setBackLocation(target.getUniqueId(), target.getLocation());
        target.teleport(world.getSpawnLocation());
        cmd.info(sender, "SPAWN_SUCCESS", "player", target.getName(), "world", world.getName());
    }

    private static String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "(unknown)";
        }

        return location.getWorld().getName()
            + "("
            + formatCoordinate(location.getX()) + ","
            + formatCoordinate(location.getY()) + ","
            + formatCoordinate(location.getZ()) + ")";
    }

    private static String formatCoordinate(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }

        return String.format(Locale.ROOT, "%.2f", value);
    }
}
