package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.factories.ChunkGeneratorFactory;
import dev.stemcraft.api.services.WorldService;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager implements WorldService {
    private final STEMCraft plugin;
    private final Map<String, ChunkGeneratorFactory> registry = new ConcurrentHashMap<>();

    private Map<World,RecordedWorldState> recordState = new HashMap<>();

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
                    plugin.warn("Configuration contains the world {name} however it does not exist", "name", worldName);
                }
            }
        }

        List<String> discoveredWorlds = listWorlds();
        for (String worldName : discoveredWorlds) {
            if(Bukkit.getWorld(worldName) != null) {
                plugin.log("World {name}: <aqua>Loaded by Server</aqua>", "name", worldName);
            } else {
                if (configuredWorlds.contains(worldName)) {
                    boolean load = worldsSection.getBoolean(worldName + ".load", false);
                    if (load) {
                        loadWorld(worldName);
                        plugin.log("World {name}: <green>Loaded</green>", "name", worldName);
                    }
                } else {
                    plugin.log("World {name}: <red>Unloaded</red>", "name", worldName);
                }
            }
        }

        plugin.registerCommand("world")
                .addTabCompletion("start")
                .addTabCompletion("enable")
                .addTabCompletion("disable")
                .addTabCompletion("enable")
                .addTabCompletion("status")
                .setUsage("webserver <start|stop|enable|disable>")
                .setExecutor((api, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        api.info(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    switch (ctx.args().getFirst().toLowerCase(Locale.ROOT)) {
                        case "start" -> {
                            Player player = (Player)ctx.getSender();
                            World world = player.getWorld();

                            startRecordingChanges(world);
                            api.info(player, "Started recording");
                        }
                        case "stop" -> {
                            Player player = (Player)ctx.getSender();
                            World world = player.getWorld();

                            stopRecordingChanges(world, false);
                            api.info(player, "Stopped recording and cleared data");
                        }
                        case "rollback" -> {
                            Player player = (Player)ctx.getSender();
                            World world = player.getWorld();

                            stopRecordingChanges(world, true);
                            api.info(player, "Stopped recording and rolled back data");
                        }
                        default -> api.info(ctx.getSender(), cmd.getUsage());
                    }
                })
                .register(plugin);

        // World State Recording
        plugin.registerEvent(BlockBreakEvent.class, event -> {
            recordBlockChange(event.getBlock());
        });

        plugin.registerEvent(BlockPlaceEvent.class, event -> {
//            BlockState oldState = event.getBlockReplacedState();
//            BlockState newState = event.getBlockPlaced().getState();

//            recordBlockChange(oldState, newState);
            World world = event.getBlock().getWorld();
            if (!isRecordingChanges(world)) return;

            // For multi-block placements (doors, beds, etc), Paper/Spigot uses BlockMultiPlaceEvent
            if (event instanceof BlockMultiPlaceEvent multi) {
                for (BlockState replaced : multi.getReplacedBlockStates()) {
                    // Record the original state of every affected block (e.g., both halves of a door)
                    recordBlockChange(replaced);
                }
            } else {
                // Single-block placement: just record what was replaced at this position
                recordBlockChange(event.getBlockReplacedState());
            }
        });

        plugin.registerEvent(BlockBurnEvent.class, event -> {
            recordBlockChange(event.getBlock());
        });

        plugin.registerEvent(BlockIgniteEvent.class, event -> {
            recordBlockChange(event.getBlock());
        });

        plugin.registerEvent(BlockExplodeEvent.class, event -> {
            for (Block block : event.blockList()) {
                recordBlockChange(block);
            }
        });

        plugin.registerEvent(EntityExplodeEvent.class, event -> {
            for (Block block : event.blockList()) {
                recordBlockChange(block);
            }
        });

        plugin.registerEvent(BlockFromToEvent.class, event -> {
            recordBlockChange(event.getToBlock());
        });

        plugin.registerEvent(BlockFadeEvent.class, event -> {
            recordBlockChange(event.getBlock());
        });

        plugin.registerEvent(BlockFormEvent.class, event -> {
            // Snow, ice, etc
            recordBlockChange(event.getBlock());
        });

        plugin.registerEvent(BlockSpreadEvent.class, event -> {
            recordBlockChange(event.getBlock());
        });

        plugin.registerEvent(LeavesDecayEvent.class, event -> {
            recordBlockChange(event.getBlock());
        });

        plugin.registerEvent(StructureGrowEvent.class, event -> {
            // Trees etc
            for (org.bukkit.block.BlockState state : event.getBlocks()) {
                recordBlockChange(state.getBlock());
            }
        });

        plugin.registerEvent(EntityChangeBlockEvent.class, event -> {
            if (event.getEntityType() == EntityType.ENDERMAN
                    || event.getEntityType() == EntityType.FALLING_BLOCK
                    || event.getEntityType() == EntityType.SILVERFISH) {
                recordBlockChange(event.getBlock());
            }
        });

        plugin.registerEvent(PlayerInteractEvent.class, event -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            Block block = event.getClickedBlock();
            if (block == null) return;

            BlockData data = block.getBlockData();

            // Doors, trapdoors, fence gates
            if (data instanceof Openable) {
                recordBlockChange(block);
            }
        });

        plugin.registerEvent(PlayerBucketEmptyEvent.class, event -> {
            recordBlockChange(event.getBlockClicked().getRelative(event.getBlockFace()));
        });

        plugin.registerEvent(ItemSpawnEvent.class, event -> {
            World world = event.getLocation().getWorld();
            if (!isRecordingChanges(world)) return;

            recordState.get(world).recordEntity(event.getEntity());
        });

        plugin.registerEvent(EntitySpawnEvent.class, event -> {
            World world = event.getLocation().getWorld();
            if (!isRecordingChanges(world)) return;

            if (isTemporaryEntity(event.getEntityType())) {
                recordState.get(world).recordEntity(event.getEntity());
            }
        });

        plugin.registerEvent(EntityPlaceEvent.class, event -> {
            World world = event.getEntity().getWorld();
            if (!isRecordingChanges(world)) return;

            if (isTemporaryEntity(event.getEntityType())) {
                recordState.get(world).recordEntity(event.getEntity());
            }
        });

        plugin.registerEvent(SpongeAbsorbEvent.class, event -> {
            if (!isRecordingChanges(event.getBlock().getWorld())) return;

            World world = event.getBlock().getWorld();

            for (BlockState pending : event.getBlocks()) {
                // World is still in previous state here, so this is the *water* snapshot
                Block liveBlock = world.getBlockAt(pending.getX(), pending.getY(), pending.getZ());
                recordBlockChange(liveBlock.getState());
            }
        });

        plugin.registerEvent(InventoryOpenEvent.class, event -> {
            if (!isRecordingChanges(event.getPlayer().getWorld())) return;

            // snapshot the top inventory's container if it is block-based (chest, barrel, etc)
            recordInventoryContainer(event.getView().getTopInventory());
        });

        plugin.registerEvent(InventoryMoveItemEvent.class, event -> {
            // hopper world
            if (!isRecordingChanges(event.getSource().getLocation().getWorld())) return;

            // record source container (if block-backed)
            recordInventoryContainer(event.getSource());
            // record destination container (if block-backed)
            recordInventoryContainer(event.getDestination());
        });

        plugin.registerEvent(InventoryClickEvent.class, event -> {
            if (!isRecordingChanges(event.getWhoClicked().getWorld())) return;

            // top inventory is the container UI (chest, barrel, etc)
            recordInventoryContainer(event.getView().getTopInventory());
        });

        plugin.registerEvent(InventoryDragEvent.class, event -> {
            if (!isRecordingChanges(event.getWhoClicked().getWorld())) return;

            recordInventoryContainer(event.getView().getTopInventory());
        });
    }

    public void onDisable() { }

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

    private World.Environment resolveEnv(String name, @Nullable World.Environment override) {
        if (override != null) return override;

        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether")) return World.Environment.NETHER;
        if (lower.endsWith("_end")) return World.Environment.THE_END;

        return World.Environment.NORMAL;
    }

    private World ensure(String name, ChunkGenerator gen) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;

        World.Environment env = resolveEnv(name, null);

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
        plugin.log("Registered chunk generator {key}", "key", key);
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
        Map<String,RecordedBlockState> blockStateMap = new HashMap<String, RecordedBlockState>();
        Set<UUID> spawnedEntities = new HashSet<>();

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
        Material type;
        String data;
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
                // deep copy for safety
                this.inventoryContents = Arrays.stream(contents)
                        .map(item -> item == null ? null : item.clone())
                        .toArray(ItemStack[]::new);
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

            String locString = block.getX() + "," + block.getY() + "," + block.getZ();

            Material savedType = this.type;
            Material currentType = block.getType();

            // If this location was NOT a chest when we recorded, but is a chest now,
            // we are likely "removing" one half of a double chest.
            if (!isChest(savedType) && isChest(currentType)) {
                for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[] {
                        org.bukkit.block.BlockFace.NORTH,
                        org.bukkit.block.BlockFace.SOUTH,
                        org.bukkit.block.BlockFace.EAST,
                        org.bukkit.block.BlockFace.WEST
                }) {
                    Block neighbour = block.getRelative(face);
                    if (!isChest(neighbour.getType())) continue;

                    BlockData nbData = neighbour.getBlockData();
                    if (nbData instanceof org.bukkit.block.data.type.Chest nbChest &&
                            nbChest.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
                        nbChest.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                        neighbour.setBlockData(nbChest, applyPhysics);
                    }
                }
            }

            // Now restore this block to its recorded state
            block.setType(savedType, applyPhysics);
            BlockData data = Bukkit.createBlockData(this.data);
            block.setBlockData(data, applyPhysics);

            if (inventoryContents != null) {
                long nonNull = Arrays.stream(inventoryContents).filter(Objects::nonNull).count();

                org.bukkit.block.BlockState state = block.getState();
                if (state instanceof org.bukkit.block.Container container) {
                    int invSize = container.getInventory().getSize();
                    ItemStack[] toApply = new ItemStack[invSize];
                    int copyLen = Math.min(invSize, inventoryContents.length);
                    System.arraycopy(inventoryContents, 0, toApply, 0, copyLen);

                    // Clear then set contents; do NOT call update() here to avoid wiping items
                    container.getInventory().clear();
                    container.getInventory().setContents(toApply);
                }
            }
        }
    }

    @Override
    public boolean isRecordingChanges(World world) {
        return recordState.containsKey(world);
    }

    @Override
    public void startRecordingChanges(World world) {
        if(!isRecordingChanges(world)) {
            recordState.put(world, new RecordedWorldState());
        }
    }

    @Override
    public void stopRecordingChanges(World world, boolean rollback) {
        if(rollback) rollbackWorldChanges(world);
        recordState.remove(world);
    }

    @Override
    public void rollbackWorldChanges(World world) {
        if(isRecordingChanges(world)) {
            recordState.get(world).rollback(world);
        }
    }

    @Override
    public void clearWorldChanges(World world) {
        if(isRecordingChanges(world)) {
            recordState.put(world, new RecordedWorldState());
        }
    }

    private void recordBlockChange(Block block) {
        recordBlockChange(block.getState());
    }

    private void recordBlockChange(BlockState state) {
        World world = state.getWorld();
        if (!isRecordingChanges(world)) return;

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

        // Chests (double chest – record partner half too)
        if (isChest(type) && data instanceof org.bukkit.block.data.type.Chest chest) {
            org.bukkit.block.BlockFace partnerFace = getChestPartnerOffset(chest);
            if (partnerFace != null) {
                Block other = block.getRelative(partnerFace);
                worldState.recordBlock(other.getState());
            }
        }
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

    private static org.bukkit.block.BlockFace getChestPartnerOffset(org.bukkit.block.data.type.Chest chest) {
        if (chest.getType() == org.bukkit.block.data.type.Chest.Type.SINGLE) return null;

        org.bukkit.block.BlockFace facing = chest.getFacing();
        org.bukkit.block.data.type.Chest.Type type = chest.getType();

        // “Left” and “Right” are relative to the chest facing
        return switch (facing) {
            case NORTH -> (type == org.bukkit.block.data.type.Chest.Type.LEFT
                    ? org.bukkit.block.BlockFace.EAST
                    : org.bukkit.block.BlockFace.WEST);
            case SOUTH -> (type == org.bukkit.block.data.type.Chest.Type.LEFT
                    ? org.bukkit.block.BlockFace.WEST
                    : org.bukkit.block.BlockFace.EAST);
            case WEST -> (type == org.bukkit.block.data.type.Chest.Type.LEFT
                    ? org.bukkit.block.BlockFace.SOUTH
                    : org.bukkit.block.BlockFace.NORTH);
            case EAST -> (type == org.bukkit.block.data.type.Chest.Type.LEFT
                    ? org.bukkit.block.BlockFace.NORTH
                    : org.bukkit.block.BlockFace.SOUTH);
            default -> null;
        };
    }

    private void recordInventoryContainer(org.bukkit.inventory.Inventory inv) {
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof BlockState state) {
            recordBlockChange(state); // this will only snapshot once per location
        }
    }
}
