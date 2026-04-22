package dev.stemcraft.minigame.tntrun;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TntRunConfig {
    private static final int DEFAULT_START_COUNTDOWN_SECONDS = 10;
    private static final int DEFAULT_ROUND_SECONDS = 180;
    private static final int DEFAULT_ENDING_SECONDS = 8;
    private static final int DEFAULT_FADE_DELAY_TICKS = 8;

    private final STEMCraftAPI api;
    private ConfigSection config;

    public TntRunConfig(STEMCraftAPI api, TntRunMiniGame tntRun) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("arenas");
    }

    public @NotNull TntRunArenaRecord load(@NotNull String arenaId, @NotNull ConfigSection section) {
        boolean enabled = section.getBoolean("enabled", true);

        String worldName = section.getString("world");
        if (worldName.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("World not defined for arena '" + arenaId + "'.");
        }

        World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);

        Location lobby = loadLocation(section, world, arenaId, "lobby", true);
        Location spectator = loadLocation(section, world, arenaId, "spectator", false);
        if (spectator == null) {
            spectator = lobby;
        }

        SCRegion arenaRegion = loadRegion(section, world, arenaId);
        List<SCRegion> floorRegions = loadRegions(section, world, arenaId);
        List<Location> startingGrid = loadLocations(section, world, arenaId);
        int minPlayers = section.getInt("min-players", 2);
        int maxPlayers = section.getInt("max-players", Math.max(2, startingGrid.size()));
        int startCountdownSeconds = section.getInt("start-countdown-seconds", DEFAULT_START_COUNTDOWN_SECONDS);
        int roundSeconds = section.getInt("round-seconds", DEFAULT_ROUND_SECONDS);
        int endingSeconds = section.getInt("ending-seconds", DEFAULT_ENDING_SECONDS);
        int fadeDelayTicks = section.getInt("fade-delay-ticks", DEFAULT_FADE_DELAY_TICKS);
        int voidY = section.getInt("void-y", arenaRegion.getMinimumLocation().getBlockY() - 6);
        String name = section.getString("name", StringUtil.beautify(arenaId));

        return new TntRunArenaRecord(
            arenaId,
            enabled,
            name,
            world,
            lobby,
            spectator,
            arenaRegion,
            floorRegions,
            startingGrid,
            minPlayers,
            maxPlayers,
            startCountdownSeconds,
            roundSeconds,
            endingSeconds,
            fadeDelayTicks,
            voidY
        );
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        ensureLoaded();
        validateArenaForSave(arena);

        ConfigSection arenaConfig = config.createSection("arenas." + arena.id(), true);
        arenaConfig.set("enabled", arena.getStatus() != MiniGameArena.ArenaStatus.DISABLED);
        arenaConfig.set("world", arena.world().getName());
        arenaConfig.set("name", arena.getName());
        arenaConfig.set("lobby", serializeLocation(arena.getLobbySpawn(), arena.id(), "lobby"));
        arenaConfig.set("spectator", serializeLocation(arena.getSpectatorSpawn(), arena.id(), "spectator"));
        arenaConfig.set("arena", serializeRegion(arena.get("arenaRegion", SCRegion.class), arena.id(), "arena"));
        arenaConfig.set("floors", serializeRegions(floorRegions(arena), arena.id()));
        arenaConfig.set("starting-grid", serializeLocations(startingGrid(arena), arena.id()));
        arenaConfig.set("min-players", arena.getMinPlayers());
        arenaConfig.set("max-players", arena.getMaxPlayers());
        arenaConfig.set("start-countdown-seconds", arena.get("startCountdownSeconds", Integer.class, DEFAULT_START_COUNTDOWN_SECONDS));
        arenaConfig.set("round-seconds", arena.get("roundSeconds", Integer.class, DEFAULT_ROUND_SECONDS));
        arenaConfig.set("ending-seconds", arena.get("endingSeconds", Integer.class, DEFAULT_ENDING_SECONDS));
        arenaConfig.set("fade-delay-ticks", arena.get("fadeDelayTicks", Integer.class, DEFAULT_FADE_DELAY_TICKS));
        arenaConfig.set("void-y", arena.get("voidY", Integer.class, arena.world().getMinHeight()));

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

    @SuppressWarnings("unchecked")
    private @NotNull List<SCRegion> floorRegions(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("floorRegions", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    private @NotNull List<Location> startingGrid(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("startingGrid", List.class, ArrayList::new);
    }

    private void validateArenaForSave(@NotNull MiniGameArena arena) {
        if (arena.getLobbySpawn() == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a lobby spawn.");
        }
        if (arena.getSpectatorSpawn() == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a spectator spawn.");
        }
        if (arena.get("arenaRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing an arena region.");
        }
        if (floorRegions(arena).isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing floor regions.");
        }
        if (startingGrid(arena).isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing starting grid locations.");
        }
    }

    private @NotNull SCRegion loadRegion(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId) {
        String regionString = section.getString("arena");
        if (regionString.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena" + " region for arena '" + arenaId + "' is not defined.");
        }

        SCRegion region = SCRegion.fromString(regionString, world);
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Arena" + " region for arena '" + arenaId + "' is invalid.");
        }
        return region;
    }

    private @NotNull List<SCRegion> loadRegions(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId) {
        List<SCRegion> regions = new ArrayList<>();
        List<String> values = section.getStringList("floors");
        int index = 1;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                index++;
                continue;
            }

            SCRegion region = SCRegion.fromString(value, world);
            if (region == null) {
                throw new MiniGameInvalidArenaConfigException("Floor" + " region #" + index + " for arena '" + arenaId + "' is invalid.");
            }
            regions.add(region);
            index++;
        }
        return regions;
    }

    private @Nullable Location loadLocation(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId, @NotNull String key, boolean required) {
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

    private @NotNull List<Location> loadLocations(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId) {
        List<String> values = section.getStringList("starting-grid");
        if (values.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Location list '" + "starting-grid" + "' for arena '" + arenaId + "' is not defined.");
        }

        List<Location> locations = new ArrayList<>();
        int index = 1;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                index++;
                continue;
            }

            Location location = LocationUtil.deserialize(value, world);
            if (location == null) {
                throw new MiniGameInvalidArenaConfigException("Location '" + "starting-grid" + "' #" + index + " for arena '" + arenaId + "' is invalid.");
            }
            locations.add(location);
            index++;
        }
        return locations;
    }

    private @NotNull String serializeLocation(@Nullable Location location, @NotNull String arenaId, @NotNull String name) {
        if (location == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return LocationUtil.serialize(location, false, true);
    }

    private @NotNull List<String> serializeLocations(@NotNull List<Location> locations, @NotNull String arenaId) {
        if (locations.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + "starting-grid" + ".");
        }
        List<String> values = new ArrayList<>();
        for (Location location : locations) {
            values.add(serializeLocation(location, arenaId, "starting-grid"));
        }
        return values;
    }

    private @NotNull String serializeRegion(@Nullable SCRegion region, @NotNull String arenaId, @NotNull String name) {
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return region.toString();
    }

    private @NotNull List<String> serializeRegions(@NotNull List<SCRegion> regions, @NotNull String arenaId) {
        if (regions.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + "floors" + ".");
        }
        List<String> values = new ArrayList<>();
        for (SCRegion region : regions) {
            values.add(serializeRegion(region, arenaId, "floors"));
        }
        return values;
    }

    private void ensureLoaded() {
        if (config == null) {
            throw new IllegalStateException("TNT Run config is not loaded.");
        }
    }
}
