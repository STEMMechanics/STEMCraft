package dev.stemcraft.minigame.minefield;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.service.region.RegionLocationSupport;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class MinefieldConfig {
    static final int DEFAULT_START_COUNTDOWN_SECONDS = 30;
    static final int DEFAULT_ENDING_SECONDS = 20;
    static final double DEFAULT_MINE_RATIO = 0.18d;
    static final Material DEFAULT_HIDDEN_BLOCK = Material.COBBLESTONE;
    static final Material DEFAULT_CLEAR_BLOCK = Material.WHITE_CONCRETE;
    static final Material DEFAULT_ADJACENT_BLOCK = Material.BLUE_CONCRETE;
    static final Material DEFAULT_TRIGGERED_MINE_BLOCK = Material.TNT;
    static final int DEFAULT_COMPLETION_BONUS = 10;

    private final STEMCraftAPI api;
    private ConfigSection config;

    public MinefieldConfig(STEMCraftAPI api, MinefieldMiniGame minefield) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("arenas");
    }

    public @NotNull MinefieldArenaRecord load(@NotNull String arenaId, @NotNull ConfigSection section) {
        boolean enabled = section.getBoolean("enabled", true);

        String worldName = section.getString("world");
        if (worldName.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("World not defined for arena '" + arenaId + "'.");
        }
        if (Bukkit.getWorld(worldName) == null) {
            throw new MiniGameInvalidArenaConfigException("World '" + worldName + "' for arena '" + arenaId + "' does not exist.");
        }

        World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);

        SCRegion arenaRegion = loadRegion(section, world, arenaId, "arena", "Arena");
        SCRegion startRegion = loadRegion(section, world, arenaId, "start", "Start");
        SCRegion fieldRegion = loadRegion(section, world, arenaId, "field", "Field");
        SCRegion finishRegion = loadRegion(section, world, arenaId, "finish", "Finish");
        Location spectator = loadLocation(section, world, arenaId, "spectator", false);
        if (spectator == null) {
            spectator = startSpawn(startRegion);
        }

        return new MinefieldArenaRecord(
            arenaId,
            enabled,
            section.getString("name", StringUtil.beautify(arenaId)),
            worldName,
            spectator,
            arenaRegion,
            startRegion,
            fieldRegion,
            finishRegion,
            section.getInt("min-players", 1),
            section.getInt("max-players", 12),
            section.getInt("start-countdown-seconds", DEFAULT_START_COUNTDOWN_SECONDS),
            section.getInt("ending-seconds", DEFAULT_ENDING_SECONDS),
            section.getDouble("mine-ratio", DEFAULT_MINE_RATIO),
            loadMaterial(section, "hidden-block", DEFAULT_HIDDEN_BLOCK),
            loadMaterial(section, "clear-block", DEFAULT_CLEAR_BLOCK),
            loadMaterial(section, "adjacent-block", DEFAULT_ADJACENT_BLOCK),
            loadMaterial(section, "triggered-mine-block", DEFAULT_TRIGGERED_MINE_BLOCK),
            section.getInt("completion-bonus", DEFAULT_COMPLETION_BONUS)
        );
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        ensureLoaded();
        validateArenaForSave(arena);

        ConfigSection arenaConfig = config.createSection("arenas." + arena.id(), true);
        arenaConfig.set("enabled", arena.getStatus() != MiniGameArena.ArenaStatus.DISABLED);
        arenaConfig.set("world", arena.world().getName());
        arenaConfig.set("name", arena.getName());
        arenaConfig.set("spectator", serializeLocation(arena.getSpectatorSpawn(), arena.id(), "spectator"));
        arenaConfig.set("arena", serializeRegion(arena.get(MinefieldMiniGame.ARENA_REGION_KEY, SCRegion.class), arena.id(), "arena"));
        arenaConfig.set("start", serializeRegion(arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class), arena.id(), "start"));
        arenaConfig.set("field", serializeRegion(arena.get(MinefieldMiniGame.FIELD_REGION_KEY, SCRegion.class), arena.id(), "field"));
        arenaConfig.set("finish", serializeRegion(arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class), arena.id(), "finish"));
        arenaConfig.set("min-players", arena.getMinPlayers());
        arenaConfig.set("max-players", arena.getMaxPlayers());
        arenaConfig.set("start-countdown-seconds", arena.get(MinefieldMiniGame.START_COUNTDOWN_SECONDS_KEY, Integer.class, DEFAULT_START_COUNTDOWN_SECONDS));
        arenaConfig.set("ending-seconds", arena.get(MinefieldMiniGame.ENDING_SECONDS_KEY, Integer.class, DEFAULT_ENDING_SECONDS));
        arenaConfig.set("mine-ratio", arena.get(MinefieldMiniGame.MINE_RATIO_KEY, Double.class, DEFAULT_MINE_RATIO));
        arenaConfig.set("hidden-block", serializeMaterial(arena.get(MinefieldMiniGame.HIDDEN_BLOCK_KEY, Material.class, DEFAULT_HIDDEN_BLOCK)));
        arenaConfig.set("clear-block", serializeMaterial(arena.get(MinefieldMiniGame.CLEAR_BLOCK_KEY, Material.class, DEFAULT_CLEAR_BLOCK)));
        arenaConfig.set("adjacent-block", serializeMaterial(arena.get(MinefieldMiniGame.ADJACENT_BLOCK_KEY, Material.class, DEFAULT_ADJACENT_BLOCK)));
        arenaConfig.set("triggered-mine-block", serializeMaterial(arena.get(MinefieldMiniGame.TRIGGERED_MINE_BLOCK_KEY, Material.class, DEFAULT_TRIGGERED_MINE_BLOCK)));
        arenaConfig.set("completion-bonus", arena.get(MinefieldMiniGame.COMPLETION_BONUS_KEY, Integer.class, DEFAULT_COMPLETION_BONUS));

        config.save();
    }

    public void deleteArena(@NotNull String arenaId) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas");
        arenas.remove(arenaId);
        config.save();
    }

    public boolean hasArena(@NotNull String arenaId) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas", false);
        return arenas != null && arenas.isSection(arenaId);
    }

    public void setArenaEnabled(@NotNull String arenaId, boolean enabled) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas", false);
        if (arenas == null || !arenas.isSection(arenaId)) {
            return;
        }

        ConfigSection arenaConfig = arenas.getSection(arenaId, false);
        if (arenaConfig == null) {
            return;
        }

        arenaConfig.set("enabled", enabled);
        config.save();
    }

    public @Nullable ConfigSection getSection(String path) {
        if (config == null) {
            return null;
        }
        return config.getSection(path, false);
    }

    private void validateArenaForSave(@NotNull MiniGameArena arena) {
        if (arena.get(MinefieldMiniGame.ARENA_REGION_KEY, SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing an arena region.");
        }
        if (arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a start region.");
        }
        if (arena.get(MinefieldMiniGame.FIELD_REGION_KEY, SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a field region.");
        }
        if (arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a finish region.");
        }
        if (arena.getSpectatorSpawn() == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a spectator spawn.");
        }
    }

    private @NotNull SCRegion loadRegion(
        @NotNull ConfigSection section,
        @NotNull World world,
        @NotNull String arenaId,
        @NotNull String key,
        @NotNull String title
    ) {
        String regionString = section.getString(key);
        if (regionString.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException(title + " region for arena '" + arenaId + "' is not defined.");
        }

        SCRegion region = SCRegion.fromString(regionString, world);
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException(title + " region for arena '" + arenaId + "' is invalid.");
        }
        return region;
    }

    private @Nullable Location loadLocation(
        @NotNull ConfigSection section,
        @NotNull World world,
        @NotNull String arenaId,
        @NotNull String key,
        boolean required
    ) {
        String locationString = section.getString(key);
        if (locationString.isEmpty()) {
            if (required) {
                throw new MiniGameInvalidArenaConfigException("Location '" + key + "' for arena '" + arenaId + "' is not defined.");
            }
            return null;
        }

        Location location = LocationUtil.deserialize(locationString, world);
        if (location == null && required) {
            throw new MiniGameInvalidArenaConfigException("Location '" + key + "' for arena '" + arenaId + "' is invalid.");
        }
        return location;
    }

    private @NotNull Material loadMaterial(@NotNull ConfigSection section, @NotNull String key, @NotNull Material fallback) {
        String value = section.getString(key, fallback.name());
        Material material = Material.matchMaterial(value.trim().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir()) {
            throw new MiniGameInvalidArenaConfigException("Material '" + value + "' for key '" + key + "' is invalid.");
        }
        return material;
    }

    private @NotNull String serializeLocation(@Nullable Location location, @NotNull String arenaId, @NotNull String name) {
        if (location == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return LocationUtil.serialize(location, false, true);
    }

    private @NotNull String serializeRegion(@Nullable SCRegion region, @NotNull String arenaId, @NotNull String name) {
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return region.toString();
    }

    private @NotNull String serializeMaterial(@NotNull Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    private @Nullable Location startSpawn(@Nullable SCRegion startRegion) {
        if (startRegion == null || startRegion.getWorld() == null) {
            return null;
        }

        Location min = startRegion.getMinimumLocation();
        Location max = startRegion.getMaximumLocation();
        Location center = new Location(
            startRegion.getWorld(),
            (min.getBlockX() + max.getBlockX() + 1) / 2.0d,
            min.getBlockY(),
            (min.getBlockZ() + max.getBlockZ() + 1) / 2.0d
        );
        if (startRegion.contains(center)) {
            return center;
        }

        Location ground = RegionLocationSupport.randomGroundLocation(startRegion);
        return ground != null ? ground : RegionLocationSupport.randomLocation(startRegion);
    }

    private void ensureLoaded() {
        if (config == null) {
            throw new IllegalStateException("Minefield config is not loaded.");
        }
    }
}
