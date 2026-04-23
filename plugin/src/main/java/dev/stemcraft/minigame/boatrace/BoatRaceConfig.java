package dev.stemcraft.minigame.boatrace;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoatRaceConfig {
    static final int DEFAULT_START_COUNTDOWN_SECONDS = 30;
    static final int DEFAULT_ENDING_SECONDS = 20;
    private final STEMCraftAPI api;
    private ConfigSection config;

    public BoatRaceConfig(STEMCraftAPI api, BoatRaceMiniGame boatRace) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("arenas");
    }

    public @NotNull BoatRaceArenaRecord load(@NotNull String arenaId, @NotNull ConfigSection section) {
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

        SCRegion arenaRegion = loadRegion(section, world, arenaId, "arena", "Arena");
        SCRegion finishRegion = loadRegion(section, world, arenaId, "finish", "Finish");
        List<SCRegion> stages = loadRegions(section, world, arenaId, "stages", "Stage");
        List<Location> grid = loadLocations(section, world, arenaId, "starting-grid", true);
        int minPlayers = section.getInt("min-players", 1);
        int maxPlayers = section.getInt("max-players", Math.max(1, grid.size()));
        int startCountdownSeconds = section.getInt("start-countdown-seconds", DEFAULT_START_COUNTDOWN_SECONDS);
        int endingSeconds = section.getInt("ending-seconds", DEFAULT_ENDING_SECONDS);
        String name = section.getString("name", StringUtil.beautify(arenaId));
        Map<UUID, BoatRaceArenaRecord.BestTime> bestTimes = new LinkedHashMap<>();
        ConfigSection records = section.getSection("records", false);
        if (records != null) {
            for (String uuidText : records.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidText);
                } catch (IllegalArgumentException exception) {
                    continue;
                }

                ConfigSection recordSection = records.getSection(uuidText, false);
                if (recordSection == null) {
                    continue;
                }

                long timeMillis = recordSection.getLong("time", -1L);
                if (timeMillis < 0L) {
                    continue;
                }

                bestTimes.put(uuid, new BoatRaceArenaRecord.BestTime(
                    uuid,
                    recordSection.getString("name", "Unknown"),
                    timeMillis
                ));
            }
        }

        return new BoatRaceArenaRecord(
            arenaId,
            enabled,
            name,
            world,
            lobby,
            spectator,
            arenaRegion,
            finishRegion,
            stages,
            grid,
            minPlayers,
            maxPlayers,
            startCountdownSeconds,
            endingSeconds,
            bestTimes
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
        arenaConfig.set("finish", serializeRegion(arena.get("finishRegion", SCRegion.class), arena.id(), "finish"));
        arenaConfig.set("stages", serializeRegions(stageRegions(arena), arena.id(), "stages"));
        arenaConfig.set("starting-grid", serializeGridLocations(startingGrid(arena), arena.id(), "starting-grid"));
        arenaConfig.set("min-players", arena.getMinPlayers());
        arenaConfig.set("max-players", arena.getMaxPlayers());
        arenaConfig.set("start-countdown-seconds", arena.get("startCountdownSeconds", Integer.class, DEFAULT_START_COUNTDOWN_SECONDS));
        arenaConfig.set("ending-seconds", arena.get("endingSeconds", Integer.class, DEFAULT_ENDING_SECONDS));
        ConfigSection records = arenaConfig.createSection("records", true);
        for (BoatRaceArenaRecord.BestTime bestTime : bestTimes(arena).values()) {
            ConfigSection record = records.createSection(bestTime.playerId().toString(), true);
            record.set("name", bestTime.playerName());
            record.set("time", bestTime.timeMillis());
        }

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

    public boolean setArenaEnabled(@NotNull String arenaId, boolean enabled) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas", false);
        if (arenas == null || !arenas.isSection(arenaId)) {
            return false;
        }

        ConfigSection arenaConfig = arenas.getSection(arenaId, false);
        if (arenaConfig == null) {
            return false;
        }

        arenaConfig.set("enabled", enabled);
        config.save();
        return true;
    }

    public @Nullable ConfigSection getSection(String path) {
        if (config == null) {
            return null;
        }
        return config.getSection(path, false);
    }

    @SuppressWarnings("unchecked")
    private List<SCRegion> stageRegions(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("stageRegions", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    private List<Location> startingGrid(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("startingGrid", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, BoatRaceArenaRecord.BestTime> bestTimes(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("bestTimes", Map.class, LinkedHashMap::new);
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
        if (arena.get("finishRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a finish region.");
        }
        if (startingGrid(arena).isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing starting grid locations.");
        }
    }

    private @NotNull SCRegion loadRegion(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId, @NotNull String key, @NotNull String title) {
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

    private @NotNull List<SCRegion> loadRegions(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId, @NotNull String key, @NotNull String title) {
        List<SCRegion> regions = new ArrayList<>();
        List<String> values = section.getStringList(key);
        int index = 1;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                index++;
                continue;
            }
            SCRegion region = SCRegion.fromString(value, world);
            if (region == null) {
                throw new MiniGameInvalidArenaConfigException(title + " region #" + index + " for arena '" + arenaId + "' is invalid.");
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

    private @NotNull List<Location> loadLocations(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId, @NotNull String key, boolean required) {
        List<String> values = section.getStringList(key);
        if (values.isEmpty() && required) {
            throw new MiniGameInvalidArenaConfigException("Location list '" + key + "' for arena '" + arenaId + "' is not defined.");
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
                throw new MiniGameInvalidArenaConfigException("Location '" + key + "' #" + index + " for arena '" + arenaId + "' is invalid.");
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
        return LocationUtil.serialize(location, false, false);
    }

    private @NotNull List<String> serializeLocations(@NotNull List<Location> locations, @NotNull String arenaId, @NotNull String name) {
        if (locations.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        List<String> values = new ArrayList<>();
        for (Location location : locations) {
            values.add(serializeLocation(location, arenaId, name));
        }
        return values;
    }

    private @NotNull List<String> serializeGridLocations(@NotNull List<Location> locations, @NotNull String arenaId, @NotNull String name) {
        if (locations.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        List<String> values = new ArrayList<>();
        for (Location location : locations) {
            if (location == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
            }
            values.add(LocationUtil.serialize(location, false, true));
        }
        return values;
    }

    private @NotNull String serializeRegion(@Nullable SCRegion region, @NotNull String arenaId, @NotNull String name) {
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return region.toString();
    }

    private @NotNull List<String> serializeRegions(@NotNull List<SCRegion> regions, @NotNull String arenaId, @NotNull String name) {
        List<String> values = new ArrayList<>();
        for (SCRegion region : regions) {
            values.add(serializeRegion(region, arenaId, name));
        }
        return values;
    }

    private void ensureLoaded() {
        if (config == null) {
            throw new IllegalStateException("Boat Race config is not loaded.");
        }
    }
}
