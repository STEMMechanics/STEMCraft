package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class NightfallConfig {
    private static final String RECOVERY_PENDING_KEY = "pending-world-rollback";
    private static final String RECOVERY_TIME_KEY = "saved-time-setting";
    private static final String RECOVERY_WEATHER_KEY = "saved-weather-setting";

    private final STEMCraftAPI api;
    private ConfigSection config;

    public NightfallConfig(STEMCraftAPI api, NightfallMiniGame nightfall) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("arenas");
    }

    public @NotNull NightfallArenaRecord load(@NotNull String arenaId, @NotNull ConfigSection section) {
        boolean enabled = section.getBoolean("enabled", true);

        String worldName = section.getString("world");
        if (worldName.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("World not defined for arena '" + arenaId + "'.");
        }
        if (Bukkit.getWorld(worldName) == null) {
            throw new MiniGameInvalidArenaConfigException("World '" + worldName + "' for arena '" + arenaId + "' does not exist.");
        }

        World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);

        Location spawn = loadLocation(section, world, arenaId, "spawn", true);
        Location lobby = loadLocation(section, world, arenaId, "lobby", false);
        if (lobby == null) {
            lobby = spawn;
        }
        Location spectator = loadLocation(section, world, arenaId, "spectator", false);
        if (spectator == null) {
            spectator = lobby;
        }

        SCRegion arenaRegion = loadRegion(section, world, arenaId);
        int minPlayers = section.getInt("min-players", 1);
        int maxPlayers = section.getInt("max-players", 8);
        int lives = section.getInt("lives", 3);
        int prepSeconds = section.getInt("prep-seconds", 300);
        double legacyTimeSpeedMultiplier = section.getDouble("time-speed-multiplier", 2.0d);
        double dayTimeSpeedMultiplier = section.getDouble("day-time-speed-multiplier", legacyTimeSpeedMultiplier);
        double nightTimeSpeedMultiplier = section.getDouble("night-time-speed-multiplier", legacyTimeSpeedMultiplier);
        int dropMinSeconds = section.getInt("drop-min-seconds", 1);
        int dropMaxSeconds = section.getInt("drop-max-seconds", 5);
        int zombieBaseNightlySpawns = section.getInt("zombie-base-nightly-spawns", 4);
        int zombieNightlySpawnIncrease = section.getInt("zombie-nightly-spawn-increase", 3);
        int zombieWaveSize = section.getInt("zombie-wave-size", 2);
        int zombieWaveIntervalSeconds = section.getInt("zombie-wave-interval-seconds", 8);
        int zombieSpawnRadiusMin = section.getInt("zombie-spawn-radius-min", 20);
        int zombieSpawnRadiusMax = section.getInt("zombie-spawn-radius-max", 30);
        int bloodMoonChancePercent = section.getInt("blood-moon-chance", 0);
        String name = section.getString("name", StringUtil.beautify(arenaId));
        List<Location> generatorLocations = loadLocations(section, world, arenaId);
        Map<Integer, List<Material>> dropItems = loadDropItems(section, arenaId);
        boolean pendingWorldRollback = section.getBoolean(RECOVERY_PENDING_KEY, false);
        String savedTimeSetting = normalizeRecoverySetting(section.getString(RECOVERY_TIME_KEY, "unset"));
        String savedWeatherSetting = normalizeRecoverySetting(section.getString(RECOVERY_WEATHER_KEY, "unset"));

        return new NightfallArenaRecord(
            arenaId,
            enabled,
            name,
            worldName,
            lobby,
            spectator,
            spawn,
            arenaRegion,
            minPlayers,
            maxPlayers,
            lives,
            prepSeconds,
            dayTimeSpeedMultiplier,
            nightTimeSpeedMultiplier,
            dropMinSeconds,
            dropMaxSeconds,
            zombieBaseNightlySpawns,
            zombieNightlySpawnIncrease,
            zombieWaveSize,
            zombieWaveIntervalSeconds,
            zombieSpawnRadiusMin,
            zombieSpawnRadiusMax,
            bloodMoonChancePercent,
            generatorLocations,
            dropItems,
            pendingWorldRollback,
            savedTimeSetting,
            savedWeatherSetting
        );
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        ensureLoaded();
        validateArenaForSave(arena);

        ConfigSection arenaConfig = config.createSection("arenas." + arena.id(), true);
        Location playSpawn = arena.get("playSpawn", Location.class);
        Location lobbySpawn = arena.getLobbySpawn() != null ? arena.getLobbySpawn() : playSpawn;
        Location spectatorSpawn = arena.getSpectatorSpawn() != null ? arena.getSpectatorSpawn() : lobbySpawn;
        arenaConfig.set("enabled", arena.getStatus() != MiniGameArena.ArenaStatus.DISABLED);
        arenaConfig.set("world", arena.world().getName());
        arenaConfig.set("name", arena.getName());
        arenaConfig.set("lobby", serializeLocation(lobbySpawn, arena.id(), "lobby"));
        arenaConfig.set("spectator", serializeLocation(spectatorSpawn, arena.id(), "spectator"));
        arenaConfig.set("spawn", serializeLocation(playSpawn, arena.id(), "spawn"));
        arenaConfig.set("arena", serializeRegion(arena.get("arenaRegion", SCRegion.class), arena.id()));
        arenaConfig.set("min-players", arena.getMinPlayers());
        arenaConfig.set("max-players", arena.getMaxPlayers());
        arenaConfig.set("lives", arena.get("lives", Integer.class, 3));
        arenaConfig.set("prep-seconds", arena.get("prepSeconds", Integer.class, 300));
        double dayTimeSpeed = arena.get("dayTimeSpeedMultiplier", Double.class, 2.0d);
        double nightTimeSpeed = arena.get("nightTimeSpeedMultiplier", Double.class, 2.0d);
        arenaConfig.set("time-speed-multiplier", dayTimeSpeed);
        arenaConfig.set("day-time-speed-multiplier", dayTimeSpeed);
        arenaConfig.set("night-time-speed-multiplier", nightTimeSpeed);
        arenaConfig.set("drop-min-seconds", arena.get("dropMinSeconds", Integer.class, 1));
        arenaConfig.set("drop-max-seconds", arena.get("dropMaxSeconds", Integer.class, 5));
        arenaConfig.set("zombie-base-nightly-spawns", arena.get("zombieBaseNightlySpawns", Integer.class, 4));
        arenaConfig.set("zombie-nightly-spawn-increase", arena.get("zombieNightlySpawnIncrease", Integer.class, 3));
        arenaConfig.set("zombie-wave-size", arena.get("zombieWaveSize", Integer.class, 2));
        arenaConfig.set("zombie-wave-interval-seconds", arena.get("zombieWaveIntervalSeconds", Integer.class, 8));
        arenaConfig.set("zombie-spawn-radius-min", arena.get("zombieSpawnRadiusMin", Integer.class, 20));
        arenaConfig.set("zombie-spawn-radius-max", arena.get("zombieSpawnRadiusMax", Integer.class, 30));
        arenaConfig.set("blood-moon-chance", arena.get("bloodMoonChancePercent", Integer.class, 0));
        if (generatorLocations(arena).isEmpty()) {
            arenaConfig.set("generator-locations", new ArrayList<>());
        } else {
            arenaConfig.set("generator-locations", serializeLocations(generatorLocations(arena), arena.id()));
        }

        arenaConfig.remove("drop-blocks");
        ConfigSection items = arenaConfig.createSection("items", true);
        for (Map.Entry<Integer, List<Material>> entry : dropItems(arena).entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList()) {
            List<String> values = new ArrayList<>();
            for (Material material : entry.getValue()) {
                if (material != null && !material.isAir()) {
                    values.add(material.name());
                }
            }
            items.set(Integer.toString(entry.getKey()), values);
        }

        arenaConfig.set(RECOVERY_PENDING_KEY, arena.get("pendingWorldRollback", Boolean.class, false));
        arenaConfig.set(RECOVERY_TIME_KEY, normalizeRecoverySetting(arena.get("savedTimeSetting", String.class, "unset")));
        arenaConfig.set(RECOVERY_WEATHER_KEY, normalizeRecoverySetting(arena.get("savedWeatherSetting", String.class, "unset")));

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

    public void setArenaRecoveryState(
        @NotNull String arenaId,
        boolean pendingWorldRollback,
        @Nullable String savedTimeSetting,
        @Nullable String savedWeatherSetting
    ) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas", false);
        if (arenas == null || !arenas.isSection(arenaId)) {
            return;
        }

        ConfigSection arenaConfig = arenas.getSection(arenaId, false);
        if (arenaConfig == null) {
            return;
        }

        arenaConfig.set(RECOVERY_PENDING_KEY, pendingWorldRollback);
        arenaConfig.set(RECOVERY_TIME_KEY, normalizeRecoverySetting(savedTimeSetting));
        arenaConfig.set(RECOVERY_WEATHER_KEY, normalizeRecoverySetting(savedWeatherSetting));
        config.save();
    }

    public @Nullable ConfigSection getSection(String path) {
        if (config == null) {
            return null;
        }
        return config.getSection(path, false);
    }

    @SuppressWarnings("unchecked")
    private @NotNull List<Location> generatorLocations(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("generatorLocations", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    private @NotNull Map<Integer, List<Material>> dropItems(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("dropItems", Map.class, LinkedHashMap::new);
    }

    private void validateArenaForSave(@NotNull MiniGameArena arena) {
        if (arena.get("playSpawn", Location.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a play spawn.");
        }
        if (arena.get("arenaRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing an arena region.");
        }
        if (dropItems(arena).isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' has no configured drop items.");
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
        List<String> values = section.getStringList("generator-locations");

        List<Location> locations = new ArrayList<>();
        int index = 1;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                index++;
                continue;
            }

            Location location = LocationUtil.deserialize(value, world);
            if (location == null) {
                throw new MiniGameInvalidArenaConfigException("Location '" + "generator-locations" + "' #" + index + " for arena '" + arenaId + "' is invalid.");
            }
            locations.add(location);
            index++;
        }
        return locations;
    }

    private @NotNull Map<Integer, List<Material>> loadDropItems(@NotNull ConfigSection section, @NotNull String arenaId) {
        ConfigSection itemSection = section.getSection("items", false);
        if (itemSection == null) {
            ConfigSection legacySection = section.getSection("drop-blocks", false);
            if (legacySection != null) {
                Map<Integer, List<Material>> converted = convertLegacyDropBlocks(legacySection, arenaId);
                writeDropItemsSection(section.createSection("items", true), converted);
                section.save();
                return converted;
            }

            Map<Integer, List<Material>> defaults = NightfallMiniGame.defaultDropItems();
            writeDropItemsSection(section.createSection("items", true), defaults);
            section.save();
            return copyDropItems(defaults);
        }

        Map<Integer, List<Material>> items = new TreeMap<>();
        for (String key : itemSection.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an invalid drop tier '" + key + "'.");
            }

            if (threshold < 1 || threshold > 100) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has a drop tier '" + key + "' outside 1-100.");
            }

            List<String> names = itemSection.getStringList(key);
            if (names.isEmpty()) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an empty drop tier '" + key + "'.");
            }

            List<Material> tierItems = new ArrayList<>();
            for (String name : names) {
                Material material = Material.matchMaterial(name);
                if (material == null || material.isAir()) {
                    throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an invalid drop item material '" + name + "'.");
                }
                tierItems.add(material);
            }
            items.put(threshold, tierItems);
        }

        if (items.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' must define at least one drop item tier.");
        }
        return new LinkedHashMap<>(items);
    }

    private @NotNull Map<Integer, List<Material>> convertLegacyDropBlocks(@NotNull ConfigSection blockSection, @NotNull String arenaId) {
        Map<Material, Double> weights = new LinkedHashMap<>();
        double totalWeight = 0.0d;

        for (String key : blockSection.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || material.isAir()) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an invalid legacy drop block material '" + key + "'.");
            }

            double weight = blockSection.getDouble(key, 0.0d);
            if (weight <= 0.0d) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an invalid legacy drop weight for '" + key + "'.");
            }
            weights.put(material, weight);
            totalWeight += weight;
        }

        if (weights.isEmpty() || totalWeight <= 0.0d) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' must define at least one legacy drop block.");
        }

        Map<Integer, List<Material>> tiers = new TreeMap<>();
        double cumulativeWeight = 0.0d;
        int lastThreshold = 0;
        int index = 0;
        int size = weights.size();
        for (Map.Entry<Material, Double> entry : weights.entrySet()) {
            cumulativeWeight += entry.getValue();
            index++;
            int threshold = (index == size)
                ? 100
                : Math.clamp((int) Math.round((cumulativeWeight / totalWeight) * 100.0d), lastThreshold + 1, 100);
            tiers.computeIfAbsent(threshold, ignored -> new ArrayList<>()).add(entry.getKey());
            lastThreshold = threshold;
        }

        return new LinkedHashMap<>(tiers);
    }

    private void writeDropItemsSection(@NotNull ConfigSection itemsSection, @NotNull Map<Integer, List<Material>> items) {
        for (Map.Entry<Integer, List<Material>> entry : items.entrySet().stream()
            .sorted(Comparator.comparingInt(Map.Entry::getKey))
            .toList()) {
            List<String> names = new ArrayList<>();
            for (Material material : entry.getValue()) {
                if (material != null && !material.isAir()) {
                    names.add(material.name());
                }
            }
            itemsSection.set(Integer.toString(entry.getKey()), names);
        }
    }

    private @NotNull Map<Integer, List<Material>> copyDropItems(@NotNull Map<Integer, List<Material>> source) {
        Map<Integer, List<Material>> copy = new LinkedHashMap<>();
        source.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> copy.put(entry.getKey(), new ArrayList<>(entry.getValue())));
        return copy;
    }

    private @NotNull String serializeLocation(@Nullable Location location, @NotNull String arenaId, @NotNull String name) {
        if (location == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return LocationUtil.serialize(location, false, true);
    }

    private @NotNull List<String> serializeLocations(@NotNull List<Location> locations, @NotNull String arenaId) {
        if (locations.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + "generator locations" + ".");
        }
        List<String> values = new ArrayList<>();
        for (Location location : locations) {
            values.add(serializeLocation(location, arenaId, "generator locations"));
        }
        return values;
    }

    private @NotNull String serializeRegion(@Nullable SCRegion region, @NotNull String arenaId) {
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + "arena" + ".");
        }
        return region.toString();
    }

    private void ensureLoaded() {
        if (config == null) {
            throw new IllegalStateException("Nightfall config is not loaded.");
        }
    }

    private @NotNull String normalizeRecoverySetting(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "unset";
        }
        return value;
    }
}
