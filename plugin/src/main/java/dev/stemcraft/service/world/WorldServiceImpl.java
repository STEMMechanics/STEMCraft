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

package dev.stemcraft.service.world;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldChangeSession;
import dev.stemcraft.api.service.world.WorldGeneration;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.event.world.WorldDeleteEvent;
import dev.stemcraft.api.util.WorldUtil;
import dev.stemcraft.service.BaseService;
import dev.stemcraft.service.world.recorder.WorldChangeRecorder;
import dev.stemcraft.service.world.setting.*;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the WorldService for managing worlds.
 */
public class WorldServiceImpl extends BaseService implements WorldService {
    private final Map<String, WorldSettingData> settings = new ConcurrentHashMap<>();
    private final WorldCommand worldCommand;
    private final WorldGenerationImpl worldGeneration;
    private final WorldChangeRecorder worldChangeRecorder;

    /**
     * Data holder for world setting and its command mode.
     */
    private record WorldSettingData(WorldBaseSetting setting, SettingCommandMode mode) {}

    @Getter
    @Setter
    private World defaultWorld;

    /**
     * Creates a new WorldServiceImpl instance.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api    The STEMCraft API instance.
     */
    public WorldServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);

        this.worldCommand = new WorldCommand(api, this);
        this.worldGeneration = new WorldGenerationImpl(api);
        this.worldChangeRecorder = new WorldChangeRecorder(api, this);

        this.defaultWorld = Bukkit.getWorlds().getFirst();
    }

    /**
     * Called when the service is enabled.
     */
    public void onEnable() {
        worldCommand.onEnable();
        worldGeneration.onEnable();

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

        registerSettingHandler(worldChangeRecorder, SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldDenySpawnSetting(), SettingCommandMode.FLAG);
        registerSettingHandler(new WorldForceSpawnSetting(), SettingCommandMode.FLAG);
        registerSettingHandler(new WorldGameModeSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldNoDamageSetting(), SettingCommandMode.FLAG);
        registerSettingHandler(new WorldNoHungerSetting(), SettingCommandMode.FLAG);
        registerSettingHandler(new WorldTickSpeedSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldTimeSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldWeatherSetting(), SettingCommandMode.SUBCOMMAND);
    }

    /**
     * Called when the service is disabled.
     */
    @Override
    public void onDisable() {
        settings.forEach((key, value) -> value.setting().onDisable());

        settings.clear();

        worldChangeRecorder.onDisable();
        worldGeneration.onDisable();
        worldCommand.onDisable();
    }

    /**
     * Get the world command instance.
     *
     * @return The WorldCommand instance.
     */
    public WorldCommand getCommand() {
        return worldCommand;
    }

    /**
     * Evict all players from the given world, teleporting them to the default world.
     *
     * @param world The world to evict players from.
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
     *
     * @param name The name of the world.
     * @return true if loaded, false otherwise.
     */
    @Override public boolean isWorldLoaded(String name) {
        return Bukkit.getWorld(name) != null;
    }

    /**
     * Does the given world exist on disk or in config?
     *
     * @param name The name of the world.
     * @return true if exists, false otherwise.
     */
    @Override public boolean worldExists(String name)   {
        return listWorlds().contains(name);
    }

    /**
     * Load the given world by name.
     *
     * @param name The name of the world.
     * @return The loaded World instance.
     */
    @Override public World loadWorld(String name) {
        return ensure(name, null);
    }

    /**
     * Unload the given world by name.
     *
     * @param name The name of the world.
     * @param save Whether to save the world before unloading.
     * @return true if unloaded, false otherwise.
     */
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

    /**
     * Create a new world with the given name and generator.
     *
     * @param name The name of the world.
     * @param generatorName The name of the custom generator to use (or null for default).
     * @param generatorOptions The options for the custom generator (or null for default).
     * @return The created World instance.
     */
    @Override public World createWorld(String name, String generatorName, String generatorOptions) {
        return ensure(name, worldGeneration.get(generatorName, generatorOptions));
    }

    /**
     * Check if a setting with the given key is registered.
     *
     * @param key The key of the setting.
     * @return True if the setting is registered, false otherwise.
     */
    @Override
    public boolean isSettingRegistered(String key) {
        return settings.containsKey(key);
    }

    /**
     * Check if a setting exists for a specific world.
     *
     * @param world The world to check.
     * @param key The key of the setting.
     * @return True if the setting exists, false otherwise.
     */
    @Override
    public boolean settingExists(World world, String key) {
        WorldBaseSetting setting = getSettingHandler(key);
        if(setting != null) {
            String value = setting.get(world, getConfigSection(world));
            return value != null && !value.equals("unset");
        }
        return false;
    }

    /**
     * Get the value of a setting for a specific world.
     *
     * @param world The world to get the setting for.
     * @param key   The key of the setting.
     * @return The value of the setting, or null if not found.
     */
    @Override
    public String getSetting(World world, String key) {
        WorldBaseSetting setting = getSettingHandler(key);
        if(setting != null) {
            return setting.get(world, getConfigSection(world));
        }
        return null;
    }

    /**
     * Set the value of a setting for a specific world.
     *
     * @param world The world to set the setting for.
     * @param key The key of the setting.
     * @param value The value to set.
     * @throws IllegalArgumentException if the key or value is invalid.
     */
    @Override
    public void setSetting(World world, String key, String value) {
        WorldBaseSetting setting = getSettingHandler(key);
        if(setting != null) {
            setting.set(world, getConfigSection(world), value);
        } else {
            throw new IllegalArgumentException("No setting registered with key '" + key + "'.");
        }
    }

    /**
     * Register a world base setting.
     *
     * @param setting The WorldBaseSetting to register.
     * @param commandMode The command mode for the setting.
     */
    @Override
    public void registerSettingHandler(WorldBaseSetting setting, SettingCommandMode commandMode) {
        String key = setting.key();

        if(settings.containsKey(key)) {
            throw new IllegalArgumentException("A world setting with the key '" + key + "' is already registered.");
        }

        settings.put(key, new WorldSettingData(setting, commandMode));

        setting.tabCompletions().forEach(completions -> {
            String[] out;
            if(commandMode == SettingCommandMode.FLAG) {
                out = new String[completions.length + 3];
                out[0] = "flags";
                out[1] = "{world}";
                out[2] = setting.key();
                System.arraycopy(completions, 0, out, 3, completions.length);
            } else {
                out = new String[completions.length + 2];
                out[0] = setting.key();
                out[1] = "{world}";
                System.arraycopy(completions, 0, out, 2, completions.length);
            }
            worldCommand.getCommand().addTabCompletion(out);
        });

        Bukkit.getWorlds().forEach(world -> {
            ConfigSection config = getConfigSection(world);
            setting.onWorldLoad(world, config);
        });
    }

    /**
     * Get a list of all registered setting handler keys.
     *
     * @return A list of setting handler keys.
     */
    public List<String> getSettingHandlerKeys(SettingCommandMode commandMode) {
        List<String> keys = new ArrayList<>();
        settings.forEach((key, value) -> {
            if(commandMode == null || value.mode() == commandMode) {
                keys.add(key);
            }
        });

        return keys;
    }

    /**
     * Get the setting handler for a specific key.
     *
     * @param key The key of the setting.
     * @param commandMode Filter by command mode or null for any.
     * @return The WorldBaseSetting handler, or null if not found.
     */
    public WorldBaseSetting getSettingHandler(String key, SettingCommandMode commandMode) {
        if(key == null) {
            return null;
        }

        WorldSettingData data = settings.get(key);
        if(data != null && (commandMode == null || data.mode() == commandMode)) {
            return data.setting();
        }

        return null;
    }

    public WorldBaseSetting getSettingHandler(String key) {
        return getSettingHandler(key, null);
    }

    /**
     * Get the config section for specific world
     *
     * @param worldName The name of the world
     * @return The ConfigSection for the world
     */
    @Override
    public ConfigSection getConfigSection(String worldName) {
        return getConfigSection().getSection(worldName);
    }

    @Override
    public ConfigSection getConfigSection(World world) {
        return getConfigSection(world.getName());
    }
  
    /**
     * Load settings for the world
     *
     * @param world The world to load settings for
     */
    private void loadWorldSettings(World world) {
        ConfigSection config = getConfigSection(world);

        settings.forEach((key, value) -> value.setting().onWorldLoad(world, config));
    }

    /**
     * Unload settings for the world
     *
     * @param world The world to unload settings for
     */
    private void unloadWorldSettings(World world) {
        ConfigSection config = getConfigSection(world);

        settings.forEach((key, value) -> value.setting().onWorldUnload(world, config));
    }

    /**
     * Delete settings for the world
     *
     * @param worldName The name of the world to delete settings for
     */
    private void deleteWorldSettings(String worldName) {
        ConfigSection config = getConfigSection().getSection(worldName);

        settings.forEach((key, value) -> value.setting().onWorldDeleted(worldName, config));
    }

    /**
     * Handle portal routing for multi-world setups.
     *
     * @param event The PlayerPortalEvent to handle.
     * @return The target Location, or null if no special routing is needed.
     */
    private Location handlePortalRouting(PlayerPortalEvent event) {
        var cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL) return null;

        Location from = event.getFrom();
        World fromWorld = from.getWorld();
        if (fromWorld == null) return null;

        String base = WorldUtil.baseName(fromWorld.getName());
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

    /**
     * Ensure the world with the given name is loaded, creating it if necessary.
     *
     * @param name The name of the world.
     * @param gen  The chunk generator to use, or null for default.
     * @return The World instance.
     */
    private World ensure(String name, ChunkGenerator gen) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;

        World.Environment env = WorldUtil.resolveEnvironment(name);

        WorldCreator wc = new WorldCreator(name).environment(env);
        if (gen != null) wc.generator(gen);

        World world = wc.createWorld();

        if (world != null) {
            getConfigSection().set(name + ".load", true);
            saveConfig();
            loadWorldSettings(world);
        }

        return world;
    }

    /**
     * Delete the world with the given name from disk.
     *
     * @param name The name of the world.
     * @throws IOException If an I/O error occurs.
     */
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

    /**
     * Rename the world with the given old name to the new name.
     *
     * @param oldName The current name of the world.
     * @param newName The new name for the world.
     * @throws IOException If an I/O error occurs.
     */
    @Override public void renameWorld(String oldName, String newName) throws IOException {
        requireUnloaded(oldName); requireUnloaded(newName);
        Files.move(worldRoot(oldName), worldRoot(newName), StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Duplicate the world with the given source name to the destination name.
     *
     * @param src The source world name.
     * @param dst The destination world name.
     * @throws IOException If an I/O error occurs.
     */
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

    /**
     * Get a list of all worlds currently loaded and on disk.
     *
     * @return A list of world names.
     */
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

    /**
     * Get the file system path to the world folder with the given name.
     *
     * @param name The name of the world.
     * @return The Path to the world folder.
     */
    @Override public Path getWorldFolder(String name) {
        return worldRoot(name);
    }

    /**
     * Get the WorldGeneration service instance.
     *
     * @return The WorldGeneration instance.
     */
    @Override
    public WorldGeneration generator() {
        return worldGeneration;
    }

    /**
     * Require that the world with the given name is not loaded.
     *
     * @param name The name of the world.
     * @throws IOException If the world is loaded.
     */
    private void requireUnloaded(String name) throws IOException {
        if (isWorldLoaded(name)) {

            // evict players if loaded and unload world
            World world = Bukkit.getWorld(name);
            if (world != null) {
                evictAllPlayers(world);
                if(!unloadWorld(name, true)) {
                    throw new IOException("World is loaded: " + name);
                }
            }
        }
    }

    /**
     * Get the root path of the world with the given name.
     *
     * @param name The name of the world.
     * @return The Path to the world root folder.
     */
    private Path worldRoot(String name) {
        return plugin.getServer().getWorldContainer().toPath().resolve(name);
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
                    api.messages().warn("WORLD_CONFIG_WORLD_NOT_EXIST", "world", worldName);
                }
            }
        }

        List<String> discoveredWorlds = listWorlds();
        for (String worldName : discoveredWorlds) {
            World existing = Bukkit.getWorld(worldName);
            if (existing != null) {
                api.messages().log("WORLD_CONFIG_LOADED_BY_SERVER", "world", worldName);
                loadWorldSettings(existing);
            } else {
                if (configuredWorlds.contains(worldName)) {
                    boolean load = worldsSection.getBoolean(worldName + ".load", false);
                    if (load) {
                        World world = loadWorld(worldName);
                        if (world != null) {
                            loadWorldSettings(world);
                        }
                        api.messages().log("WORLD_CONFIG_LOADED", "world", worldName);
                    }
                } else {
                    api.messages().log("WORLD_CONFIG_UNLOADED", "world", worldName);
                }
            }
        }
    }

    /**
     * Get or create a world change session for the given world.
     *
     * @param world The world to get the session for.
     * @return The WorldChangeSession instance.
     */
    public WorldChangeSession changes(World world) {
        return worldChangeRecorder.getSession(world);
    }
}
