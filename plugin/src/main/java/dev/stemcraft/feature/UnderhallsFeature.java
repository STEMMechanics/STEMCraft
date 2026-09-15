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
import dev.stemcraft.api.event.world.SurvivalPortalActivateEvent;
import dev.stemcraft.chunkgen.UnderhallsGenerator;
import dev.stemcraft.feature.underhalls.UnderhallsProtection;
import dev.stemcraft.feature.underhalls.UnderhallsStore;
import dev.stemcraft.feature.underhalls.UnderhallsStore.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.function.Consumer;

/** Persistent one-way entrances and fixed exit rooms for The Underhalls. */
public class UnderhallsFeature extends BaseFeature {
    private static final String TASK = "feature:underhalls";
    private static final NamespacedKey KEY = new NamespacedKey("stemcraft", "underhalls");
    private static final NamespacedKey TOUCHED = new NamespacedKey("stemcraft", "underhalls-builds");
    private static final int OVERWORLD_TILE = 512;
    private static final Set<Material> GROUND = EnumSet.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
        Material.PODZOL, Material.STONE, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.DEEPSLATE,
        Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.MYCELIUM, Material.MOSS_BLOCK, Material.SNOW_BLOCK);
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final Random random = new Random();
    private UnderhallsStore store;
    private UnderhallsProtection protection;
    private Command command;
    private volatile boolean running;
    private long nextAttempt;
    private String sourceName;
    private String mazeName;
    private boolean natural;
    private int maxEntrances;
    private int spacing;
    private long interval;
    private double chance;
    private String mazeFailure;

    public UnderhallsFeature(STEMCraftAPI api) { super(api); }
    @Override public void onEnable() {
        store = new UnderhallsStore(api.database());
        store.load();
        readSettings();
        running = true;
        protection = new UnderhallsProtection(api, store, this::isMaze);
        protection.enable();
        listen(PlayerMoveEvent.class, EventPriority.MONITOR, this::move);
        listen(PlayerJoinEvent.class, EventPriority.MONITOR, e -> cooldowns.put(e.getPlayer().getUniqueId(), System.currentTimeMillis() + 5000));
        listen(PlayerQuitEvent.class, EventPriority.MONITOR, e -> {
            cooldowns.remove(e.getPlayer().getUniqueId()); pending.remove(e.getPlayer().getUniqueId());
        });
        listen(PlayerTeleportEvent.class, EventPriority.MONITOR, e -> cooldowns.put(e.getPlayer().getUniqueId(), System.currentTimeMillis() + 5000));
        listen(PlayerTeleportEvent.class, EventPriority.HIGHEST, e -> {
            if (e.getTo() != null && isMaze(e.getTo().getWorld()) &&
                (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || e.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) && !safe(e.getTo())) {
                e.setCancelled(true);
            }
        });
        listen(BlockPlaceEvent.class, EventPriority.MONITOR, e -> markBuild(e.getBlock()));
        listen(BlockBreakEvent.class, EventPriority.MONITOR, e -> { markBuild(e.getBlock()); retireAt(e.getBlock()); });
        listen(BlockBurnEvent.class, EventPriority.MONITOR, e -> retireAt(e.getBlock()));
        listen(org.bukkit.event.entity.EntityChangeBlockEvent.class, EventPriority.MONITOR, e -> retireAt(e.getBlock()));
        listen(EntityExplodeEvent.class, EventPriority.MONITOR, e -> e.blockList().forEach(this::retireAt));
        listen(BlockExplodeEvent.class, EventPriority.MONITOR, e -> e.blockList().forEach(this::retireAt));
        // Do not let pistons move an active entrance without retiring it first.
        listen(BlockPistonExtendEvent.class, EventPriority.HIGHEST, e -> {
            if (e.getBlocks().stream().anyMatch(this::entranceBlock)) e.setCancelled(true);
        });
        listen(BlockPistonRetractEvent.class, EventPriority.HIGHEST, e -> {
            if (e.getBlocks().stream().anyMatch(this::entranceBlock)) e.setCancelled(true);
        });
        api.tasks().repeating(TASK, 20L, 20L, this::tick);
        command = api.commands().create("underhalls").description("Test and manage The Underhalls.")
            .permission("stemcraft.command.underhalls").usage("/underhalls <entrance|enter|status>")
            .tabCompletion("entrance").tabCompletion("enter").tabCompletion("status")
            .executor((unused, cmd, ctx) -> {
                if ("status".equals(ctx.getArgLower(0))) {
                    ctx.info("Underhalls: {active} active entrances, {closed} permanently closed.",
                        "active", store.entrances().stream().filter(e -> !e.retired()).count(),
                        "closed", store.entrances().stream().filter(Entrance::retired).count());
                    return;
                }
                Player player = ctx.asPlayer();
                if (player == null) { ctx.error("Run this command in-game."); return; }
                switch (Objects.toString(ctx.getArgLower(0), "")) {
                    case "entrance" -> {
                        if (!player.getWorld().getName().equals(sourceName)) { ctx.error("Create entrances in {world}.", "world", sourceName); return; }
                        var hit = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                            8, FluidCollisionMode.NEVER, true);
                        Block ground = hit == null ? null : hit.getHitBlock();
                        if (ground == null) { ctx.error("Aim at solid ground within eight blocks; grass and flowers are ignored."); return; }
                        EntranceResult result = createEntrance(ground.getRelative(BlockFace.UP), player, false);
                        if (!result.created()) ctx.error(result.reason());
                        else ctx.success("An Underhalls doorway has appeared.");
                    }
                    case "enter" -> api.tasks().nextTick(() -> {
                        World maze = ensureMaze();
                        if (maze == null) { tell(player, mazeFailure); return; }
                        transfer(player, arrival(maze, 0, 0), null);
                    });
                    default -> ctx.error("Usage: /underhalls <entrance|enter|status>");
                }
            }).register(STEMCraft.getPlugin());
    }

    private void readSettings() {
        var config = getConfigSection();
        sourceName = config.getString("source-world", "survival");
        mazeName = config.getString("world", "survival_underhalls");
        natural = config.getBoolean("entrances.enabled", true);
        maxEntrances = Math.max(1, Math.min(256, config.getInt("entrances.max-active", 16)));
        spacing = Math.max(32, Math.min(4096, config.getInt("entrances.minimum-distance", 256)));
        interval = Math.max(60, config.getInt("entrances.interval-seconds", 900)) * 1000L;
        chance = Math.max(0, Math.min(1, config.getDouble("entrances.chance", .2)));
        nextAttempt = System.currentTimeMillis() + interval;
    }
    @Override public void onReload() { super.onReload(); readSettings(); }
    @Override public void onDisable() {
        running = false;
        api.tasks().cancel(TASK);
        if (protection != null) protection.disable();
        listeners.forEach(HandlerList::unregisterAll); listeners.clear();
        if (command != null) command.unregister();
        pending.clear(); cooldowns.clear();
    }
    private <T extends Event> void listen(Class<T> type, EventPriority priority, dev.stemcraft.api.service.event.EventHandler<T> handler) {
        listeners.add(api.events().register(type, handler, priority, true));
    }
    private boolean isMaze(World world) { return world != null && world.getGenerator() instanceof UnderhallsGenerator; }
    private World ensureMaze() {
        mazeFailure = "Could not load " + mazeName + ". Check world-generation.underhalls.enabled and the server log.";
        World world = Bukkit.getWorld(mazeName);
        if (world == null) {
            if (api.worlds().worldExists(mazeName)) {
                String generator = api.worlds().getConfigSection(mazeName).getString("generator.key", "").toLowerCase(Locale.ROOT);
                if (!generator.equals("underhalls") && !generator.equals("stemcraft:underhalls")) {
                    mazeFailure = wrongGeneratorMessage();
                    return null;
                }
                world = api.worlds().loadWorld(mazeName);
            } else {
                World source = Bukkit.getWorld(sourceName);
                if (source == null) { mazeFailure = "Load the source world " + sourceName + " first."; return null; }
                if (sourceName.equals(mazeName)) { mazeFailure = "The source and Underhalls world names must be different."; return null; }
                // loadWorld also creates missing worlds, using normal terrain without saved metadata.
                world = api.worlds().createWorld(mazeName, "underhalls", "", source.getSeed());
            }
        }
        if (world != null && !isMaze(world)) mazeFailure = wrongGeneratorMessage();
        return isMaze(world) ? world : null;
    }
    private String wrongGeneratorMessage() {
        return mazeName + " already exists or is configured without the Underhalls generator. Set underhalls.world to a new unused name and run /stemcraft reload. The existing world has not been changed.";
    }
    private void markBuild(Block block) {
        if (block.getWorld().getName().equals(sourceName)) {
            block.getChunk().getPersistentDataContainer().set(TOUCHED, PersistentDataType.BYTE, (byte) 1);
        }
    }
    private void tick() {
        for (Entrance entrance : store.entrances()) {
            World world = Bukkit.getWorld(entrance.origin().world());
            if (!entrance.retired() && world != null && footprintLoaded(entrance.origin()) && !intact(entrance)) store.save(entrance.retire());
        }
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (!natural || now < nextAttempt) return;
        nextAttempt = now + interval;
        if (random.nextDouble() >= chance) return;
        World world = Bukkit.getWorld(sourceName);
        if (world == null || world.getPlayers().isEmpty() || world.getEnvironment() != World.Environment.NORMAL) return;
        Chunk[] chunks = world.getLoadedChunks();
        for (int attempt = 0; attempt < Math.min(16, chunks.length); attempt++) {
            Chunk chunk = chunks[random.nextInt(chunks.length)];
            int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
            if (!untouchedNeighborhood(world, chunk.getX(), chunk.getZ())) continue;
            if (world.getPlayers().stream().anyMatch(p -> Math.hypot(p.getLocation().getX() - x, p.getLocation().getZ() - z) < 96)) continue;
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (createEntrance(world.getBlockAt(x, y, z), null, true).created()) break;
        }
    }
    private boolean untouchedNeighborhood(World world, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!world.isChunkLoaded(cx + dx, cz + dz)) return false;
            Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
            if (chunk.getPersistentDataContainer().has(TOUCHED, PersistentDataType.BYTE) || chunk.getTileEntities().length != 0) return false;
        }
        return true;
    }
    record EntranceResult(boolean created, String reason) {
        static EntranceResult rejected(String reason) { return new EntranceResult(false, reason); }
    }
    private EntranceResult createEntrance(Block origin, Player player, boolean automatic) {
        World source = origin.getWorld();
        if (!source.getName().equals(sourceName)) return EntranceResult.rejected("Create entrances in " + sourceName + ".");
        if (source.getEnvironment() != World.Environment.NORMAL) return EntranceResult.rejected("The source world must use the NORMAL environment.");
        Pos pos = Pos.of(origin);
        if (store.entrances().stream().filter(e -> !e.retired()).count() >= maxEntrances) return EntranceResult.rejected("The active entrance limit (" + maxEntrances + ") has been reached.");
        if (store.entrances().stream().anyMatch(e -> e.origin().world().equals(pos.world()) &&
            Math.hypot((double)e.origin().x() - pos.x(), (double)e.origin().z() - pos.z()) < spacing)) return EntranceResult.rejected("Move at least " + spacing + " blocks from existing or permanently closed entrances.");
        String problem = siteProblem(origin, automatic);
        if (problem != null) return EntranceResult.rejected(problem);
        if (automatic && !untouchedNeighborhood(source, origin.getX() >> 4, origin.getZ() >> 4)) {
            return EntranceResult.rejected("Nearby chunks are unloaded, contain block entities, or have recorded player builds.");
        }
        // Use the existing portal protection hook, including for automatic entrances.
        SurvivalPortalActivateEvent event = new SurvivalPortalActivateEvent(KEY, origin.getLocation(), player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return EntranceResult.rejected("A protection rule prevented this doorway.");
        World maze = ensureMaze();
        if (maze == null) return EntranceResult.rejected(mazeFailure);
        Entrance entrance = new Entrance(pos, maze.getUID(), Math.floorDiv(pos.x(), OVERWORLD_TILE), Math.floorDiv(pos.z(), OVERWORLD_TILE), false);
        // Save first: an interrupted construction is permanently retired by the next integrity check.
        store.save(entrance);
        // Only soft vegetation in the validated footprint is cleared; solid obstacles are never replaced.
        int radius = automatic ? 2 : 1;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = 0; dy <= (automatic ? 3 : 2); dy++) {
                Block block = origin.getRelative(dx, dy, dz);
                if (!block.getType().isAir() && replaceable(block)) block.setType(Material.AIR, true);
            }
        }
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) {
            Block block = origin.getRelative(dx, dy, 0);
            if (dx == 0 && dy <= 1) {
                Door door = (Door) Material.DARK_OAK_DOOR.createBlockData();
                door.setFacing(BlockFace.NORTH);
                door.setHalf(dy == 0 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
                block.setBlockData(door, false);
            } else block.setType(dx == 0 ? Material.CHISELED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS, false);
        }
        return new EntranceResult(true, "");
    }
    private static boolean replaceable(Block block) {
        Material type = block.getType();
        return type.isAir() || (type == Material.LIGHT && HeldLightFeature.isTemporaryLight(block)) || type == Material.SHORT_GRASS || type == Material.TALL_GRASS ||
            type == Material.FERN || type == Material.LARGE_FERN || type == Material.DEAD_BUSH || type == Material.SNOW ||
            (block.isPassable() && Tag.FLOWERS.isTagged(type));
    }
    private String siteProblem(Block origin, boolean automatic) {
        World world = origin.getWorld();
        int radius = automatic ? 2 : 1, height = automatic ? 4 : 3;
        if (origin.getY() <= world.getMinHeight() || origin.getY() + height >= world.getMaxHeight()) return "The doorway is too close to the world's height limit.";
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (!world.isChunkLoaded((origin.getX() + dx) >> 4, (origin.getZ() + dz) >> 4)) return "Part of the doorway site is in an unloaded chunk. Move closer and try again.";
            Block ground = origin.getRelative(dx, -1, dz);
            boolean supported = automatic ? GROUND.contains(ground.getType()) : ground.getType().isSolid() &&
                ground.getBoundingBox().getMaxY() == origin.getY() && ground.getType() != Material.MAGMA_BLOCK && ground.getType() != Material.CACTUS;
            if (!supported) return "The doorway needs level " + (automatic ? "natural" : "solid") + " ground across " + (radius * 2 + 1) + "x" + (radius * 2 + 1) + " blocks; unsuitable ground at " + coordinates(ground) + ".";
            for (int dy = 0; dy < height; dy++) {
                Block block = origin.getRelative(dx, dy, dz);
                if (!world.getWorldBorder().isInside(block.getLocation())) return "The doorway would cross the world border.";
                if (!replaceable(block)) return "Clear " + block.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " at " + coordinates(block) + "; the doorway needs " + height + " blocks of headroom.";
            }
        }
        return null;
    }
    private static String coordinates(Block block) { return block.getX() + ", " + block.getY() + ", " + block.getZ(); }
    private boolean entranceBlock(Block block) {
        Pos pos = Pos.of(block);
        return store.entrances().stream().anyMatch(e -> !e.retired() && inFrame(e.origin(), pos));
    }
    private static boolean inFrame(Pos origin, Pos block) {
        return origin.world().equals(block.world()) && Math.abs((long)origin.x() - block.x()) <= 1 &&
            block.y() >= origin.y() - 1 && block.y() <= origin.y() + 2 && block.z() == origin.z();
    }
    private void retireAt(Block block) {
        Pos pos = Pos.of(block);
        for (Entrance entrance : store.entrances()) if (!entrance.retired() && inFrame(entrance.origin(), pos)) store.save(entrance.retire());
    }
    private boolean footprintLoaded(Pos pos) {
        World world = Bukkit.getWorld(pos.world());
        if (world == null) return false;
        return world.isChunkLoaded((pos.x() - 1) >> 4, pos.z() >> 4) && world.isChunkLoaded((pos.x() + 1) >> 4, pos.z() >> 4);
    }
    private boolean intact(Entrance entrance) {
        Block origin = entrance.origin().location().getBlock();
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) {
            Material expected = dx == 0 && dy <= 1 ? Material.DARK_OAK_DOOR : dx == 0 ? Material.CHISELED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
            if (origin.getRelative(dx, dy, 0).getType() != expected) return false;
        }
        return true;
    }
    private void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        Location to = event.getTo(), from = event.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) return;
        Player player = event.getPlayer();
        if (pending.contains(player.getUniqueId()) || cooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        if (isMaze(to.getWorld())) {
            if (to.getBlockY() == UnderhallsGenerator.floor(to.getWorld()) + 1 && UnderhallsGenerator.exitTrigger(to.getBlockX(), to.getBlockZ())) {
                exit(player, to);
            }
            return;
        }
        Pos pos = Pos.of(to.getBlock());
        Entrance entrance = store.entrance(pos);
        if (entrance == null) return;
        if (entrance.retired()) return;
        if (!intact(entrance)) { store.save(entrance.retire()); return; }
        pending.add(player.getUniqueId());
        api.tasks().nextTick(() -> {
            pending.remove(player.getUniqueId());
            if (!stillHere(player, to)) return;
            Entrance current = store.entrance(pos);
            if (current == null || current.retired() || !intact(current)) return;
            World maze = Bukkit.getWorld(entrance.maze());
            if (maze == null) maze = ensureMaze();
            if (maze == null || !maze.getUID().equals(entrance.maze()) || !isMaze(maze)) {
                tell(player, "This doorway no longer leads anywhere."); return;
            }
            prepare(player, arrival(maze, entrance.tileX(), entrance.tileZ()), to, target -> {
                Entrance latest = store.entrance(pos);
                if (latest == null || latest.retired() || !intact(latest)) return;
                if (!safe(target)) { tell(player, "The destination is blocked. Clear it before using this doorway."); return; }
                teleport(player, target);
            });
        });
    }
    private Location arrival(World maze, int tileX, int tileZ) {
        return new Location(maze, tileX * (double)UnderhallsGenerator.TILE + 4.5,
            UnderhallsGenerator.floor(maze) + 1, tileZ * (double)UnderhallsGenerator.TILE + 4.5);
    }
    private void exit(Player player, Location from) {
        int tileX = Math.floorDiv(from.getBlockX(), UnderhallsGenerator.TILE), tileZ = Math.floorDiv(from.getBlockZ(), UnderhallsGenerator.TILE);
        Pos room = new Pos(from.getWorld().getUID(), tileX * UnderhallsGenerator.TILE + UnderhallsGenerator.ROOM + 4,
            UnderhallsGenerator.floor(from.getWorld()) + 1, tileZ * UnderhallsGenerator.TILE + UnderhallsGenerator.ROOM + 5);
        Pos saved = store.exit(room);
        if (saved != null) {
            Location target = saved.location();
            if (target.getWorld() == null) { tell(player, "This exit's overworld is unavailable."); return; }
            transfer(player, target, from);
            return;
        }
        World source = Bukkit.getWorld(sourceName);
        if (source == null) { tell(player, "The overworld is unavailable."); return; }
        long x = (long)tileX * OVERWORLD_TILE + 8, z = (long)tileZ * OVERWORLD_TILE + 8;
        if (Math.abs(x) > 29_999_000 || Math.abs(z) > 29_999_000) { tell(player, "This exit lies beyond the overworld border."); return; }
        Location column = new Location(source, x + .5, 0, z + .5);
        if (!source.getWorldBorder().isInside(column)) { tell(player, "This exit lies beyond the overworld border."); return; }
        prepare(player, column, from, ignored -> {
            Location destination = findLanding(source, (int)x, (int)z);
            if (destination == null) { tell(player, "The far side has no safe landing. Try another exit room."); return; }
            // First successful use pins the destination forever; later obstruction never moves it.
            Pos pinned = store.pinExit(room, Pos.of(destination.getBlock()));
            Location target = pinned.location();
            if (!safe(target)) { tell(player, "The destination is blocked. Try another exit room."); return; }
            teleport(player, target);
        });
    }
    private Location findLanding(World world, int x, int z) {
        for (int radius = 0; radius <= 3; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            int y = world.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            Location target = new Location(world, x + dx + .5, y, z + dz + .5);
            if (safe(target)) return target;
        }
        return null;
    }
    private boolean clearForArrival(Block block) {
        return block.getType().isAir() || (block.getType() == Material.LIGHT && HeldLightFeature.isTemporaryLight(block));
    }

    private boolean safe(Location target) {
        World world = target.getWorld();
        if (world == null || target.getY() <= world.getMinHeight() || target.getY() + 1 >= world.getMaxHeight() || !world.getWorldBorder().isInside(target)) return false;
        if (isMaze(world) && target.getBlockY() != UnderhallsGenerator.floor(world) + 1) return false;
        Block feet = target.getBlock(), floor = feet.getRelative(BlockFace.DOWN);
        Material ground = floor.getType();
        return clearForArrival(feet) && clearForArrival(feet.getRelative(BlockFace.UP)) && ground.isSolid() &&
            ground != Material.MAGMA_BLOCK && ground != Material.CACTUS && ground != Material.CAMPFIRE && ground != Material.SOUL_CAMPFIRE;
    }
    private void transfer(Player player, Location target, Location from) {
        prepare(player, target, from, destination -> {
            if (!safe(destination)) { tell(player, "The destination is blocked. Clear it before using this doorway."); return; }
            teleport(player, destination);
        });
    }
    private void prepare(Player player, Location target, Location from, Consumer<Location> ready) {
        if (!running || target.getWorld() == null || !player.isOnline()) return;
        if (!target.getWorld().getWorldBorder().isInside(target)) { tell(player, "The passage lies beyond the world border."); return; }
        if (!pending.add(player.getUniqueId())) return;
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
        target.getWorld().getChunkAtAsync(target.getBlockX() >> 4, target.getBlockZ() >> 4, true).whenComplete((chunk, error) -> {
            if (!running) return;
            api.tasks().runSync(() -> {
                pending.remove(player.getUniqueId());
                if (!running || !player.isOnline() || (from != null && !stillHere(player, from))) return;
                if (error != null || chunk == null) { tell(player, "The passage could not be prepared. Try again."); return; }
                ready.accept(target);
            });
        });
    }
    private boolean stillHere(Player player, Location from) {
        return running && player.isOnline() && player.getWorld().equals(from.getWorld()) && player.getLocation().distanceSquared(from) < 9;
    }
    private void teleport(Player player, Location target) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
        if (player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            player.setFallDistance(0);
            player.playSound(target, Sound.BLOCK_DEEPSLATE_BRICKS_STEP, .8f, .5f);
        }
    }
    private void tell(Player player, String message) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
        api.messages().send(player, message);
    }
}
