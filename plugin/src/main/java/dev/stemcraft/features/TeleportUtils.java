package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.utils.SCLocation;
import dev.stemcraft.api.utils.SCPlayer;
import dev.stemcraft.api.utils.SCString;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.List;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportUtils implements STEMCraftFeature {
    private STEMCraft plugin;
    private STEMCraftAPI api;

    private FileConfiguration backConfig;

    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();
    private final Map<String, Location> warps = new ConcurrentHashMap<>();

    @Override
    public void onEnable(STEMCraftAPI api) {
        this.plugin = STEMCraft.getInstance();
        this.api = api;
        this.backConfig = api.getCacheConfig("back-locations.yml");

        loadWarpsFromConfig();
        loadBackLocationsFromConfig();

        // Track previous locations for /back
        api.registerEvent(PlayerTeleportEvent.class, event -> {
            Player player = event.getPlayer();
            Location from = event.getFrom();
            setBackLocation(player.getUniqueId(), from);
        });

        // Run configured commands when a player teleports to a different world
        api.registerEvent(PlayerTeleportEvent.class, event -> {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;
            if (from.getWorld() == null || to.getWorld() == null) return;
            if (from.getWorld().equals(to.getWorld())) return;

            Player player = event.getPlayer();
            for (String raw : getWorldChangeCommands()) {
                dispatchWorldChangeCommand(player, from, to, raw);
            }
        }, EventPriority.MONITOR, true);

        api.registerEvent(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            Location loc = player.getLocation();
            setBackLocation(player.getUniqueId(), loc);
        });

        // /tpall
        api.registerCommand("tpall")
                .setUsage("TPALL_USAGE")
                .setPermission("stemcraft.command.tpall")
                .setDescription("TPALL_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    ctx.checkNotConsole();
                    Player sender = ctx.getSenderAsPlayer();
                    Location targetLocation = sender.getLocation();
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        if (target.equals(sender)) continue;
                        SCPlayer.teleport(target, targetLocation);
                        cmd.info(target, "TPALL_TARGET_NOTIFY", "player", sender.getName());
                    }

                    ctx.returnInfo("TPALL_SUCCESS", "count", (Bukkit.getOnlinePlayers().size() - 1));
                })
                .register(plugin);
        
        // /tphere <player>
        api.registerCommand("tphere")
                .setUsage("TPHERE_USAGE")
                .setPermission("stemcraft.command.tphere")
                .setDescription("TPHERE_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                .register(plugin);

        api.registerCommand("tpspawn")
                .setUsage("TPSPAWN_USAGE")
                .setPermission("stemcraft.command.tpspawn")
                .setDescription("TPSPAWN_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                    SCPlayer.teleport(target, world.getSpawnLocation());
                    cmd.info(ctx.getSender(), "TPSPAWN_SUCCESS", "player", target.getName(), "world", world.getName());
                })
                .register(plugin);

        // /back [player]
        api.registerCommand("back")
                .setUsage("BACK_USAGE")
                .setPermission("stemcraft.command.back")
                .setDescription("BACK_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                .register(plugin);

        // /warp <name>
        api.registerCommand("warp")
                .setUsage("WARP_USAGE")
                .setPermission("stemcraft.command.warp")
                .setDescription("WARP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                .register(plugin);

        // /setwarp <name>
        api.registerCommand("setwarp")
                .setUsage("SETWARP_USAGE")
                .setPermission("stemcraft.command.setwarp")
                .setDescription("SETWARP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                .register(plugin);

        // /delwarp <name>
        api.registerCommand("delwarp")
                .setUsage("DELWARP_USAGE")
                .setPermission("stemcraft.command.delwarp")
                .setDescription("DELWARP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                .register(plugin);

        // /spawn <world> [player]
        api.registerCommand("spawn")
                .setUsage("SPAWN_USAGE")
                .setPermission("stemcraft.command.spawn")
                .setDescription("SPAWN_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                .register(plugin);

        // /tpworld <world>
        api.registerCommand("tpworld")
                .setUsage("TPWORLD_USAGE")
                .setPermission("stemcraft.command.tpworld")
                .setDescription("TPWORLD_DESCRIPTION")
                .addTabCompletion("{world}", "{player}")
                .setExecutor((plugin, cmd, ctx) -> {
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

                    SCPlayer.teleport(targetPlayer, world.getSpawnLocation());
                    ctx.returnInfo("TPWORLD_SUCCESS", "world", worldName);
                })
                .register(plugin);

        // /top
        api.registerCommand("top")
                .setUsage("TOP_USAGE")
                .setPermission("stemcraft.command.top")
                .setDescription("TOP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                .register(plugin);


// /jump
        api.registerCommand("jump")
                .setUsage("JUMP_USAGE")
                .setPermission("stemcraft.command.jump")
                .setDescription("JUMP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "COMMAND_PLAYER_ONLY");
                        return;
                    }

                    int maxDistance = 120; // adjustable
                    Location target = player.getTargetBlockExact(maxDistance) != null
                            ? player.getTargetBlockExact(maxDistance).getLocation()
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
                .register(plugin);


// /thru
        api.registerCommand("thru")
                .setUsage("THRU_USAGE")
                .setPermission("stemcraft.command.thru")
                .setDescription("THRU_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
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
                .register(plugin);
    }

    private void loadWarpsFromConfig() {
        String base = getConfigBase("warps");
        if (!api.config().isConfigurationSection(base)) {
            return;
        }

        var section = api.config().getConfigurationSection(base);
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = base + "." + key;
            String value = api.config().getString(path);
            if (value == null || value.isEmpty()) continue;

            Location loc = SCLocation.deserialize(value);
            warps.put(key.toLowerCase(Locale.ROOT), loc);
        }
    }

    private void saveWarpToConfig(String name, Location loc) {
        String path = getConfigBase("warps") + "." + name;
        String value = SCLocation.serialize(loc, true, true);
        api.config().set(path, value);
        plugin.saveConfig();
    }

    private void deleteWarpFromConfig(String name) {
        String path = getConfigBase("warps") + "." + name;
        api.config().set(path, null);
        plugin.saveConfig();
    }

    private void loadBackLocationsFromConfig() {
        var section = backConfig.getConfigurationSection("locations");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = "locations." + key;
            String value = backConfig.getString(path);
            if (value == null || value.isEmpty()) continue;

            try {
                UUID uuid = UUID.fromString(key);
                Location loc = SCLocation.deserialize(value);
                if(loc == null) continue;
                backLocations.put(uuid, loc);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveBackLocation(UUID uuid, Location loc) {
        String path = "locations." + uuid;
        if (loc == null) {
            backConfig.set(path, null);
        } else {
            String value = SCLocation.serialize(loc, true, true);
            backConfig.set(path, value);
        }
        plugin.saveCacheConfig("back-locations.yml", backConfig);
    }

    private void setBackLocation(UUID uuid, Location loc) {
        if (loc == null) return;
        Location clone = loc.clone();
        backLocations.put(uuid, clone);
        saveBackLocation(uuid, clone);
    }

    private List<String> getWorldChangeCommands() {
        if (!api.config().isList("teleport-commands")) return List.of();
        List<String> list = api.config().getStringList("teleport-commands");
        return list == null ? List.of() : list;
    }

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

    private void teleportToWorldSpawn(STEMCraftCommand cmd,
                                      CommandSender sender,
                                      World world,
                                      Player target) {
        if (target == null || world == null) return;

        setBackLocation(target.getUniqueId(), target.getLocation());
        target.teleport(world.getSpawnLocation());
        cmd.info(sender, "SPAWN_SUCCESS", "player", target.getName(), "world", world.getName());
    }
}