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

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.PlayerUtil;
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
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.List;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature that provides various teleportation utilities such as /tpall, /tphere, /back, /warp, /setwarp, /delwarp, /spawn, /tpworld, /top, /jump, and /thru.
 */
public class TeleportUtils extends BaseFeature {
    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();
    private final Map<String, Location> warps = new ConcurrentHashMap<>();

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
        loadBackLocationsFromConfig();

        // Track previous locations for /back
        api.events().register(PlayerTeleportEvent.class, event -> {
            Player player = event.getPlayer();
            Location from = event.getFrom();
            setBackLocation(player.getUniqueId(), from);
        });

        // Run configured commands when a player teleports to a different world
        api.events().register(PlayerTeleportEvent.class, event -> {
            Location from = event.getFrom();
            Location to = event.getTo();
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
                    Player sender = ctx.getSenderAsPlayer();
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
                .executor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player sender)) {
                        cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                        return;
                    }

                    if (ctx.args().isEmpty()) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    Player target = ctx.getArgAsPlayer(1);
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
                    if(ctx.isConsole() && ctx.numArgs() < 2) {
                        ctx.returnError("CONSOLE_PLAYER_REQUIRED");
                    }

                    Player target = ctx.getArgAsPlayer(2);
                    if (target == null) {
                        ctx.returnError("PLAYER_NOT_FOUND", "player", ctx.getArg(2));
                    }

                    World world = ctx.getArgAsWorld(1, target.getWorld());
                    if (world == null) {
                        ctx.returnError("WORLD_NOT_FOUND", "world", ctx.getArg(1));
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
                        OfflinePlayer off = ctx.getArgAsOfflinePlayer(1);
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

                    String name = ctx.getArg(1).toLowerCase(Locale.ROOT);
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

                    String name = ctx.getArg(1).toLowerCase(Locale.ROOT);
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

                    String name = ctx.getArg(1).toLowerCase(Locale.ROOT);
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

                    String worldName = ctx.getArg(1);
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        cmd.error(ctx.getSender(), "WORLD_NOT_FOUND", "world", worldName);
                        return;
                    }

                    Player target;
                    if (ctx.args().size() >= 2) {
                        OfflinePlayer off = ctx.getArgAsOfflinePlayer(2);
                        if (off == null || !off.isOnline()) {
                            cmd.error("PLAYER_NOT_FOUND", "player", ctx.getArg(2));
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

                    String worldName = ctx.getArg(1);
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        ctx.returnError("WORLD_NOT_FOUND", "world", worldName);
                    }

                    Player targetPlayer = ctx.getArgAsPlayer(2, ctx.getSender());
                    if (targetPlayer == null) {
                        ctx.returnError("PLAYER_NOT_FOUND", "player", ctx.getArg(2));
                    }

                    PlayerUtil.teleport(targetPlayer, world.getSpawnLocation());
                    ctx.returnInfo("TPWORLD_SUCCESS", "world", worldName);
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
            if (value == null || value.isEmpty()) continue;

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

    /**
     * Load back locations from configuration.
     */
    private void loadBackLocationsFromConfig() {
        ConfigSection section = getRootConfigSection().getSection("last-locations");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value == null || value.isEmpty()) continue;

            try {
                UUID uuid = UUID.fromString(key);
                Location loc = LocationUtil.deserialize(value);
                if(loc == null) continue;
                backLocations.put(uuid, loc);
            } catch (IllegalArgumentException ignored) {
                // ignored
            }
        }
    }

    /**
     * Save a back location to configuration.
     *
     * @param uuid The UUID of the player.
     * @param loc The location to save.
     */
    private void saveBackLocation(UUID uuid, Location loc) {
        ConfigSection section = getRootConfigSection().getSection("last-locations");

        if (loc == null) {
            section.set(uuid.toString(), null);
        } else {
            String value = LocationUtil.serialize(loc, true, true);
            section.set(uuid.toString(), value);
        }

        getRootConfigSection().save();
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
     * Get the list of commands to run on world change.
     *
     * @return List of commands.
     */
    private List<String> getWorldChangeCommands() {
        List<String> list = getRootConfigSection().getStringList("teleport-commands");
        return list == null ? List.of() : list;
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
}