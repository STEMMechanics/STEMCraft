package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import dev.stemcraft.minigame.mobarena.MobArenaSpawnerRecord.IncrementType;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>Manages Mob Arena's config file.</p>
 */
final class MobArenaConfig {
    private final STEMCraftAPI api;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame = null;

    @Getter
    @Accessors(fluent = true)
    private ConfigFile config = null;

    @Getter
    @Accessors(fluent = true)
    private boolean configEnabled = false;

    /**
     * <p>Ensures that the config file is loaded, otherwise throws an {@code IllegalStateException}.</p>
     *
     * @throws IllegalStateException Thrown whenever the config is not loaded.
     */
    @Contract(pure = true)
    private void ensureLoaded() {
        if (!configEnabled) {
            throw new IllegalStateException("Mob Arena config not loaded or broken.");
        }
    }

    /**
     * <p>Creates a new {@code MobArenaConfig}.</p>
     *
     * @param api      The {@link STEMCraftAPI} to use.
     */
    @Contract(pure = true)
    MobArenaConfig(@NotNull final STEMCraftAPI api) {
        this.api = api;
    }

    /**
     * <p>Enables the Mob Arena config.</p>
     * <p>This registers listeners for {@code EntityDamageEvent}, {@code EntityDeathEvent}, {@code EntityRemoveFromWorldEvent}, and {@code EntityTransformEvent}</p>
     */
    void onEnable(@NotNull final MiniGame miniGame) {
        minigame = miniGame;

        configEnabled = false;

        config = api.config().load("mobarena.yml", true);
        if (config == null) {
            api.messages().warn("Mob Arena config could not be loaded (saving disabled!)");
        } else {
            config.setAutoSave(true);

            config.getSection("arenas", true);

            configEnabled = true;
        }
    }

    /**
     * <p>Loads all arenas from the config file.</p>
     *
     * @return A list of all arena records loaded.
     */
    @NotNull List<@NotNull MobArenaArenaRecord> loadArenas() {
        ensureLoaded();

        @NotNull final List<@NotNull MobArenaArenaRecord> MiniGameArenas = new ArrayList<>();

        config.getSectionKeys("arenas", false).parallelStream().forEach(key -> {
            try {
                MiniGameArenas.add(loadArena(key));
            } catch (final MiniGameInvalidArenaConfigException ignored) {}
        });

        return MiniGameArenas;
    }

    /**
     * @param arenaId The arena ID to load in.
     * @return An arena record for the given Arena ID.
     * @throws MiniGameInvalidArenaConfigException Thrown if the arena config in the config file is not valid.
     */
    @Contract("_ -> new")
    @NotNull MobArenaArenaRecord loadArena(@NotNull final String arenaId) throws MiniGameInvalidArenaConfigException {
        ensureLoaded();
        @NotNull final ConfigSection arenaSection = config.getSection("arenas").getSection(arenaId, false);

        if (arenaSection == null) {
            throw new MiniGameInvalidArenaConfigException("Arena not found: '" + arenaId + "'");
        }

        final boolean enabled = arenaSection.getBoolean("enabled", true);

        @NotNull final String worldName = arenaSection.getString("world");
        if (worldName.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("World not defined for arena '" + arenaId + "'.");
        }
        if (Bukkit.getWorld(worldName) == null) {
            throw new MiniGameInvalidArenaConfigException("World '" + worldName + "' for arena '" + arenaId + "' does not exist.");
        }

        @NotNull final World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);

        @NotNull final SCRegion arenaRegion = loadRegion(arenaSection, world, arenaId, "arena", "Arena Region");
        @Nullable final Location lobbyLocation = loadLocation(arenaSection, world, arenaId, "lobby", true);
        @Nullable Location spectatorLocation = loadLocation(arenaSection, world, arenaId, "spectator", false);
        if (spectatorLocation == null) {
            spectatorLocation = lobbyLocation;
        }

        final int minPlayers = arenaSection.getInt("min-players", 2);
        final int maxPlayers = arenaSection.getInt("max-players", 16);
        @NotNull final String name = arenaSection.getString("name", StringUtil.beautify(arenaId));

        @NotNull final List<MobArenaSpawnerRecord> spawnerRecords = loadSpawnerRecordsFromArena(arenaSection);

        @NotNull final Map<String, SCRegion> zones = loadZonesFromArena(arenaId, arenaSection, world);

