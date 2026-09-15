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
import dev.stemcraft.api.util.TeleportContext;
import dev.stemcraft.api.util.TeleportOptions;
import dev.stemcraft.api.event.world.SurvivalPortalActivateEvent;
import dev.stemcraft.chunkgen.UnderhallsGenerator;
import dev.stemcraft.feature.underhalls.UnderhallsProtection;
import dev.stemcraft.feature.underhalls.UnderhallsStore;
import dev.stemcraft.feature.underhalls.UnderhallsRooms;
import dev.stemcraft.feature.underhalls.UnderhallsTerrain;
import dev.stemcraft.feature.underhalls.UnderhallsPassages;
import dev.stemcraft.feature.underhalls.UnderhallsMobs;
import org.bukkit.event.world.ChunkLoadEvent;
import dev.stemcraft.feature.underhalls.UnderhallsStore.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
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

/** Persistent paired entrances and fixed exit rooms for The Underhalls. */
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
    private final Set<Pos> pairing = new HashSet<>();
    private final ArrayDeque<Chunk> roomUpgrades = new ArrayDeque<>();
    private final Random random = new Random();
    private final UnderhallsMobs mobs = new UnderhallsMobs();
    private double passageChance;
    private boolean mobsEnabled;
    private int mobInterval, mobCap;
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
        migrateSharedRooms();
        readSettings();
        running = true;
        protection = new UnderhallsProtection(api, store, this::isMaze);
        protection.enable();
        for (World world : Bukkit.getWorlds()) if (isMaze(world)) Collections.addAll(roomUpgrades, world.getLoadedChunks());
        listen(ChunkLoadEvent.class, EventPriority.MONITOR, event -> {
            if (isMaze(event.getWorld())) roomUpgrades.add(event.getChunk());
        });
        api.tasks().repeating(TASK + "-rooms", 1L, 1L, () -> {
            Chunk chunk = roomUpgrades.poll();
            if (chunk != null && chunk.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ())) {
                UnderhallsRooms.upgrade(chunk, store);
                UnderhallsPassages.upgrade(chunk, store, passageChance);
                if (!UnderhallsTerrain.upgrade(chunk, store)) roomUpgrades.add(chunk);
            }
        });
        listen(org.bukkit.event.world.ChunkUnloadEvent.class, EventPriority.MONITOR, event -> {
            if (isMaze(event.getWorld())) UnderhallsMobs.clear(event.getChunk());
        });
        listen(org.bukkit.event.entity.EntityShootBowEvent.class, EventPriority.HIGHEST, event -> {
            if (UnderhallsMobs.isStalker(event.getEntity())) event.setCancelled(true);
        });
        listen(org.bukkit.event.entity.CreatureSpawnEvent.class, EventPriority.HIGHEST, event -> {
            if (isMaze(event.getLocation().getWorld()) &&
                (event.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL ||
                 event.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) event.setCancelled(true);
        });
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
        listen(BlockPhysicsEvent.class, EventPriority.MONITOR, e -> {
            Block block = e.getBlock();
            if (isMaze(block.getWorld()) && block.getType() == Material.DARK_OAK_DOOR) {
                api.tasks().nextTick(() -> { if (block.getType() != Material.DARK_OAK_DOOR) retireAt(block); });
            }
        });
        listen(BlockBurnEvent.class, EventPriority.MONITOR, e -> retireAt(e.getBlock()));
        listen(org.bukkit.event.entity.EntityChangeBlockEvent.class, EventPriority.MONITOR, e -> retireAt(e.getBlock()));
        listen(EntityExplodeEvent.class, EventPriority.MONITOR, e -> e.blockList().forEach(this::retireAt));
        listen(BlockExplodeEvent.class, EventPriority.MONITOR, e -> e.blockList().forEach(this::retireAt));
        // Do not let pistons move an active entrance without retiring it first.
        listen(BlockPistonExtendEvent.class, EventPriority.MONITOR, e -> e.getBlocks().forEach(this::retireAt));
        listen(BlockPistonRetractEvent.class, EventPriority.MONITOR, e -> e.getBlocks().forEach(this::retireAt));
        listen(BlockPistonExtendEvent.class, EventPriority.HIGHEST, e -> {
            if (e.getBlocks().stream().anyMatch(this::entranceBlock)) e.setCancelled(true);
        });
        listen(BlockPistonRetractEvent.class, EventPriority.HIGHEST, e -> {
            if (e.getBlocks().stream().anyMatch(this::entranceBlock)) e.setCancelled(true);
        });
        api.tasks().repeating(TASK, 20L, 20L, this::tick);
        command = api.commands().create("underhalls").description("Test and manage The Underhalls.")
            .permission("stemcraft.command.underhalls").usage("/underhalls <entrance|enter|status|list [page]>")
            .tabCompletion("entrance").tabCompletion("enter").tabCompletion("status").tabCompletion("list")
            .executor((unused, cmd, ctx) -> {
                if ("status".equals(ctx.getArgLower(0))) {
                    ctx.info("Underhalls: {active} active entrances, {closed} permanently closed.",
                        "active", store.entrances().stream().filter(e -> !e.retired()).count(),
                        "closed", store.entrances().stream().filter(Entrance::retired).count());
                    return;
                }
                if ("list".equals(ctx.getArgLower(0))) {
                    try {
                        listRoutes(ctx.getSender(), ctx.args().size() > 1 ? Integer.parseInt(ctx.getArg(1)) : 1);
                    } catch (NumberFormatException error) { ctx.error("Usage: /underhalls list [page]"); }
                    return;
                }
                Player player = ctx.asPlayer();
                if (player == null) { ctx.error("Run this command in-game."); return; }
                switch (Objects.toString(ctx.getArgLower(0), "")) {
                    case "visit" -> {
                        if (ctx.args().size() != 3) { ctx.error("Use the teleport links in /underhalls list."); return; }
                        try { visitRoute(player, ctx.getArgLower(1), Pos.decode(ctx.getArg(2))); }
                        catch (IllegalArgumentException error) { ctx.error("Invalid portal link. Run /underhalls list again."); }
                    }
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
                    default -> ctx.error("Usage: /underhalls <entrance|enter|status|list [page]>");
                }
            }).register(STEMCraft.getPlugin());
    }

    private record DebugRoute(String kind, Pos origin, Pos destination, String detail) { }

    private List<DebugRoute> debugRoutes() {
        List<DebugRoute> routes = new ArrayList<>();
        Map<Pos, Pos> exits = new HashMap<>(store.exits());
        exits.replaceAll((room, destination) -> {
            Entrance paired = pairedEntrance(room);
            return paired == null ? destination : paired.origin().offset(0, 0, -1);
        });
        for (Entrance entrance : store.entrances()) {
            World maze = Bukkit.getWorld(entrance.maze());
            Pos destination = entrance.room() != null ? entrance.room().offset(0, 0, -4) : maze == null ? null : Pos.of(entranceArrival(maze, entrance).getBlock());
            routes.add(new DebugRoute("entrance", entrance.origin(), destination,
                (entrance.retired() ? "RETIRED" : "ACTIVE") + (maze == null ? " -> maze " + entrance.maze() +
                    " (unloaded), tile " + entrance.tileX() + ", " + entrance.tileZ() : "")));
            if (destination != null) exits.put(destination.offset(0, 0, 4), entrance.origin().offset(0, 0, -1));
        }
        exits.forEach((room, destination) -> {
            boolean paired = pairedEntrance(room) != null;
            routes.add(new DebugRoute("exit", room, destination, store.roomClosed(room) ? "RETIRED" : paired ? "PAIRED RETURN" : "UNPAIRED FIXED EXIT"));
        });
        routes.sort(Comparator.comparing(DebugRoute::kind).thenComparing(route -> route.origin().encode()));
        return routes;
    }

    void listRoutes(CommandSender sender, int page) {
        List<DebugRoute> routes = debugRoutes();
        int pageSize = 6, pages = Math.max(1, (routes.size() + pageSize - 1) / pageSize);
        if (page < 1 || page > pages) { sender.sendMessage("Choose a page from 1 to " + pages + "."); return; }
        sender.sendMessage(Component.text("Underhalls routes — " + routes.size() + " known, page " + page + "/" + pages, NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Paired doorways return to their original overworld entrance. Unpaired exits are labelled separately.", NamedTextColor.GRAY));
        if (routes.isEmpty()) sender.sendMessage("No registered entrances or used exit rooms yet.");
        for (DebugRoute route : routes.subList((page - 1) * pageSize, Math.min(page * pageSize, routes.size()))) {
            sender.sendMessage(Component.text(route.kind().toUpperCase(Locale.ROOT) + " [" + route.detail() + "]", NamedTextColor.YELLOW));
            Pos doorway = route.kind().equals("entrance") ? route.origin() : route.origin().offset(0, 0, -3);
            Component line = Component.text("  " + positionText(doorway), NamedTextColor.WHITE)
                .append(routeLink(" [door]", route.kind(), route.origin(), doorway));
            if (route.destination() != null) {
                line = line.append(Component.text(" -> " + positionText(route.destination()), NamedTextColor.WHITE))
                    .append(routeLink(" [destination]", route.kind() + "-destination", route.origin(), route.destination()));
            }
            sender.sendMessage(line);
        }
        Component navigation = Component.empty();
        if (page > 1) navigation = navigation.append(Component.text("[Previous] ", NamedTextColor.AQUA)
            .clickEvent(ClickEvent.runCommand("/underhalls list " + (page - 1))));
        if (page < pages) navigation = navigation.append(Component.text("[Next]", NamedTextColor.AQUA)
            .clickEvent(ClickEvent.runCommand("/underhalls list " + (page + 1))));
        sender.sendMessage(navigation);
    }

    private String positionText(Pos pos) {
        World world = Bukkit.getWorld(pos.world());
        return (world == null ? pos.world() + " (unloaded)" : world.getName()) + " " + pos.x() + ", " + pos.y() + ", " + pos.z();
    }

    private Component routeLink(String label, String action, Pos id, Pos target) {
        if (Bukkit.getWorld(target.world()) == null) return Component.text(" [world unloaded]", NamedTextColor.GRAY);
        return Component.text(label, NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/underhalls visit " + action + " " + id.encode()))
            .hoverEvent(Component.text("Teleport to " + positionText(target) + ". Door links place you just outside the doorway."));
    }

    void visitRoute(Player player, String action, Pos id) {
        Pos target = null;
        if (action.equals("entrance") || action.equals("entrance-destination")) {
            Entrance entrance = store.entrance(id);
            if (entrance != null) {
                World maze = Bukkit.getWorld(entrance.maze());
                target = action.equals("entrance") ? entrance.origin().offset(0, 0, -1) :
                    maze == null ? null : Pos.of(entranceArrival(maze, entrance).getBlock());
            }
        } else if (action.equals("exit") || action.equals("exit-destination")) {
            boolean known = debugRoutes().stream().anyMatch(route -> route.kind().equals("exit") && route.origin().equals(id));
            if (known) {
                Entrance paired = pairedEntrance(id);
                target = action.equals("exit") ? id.offset(0, 0, -4) : paired == null ? store.exit(id) : paired.origin().offset(0, 0, -1);
            }
        }
        if (target == null || Bukkit.getWorld(target.world()) == null) {
            tell(player, "This route is unavailable or its world is unloaded. Run /underhalls list again."); return;
        }
        Location destination = target.location();
        api.tasks().nextTick(() -> transfer(player, destination, null));
    }

    private record RoomTile(UUID maze, int x, int z) { }

    private Entrance pairedEntrance(Pos room) {
        return store.entrances().stream().filter(entrance -> entrance.room() != null ? entrance.room().equals(room) :
            entrance.maze().equals(room.world()) && room.x() == entrance.tileX() * UnderhallsGenerator.TILE + 68 &&
            room.z() == entrance.tileZ() * UnderhallsGenerator.TILE + 69).findFirst().orElse(null);
    }
    private Location entranceArrival(World maze, Entrance entrance) {
        return entrance.room() == null ? arrival(maze, entrance.tileX(), entrance.tileZ()) : entrance.room().offset(0, 0, -4).location();
    }

    private int freeTileX(UUID maze, int start, int z) {
        int x = start;
        Set<RoomTile> occupied = new HashSet<>();
        for (Entrance entrance : store.entrances()) occupied.add(new RoomTile(entrance.maze(), entrance.tileX(), entrance.tileZ()));
        for (Pos room : store.closedRooms()) occupied.add(new RoomTile(room.world(), Math.floorDiv(room.x(), UnderhallsGenerator.TILE), Math.floorDiv(room.z(), UnderhallsGenerator.TILE)));
        for (Pos room : store.exits().keySet()) occupied.add(new RoomTile(room.world(), Math.floorDiv(room.x(), UnderhallsGenerator.TILE), Math.floorDiv(room.z(), UnderhallsGenerator.TILE)));
        while (occupied.contains(new RoomTile(maze, x, z))) x++;
        return x;
    }

    /** Older versions could send multiple overworld doors to one maze room. Persist distinct assignments. */
    void migrateSharedRooms() {
        Set<RoomTile> claimed = new HashSet<>();
        List<Entrance> entrances = new ArrayList<>(store.entrances());
        entrances.sort(Comparator.comparing(Entrance::retired).thenComparing(entrance -> entrance.origin().encode()));
        for (Entrance entrance : entrances) {
            if (entrance.room() != null) continue;
            RoomTile tile = new RoomTile(entrance.maze(), entrance.tileX(), entrance.tileZ());
            if (!claimed.add(tile)) {
                int x = freeTileX(entrance.maze(), entrance.tileX(), entrance.tileZ());
                Entrance migrated = new Entrance(entrance.origin(), entrance.maze(), x, entrance.tileZ(), entrance.retired());
                store.save(migrated);
                claimed.add(new RoomTile(migrated.maze(), migrated.tileX(), migrated.tileZ()));
            }
        }
    }

    private void readSettings() {
        var config = getConfigSection();
        passageChance = Math.max(0, Math.min(1, config.getDouble("passages.chance", .12)));
        mobsEnabled = config.getBoolean("mobs.enabled", true);
        mobInterval = Math.max(10, config.getInt("mobs.interval-seconds", 120));
        mobCap = Math.max(1, Math.min(16, config.getInt("mobs.max-per-world", 3)));
        if (!mobsEnabled) UnderhallsMobs.clearAll();
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
        UnderhallsMobs.clearAll();
        api.tasks().cancel(TASK);
        api.tasks().cancel(TASK + "-rooms");
        roomUpgrades.clear(); pairing.clear();
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
        mobs.tick(mobsEnabled, mobInterval, mobCap);
        for (Entrance entrance : store.entrances()) {
            World world = Bukkit.getWorld(entrance.origin().world());
            if (!entrance.retired() && world != null && footprintLoaded(entrance.origin()) && !intact(entrance)) store.save(entrance.retire());
        }
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        // A player may enter during cooldown and stop moving, or wait while a chunk loads.
        for (Player player : Bukkit.getOnlinePlayers()) tryExit(player);
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
        int tileZ = Math.floorDiv(pos.z(), OVERWORLD_TILE);
        int tileX = freeTileX(maze.getUID(), Math.floorDiv(pos.x(), OVERWORLD_TILE), tileZ);
        if (!maze.getWorldBorder().isInside(arrival(maze, tileX, tileZ))) return EntranceResult.rejected("No unused paired room is available within the maze border.");
        Entrance entrance = new Entrance(pos, maze.getUID(), tileX, tileZ, false);
        // Save first: an interrupted construction is permanently retired by the next integrity check.
        store.save(entrance);
        buildEntrance(origin);
        return new EntranceResult(true, "");
    }
    private void buildEntrance(Block origin) {
        // Only soft vegetation in the validated footprint is cleared; solid obstacles are never replaced.
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) {
            Block block = origin.getRelative(dx, dy, 0);
            if (!block.getType().isAir() && replaceable(block)) block.setType(Material.AIR, true);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) {
            Block block = origin.getRelative(dx, dy, 0);
            if (dx == 0 && dy <= 1) {
                Door door = (Door) Material.DARK_OAK_DOOR.createBlockData();
                door.setFacing(BlockFace.NORTH);
                door.setHalf(dy == 0 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
                block.setBlockData(door, false);
            } else block.setType(Material.END_STONE_BRICKS, false);
        }
    }
    private static boolean replaceable(Block block) {
        Material type = block.getType();
        return type.isAir() || (type == Material.LIGHT && HeldLightFeature.isTemporaryLight(block)) || type == Material.SHORT_GRASS || type == Material.TALL_GRASS ||
            type == Material.FERN || type == Material.LARGE_FERN || type == Material.DEAD_BUSH || type == Material.SNOW ||
            (block.isPassable() && Tag.FLOWERS.isTagged(type));
    }
    private String siteProblem(Block origin, boolean automatic) {
        World world = origin.getWorld();
        int height = 3;
        if (origin.getY() <= world.getMinHeight() || origin.getY() + height > world.getMaxHeight()) return "The doorway is too close to the world's height limit.";
        for (int dx = -1; dx <= 1; dx++) {
            if (!world.isChunkLoaded((origin.getX() + dx) >> 4, origin.getZ() >> 4)) return "Part of the doorway site is in an unloaded chunk. Move closer and try again.";
            // Only the door itself needs support; the stone frame can overhang a drop.
            if (dx == 0) {
                Block ground = origin.getRelative(0, -1, 0);
                if (!ground.getType().isSolid() || ground.getBoundingBox().getMaxY() != origin.getY() ||
                    (automatic && !GROUND.contains(ground.getType()))) {
                    return "The door needs a supporting block directly below it at " + coordinates(ground) + ".";
                }
            }
            for (int dy = 0; dy < height; dy++) {
                Block block = origin.getRelative(dx, dy, 0);
                if (!world.getWorldBorder().isInside(block.getLocation())) return "The doorway would cross the world border.";
                if (!replaceable(block)) return "Clear " + block.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " at " + coordinates(block) + "; the door and frame need a 3-wide, 3-high, 1-deep space.";
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
        if (isMaze(block.getWorld())) {
            int floor = UnderhallsGenerator.floor(block.getWorld());
            if (Math.floorMod(pos.x(), 8) == 4 && Math.floorMod(pos.z(), 8) == 2 &&
                pos.y() >= floor && pos.y() <= floor + 2 &&
                ((UnderhallsGenerator) block.getWorld().getGenerator()).roomAt(block.getWorld(), pos.x(), pos.z())) {
                Pos room = new Pos(pos.world(), pos.x(), floor + 1, pos.z() + 3);
                store.closeRoom(room);
                Entrance paired = pairedEntrance(room);
                if (paired != null && !paired.retired()) store.save(paired.retire());
            }
        }
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
            Material actual = origin.getRelative(dx, dy, 0).getType();
            if (dx == 0 && dy <= 1) {
                if (actual != Material.DARK_OAK_DOOR) return false;
            } else if (actual != Material.END_STONE_BRICKS &&
                actual != (dx == 0 ? Material.CHISELED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS)) return false;
        }
        // Upgrade only an intact legacy frame, never restore broken or retired portals.
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) {
            if (dx == 0 && dy <= 1) continue;
            Block block = origin.getRelative(dx, dy, 0);
            if (block.getType() != Material.END_STONE_BRICKS) block.setType(Material.END_STONE_BRICKS, false);
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
            // Finish the movement event before teleporting, including when the target chunk is already loaded.
            api.tasks().nextTick(() -> { if (stillHere(player, to)) tryExit(player); });
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
            prepare(player, entranceArrival(maze, entrance), to, target -> {
                Entrance latest = store.entrance(pos);
                if (latest == null || latest.retired() || !intact(latest)) return;
                if (store.roomClosed(Pos.of(target.getBlock()).offset(0, 0, 4))) return;
                if (!safe(target)) { tell(player, "The destination is blocked. Clear it before using this doorway."); return; }
                teleport(player, target);
            });
        });
    }
    private Location arrival(World maze, int tileX, int tileZ) {
        return new Location(maze, tileX * (double)UnderhallsGenerator.TILE + UnderhallsGenerator.ROOM + 4.5,
            UnderhallsGenerator.floor(maze) + 1, tileZ * (double)UnderhallsGenerator.TILE + UnderhallsGenerator.ROOM + 1.5);
    }
    private void tryExit(Player player) {
        Location location = player.getLocation();
        if (!running || !player.isOnline() || pending.contains(player.getUniqueId()) ||
            cooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        if (isMaze(location.getWorld()) && location.getBlockY() == UnderhallsGenerator.floor(location.getWorld()) + 1 &&
            ((UnderhallsGenerator) location.getWorld().getGenerator()).roomAt(location.getWorld(), location.getBlockX(), location.getBlockZ()) &&
            UnderhallsGenerator.exitTrigger(location.getBlockX(), location.getBlockZ())) {
            int x = Math.floorDiv(location.getBlockX(), 8) * 8 + 4, z = Math.floorDiv(location.getBlockZ(), 8) * 8 + 2;
            // A retrofit may have skipped this room to preserve player construction.
            if (store.isPlaced(new Pos(location.getWorld().getUID(), x, location.getBlockY(), z))) return;
            boolean originalRoom = Math.floorMod(x, 128) == 68 && Math.floorMod(z, 128) == 66;
            if (originalRoom || location.getWorld().getBlockAt(x, location.getBlockY(), z).getType() == Material.DARK_OAK_DOOR) exit(player, location);
        }
    }
    private void exit(Player player, Location from) {
        Pos room = new Pos(from.getWorld().getUID(), Math.floorDiv(from.getBlockX(), 8) * 8 + 4,
            UnderhallsGenerator.floor(from.getWorld()) + 1, Math.floorDiv(from.getBlockZ(), 8) * 8 + 5);
        if (store.roomClosed(room)) return;
        Entrance paired = pairedEntrance(room);
        if (paired != null) {
            Location target = paired.origin().offset(0, 0, -1).location();
            if (target.getWorld() == null) { tell(player, "This doorway's overworld is unavailable."); return; }
            transfer(player, target, from);
            return;
        }
        if (!pairing.add(room)) { tell(player, "This doorway is being paired. Wait here a moment."); return; }
        pending.add(player.getUniqueId());
        api.messages().send(player, "The doorway is finding its other side...");
        createRandomPartner(player, from, room, 0);
    }

    private void createRandomPartner(Player player, Location from, Pos room, int attempt) {
        if (store.roomClosed(room) || !stillHere(player, from)) { finishPairing(player, room); return; }
        World source = Bukkit.getWorld(sourceName);
        if (source == null || attempt >= 16) {
            finishPairing(player, room); tell(player, "The doorway could not form its other side. Try again."); return;
        }
        Pos oldDestination = store.exit(room);
        Location column;
        if (oldDestination != null) {
            column = oldDestination.offset(0, 0, 1).location();
            if (column.getWorld() == null || attempt > 0) {
                finishPairing(player, room); tell(player, "Clear space for the door and frame at this exit's saved destination."); return;
            }
        } else {
            Location center = source.getSpawnLocation();
            int radius = Math.max(64, Math.min(1_000_000, getConfigSection().getInt("exits.random-radius", 10000)));
            double halfBorder = source.getWorldBorder().getSize() / 2;
            Location border = source.getWorldBorder().getCenter();
            int minX = (int)Math.ceil(Math.max(-29_999_000, Math.max(center.getX() - radius, border.getX() - halfBorder + 2)));
            int maxX = (int)Math.floor(Math.min(29_999_000, Math.min(center.getX() + radius, border.getX() + halfBorder - 2)));
            int minZ = (int)Math.ceil(Math.max(-29_999_000, Math.max(center.getZ() - radius, border.getZ() - halfBorder + 2)));
            int maxZ = (int)Math.floor(Math.min(29_999_000, Math.min(center.getZ() + radius, border.getZ() + halfBorder - 2)));
            if (minX > maxX || minZ > maxZ) { finishPairing(player, room); tell(player, "No doorway space exists inside the overworld border."); return; }
            int x = random.nextInt(minX, maxX + 1), z = random.nextInt(minZ, maxZ + 1);
            // Keep the 3-wide frame within the single asynchronously prepared chunk.
            x = Math.clamp(x, (x >> 4) * 16 + 1, (x >> 4) * 16 + 14);
            z = Math.clamp(z, (z >> 4) * 16 + 1, (z >> 4) * 16 + 14);
            column = new Location(source, x + .5, 0, z + .5);
        }
        World targetWorld = column.getWorld();
        targetWorld.getChunkAtAsync(column.getBlockX() >> 4, column.getBlockZ() >> 4, true).whenComplete((chunk, error) -> {
            if (!running) return;
            api.tasks().nextTick(() -> {
                if (store.roomClosed(room) || !stillHere(player, from)) { finishPairing(player, room); return; }
                if (error != null || chunk == null) { finishPairing(player, room); tell(player, "The passage could not load its other side."); return; }
                Location origin = column.clone();
                if (oldDestination == null) origin.setY(Math.max(targetWorld.getMinHeight() + 1,
                    targetWorld.getHighestBlockYAt(origin.getBlockX(), origin.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1));
                try {
                    if (!buildRandomPartner(origin.getBlock(), player, room)) {
                        createRandomPartner(player, from, room, attempt + 1); return;
                    }
                } catch (RuntimeException failure) {
                    finishPairing(player, room);
                    api.messages().error("Could not create an Underhalls partner: " + failure.getMessage());
                    tell(player, "The doorway could not save its other side. Check the server log.");
                    return;
                }
                finishPairing(player, room);
                transfer(player, origin.clone().add(0, 0, -1), from);
            });
        });
    }
    private void finishPairing(Player player, Pos room) {
        pending.remove(player.getUniqueId()); pairing.remove(room);
    }

    private boolean buildRandomPartner(Block origin, Player player, Pos room) {
        World world = origin.getWorld();
        if (origin.getY() <= world.getMinHeight() || origin.getY() + 3 > world.getMaxHeight()) return false;
        if (store.roomClosed(room) || pairedEntrance(room) != null) return false;
        if (origin.getChunk().getPersistentDataContainer().has(TOUCHED, PersistentDataType.BYTE) || origin.getChunk().getTileEntities().length > 0) return false;
        for (Entrance existing : store.entrances()) if (existing.origin().world().equals(world.getUID()) &&
            Math.hypot((double)existing.origin().x() - origin.getX(), (double)existing.origin().z() - origin.getZ()) < spacing) return false;
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) {
            Block block = origin.getRelative(x, y, 0);
            if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) || !world.getWorldBorder().isInside(block.getLocation()) || !replaceable(block)) return false;
        }
        Block support = origin.getRelative(0, -1, 0);
        boolean foundation = !support.getType().isSolid();
        if (foundation && !(support.getType().isAir() || support.isLiquid() || replaceable(support))) return false;
        if (!foundation && support.getBoundingBox().getMaxY() != origin.getY()) return false;
        SurvivalPortalActivateEvent event = new SurvivalPortalActivateEvent(KEY, origin.getLocation(), player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || store.roomClosed(room)) return false;
        store.save(new Entrance(Pos.of(origin), room.world(), Math.floorDiv(room.x(), UnderhallsGenerator.TILE),
            Math.floorDiv(room.z(), UnderhallsGenerator.TILE), false, room));
        if (foundation) support.setType(Material.END_STONE_BRICKS, false);
        buildEntrance(origin);
        return true;
    }
    private boolean exitSpace(Location target) {
        World world = target.getWorld();
        return world != null && target.getY() > world.getMinHeight() && target.getY() + 1 < world.getMaxHeight() &&
            world.getWorldBorder().isInside(target) && clearForArrival(target.getBlock()) &&
            clearForArrival(target.getBlock().getRelative(BlockFace.UP));
    }
    private boolean clearForArrival(Block block) {
        Material type = block.getType();
        return type.isAir() || (type == Material.LIGHT && HeldLightFeature.isTemporaryLight(block)) ||
            type == Material.SHORT_GRASS || type == Material.TALL_GRASS || type == Material.FERN || type == Material.LARGE_FERN ||
            (block.isPassable() && Tag.FLOWERS.isTagged(type));
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
            if (!(isMaze(destination.getWorld()) ? safe(destination) : exitSpace(destination))) {
                tell(player, "The destination is blocked. Clear it before using this doorway."); return;
            }
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
                if (from != null && isMaze(from.getWorld())) {
                    Pos room = new Pos(from.getWorld().getUID(), Math.floorDiv(from.getBlockX(), 8) * 8 + 4,
                        UnderhallsGenerator.floor(from.getWorld()) + 1, Math.floorDiv(from.getBlockZ(), 8) * 8 + 5);
                    if (store.roomClosed(room)) return;
                }
                ready.accept(target);
            });
        });
    }
    private boolean stillHere(Player player, Location from) {
        return running && player.isOnline() && player.getWorld().equals(from.getWorld()) && player.getLocation().distanceSquared(from) < 9;
    }
    private void teleport(Player player, Location target) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
        if (TeleportContext.callWithOptions(player.getUniqueId(), new TeleportOptions(true, true, true, false),
            () -> player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN))) {
            player.setFallDistance(0);
            player.playSound(target, Sound.BLOCK_DEEPSLATE_BRICKS_STEP, .8f, .5f);
        } else {
            tell(player, "The teleport was blocked by another server rule.");
        }
    }
    private void tell(Player player, String message) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
        api.messages().send(player, message);
    }
}
