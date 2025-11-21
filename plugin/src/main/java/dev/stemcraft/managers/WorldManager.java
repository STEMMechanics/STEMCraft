package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.factories.ChunkGeneratorFactory;
import dev.stemcraft.api.services.WorldService;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.jetbrains.annotations.NotNull;
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

        // World State Recording
        plugin.registerEvent(BlockBreakEvent.class, event -> {
            recordBlockChange(event.getBlock());
        });

        plugin.registerEvent(BlockPlaceEvent.class, event -> {
            recordBlockChange(event.getBlock());
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

        public void recordBlock(Block block) {
            String locString = block.getX() + "," + block.getY() + "," + block.getZ();
            if(!blockStateMap.containsKey(locString)) {
                blockStateMap.put(locString, new RecordedBlockState(block));
            }
        }

        public void restore(World world) {
            blockStateMap.forEach((locString, state) -> {
                String[] locParts = locString.split(",");
                if(locParts.length == 3) {
                    Location location = new Location(world, Integer.parseInt(locParts[0]), Integer.parseInt(locParts[1]), Integer.parseInt(locParts[2]));
                    state.restore(location, false);
                }
            });
        }
    }

    static class RecordedBlockState {
        Material type;
        String data;
        ItemStack[] inventoryContents;

        RecordedBlockState(Block block) {
            type = block.getType();
            data = block.getBlockData().getAsString();
        }

        RecordedBlockState(Block block, ItemStack[] inventoryContents) {
            type = block.getType();
            data = block.getBlockData().getAsString();
            this.inventoryContents = inventoryContents;
        }

        public void restore(Location location, boolean applyPhysics) {
            Block block = location.getBlock();

            block.setType(type, applyPhysics);
            BlockData data = Bukkit.createBlockData(this.data);
            block.setBlockData(data, applyPhysics);

            if (inventoryContents != null) {
                org.bukkit.block.BlockState state = block.getState();
                if (state instanceof org.bukkit.block.Container container) {
                    container.getInventory().setContents(inventoryContents);
                    container.update(true, applyPhysics);
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
    public void stopRecordingChanges(World world) {
        recordState.remove(world);
    }

    @Override
    public void resetWorldChanges(World world) {
        if(isRecordingChanges(world)) {
            // @TODO - implement
        }
    }

    @Override
    public void clearWorldChanges(World world) {
        if(isRecordingChanges(world)) {
            recordState.put(world, new RecordedWorldState());
        }
    }

    private void recordBlockChange(Block block) {
        if(isRecordingChanges(block.getWorld())) {
            recordState.get(block.getWorld()).recordBlock(block);
        }
    }
}
