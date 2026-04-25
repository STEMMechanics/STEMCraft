package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.checkerframework.dataflow.qual.Pure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class MobArenaConfig {
    private final STEMCraftAPI api;
    private MiniGame miniGame;

    @Getter
    @Accessors(fluent = true)
    private ConfigFile config;

    @Getter
    @Accessors(fluent = true)
    private boolean configEnabled = false;

    @Pure
    private void ensureLoaded() {
        if (!configEnabled) {
            throw new IllegalStateException("Mob Arena config not loaded or broken.");
        }
    }

    public MobArenaConfig(STEMCraftAPI api) {
        this.api = api;
    }

    public void onEnable(MiniGame miniGame) {
        this.miniGame = miniGame;

        configEnabled = false;

        this.config = api.config().load("mobarena.yml", true);
        if (this.config == null) {
            api.messages().warn("Mob Arena config could not be loaded (saving disabled!)");
        }
        config.setAutoSave(true);

        config.getSection("arenas", true);

        configEnabled = true;
    }

    public @NotNull List<@NotNull MobArenaArenaRecord> loadArenas() {
        ensureLoaded();

        @NotNull List<@NotNull MobArenaArenaRecord> MiniGameArenas = new ArrayList<>();

        config.getSectionKeys("arenas", false).parallelStream().forEach(key -> {
            try {
                MiniGameArenas.add(loadArena(key));
            } catch (MiniGameInvalidArenaConfigException ignored) {}
        });

        return MiniGameArenas;
    }

    public @NotNull MobArenaArenaRecord loadArena(@NotNull String arenaId) throws MiniGameInvalidArenaConfigException {
        ensureLoaded();
        ConfigSection arenaSection = config.getSection("arenas").getSection(arenaId, false);

        boolean enabled = arenaSection.getBoolean("enabled", true);

        if (arenaSection.getString(arenaId) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena not found: '" + arenaId + "'");
        }

        String worldName = arenaSection.getString("world");
        if (worldName.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("World not defined for arena '" + arenaId + "'.");
        }
        if (Bukkit.getWorld(worldName) == null) {
            throw new MiniGameInvalidArenaConfigException("World '" + worldName + "' for arena '" + arenaId + "' does not exist.");
        }

        World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);

        @Nullable Location lobbyLocation = loadLocation(arenaSection, world, arenaId, "lobby", true);
        @Nullable Location spectatorLocation = loadLocation(arenaSection, world, arenaId, "spectator", false);
        if (spectatorLocation == null) {
            spectatorLocation = lobbyLocation;
        }

        @NotNull String spawnZone = arenaSection.getString("spawn-zone", "arena");

        int minPlayers = arenaSection.getInt("min-players", 2);
        int maxPlayers = arenaSection.getInt("max-players", 16);
        @NotNull String name = arenaSection.getString("name", StringUtil.beautify(arenaId));

        @NotNull List<MobArenaArenaRecord.SpawnerRecord> spawnerRecords = loadSpawnerRecordsFromArena(arenaSection);

        @NotNull Map<String, SCRegion> zones = loadZonesFromArena(arenaId, arenaSection, world);

        return new MobArenaArenaRecord(
                arenaId,
                enabled,
                name,
                world,
                lobbyLocation,
                spectatorLocation,
                spawnZone,
                minPlayers,
                maxPlayers,
                spawnerRecords,
                zones
        );
    }

    private static @NotNull List<MobArenaArenaRecord.SpawnerRecord> loadSpawnerRecordsFromArena(@NonNull ConfigSection section) {
        @NotNull Set<String> configSpawnTickets = section.getSectionKeys("spawner-configs", false);

        return configSpawnTickets.stream().map(spawnerConfigIdString -> {
            try {
                return Integer.parseInt(spawnerConfigIdString);
            } catch (NumberFormatException e) {
                throw new MiniGameInvalidArenaConfigException("Spawner Config ID '" + spawnerConfigIdString + "' could not be parsed.");
            }
        }).sorted().map(spawnTicketId -> {
            @NotNull ConfigSection currentSection = section.getSection("spawner-configs").getSection(spawnTicketId.toString());

            EntityType entityType = EntityType.valueOf(currentSection.getString("entity-type"));

            int initialAmount = section.getInt("initial-amount", 1);

            int incrementAmount = section.getInt("increment-amount", 1);
            MobArenaArenaRecord.SpawnerRecord.IncrementType incrementType = MobArenaArenaRecord.SpawnerRecord.IncrementType.valueOf(section.getString("increment-type"));

            int initialWave = section.getInt("initial-wave", 1);

            String mobSpawnZone = section.getString("spawn-zone", "arena");

            boolean countTowardsMobCount = section.getBoolean("count-towards-mob-count", true);

            return new MobArenaArenaRecord.SpawnerRecord(
                    entityType,
                    initialAmount,
                    incrementAmount,
                    incrementType,
                    initialWave,
                    mobSpawnZone,
                    countTowardsMobCount
            );
        }).toList();
    }

    private Map<String, SCRegion> loadZonesFromArena(@NonNull String arenaId, @NonNull ConfigSection section, World world) {
        Map<String, SCRegion> zones = new HashMap<>();

        section.getSection("zones").getKeys(false).forEach(key -> {
            loadRegion(section.getSection("zones"), world, arenaId, key, key + " zone");
        });

        return zones;
    }

    public void saveArenas(@NotNull Map<@NotNull String, @NotNull MiniGameArena> arena) {
        ensureLoaded();
        arena.forEach((key, arenaRecord) -> {
            saveArena(key, arenaRecord);
        });
    }

    public void saveArena(@NotNull String key, @NotNull MiniGameArena arenaRecord) {
        ensureLoaded();

        ConfigSection toSave = config.createSection("arenas." + key, true);

        // TODO: Saving logic, would be great... - ProjectHSI

        //ConfigSection arenaSection = config.createSection("arenas." + )
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

    // FIXME: I want these "load*" and "serialize*" merged into a single static class, but #20 needs to be reviewed. - ProjectHSI
    //  These are copied from the Bridge impl.
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
}