        return new MobArenaArenaRecord(
                arenaId,
                enabled,
                name,
                world,
                arenaRegion,
                lobbyLocation,
                spectatorLocation,
                minPlayers,
                maxPlayers,
                spawnerRecords,
                zones
        );
    }

    /**
     * @param section The config section to convert into spawner configs.
     * @return The list of {@link MobArenaSpawnerRecord} found.
     */
    private static @NotNull @Unmodifiable List<MobArenaSpawnerRecord> loadSpawnerRecordsFromArena(@NonNull final ConfigSection section) {
        @NotNull final Set<String> configSpawnTickets = section.getSectionKeys("spawner-configs", false);

        return configSpawnTickets.stream().map(spawnerConfigIdString -> {
            try {
                return Integer.parseInt(spawnerConfigIdString);
            } catch (final NumberFormatException e) {
                throw new MiniGameInvalidArenaConfigException("Spawner Config ID '" + spawnerConfigIdString + "' could not be parsed.", e);
            }
        }).sorted().map(spawnTicketId -> {
            @NotNull final ConfigSection currentSection = section.getSection("spawner-configs").getSection(spawnTicketId.toString());

            @NotNull final EntityType entityType = EntityType.valueOf(currentSection.getString("entity-type", "ZOMBIE").toUpperCase());

            final int initialAmount = currentSection.getInt("initial-amount", 1);

            final int incrementAmount = currentSection.getInt("increment-amount", 1);
            @NotNull final IncrementType incrementType = IncrementType.valueOf(currentSection.getString("increment-type"));

            final int initialWave = currentSection.getInt("initial-wave", 1);

            @NotNull final String mobSpawnZone = currentSection.getString("spawn-zone", "");

            final boolean countTowardsMobCount = currentSection.getBoolean("count-towards-mob-count", true);

            return new MobArenaSpawnerRecord(
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

    private @NonNull Map<String, SCRegion> loadZonesFromArena(@NonNull final String arenaId, @NonNull final ConfigSection section, final World world) {
        @NotNull final Map<String, SCRegion> zones = new HashMap<>();

        section.getSection("zones").getKeys(false).forEach(key -> zones.put(key, loadRegion(section.getSection("zones"), world, arenaId, key, key + " zone")));

        return zones;
    }

    /**
     * <p>Saves a list of arenas.</p>
     *
     * @param arenas The list of arenas to save.
     */
    void saveArenas(@NotNull final List<@NotNull MobArenaArenaRecord> arenas) {
        ensureLoaded();
        arenas.forEach(arenaRecord -> saveArena(arenaRecord.arenaId(), arenaRecord));
    }

    /**
     * @param key         The ID of the arena.
     * @param arenaRecord The arena record to save.
     */
    void saveArena(@NotNull final String key, @NotNull final MobArenaArenaRecord arenaRecord) {
        ensureLoaded();

        @NotNull final ConfigSection toSave = config.createSection("arenas." + key, true);

        toSave.set("enabled", arenaRecord.enabled());
        toSave.set("name", arenaRecord.name());
        toSave.set("world", arenaRecord.world().getName());
        toSave.set("arena", serializeRegion(arenaRecord.arenaRegion(), key, "Arena Region"));
        toSave.set("lobby", serializeLocation(arenaRecord.lobby(), key,  "Lobby Location"));
        toSave.set("spectator", serializeLocation(arenaRecord.spectator(), key,  "Spectator Location"));
        toSave.set("min-players", arenaRecord.minPlayers());
        toSave.set("max-players", arenaRecord.maxPlayers());
        saveSpawnerRecordsToArena(toSave, arenaRecord);
        saveZonesToArena(toSave, arenaRecord);

        config.save();
    }

    /**
     * <p>Saves an arena record to an arena section.</p>
     *
     * @param toSave The arena config section to save to.
     * @param arenaRecord The arena record with the spawner configs.
     */
    private void saveSpawnerRecordsToArena(@NotNull final ConfigSection toSave, @NotNull final MobArenaArenaRecord arenaRecord) {
        final ConfigSection spawnerConfigsSection = toSave.getSection("spawner-configs");

        for (int i = 0; i < arenaRecord.spawnerConfigs().size(); i++) {
            final MobArenaSpawnerRecord spawnerRecord = arenaRecord.spawnerConfigs().get(i);
            final ConfigSection currentSpawnerConfigSection = spawnerConfigsSection.getSection(String.valueOf(i), true);

            currentSpawnerConfigSection.set("entity-type", spawnerRecord.entityType().toString());
            currentSpawnerConfigSection.set("initial-amount", spawnerRecord.initialAmount());
            currentSpawnerConfigSection.set("increment-amount", spawnerRecord.incrementAmount());
            currentSpawnerConfigSection.set("increment-type", spawnerRecord.incrementType().toString());
            currentSpawnerConfigSection.set("initial-wave", spawnerRecord.initialWave());
            currentSpawnerConfigSection.set("spawn-zone", spawnerRecord.spawnZone());
            currentSpawnerConfigSection.set("count-towards-mob-count", spawnerRecord.countTowardsMobCount());
        }
    }

    /**
     * <p>Saves a map of zones to an arena section.</p>
     *
     * @param toSave The arena config section to save to.
     * @param arenaRecord The arena record with the zones.
     */
    private void saveZonesToArena(@NotNull final ConfigSection toSave, @NotNull final MobArenaArenaRecord arenaRecord) {
        final ConfigSection zonesSection = toSave.getSection("zones");
        arenaRecord.zones().forEach((zoneId, zone) -> zonesSection.set(zoneId, serializeRegion(zone, arenaRecord.arenaId(), "Zone " + zoneId)));
    }

    /**
     * <p>Deletes an arena from the config file by ID.</p>
     *
     * @param arenaId The Arena ID to delete.
     */
    void deleteArena(@NotNull final String arenaId) {
        ensureLoaded();

        final ConfigSection arenas = config.getSection("arenas");
        arenas.remove(arenaId);
        config.save();
    }

    /**
     * <p>Checks whether an arena is in the config file or not.</p>
     *
     * @param arenaId The Arena ID to check for.
     * @return Whether the arena is config file or not.
     */
    boolean hasArena(@NotNull final String arenaId) {
        ensureLoaded();

        final ConfigSection arenas = config.getSection("arenas", false);
        return arenas != null && arenas.isSection(arenaId);
    }

    /**
     * <p>Sets the status of an arena in the config.</p>
     *
     * @param arenaId The Arena ID to set the status of.
     * @param enabled Whether the Arena ID is enabled or not.
     */
    void setArenaEnabled(@NotNull final String arenaId, final boolean enabled) {
        ensureLoaded();
        final ConfigSection arenas = config.getSection("arenas", false);
        if (arenas == null || !arenas.isSection(arenaId)) {
            return;
        }

        final ConfigSection arenaConfig = arenas.getSection(arenaId, false);
        if (arenaConfig == null) {
            return;
        }

        arenaConfig.set("enabled", enabled);
        config.save();
    }

    // FIXME: I want these "load*" and "serialize*" merged into a single static class, but #20 needs to be redone. - ProjectHSI
    //  These are copied from the Bridge impl.
    private @Nullable Location loadLocation(@NotNull final ConfigSection section, @NotNull final World world, @NotNull final String arenaId, @NotNull final String key, final boolean required) {
        final String locationString = section.getString(key);
        if (locationString.isEmpty()) {
            if (required) {
                throw new MiniGameInvalidArenaConfigException("Location '" + key + "' for arena '" + arenaId + "' is not defined.");
            }
            return null;
        }

        final Location location = LocationUtil.deserialize(locationString, world);
        if (location == null && required) {
            throw new MiniGameInvalidArenaConfigException("Location '" + key + "' for arena '" + arenaId + "' is invalid.");
        }
        return location;
    }

    private @NotNull SCRegion loadRegion(@NotNull final ConfigSection section, @NotNull final World world, @NotNull final String arenaId, @NotNull final String key, @NotNull final String title) {
        final String regionString = section.getString(key);
        if (regionString.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException(title + " region for arena '" + arenaId + "' is not defined.");
        }

        final SCRegion region = SCRegion.fromString(regionString, world);
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException(title + " region for arena '" + arenaId + "' is invalid.");
        }
        return region;
    }

    @Contract("null, _, _ -> fail")
    private @NotNull String serializeLocation(@Nullable final Location location, @NotNull final String arenaId, @NotNull final String name) {
        if (location == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return LocationUtil.serialize(location, false, false);
    }

    @Contract("null, _, _ -> fail")
    private @NotNull String serializeRegion(@Nullable final SCRegion region, @NotNull final String arenaId, @NotNull final String name) {
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return region.toString();
    }
}
