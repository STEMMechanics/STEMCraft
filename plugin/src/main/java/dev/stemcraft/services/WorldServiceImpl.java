package dev.stemcraft.services;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.ChunkGeneratorFactory;
import dev.stemcraft.api.utils.SCText;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldServiceImpl implements dev.stemcraft.api.services.WorldService {
    private final Plugin plugin;
    private final Map<String, ChunkGeneratorFactory> registry = new ConcurrentHashMap<>();

    public WorldServiceImpl(Plugin plugin) {
        this.plugin = plugin;


        // Load worlds
        ConfigurationSection worldsSection = STEMCraft.getPluginConfig().getConfigurationSection("worlds");
        Set<String> configuredWorlds = new HashSet<>();
        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                if(worldExists(worldName)) {
                    configuredWorlds.add(worldName);
                } else {
                    STEMCraft.warn("Configuration contains the world {name} however it does not exist", "name", worldName);
                }
            }
        }

        List<String> discoveredWorlds = listWorlds();
        for (String worldName : discoveredWorlds) {
            if(Bukkit.getWorld(worldName) != null) {
                STEMCraft.log("World {name}: <aqua>Loaded by Server</aqua>", "name", worldName);
            } else {
                if (configuredWorlds.contains(worldName)) {
                    boolean load = worldsSection.getBoolean(worldName + ".load", false);
                    if (load) {
                        loadWorld(worldName);
                        STEMCraft.log("World {name}: <green>Loaded</green>", "name", worldName);
                    }
                } else {
                    STEMCraft.log("World {name}: <red>Unloaded</red>", "name", worldName);
                }
            }
        }
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
        STEMCraft.log("Registered chunk generator {key}", "key", key);
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
}
