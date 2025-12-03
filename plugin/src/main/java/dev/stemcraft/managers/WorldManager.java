package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.factories.ChunkGeneratorFactory;
import dev.stemcraft.api.services.WorldService;
import dev.stemcraft.api.utils.SCString;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

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

    public WorldManager(STEMCraft plugin) {
        this.plugin = plugin;
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
            if(Bukkit.getWorld(worldName) != null) {
                plugin.log("WORLD_CONFIG_LOADED_BY_SERVER", "world", worldName);
            } else {
                if (configuredWorlds.contains(worldName)) {
                    boolean load = worldsSection.getBoolean(worldName + ".load", false);
                    if (load) {
                        loadWorld(worldName);
                        plugin.log("WORLD_CONFIG_LOADED", "world", worldName);
                    }
                } else {
                    plugin.log("WORLD_CONFIG_UNLOADED", "world", worldName);
                }
            }
        }

        plugin.registerCommand("world")
                .setDescription("WORLD_DESCRIPTION")
                .setPermission("stemcraft.command.world")
                .addTabCompletion("record")
                .addTabCompletion("create")
                .addTabCompletion("delete")
                .addTabCompletion("load")
                .addTabCompletion("list")
                .addTabCompletion("duplicate")
                .addTabCompletion("listgenerators")
                .addTabCompletion("flags")
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
                        case "record" -> {
                            if (!(sender instanceof Player player)) {
                                api.info(sender, "PLAYER_ONLY");
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
                            if (args.size() < 2) {
                                api.info(sender, "WORLD_COMMAND_USAGE_CREATE");
                                return;
                            }

                            String name = args.get(1);
                            if (isWorldLoaded(name) || worldExists(name)) {
                                api.info(sender, "WORLD_ALREADY_EXISTS", "world", name);
                                return;
                            }

                            World world;
                            if (args.size() >= 4) {
                                String genKey = args.get(2);
                                String genOpt = args.get(3);
                                world = createWorld(name, genKey, genOpt);
                            } else {
                                world = createWorld(name);
                            }

                            if (sender instanceof Player player && world != null) {
                                world.setSpawnLocation(player.getLocation());
                            }

                            api.info(sender, "WORLD_CREATED", "world", name);
                        }
                        case "delete" -> {
                            if (args.size() < 2) {
                                api.info(sender, "WORLD_COMMAND_USAGE_DELETE");
                                return;
                            }
                            String name = args.get(1);

                            if (isWorldLoaded(name)) {
                                if (!unloadWorld(name, false)) {
                                    api.info(sender, "WORLD_NOT_FOUND", "world", name);
                                    return;
                                }
                            }

                            try {
                                deleteWorld(name);
                                api.info(sender, "WORLD_DELETED", "world", name);
                            } catch (IOException e) {
                                api.info(sender, "WORLD_FAILED_DELETE", e, "world", name);
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
                            if (worlds.isEmpty()) {
                                api.info(sender, "WORLD_NONE");
                                return;
                            }

                            StringBuilder sb = new StringBuilder("Worlds:");
                            for (String wName : worlds) {
                                boolean loaded = isWorldLoaded(wName);
                                sb.append("\n - ").append(wName).append(" [").append(loaded ? "loaded" : "unloaded").append("]");
                            }

                            api.info(sender, sb.toString());
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
                                sb.append("\n - mobspawning: ").append(mobSpawning != null && mobSpawning);
                                sb.append("\n - tickspeed: ").append(tickSpeed == null ? "default" : tickSpeed);

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
                                case "mobspawning" -> {
                                    boolean enabled = Boolean.parseBoolean(value);
                                    world.setGameRule(GameRule.DO_MOB_SPAWNING, enabled);
                                    api.info(sender, "WORLD_SET_MOB_SPAWNING",  "world",  world.getName(), "state", String.valueOf(enabled));
                                }
                                case "tickspeed" -> {
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
                                default -> api.info(sender, "WORLD_INVALID_FLAG");
                            }
                        }
                        default -> api.info(sender, cmd.getUsage());
                    }
                })
                .register(plugin);

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

    }

    // -------- status
    @Override public boolean isWorldLoaded(String name) { return Bukkit.getWorld(name) != null; }
    @Override public boolean worldExists(String name)   { return Files.exists(levelDat(name)); }

    // -------- load / unload
    @Override public World loadWorld(String name) { return ensure(name, null); }
    @Override public boolean unloadWorld(String name, boolean save) {
        World w = Bukkit.getWorld(name);
        return w != null && Bukkit.unloadWorld(w, save);
    }

    // -------- create
    @Override public World createWorld(String name) { return ensure(name, null); }
    @Override public World createWorld(String name, ChunkGenerator gen) { return ensure(name, gen); }
    @Override public World createWorld(String name, String key, String option) { return ensure(name, generatorFor(key, option)); }

    private World.Environment resolveEnv(String name) {

        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether")) return World.Environment.NETHER;
        if (lower.endsWith("_end")) return World.Environment.THE_END;

        return World.Environment.NORMAL;
    }

    private World ensure(String name, ChunkGenerator gen) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;

        World.Environment env = resolveEnv(name);

        WorldCreator wc = new WorldCreator(name).environment(env);
        if (gen != null) wc.generator(gen);
        return wc.createWorld();
    }

    private ChunkGenerator generatorFor(String key, String cfg) {
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
        Path container = plugin.getServer().getWorldContainer().toPath();
        try (var ds = Files.newDirectoryStream(container)) {
            List<String> names = new ArrayList<>();
            for (Path p : ds) if (Files.isDirectory(p) && Files.exists(p.resolve("level.dat"))) names.add(p.getFileName().toString());
            return names;
        } catch (IOException e) { return Collections.emptyList(); }
    }

    @Override public Path getWorldFolder(String name) { return worldRoot(name); }

    // -------- registry
    @Override public void registerGenerator(String key, ChunkGeneratorFactory factory) {
        registry.put(key.toLowerCase(Locale.ROOT), factory);
        plugin.log("WORLD_REGISTERED_CHUNK_GENERATOR", "key", key);
    }
    @Override public Optional<ChunkGeneratorFactory> findGenerator(String key) {
        return Optional.ofNullable(registry.get(key.toLowerCase(Locale.ROOT)));
    }

    // -------- helpers
    private void requireUnloaded(String name) throws IOException {
        if (isWorldLoaded(name)) throw new IOException("World is loaded: " + name);
    }
    private Path worldRoot(String name) { return plugin.getServer().getWorldContainer().toPath().resolve(name); }
    private Path levelDat(String name)  { return worldRoot(name).resolve("level.dat"); }

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

            Material savedType = this.type;

            // Just restore what we recorded
            block.setType(savedType, applyPhysics);
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
            items = SCString.toString(container.getInventory());
        } else {
            items = SCString.toString(inv);
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
}
