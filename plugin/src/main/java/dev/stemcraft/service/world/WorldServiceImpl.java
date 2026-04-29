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
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the WorldService for managing worlds.
 */
public class WorldServiceImpl extends BaseService implements WorldService {
    private static final String DEFAULT_WORLD_OPERATION_ERROR = "unknown error";
    private final Map<String, WorldSettingData> settings = new ConcurrentHashMap<>();
    private final Map<String, String> lastWorldOperationErrors = new ConcurrentHashMap<>();
    private WorldCommand worldCommand;
    private WorldGenerationImpl worldGeneration;
    private WorldChangeRecorder worldChangeRecorder;
    private boolean startupLoadComplete = false;

    /**
     * Data holder for world setting and its command mode.
     */
    private record WorldSettingData(WorldBaseSetting setting, SettingCommandMode mode) {}
    private record ConfiguredGeneratorSpec(String key, String options) {}

    @Setter
    private World defaultWorld;

    /**
     * Creates a new WorldServiceImpl instance.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public WorldServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "worlds");
    }

    /**
     * Called when the service is enabled.
     */
    public void onEnable() {
        worldCommand = new WorldCommand(api, this);
        worldGeneration = new WorldGenerationImpl(api);
        worldChangeRecorder = new WorldChangeRecorder(api, this);

        defaultWorld = firstLoadedWorld();

        worldCommand.onEnable();
        worldGeneration.onEnable();

        api.tabComplete().register("world-any", (player, args) -> listWorlds());

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

        api.events().register(EntityPortalEvent.class, event -> {
            Location to = handlePortalRouting(event);
            if (to != null) {
                event.setTo(to);
            }
        }, EventPriority.HIGHEST, true);

        api.events().register(WorldLoadEvent.class, event -> {
            if (defaultWorld == null) {
                defaultWorld = event.getWorld();
            }
            ConfigSection worldConfig = getConfigSection(event.getWorld());
            seedDefaultDimensionLinks(event.getWorld().getName(), worldConfig);
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
            purgeWorldScopedData(event.getWorldName());
        }, EventPriority.MONITOR, false);

        registerSettingHandler(worldChangeRecorder, SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldDenySpawnSetting(), SettingCommandMode.FLAG);
        registerSettingHandler(new WorldForceSpawnSetting(), SettingCommandMode.FLAG);
        registerSettingHandler(new WorldGameModeSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldNetherSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldNoDamageSetting(), SettingCommandMode.FLAG);
        registerSettingHandler(new WorldNoHungerSetting(), SettingCommandMode.FLAG);
        registerNaturalFlagSettings();
        registerSettingHandler(new WorldEndSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldRandomSpawnSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldTickSpeedSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldTimeSetting(), SettingCommandMode.SUBCOMMAND);
        registerSettingHandler(new WorldWeatherSetting(), SettingCommandMode.SUBCOMMAND);
    }

