package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.utils.SCString;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportFeature implements STEMCraftFeature {
    private STEMCraft plugin;
    private STEMCraftAPI api;

    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();
    private final Map<String, Location> warps = new ConcurrentHashMap<>();

    @Override
    public void onEnable(STEMCraftAPI api) {
        this.plugin = STEMCraft.getInstance();
        this.api = api;

        loadWarpsFromConfig();
        loadBackLocationsFromConfig();

        // Track previous locations for /back
        api.registerEvent(PlayerTeleportEvent.class, event -> {
            Player player = event.getPlayer();
            Location from = event.getFrom();
            setBackLocation(player.getUniqueId(), from);
        });

        api.registerEvent(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            Location loc = player.getLocation();
            setBackLocation(player.getUniqueId(), loc);
        });

        // /tphere <player>
        api.registerCommand("tphere")
                .setUsage("tphere <player>")
                .setPermission("stemcraft.command.tphere")
                .setDescription("TPHERE_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player sender)) {
                        cmd.error(ctx.getSender(), "PLAYER_ONLY");
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

        // /back [player]
        api.registerCommand("back")
                .setUsage("back [player]")
                .setPermission("stemcraft.command.back")
                .setDescription("BACK_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    Player target;

                    if (ctx.args().isEmpty()) {
                        if (!(ctx.getSender() instanceof Player sender)) {
                            cmd.error(ctx.getSender(), "PLAYER_ONLY");
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
                .setUsage("warp <name>")
                .setPermission("stemcraft.command.warp")
                .setDescription("WARP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "PLAYER_ONLY");
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
                .setUsage("setwarp <name>")
                .setPermission("stemcraft.command.setwarp")
                .setDescription("SETWARP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "PLAYER_ONLY");
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
                .setUsage("delwarp <name>")
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
                .setUsage("spawn <world> [player]")
                .setPermission("stemcraft.command.spawn")
                .setDescription("SPAWN_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        if (!(ctx.getSender() instanceof Player sender)) {
                            cmd.error(ctx.getSender(), "PLAYER_ONLY");
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
                            cmd.error(ctx.getSender(), "PLAYER_ONLY");
                            return;
                        }
                        target = sender;
                    }

                    teleportToWorldSpawn(cmd, ctx.getSender(), world, target);
                })
                .register(plugin);

        // /tpworld <world>
        api.registerCommand("tpworld")
                .setUsage("tpworld <world>")
                .setPermission("stemcraft.command.tpworld")
                .setDescription("TPWORLD_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "PLAYER_ONLY");
                        return;
                    }

                    if (ctx.args().isEmpty()) {
                        cmd.error(player, cmd.getUsage());
                        return;
                    }

                    String worldName = ctx.getArg(1);
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        cmd.error(player, "WORLD_NOT_FOUND", "world", worldName);
                        return;
                    }

                    setBackLocation(player.getUniqueId(), player.getLocation());
                    player.teleport(world.getSpawnLocation());
                    cmd.info(player, "TPWORLD_SUCCESS", "world", worldName);
                })
                .register(plugin);

        // /top
        api.registerCommand("top")
                .setUsage("top")
                .setPermission("stemcraft.command.top")
                .setDescription("TOP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "PLAYER_ONLY");
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
                .setUsage("jump")
                .setPermission("stemcraft.command.jump")
                .setDescription("JUMP_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "PLAYER_ONLY");
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
                .setUsage("thru")
                .setPermission("stemcraft.command.thru")
                .setDescription("THRU_DESCRIPTION")
                .setExecutor((plugin, cmd, ctx) -> {
                    if (!(ctx.getSender() instanceof Player player)) {
                        cmd.error(ctx.getSender(), "PLAYER_ONLY");
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

            Location loc = SCString.stringToLocation(value);
            warps.put(key.toLowerCase(Locale.ROOT), loc);
        }
    }

    private void saveWarpToConfig(String name, Location loc) {
        String path = getConfigBase("warps") + "." + name;
        String value = SCString.locationToString(loc, true, true);
        api.config().set(path, value);
        plugin.saveConfig();
    }

    private void deleteWarpFromConfig(String name) {
        String path = getConfigBase("warps") + "." + name;
        api.config().set(path, null);
        plugin.saveConfig();
    }

    private void loadBackLocationsFromConfig() {
        String base = getConfigBase("back-locations");
        if (!api.config().isConfigurationSection(base)) {
            return;
        }

        var section = api.config().getConfigurationSection(base);
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = base + "." + key;
            String value = api.config().getString(path);
            if (value == null || value.isEmpty()) continue;

            try {
                UUID uuid = UUID.fromString(key);
                Location loc = SCString.stringToLocation(value);
                backLocations.put(uuid, loc);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveBackLocation(UUID uuid, Location loc) {
        String path = getConfigBase("back-locations") + "." + uuid;
        if (loc == null) {
            api.config().set(path, null);
        } else {
            String value = SCString.locationToString(loc, true, true);
            api.config().set(path, value);
        }
        plugin.saveConfig();
    }

    private void setBackLocation(UUID uuid, Location loc) {
        if (loc == null) return;
        Location clone = loc.clone();
        backLocations.put(uuid, clone);
        saveBackLocation(uuid, clone);
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