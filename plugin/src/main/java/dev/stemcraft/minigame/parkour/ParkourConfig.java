package dev.stemcraft.minigame.parkour;

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ParkourConfig {
    private final STEMCraftAPI api;
    private ConfigSection config;

    public ParkourConfig(STEMCraftAPI api, ParkourMiniGame parkour) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("arenas");
    }

    public @NotNull ParkourArenaRecord load(@NotNull String arenaId, @NotNull ConfigSection section) {
        boolean enabled = section.getBoolean("enabled", true);

        String worldName = section.getString("world");
        if (worldName.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("World not defined for arena '" + arenaId + "'.");
        }

        World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);

        SCRegion lobbyRegion = loadRegion(section, world, "lobby");
        if (lobbyRegion == null) {
            lobbyRegion = loadRegion(section, world, "start");
        }
        if (lobbyRegion == null) {
            Location legacySpawn = loadLocation(section, world, arenaId);
            if (legacySpawn != null) {
                lobbyRegion = singleBlockRegion(legacySpawn);
            }
        }
        SCRegion arenaRegion = loadRegion(section, world, arenaId, "arena", "Arena");
        SCRegion finishRegion = loadRegion(section, world, arenaId, "finish", "Finish");
        String name = section.getString("name", StringUtil.beautify(arenaId));

        Map<UUID, ParkourArenaRecord.BestTime> bestTimes = new LinkedHashMap<>();
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

                bestTimes.put(uuid, new ParkourArenaRecord.BestTime(
                    uuid,
                    recordSection.getString("name", "Unknown"),
                    timeMillis
                ));
            }
        }

        return new ParkourArenaRecord(
            arenaId,
            enabled,
            name,
            world,
            lobbyRegion,
            arenaRegion,
            finishRegion,
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
        arenaConfig.set("lobby", serializeRegion(arena.get("lobbyRegion", SCRegion.class), arena.id(), "lobby"));
        arenaConfig.remove("spawn");
        arenaConfig.set("arena", serializeRegion(arena.get("arenaRegion", SCRegion.class), arena.id(), "arena"));
        arenaConfig.remove("start");
        arenaConfig.set("finish", serializeRegion(arena.get("finishRegion", SCRegion.class), arena.id(), "finish"));
        arenaConfig.remove("min-players");
        arenaConfig.remove("max-players");

        ConfigSection records = arenaConfig.createSection("records", true);
        for (ParkourArenaRecord.BestTime bestTime : bestTimes(arena).values()) {
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
    private Map<UUID, ParkourArenaRecord.BestTime> bestTimes(MiniGameArena arena) {
        return arena.getOrCreate("bestTimes", Map.class, LinkedHashMap::new);
    }

    private void validateArenaForSave(@NotNull MiniGameArena arena) {
        if (arena.get("lobbyRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a lobby region.");
        }
        if (arena.get("arenaRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing an arena region.");
        }
        if (arena.get("finishRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a finish region.");
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

    private @Nullable SCRegion loadRegion(@NotNull ConfigSection section, @NotNull World world, @NotNull String key) {
        String regionString = section.getString(key);
        if (regionString.isEmpty()) {
            return null;
        }

        return SCRegion.fromString(regionString, world);
    }

    private @Nullable Location loadLocation(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId) {
        String locationString = section.getString("spawn");
        if (locationString.isEmpty()) {
            return null;
        }

        return LocationUtil.deserialize(locationString, world);
    }

    private @NotNull String serializeLocation(@Nullable Location location, @NotNull String arenaId, @NotNull String name) {
        if (location == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return LocationUtil.serialize(location, false, false);
    }

    private @NotNull String serializeRegion(@Nullable SCRegion region, @NotNull String arenaId, @NotNull String name) {
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return region.toString();
    }

    private @NotNull SCRegion singleBlockRegion(@NotNull Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new MiniGameInvalidArenaConfigException("Legacy parkour spawn has no world.");
        }

        SCRegion region = SCRegion.fromString(
            "CUBOID:" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ","
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ(),
            world
        );
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Failed to convert legacy parkour spawn into a lobby region.");
        }
        return region;
    }

    private void ensureLoaded() {
        if (config == null) {
            throw new IllegalStateException("Parkour config is not loaded.");
        }
    }
}
