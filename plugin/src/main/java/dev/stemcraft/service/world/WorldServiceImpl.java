package dev.stemcraft.service.world;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.factory.ChunkGeneratorFactory;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldChangeSession;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.event.world.WorldDeleteEvent;
import dev.stemcraft.service.BaseService;
import dev.stemcraft.service.world.recorder.WorldChangeRecorder;
import dev.stemcraft.service.world.settings.*;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.event.EventPriority;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

public class WorldServiceImpl extends BaseService implements WorldService {
    private final Map<String, WorldSettingData> settings = new ConcurrentHashMap<>();
    private final WorldCommand worldCommand;
    private final WorldGeneration worldGeneration;
    private final WorldChangeRecorder worldChangeRecorder;

    private record WorldSettingData(WorldBaseSetting setting, SettingCommandMode mode) {}

    @Getter
    @Setter
    private World defaultWorld;

    public WorldServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);

        this.worldCommand = new WorldCommand(api, this);
        this.worldGeneration = new WorldGeneration(api);
        this.worldChangeRecorder = new WorldChangeRecorder(api, this);

        this.defaultWorld = Bukkit.getWorlds().getFirst();
    }

    public void onEnable() {
        worldCommand.onEnable();
        worldGeneration.onEnable();
        worldChangeRecorder.onEnable();

        loadWorlds();

        api.tabComplete().register("world-offline", (player, args) -> {
            List<String> suggestions = new ArrayList<>();
            for (String worldName : listWorlds()) {
                if (isWorldLoaded(worldName)) continue; // only offline
                suggestions.add(worldName);
            }

            return suggestions;
        });

        api.events().register(PlayerPortalEvent.class, event -> {
            Location to = handlePortalRouting(event);
            if(to != null) {
                event.setTo(to);
            }
        }, EventPriority.HIGHEST, true);

        api.events().register(WorldLoadEvent.class, event -> {
            getConfigSection().set(event.getWorld().getName() + ".load", true);
            loadWorldSettings(event.getWorld());
        }, EventPriority.MONITOR, false);

        api.events().register(WorldUnloadEvent.class, event -> {
            getConfigSection().set(event.getWorld().getName() + ".load", null);
            unloadWorldSettings(event.getWorld());
        }, EventPriority.MONITOR, false);

        api.events().register(WorldDeleteEvent.class, event -> {
            getConfigSection().set(event.getWorldName() + ".load", null);
            deleteWorldSettings(event.getWorldName());
        }, EventPriority.MONITOR, false);

        registerSetting(new WorldDenySpawnSetting(), SettingCommandMode.FLAG);
        registerSetting(new WorldForceSpawnSetting(), SettingCommandMode.FLAG);
        registerSetting(new WorldGameModeSetting(), SettingCommandMode.SUBCOMMAND);
        registerSetting(new WorldNoDamageSetting(), SettingCommandMode.FLAG);
        registerSetting(new WorldNoHungerSetting(), SettingCommandMode.FLAG);
        registerSetting(new WorldWeatherSetting(), SettingCommandMode.FLAG);
    }

    @Override
    public void onDisable() {
        settings.forEach((key, value) -> {
            value.setting().onDisable();
        });

        settings.clear();

        worldChangeRecorder.onDisable();
        worldGeneration.onDisable();
        worldCommand.onDisable();
    }

    /**
     * Get the world command instance.
     */
    public WorldCommand getCommand() {
        return worldCommand;
    }

    /**
     * Evict all players from the given world, teleporting them to the default world.
     */
    @Override
    public void evictAllPlayers(World world) {
        World firstWorld = Bukkit.getWorlds().getFirst();

        if (world.equals(firstWorld)) {
            throw new IllegalStateException("Cannot evict players from the main world");
        }

        world.getPlayers().forEach(player -> {
            api.messages().info(player, "WORLD_EVICTED", "world", world.getName());
            PlayerUtil.teleport(player, defaultWorld.getSpawnLocation());
        });
    }

    /**
     * Is the given world currently loaded?
     */
    @Override public boolean isWorldLoaded(String name) {
        return Bukkit.getWorld(name) != null;
    }

    /**
     * Does the given world exist on disk or in config?
     */
    @Override public boolean worldExists(String name)   {
        return listWorlds().contains(name);
    }

    // -------- load / unload
    @Override public World loadWorld(String name) { return ensure(name, null); }
    @Override public boolean unloadWorld(String name, boolean save) {
        World w = Bukkit.getWorld(name);
        if (w == null) return false;

        boolean result = Bukkit.unloadWorld(w, save);
        if (result) {
            getConfigSection().set(name + ".load", false);
            saveConfig();
        }

        return result;
    }

    // -------- create
    @Override public World createWorld(String name, String key, String option) { return ensure(name, generatorFor(key, option)); }


    public WorldBaseSetting getSetting(String key, SettingCommandMode commandMode) {
        if(key == null || commandMode == null) {
            return null;
        }

        WorldSettingData data = settings.get(key);
        if(data != null && data.mode() == commandMode) {
            return data.setting();
        }

        return null;
    }

    @Override
    public void registerSetting(WorldBaseSetting setting, SettingCommandMode commandMode) {
        String key = setting.key();

        if(settings.containsKey(key)) {
            throw new IllegalArgumentException("A world setting with the key '" + key + "' is already registered.");
        }

        settings.put(key, new WorldSettingData(setting, commandMode));

        setting.tabCompletions().forEach(completions -> {
            if(commandMode == SettingCommandMode.FLAG) {
                String[] out = new String[completions.length + 3];
                out[0] = "flags";
                out[1] = "{world}";
                out[2] = setting.key();
                System.arraycopy(completions, 0, out, 3, completions.length);
                worldCommand.getCommand().addTabCompletion(out);
            } else {
                String[] out = new String[completions.length + 2];
                out[0] = setting.key();
                out[1] = "{world}";
                System.arraycopy(completions, 0, out, 2, completions.length);
                worldCommand.getCommand().addTabCompletion(out);
            }
        });

        Bukkit.getWorlds().forEach(world -> {
            ConfigSection config = getConfigSection().getSection(world.getName());
            setting.onWorldLoad(world, config);
        });
    }

    /**
     * Get the config section for specific world
     */
    public ConfigSection getConfigSection(String worldName) {
        return getConfigSection().getSection(worldName);
    }

    public ConfigSection getConfigSection(World world) {
        return getConfigSection(world.getName());
    }

    /**
     * Load settings for the world
     */
    private void loadWorldSettings(World world) {
        ConfigSection config = getConfigSection().getSection(world.getName());

        settings.forEach((key, value) -> {
            value.setting().onWorldLoad(world, config);
        });
    }

    /**
     * Unload settings for the world
     */
    private void unloadWorldSettings(World world) {
        ConfigSection config = getConfigSection().getSection(world.getName());

        settings.forEach((key, value) -> {
            value.setting().onWorldUnload(world, config);
        });
    }

    /**
     * Delete settings for the world
     */
    private void deleteWorldSettings(String worldName) {
        ConfigSection config = getConfigSection().getSection(worldName);

        settings.forEach((key, value) -> {
            value.setting().onWorldDeleted(worldName, config);
        });
    }

    /**
     * Handle portal routing for multi-world setups.
     */
    private Location handlePortalRouting(PlayerPortalEvent event) {
        var cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL) return null;

        Location from = event.getFrom();
        World fromWorld = from.getWorld();
        if (fromWorld == null) return null;

        String base = baseWorldName(fromWorld.getName());
        if (base == null || base.isEmpty()) return null;

        String targetName;
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                targetName = base + "_nether";
            } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
                targetName = base;
            } else {
                return null;
            }
        } else {
            // END_PORTAL
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                targetName = base + "_the_end";
            } else if (fromWorld.getEnvironment() == World.Environment.THE_END) {
                targetName = base;
            } else {
                return null;
            }
        }

        World targetWorld = Bukkit.getWorld(targetName);
        if (targetWorld == null) {
            // load if it exists on disk/config
            if (worldExists(targetName) || Files.isDirectory(worldRoot(targetName))) {
                targetWorld = loadWorld(targetName);
            }
        }
        if (targetWorld == null) return null;

        Location to = from.clone();
        to.setWorld(targetWorld);

        // Nether coordinate scaling
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (fromWorld.getEnvironment() == World.Environment.NORMAL
                    && targetWorld.getEnvironment() == World.Environment.NETHER) {
                to.setX(from.getX() / 8.0);
                to.setZ(from.getZ() / 8.0);
            } else if (fromWorld.getEnvironment() == World.Environment.NETHER
                    && targetWorld.getEnvironment() == World.Environment.NORMAL) {
                to.setX(from.getX() * 8.0);
                to.setZ(from.getZ() * 8.0);
            }
        }

        return to;
    }

    private static String baseWorldName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether")) return name.substring(0, name.length() - "_nether".length());
        if (lower.endsWith("_the_end")) return name.substring(0, name.length() - "_the_end".length());
        if (lower.endsWith("_end")) return name.substring(0, name.length() - "_end".length());
        return name;
    }

    private void ensureDimensionWorlds(String baseName) {
        if (baseName == null || baseName.isEmpty()) return;

        String lower = baseName.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether") || lower.endsWith("_the_end") || lower.endsWith("_end")) return;

        // Vanilla naming for End is _the_end.
        String netherName = baseName + "_nether";
        String endName = baseName + "_the_end";

        if (worldExists(netherName) || Files.isDirectory(worldRoot(netherName))) {
            ensure(netherName, null);
        }
        if (worldExists(endName) || Files.isDirectory(worldRoot(endName))) {
            ensure(endName, null);
        }
    }

    private World.Environment resolveEnv(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_nether")) return World.Environment.NETHER;
        if (lower.endsWith("_the_end")) return World.Environment.THE_END;
        if (lower.endsWith("_end")) return World.Environment.THE_END;
        return World.Environment.NORMAL;
    }

    private World ensure(String name, ChunkGenerator gen) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;

        World.Environment env = resolveEnv(name);

        WorldCreator wc = new WorldCreator(name).environment(env);
        if (gen != null) wc.generator(gen);

        World world = wc.createWorld();

        if (world != null) {
            getConfigSection().set(name + ".load", true);
            saveConfig();
            applyWorldSettings(world);
            if (world.getEnvironment() == World.Environment.NORMAL) {
                ensureDimensionWorlds(world.getName());
            }
        }

        return world;
    }


    // -------- fs ops (must be unloaded)
    @Override public void deleteWorld(String name) throws IOException {
        requireUnloaded(name);
        Path root = worldRoot(name);
        if (!Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }

        // Remove from config
        getConfigSection().set(name, null); // delete the whole section for this world
        saveConfig();

        Bukkit.getPluginManager().callEvent(new WorldDeleteEvent(name));
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
        Set<String> names = new LinkedHashSet<>();

        // 1) Loaded worlds
        for (World w : Bukkit.getWorlds()) {
            names.add(w.getName());
        }

        // 2) World folders on disk
        Path container = plugin.getServer().getWorldContainer().toPath();
        try (var ds = Files.newDirectoryStream(container)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) continue;

                boolean isWorld =
                        Files.exists(p.resolve("level.dat")) ||
                                Files.isDirectory(p.resolve("region")) ||
                                Files.isDirectory(p.resolve("playerdata")) ||
                                Files.isDirectory(p.resolve("data")) ||
                                Files.isDirectory(p.resolve("DIM-1").resolve("region")) ||
                                Files.isDirectory(p.resolve("DIM1").resolve("region"));

                if (isWorld) {
                    names.add(p.getFileName().toString());
                }
            }
        } catch (IOException ignored) {}

        List<String> out = new ArrayList<>(names);
        Collections.sort(out);
        return out;
    }

    @Override public Path getWorldFolder(String name) { return worldRoot(name); }

    // -------- registry
    // -------- helpers
    private void requireUnloaded(String name) throws IOException {
        if (isWorldLoaded(name)) throw new IOException("World is loaded: " + name);
    }
    private Path worldRoot(String name) { return plugin.getServer().getWorldContainer().toPath().resolve(name); }

    // -------- chunk generator helpers
    public void registerGenerator(String key, ChunkGeneratorFactory factory) {
        worldGeneration.registerGenerator(key, factory);
    }

    // -------- world settings helpers
    private void applyWorldSettings(World world) {

    }

    /**
     * Load worlds based on config and disk state
     */
    private void loadWorlds() {
        // Load worlds
        ConfigSection worldsSection = getConfigSection();
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
            World existing = Bukkit.getWorld(worldName);
            if (existing != null) {
                plugin.log("WORLD_CONFIG_LOADED_BY_SERVER", "world", worldName);
                applyWorldSettings(existing);
                if (existing.getEnvironment() == World.Environment.NORMAL) {
                    ensureDimensionWorlds(existing.getName());
                }
            } else {
                if (configuredWorlds.contains(worldName)) {
                    boolean load = worldsSection.getBoolean(worldName + ".load", false);
                    if (load) {
                        World world = loadWorld(worldName);
                        if (world != null) {
                            applyWorldSettings(world);
                            if (world.getEnvironment() == World.Environment.NORMAL) {
                                ensureDimensionWorlds(world.getName());
                            }
                        }
                        plugin.log("WORLD_CONFIG_LOADED", "world", worldName);
                    }
                } else {
                    plugin.log("WORLD_CONFIG_UNLOADED", "world", worldName);
                }
            }
        }
    }

    /**
     * Get or create a world change session for the given world.
     */
    public WorldChangeSession changes(World world) {
        return worldChangeRecorder.getSession(world);
    }
}