    private void registerNaturalFlagSettings() {
        registerSettingHandler(
            WorldStateFlagSetting.blockIgnite("lava-fire", event -> event.getCause() == BlockIgniteEvent.IgniteCause.LAVA),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.lightning("lightning", event -> true),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockFromTo("water-flow", event -> event.getBlock().getType() == Material.WATER),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockFromTo("lava-flow", event -> event.getBlock().getType() == Material.LAVA),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockForm("snow-fall", WorldStateFlagSetting.formsMaterial(Material.SNOW)),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockFade("snow-melt", WorldStateFlagSetting.fadesMaterial(Material.SNOW)),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockForm("ice-form", WorldStateFlagSetting.formsMaterial(Material.ICE)),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockFade("ice-melt", WorldStateFlagSetting.fadesMaterial(Material.ICE)),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockFade("frosted-ice-melt", WorldStateFlagSetting.fadesMaterial(Material.FROSTED_ICE)),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.combined(
                "mushroom-growth",
                WorldStateFlagSetting.blockSpread("mushroom-growth", WorldStateFlagSetting.spreadsMushroom()),
                new WorldStateFlagSetting("mushroom-growth", WorldStateFlagSetting.structureGrow(WorldStateFlagSetting.growsHugeMushroom()))
            ),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.leavesDecay("leaf-decay"),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockSpread("grass-growth", WorldStateFlagSetting.spreadsMaterial(Material.GRASS_BLOCK)),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockSpread("mycelium-spread", WorldStateFlagSetting.spreadsMaterial(Material.MYCELIUM)),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.combined(
                "vine-growth",
                WorldStateFlagSetting.blockGrow("vine-growth", WorldStateFlagSetting.growsVineLike()),
                WorldStateFlagSetting.blockSpread("vine-growth", WorldStateFlagSetting.spreadsVineLike())
            ),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockGrow("rock-growth", WorldStateFlagSetting.growsRockLike()),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.sculkBloom("sculk-growth"),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockGrow("crop-growth", WorldStateFlagSetting.growsCropLike()),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockFade("soil-dry", WorldStateFlagSetting.fadesMaterial(Material.FARMLAND)),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockFade("coral-fade", WorldStateFlagSetting.fadesLiveCoral()),
            SettingCommandMode.FLAG
        );
        registerSettingHandler(
            WorldStateFlagSetting.blockForm("copper-fade", WorldStateFlagSetting.copperWeathering()),
            SettingCommandMode.FLAG
        );
    }

    public void completeStartupLoad() {
        if (startupLoadComplete) {
            return;
        }
        startupLoadComplete = true;

        loadWorlds();
        api.tasks().runLater(1L, this::retryExternalGeneratorWorldLoads);
    }

    /**
     * Called when the service is disabled.
     */
    @Override
    public void onDisable() {
        settings.forEach((key, value) -> value.setting().onDisable());

        settings.clear();

        if(worldChangeRecorder != null) worldChangeRecorder.onDisable();
        if(worldGeneration != null) worldGeneration.onDisable();
        if(worldCommand != null) worldCommand.onDisable();
    }

    /**
     * Get the world command instance.
     *
     * @return The WorldCommand instance.
     */
    public WorldCommand getCommand() {
        return worldCommand;
    }

    @Override
    public @NotNull World getDefaultWorld() {
        if (defaultWorld != null) {
            return defaultWorld;
        }

        World firstWorld = firstLoadedWorld();
        if (firstWorld != null) {
            defaultWorld = firstWorld;
            return firstWorld;
        }

        throw new IllegalStateException("No worlds are loaded yet.");
    }

    /**
     * Evict all players from the given world, teleporting them to the default world.
     *
     * @param world The world to evict players from.
     */
    @Override
    public void evictAllPlayers(@NotNull World world) {
        World firstWorld = firstLoadedWorld();
        if (firstWorld == null) {
            throw new IllegalStateException("Cannot evict players because no fallback world is loaded");
        }

        if (world.equals(firstWorld)) {
            throw new IllegalStateException("Cannot evict players from the main world");
        }

        world.getPlayers().forEach(player -> {
            api.messages().info(player, "WORLD_EVICTED", "world", world.getName());
            PlayerUtil.teleport(player, getDefaultWorld().getSpawnLocation());
        });
    }

    /**
     * Is the given world currently loaded?
     *
     * @param name The name of the world.
     * @return true if loaded, false otherwise.
     */
    @Override public boolean isWorldLoaded(@NotNull String name) {
        return Bukkit.getWorld(name) != null;
    }

    /**
     * Does the given world exist on disk or in config?
     *
     * @param name The name of the world.
     * @return true if exists, false otherwise.
     */
    @Override public boolean worldExists(@NotNull String name)   {
        return listWorlds().contains(name);
    }

    /**
     * Load the given world by name.
     *
     * @param name The name of the world.
     * @return The loaded World instance.
     */
    @Override public @Nullable World loadWorld(@NotNull String name) {
        clearLastWorldOperationError(name);

        try {
            return ensure(name, resolveStoredGenerator(name));
        } catch (RuntimeException exception) {
            rememberWorldOperationError(name, describeWorldOperationFailure(exception, true));
            plugin.getLogger().warning("Failed to load world '" + name + "': " + getLastWorldOperationErrorOrDefault(name));
            return null;
        }
    }

    /**
     * Unload the given world by name.
     *
     * @param name The name of the world.
     * @param save Whether to save the world before unloading.
     * @return true if unloaded, false otherwise.
     */
    @Override public boolean unloadWorld(@NotNull String name, boolean save) {
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
    @Override public @Nullable World createWorld(@NotNull String name, @NotNull String generatorName, @NotNull String generatorOptions) {
        return createWorld(name, generatorName, generatorOptions, null);
    }

    @Override public @Nullable World createWorld(@NotNull String name,
                                                 @NotNull String generatorName,
                                                 @NotNull String generatorOptions,
                                                 @Nullable Long seed) {
        clearLastWorldOperationError(name);

        String resolvedGeneratorName = generatorName.trim().isEmpty() ? "normal" : generatorName.trim();
        String resolvedGeneratorOptions = generatorOptions.trim();
        try {
            World world = ensure(
                name,
                resolveConfiguredGenerator(name, new ConfiguredGeneratorSpec(resolvedGeneratorName, resolvedGeneratorOptions)),
                seed
            );
            if (world != null) {
                ConfigSection config = getConfigSection(name);
                config.set("generator.key", resolvedGeneratorName);
                if (resolvedGeneratorOptions.isBlank()) {
                    config.set("generator.options", null);
                } else {
                    config.set("generator.options", resolvedGeneratorOptions);
                }
                seedDefaultDimensionLinks(name, config);
                config.save();
            }
            return world;
        } catch (RuntimeException exception) {
            rememberWorldOperationError(name, describeWorldOperationFailure(exception, false));
            plugin.getLogger().warning("Failed to create world '" + name + "': " + getLastWorldOperationErrorOrDefault(name));
            return null;
        }
    }

    void setStoredGenerator(@NotNull String worldName,
                            @NotNull String generatorName,
                            @NotNull String generatorOptions) {
        String resolvedGeneratorName = generatorName.trim().isEmpty() ? "normal" : generatorName.trim();
        String resolvedGeneratorOptions = generatorOptions.trim();

        resolveConfiguredGenerator(worldName, new ConfiguredGeneratorSpec(resolvedGeneratorName, resolvedGeneratorOptions));

        ConfigSection config = getConfigSection(worldName);
        config.set("generator.key", resolvedGeneratorName);
        if (resolvedGeneratorOptions.isBlank()) {
            config.set("generator.options", null);
        } else {
            config.set("generator.options", resolvedGeneratorOptions);
        }
        config.save();
    }

    /**
     * Check if a setting with the given key is registered.
     *
     * @param key The key of the setting.
     * @return True if the setting is registered, false otherwise.
     */
    @Override
    public boolean isSettingRegistered(@NotNull String key) {
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
    public boolean settingExists(@NotNull World world, @NotNull String key) {
        WorldBaseSetting setting = getSettingHandler(key);
        if(setting != null) {
            String value = setting.get(world, getConfigSection(world));
            return !value.equals("unset");
        }
        return false;
    }

    /**
     * Get the value of a setting for a specific world.
     *
     * @param world The world to get the setting for.
     * @param key The key of the setting.
     * @return The value of the setting, or null if not found.
     */
    @Override
    public @Nullable String getSetting(@NotNull World world, @NotNull String key) {
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
    public void setSetting(@NotNull World world, @NotNull String key, @NotNull String value) {
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
    public void registerSettingHandler(@NotNull WorldBaseSetting setting, @NotNull SettingCommandMode commandMode) {
        String key = setting.key();

        if(settings.containsKey(key)) {
            throw new IllegalArgumentException("A world setting with the key '" + key + "' is already registered.");
        }

        setting.onEnable(api, this);
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
     * Get the config section for specific world.
     *
     * @param worldName The name of the world.
     * @return The ConfigSection for the world.
     */
    @Override
    public @NotNull ConfigSection getConfigSection(@NotNull String worldName) {
        return getConfigSection().getSection(worldName);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ConfigSection getConfigSection(@NotNull World world) {
        return getConfigSection(world.getName());
    }

    @Nullable ConfigSection getExistingConfigSection(@NotNull String worldName) {
        if (!getConfigSection().isSection(worldName)) {
            return null;
        }
        return getConfigSection().getSection(worldName, false);
    }

    private @Nullable ChunkGenerator resolveStoredGenerator(@NotNull String worldName) {
        return resolveConfiguredGenerator(worldName, readConfiguredGenerator(getExistingConfigSection(worldName)));
    }

    private @Nullable ConfiguredGeneratorSpec readConfiguredGenerator(@Nullable ConfigSection config) {
        if (config == null) {
            return null;
        }

        Object shorthand = config.get("generator");
        if (shorthand instanceof String value) {
            String generatorKey = value.trim();
            if (!generatorKey.isEmpty()) {
                return new ConfiguredGeneratorSpec(generatorKey, "");
            }
        }

        ConfigSection generatorSection = config.getSection("generator", false);
        if (generatorSection == null) {
            return null;
        }

        Object rawKey = generatorSection.get("key");
        String generatorKey = rawKey instanceof String value ? value.trim() : "";
        if (generatorKey.isEmpty()) {
            return null;
        }

        Object rawOptions = generatorSection.get("options");
        String generatorOptions = rawOptions instanceof String value ? value.trim() : "";
        return new ConfiguredGeneratorSpec(generatorKey, generatorOptions);
    }

    private @Nullable ChunkGenerator resolveConfiguredGenerator(
        @NotNull String worldName,
        @Nullable ConfiguredGeneratorSpec generator
    ) {
        if (generator == null) {
            return null;
        }

        if (worldGeneration.isRegistered(generator.key())) {
            return worldGeneration.get(generator.key(), generator.options());
        }

        String bukkitGeneratorName = toBukkitGeneratorSpec(generator.key(), generator.options());
        ChunkGenerator resolved = WorldCreator.getGeneratorForName(worldName, bukkitGeneratorName, null);
        if (resolved == null) {
            throw new IllegalArgumentException("Unknown or unavailable Bukkit generator: " + bukkitGeneratorName);
        }
        return resolved;
    }

    private @NotNull String toBukkitGeneratorSpec(@NotNull String generatorKey, @NotNull String generatorOptions) {
        String key = generatorKey.trim();
        String options = generatorOptions.trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Generator name cannot be empty.");
        }
        if (options.isEmpty()) {
            return key;
        }
        if (key.contains(":")) {
            throw new IllegalArgumentException(
                "Generator '" + key + "' already includes an id; remove generator options or use plugin:id only."
            );
        }
        return key + ":" + options;
    }
  
    /**
     * Load settings for the world.
     *
     * @param world The world to load settings for.
     */
    private void loadWorldSettings(World world) {
        ConfigSection config = getConfigSection(world);
        migrateLegacyNestedSettingConfig(config);

        settings.forEach((key, value) -> value.setting().onWorldLoad(world, config));
    }

    /**
     * Migrates legacy nested setting paths created by older command dispatch logic
     * that passed per-setting subsections instead of the world root config section.
     *
     * @param config Root config section for a world.
     */
    private void migrateLegacyNestedSettingConfig(@NotNull ConfigSection config) {
        boolean changed = false;

        changed |= migrateLegacyNestedString(config, "deny-spawn");
        changed |= migrateLegacyNestedBoolean(config, "no-damage");
        changed |= migrateLegacyNestedBoolean(config, "no-hunger");
        changed |= migrateLegacyNestedString(config, "gamemode");
        changed |= migrateLegacyNestedString(config, "tickspeed");

        if (!config.contains("time.set") && config.contains("time.time.set")) {
            config.set("time.set", config.getLong("time.time.set", -1L));
            changed = true;
        }
        if (!config.contains("time.always") && config.contains("time.time.always")) {
            config.set("time.always", config.getBoolean("time.time.always", false));
            changed = true;
        }
        if (config.contains("time.time")) {
            config.set("time.time", null);
            changed = true;
        }

        if (!config.contains("weather.state") && config.contains("weather.weather.state")) {
            config.set("weather.state", config.getString("weather.weather.state", "unset"));
            changed = true;
        }
        if (!config.contains("weather.always") && config.contains("weather.weather.always")) {
            config.set("weather.always", config.getBoolean("weather.weather.always", false));
            changed = true;
        }
        if (config.contains("weather.weather")) {
            config.set("weather.weather", null);
            changed = true;
        }

        if (changed) {
            config.save();
        }
    }

    private boolean migrateLegacyNestedString(@NotNull ConfigSection config, @NotNull String key) {
        String legacyPath = key + "." + key;
        if (!config.contains(key) && config.contains(legacyPath)) {
            config.set(key, config.getString(legacyPath, ""));
            config.set(legacyPath, null);
            return true;
        }
        return false;
    }

    private boolean migrateLegacyNestedBoolean(@NotNull ConfigSection config, @NotNull String key) {
        String legacyPath = key + "." + key;
        if (!config.contains(key) && config.contains(legacyPath)) {
            config.set(key, config.getBoolean(legacyPath, false));
            config.set(legacyPath, null);
            return true;
        }
        return false;
    }

    /**
     * Unload settings for the world.
     *
     * @param world The world to unload settings for.
     */
    private void unloadWorldSettings(World world) {
        ConfigSection config = getConfigSection(world);

        settings.forEach((key, value) -> value.setting().onWorldUnload(world, config));
    }

    /**
     * Delete settings for the world.
     *
     * @param worldName The name of the world to delete settings for.
     */
    private void deleteWorldSettings(String worldName) {
        ConfigSection config = getConfigSection().getSection(worldName);

        settings.forEach((key, value) -> value.setting().onWorldDeleted(worldName, config));
    }

    private void purgeWorldScopedData(@NotNull String worldName) {
        if (worldName.isBlank()) {
            return;
        }

        purgeWorldRows("random_first_spawn_seen", worldName);
        purgeWorldRows("random_first_spawn_spawns", worldName);
        purgeWorldRows("player_world_last_locations", worldName);
        purgeWorldRows("world_changes_blocks", worldName);
        purgeWorldRows("world_changes_entities", worldName);
    }

    private void purgeWorldRows(@NotNull String table, @NotNull String worldName) {
        if (!tableExists(table)) {
            return;
        }

        api.database().update(
            "DELETE FROM " + table + " WHERE lower(world) = lower(?)",
            ps -> ps.setString(1, worldName)
        );
    }

    private boolean tableExists(@NotNull String table) {
        Integer exists = api.database().querySingleMapped(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            ps -> ps.setString(1, table),
            rs -> rs.getInt(1)
        );
        return exists != null && exists == 1;
    }

    /**
     * Handle portal routing for multi-world setups.
     *
     * @param event The PlayerPortalEvent to handle.
     * @return The target Location, or null if no special routing is needed.
     */
    private @Nullable Location handlePortalRouting(@NotNull PlayerPortalEvent event) {
        return handlePortalRouting(event.getFrom(), portalTypeFrom(event.getCause()));
    }

    private @Nullable Location handlePortalRouting(@NotNull EntityPortalEvent event) {
        return handlePortalRouting(event.getFrom(), event.getPortalType());
    }

    private @Nullable Location handlePortalRouting(@Nullable Location from, @Nullable PortalType portalType) {
        if (from == null || (portalType != PortalType.NETHER && portalType != PortalType.ENDER)) {
            return null;
        }

        World fromWorld = from.getWorld();
        if (fromWorld == null) {
            return null;
        }

        String base = WorldUtil.baseName(fromWorld.getName());
        if (base == null || base.isEmpty()) {
            return null;
        }

        String targetName;
        if (portalType == PortalType.NETHER) {
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                targetName = resolveLinkedDimension(base, "nether-world", base + "_nether");
            } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
                targetName = resolveOverworldForDimension(fromWorld.getName(), "nether-world", "_nether", base);
            } else {
                return null;
            }
        } else {
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                targetName = resolveLinkedDimension(base, "end-world", base + "_the_end");
            } else if (fromWorld.getEnvironment() == World.Environment.THE_END) {
                targetName = resolveOverworldForDimension(fromWorld.getName(), "end-world", "_the_end", base);
            } else {
                return null;
            }
        }

        World targetWorld = Bukkit.getWorld(targetName);
        if (targetWorld == null) {
            targetWorld = loadWorld(targetName);
        }
        if (targetWorld == null) {
            return null;
        }

        Location to = from.clone();
        to.setWorld(targetWorld);

        if (portalType == PortalType.NETHER) {
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

    private @NotNull String resolveLinkedDimension(@NotNull String baseWorld,
                                                   @NotNull String configKey,
                                                   @NotNull String defaultValue) {
        ConfigSection worldConfig = getConfigSection(baseWorld);
        String configured = worldConfig.getString(configKey, "").trim();
        if (configured.isEmpty() || configured.equalsIgnoreCase("unset")) {
            return defaultValue;
        }
        return configured;
    }

    private @NotNull String resolveOverworldForDimension(@NotNull String dimensionWorld,
                                                         @NotNull String configKey,
                                                         @NotNull String defaultSuffix,
                                                         @NotNull String fallbackBase) {
        for (String worldName : listWorlds()) {
            if (WorldUtil.resolveEnvironment(worldName) != World.Environment.NORMAL) {
                continue;
            }

            String linked = resolveLinkedDimension(worldName, configKey, worldName + defaultSuffix);
            if (linked.equalsIgnoreCase(dimensionWorld)) {
                return worldName;
            }
        }

        return fallbackBase;
    }

    private @Nullable PortalType portalTypeFrom(@NotNull PlayerTeleportEvent.TeleportCause cause) {
        return switch (cause) {
            case NETHER_PORTAL -> PortalType.NETHER;
            case END_PORTAL -> PortalType.ENDER;
            default -> null;
        };
    }

    private void seedDefaultDimensionLinks(@NotNull String worldName, @NotNull ConfigSection config) {
        if (WorldUtil.resolveEnvironment(worldName) != World.Environment.NORMAL) {
            return;
        }

        if (!config.contains("nether-world")) {
            config.set("nether-world", worldName + "_nether");
        }
        if (!config.contains("end-world")) {
            config.set("end-world", worldName + "_the_end");
        }
    }

    /**
     * Ensure the world with the given name is loaded, creating it if necessary.
     *
     * @param name The name of the world.
     * @param gen The chunk generator to use, or null for default.
     * @return The World instance.
     */
    private World ensure(String name, ChunkGenerator gen) {
        return ensure(name, gen, null);
    }

    private World ensure(String name, ChunkGenerator gen, @Nullable Long seed) {
        clearLastWorldOperationError(name);

        World w = Bukkit.getWorld(name);
        if (w != null) {
            return w;
        }

        World.Environment env = WorldUtil.resolveEnvironment(name);

        WorldCreator wc = new WorldCreator(name).environment(env);
        if (gen != null) wc.generator(gen);
        if (seed != null) wc.seed(seed);

        World world;
        try {
            world = wc.createWorld();
        } catch (RuntimeException exception) {
            boolean existingWorldFolder = Files.exists(worldRoot(name));
            rememberWorldOperationError(name, describeWorldOperationFailure(exception, existingWorldFolder));
            plugin.getLogger().warning("Failed to load world '" + name + "': " + getLastWorldOperationErrorOrDefault(name));
            return null;
        }

        if (world != null) {
            getConfigSection().set(name + ".load", true);
            saveConfig();
            loadWorldSettings(world);
            clearLastWorldOperationError(name);
        } else {
            rememberWorldOperationError(name, "Bukkit returned null while creating or loading the world.");
        }

        return world;
    }

    /**
     * Delete the world with the given name from disk.
     *
     * @param name The name of the world.
     * @throws IOException If an I/O error occurs.
     */
    @Override public void deleteWorld(@NotNull String name) throws IOException {
        requireUnloaded(name);
        Path root = worldRoot(name);
        if (!Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            for (Path path : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
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
    @Override public void renameWorld(@NotNull String oldName, @NotNull String newName) throws IOException {
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
    @Override public void duplicateWorld(@NotNull String src, @NotNull String dst) throws IOException {
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
    @Override public @NotNull List<String> listWorlds() {
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
                    Path fileName = p.getFileName();
                    if (fileName != null) {
                        names.add(fileName.toString());
                    }
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to list worlds: " + exception.getMessage());
        }

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
    @Override public @NotNull Path getWorldFolder(@NotNull String name) {
        return worldRoot(name);
    }

    /**
     * Get the WorldGeneration service instance.
     *
     * @return The WorldGeneration instance.
     */
    @Override
    public @NotNull WorldGeneration generator() {
        return worldGeneration;
    }

    @Nullable String getLastWorldOperationError(@NotNull String worldName) {
        return lastWorldOperationErrors.get(worldErrorKey(worldName));
    }

    @NotNull String getLastWorldOperationErrorOrDefault(@NotNull String worldName) {
        String error = getLastWorldOperationError(worldName);
        return error == null || error.isBlank() ? DEFAULT_WORLD_OPERATION_ERROR : error;
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
     * Load worlds based on config and disk state.
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
                            api.messages().log("WORLD_CONFIG_LOADED", "world", worldName);
                        } else {
                            api.messages().warn("WORLD_CONFIG_FAILED_LOAD",
                                "world", worldName,
                                "reason", getLastWorldOperationErrorOrDefault(worldName));
                        }
                    }
                } else {
                    api.messages().log("WORLD_CONFIG_UNLOADED", "world", worldName);
                }
            }
        }
    }

    private void retryExternalGeneratorWorldLoads() {
        ConfigSection worldsSection = getConfigSection();
        for (String worldName : worldsSection.getKeys(false)) {
            if (!worldExists(worldName) || isWorldLoaded(worldName)) {
                continue;
            }
            if (!worldsSection.getBoolean(worldName + ".load", false)) {
                continue;
            }

            ConfiguredGeneratorSpec configuredGenerator = readConfiguredGenerator(getExistingConfigSection(worldName));
            if (configuredGenerator == null || worldGeneration.isRegistered(configuredGenerator.key())) {
                continue;
            }

            World world = loadWorld(worldName);
            if (world != null) {
                api.messages().log("WORLD_CONFIG_LOADED", "world", worldName);
            } else {
                api.messages().warn("WORLD_CONFIG_FAILED_LOAD",
                    "world", worldName,
                    "reason", getLastWorldOperationErrorOrDefault(worldName));
            }
        }
    }

    /**
     * Get or create a world change session for the given world.
     *
     * @param world The world to get the session for.
     * @return The WorldChangeSession instance.
     */
    public @NotNull WorldChangeSession changes(@NotNull World world) {
        return worldChangeRecorder.getSession(world);
    }

    private @Nullable World firstLoadedWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    private void rememberWorldOperationError(@NotNull String worldName, @NotNull String message) {
        lastWorldOperationErrors.put(worldErrorKey(worldName), message);
    }

    private void clearLastWorldOperationError(@NotNull String worldName) {
        lastWorldOperationErrors.remove(worldErrorKey(worldName));
    }

    private @NotNull String worldErrorKey(@NotNull String worldName) {
        return worldName.toLowerCase(Locale.ROOT);
    }

    private @NotNull String describeWorldOperationFailure(@NotNull Throwable throwable, boolean existingWorldFolder) {
        Throwable root = rootCause(throwable);
        String detail = root.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = root.getClass().getSimpleName();
        }

        String normalized = detail.trim();
        if (normalized.equalsIgnoreCase("Overworld settings missing")) {
            return existingWorldFolder
                ? "existing world data is incomplete or invalid (missing overworld settings in level.dat)"
                : "world data is incomplete or invalid (missing overworld settings)";
        }
        return normalized;
    }

    private @NotNull Throwable rootCause(@NotNull Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
