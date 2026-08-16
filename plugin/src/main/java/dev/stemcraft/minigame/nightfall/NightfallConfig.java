package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.comet.CometLoot;
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
    static final int DEFAULT_START_COUNTDOWN_SECONDS = 30;
    static final int DEFAULT_ENDING_SECONDS = 20;
    private static final String RECOVERY_PENDING_KEY = "pending-world-rollback";
    private static final String RECOVERY_TIME_KEY = "saved-time-setting";
    private static final String RECOVERY_WEATHER_KEY = "saved-weather-setting";

    private final STEMCraftAPI api;
    private final NightfallMiniGame nightfall;
    private ConfigSection config;

    public NightfallConfig(STEMCraftAPI api, NightfallMiniGame nightfall) {
        this.api = api;
        this.nightfall = nightfall;
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
        List<Location> lobbyLocations = loadOptionalLocations(section, world, arenaId, "lobby-locations");
        if (lobbyLocations.isEmpty()) lobbyLocations = List.of(lobby.clone());
        Location spectator = loadLocation(section, world, arenaId, "spectator", false);
        if (spectator == null) {
            spectator = lobby;
        }

        SCRegion arenaRegion = loadRegion(section, world, arenaId);
        int minPlayers = section.getInt("min-players", 1);
        int maxPlayers = section.getInt("max-players", 8);
        int startCountdownSeconds = section.getInt("start-countdown-seconds", DEFAULT_START_COUNTDOWN_SECONDS);
        int endingSeconds = section.getInt("ending-seconds", DEFAULT_ENDING_SECONDS);
        int lives = section.getInt("lives", 3);
        int prepSeconds = section.getInt("prep-seconds", 300);
        double legacyTimeSpeedMultiplier = section.getDouble("time-speed-multiplier", 2.0d);
        double dayTimeSpeedMultiplier = section.getDouble("day-time-speed-multiplier", legacyTimeSpeedMultiplier);
        double nightTimeSpeedMultiplier = section.getDouble("night-time-speed-multiplier", legacyTimeSpeedMultiplier);
        boolean allowLateJoin = section.getBoolean("allow-late-join", false);
        int dropLootMinStacks = section.getInt("drop-loot-min-stacks", 2);
        int dropLootMaxStacks = section.getInt("drop-loot-max-stacks", 4);
        int dropMinSeconds = section.getInt("drop-min-seconds", 1);
        int dropMaxSeconds = section.getInt("drop-max-seconds", 5);
        int dropMaxActiveItems = section.getInt("drop-max-active-items", 10);
        int dropGroupDistance = section.getInt("drop-group-distance", 100);
        int zombieBaseNightlySpawns = section.getInt("zombie-base-nightly-spawns", 4);
        int zombieNightlySpawnIncrease = section.getInt("zombie-nightly-spawn-increase", 3);
        double zombieNightlyHealthMultiplier = section.getDouble("zombie-nightly-health-multiplier", 1.05d);
        int zombieWaveSize = section.getInt("zombie-wave-size", 2);
        int zombieWaveIntervalSeconds = section.getInt("zombie-wave-interval-seconds", 8);
        int zombieSpawnRadiusMin = section.getInt("zombie-spawn-radius-min", 20);
        int zombieSpawnRadiusMax = section.getInt("zombie-spawn-radius-max", 30);
        int bloodMoonChancePercent = section.getInt("blood-moon-chance", 0);
        double bloodMoonZombieSpawnMultiplier = section.getDouble("blood-moon-zombie-spawn-multiplier", 2.0d);
        int bloodMoonBabyZombieChancePercent = section.getInt("blood-moon-baby-zombie-chance", 20);
        int bloodMoonTntZombieChancePercent = section.getInt("blood-moon-tnt-zombie-chance", 3);
        BloodMoonEscalation escalationDefaults = BloodMoonEscalation.defaults();
        BloodMoonEscalation bloodMoonEscalation = new BloodMoonEscalation(
            section.getInt("blood-moon-tnt-chance-increase-per-night", escalationDefaults.tntIncreasePerNight()),
            section.getInt("blood-moon-tnt-maximum-chance", escalationDefaults.tntMaximumChance()),
            section.getInt("blood-moon-bucket-start-night", escalationDefaults.bucketStartNight()),
            section.getInt("blood-moon-sponge-start-night", escalationDefaults.spongeStartNight()),
            section.getInt("blood-moon-sponge-radius", escalationDefaults.spongeRadius()),
            section.getInt("blood-moon-builder-start-night", escalationDefaults.builderStartNight()),
            section.getInt("blood-moon-axe-start-night", escalationDefaults.axeStartNight()),
            section.getInt("blood-moon-knockback-start-night", escalationDefaults.knockbackStartNight()),
            section.getDouble("blood-moon-knockback-resistance", escalationDefaults.knockbackResistance()));
        int bloodMoonBuilderSourceRemovalChancePercent = Math.clamp(
            section.getInt("blood-moon-builder-source-removal-chance", 70), 0, 100);
        BloodMoonCometSettings bloodMoonComets = loadBloodMoonComets(section, arenaId);
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
            lobbyLocations,
            spectator,
            spawn,
            arenaRegion,
            minPlayers,
            maxPlayers,
            startCountdownSeconds,
            endingSeconds,
            lives,
            prepSeconds,
            dayTimeSpeedMultiplier,
            nightTimeSpeedMultiplier,
            allowLateJoin,
            dropLootMinStacks,
            dropLootMaxStacks,
            dropMinSeconds,
            dropMaxSeconds,
            dropMaxActiveItems,
            dropGroupDistance,
            zombieBaseNightlySpawns,
            zombieNightlySpawnIncrease,
            zombieNightlyHealthMultiplier,
            zombieWaveSize,
            zombieWaveIntervalSeconds,
            zombieSpawnRadiusMin,
            zombieSpawnRadiusMax,
            bloodMoonChancePercent,
            bloodMoonZombieSpawnMultiplier,
            bloodMoonBabyZombieChancePercent,
            bloodMoonTntZombieChancePercent,
            bloodMoonEscalation,
            bloodMoonBuilderSourceRemovalChancePercent,
            bloodMoonComets,
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
        arenaConfig.set("lobby-locations", serializeLocations(nightfall.lobbyLocations(arena), arena.id()));
        arenaConfig.set("spectator", serializeLocation(spectatorSpawn, arena.id(), "spectator"));
        arenaConfig.set("spawn", serializeLocation(playSpawn, arena.id(), "spawn"));
        arenaConfig.set("arena", serializeRegion(arena.get("arenaRegion", SCRegion.class), arena.id()));
        arenaConfig.set("min-players", arena.getMinPlayers());
        arenaConfig.set("max-players", arena.getMaxPlayers());
        arenaConfig.set("start-countdown-seconds", arena.get("startCountdownSeconds", Integer.class, DEFAULT_START_COUNTDOWN_SECONDS));
        arenaConfig.set("ending-seconds", arena.get("endingSeconds", Integer.class, DEFAULT_ENDING_SECONDS));
        arenaConfig.set("lives", arena.get("lives", Integer.class, 3));
        arenaConfig.set("prep-seconds", arena.get("prepSeconds", Integer.class, 300));
        double dayTimeSpeed = arena.get("dayTimeSpeedMultiplier", Double.class, 2.0d);
        double nightTimeSpeed = arena.get("nightTimeSpeedMultiplier", Double.class, 2.0d);
        arenaConfig.set("allow-late-join", arena.get("allowLateJoin", Boolean.class, false));
        arenaConfig.set("time-speed-multiplier", dayTimeSpeed);
        arenaConfig.set("day-time-speed-multiplier", dayTimeSpeed);
        arenaConfig.set("night-time-speed-multiplier", nightTimeSpeed);
        arenaConfig.set("drop-loot-min-stacks", arena.get("dropLootMinStacks", Integer.class, 2));
        arenaConfig.set("drop-loot-max-stacks", arena.get("dropLootMaxStacks", Integer.class, 4));
        arenaConfig.set("drop-min-seconds", arena.get("dropMinSeconds", Integer.class, 1));
        arenaConfig.set("drop-max-seconds", arena.get("dropMaxSeconds", Integer.class, 5));
        arenaConfig.set("drop-max-active-items", arena.get("dropMaxActiveItems", Integer.class, 10));
        arenaConfig.set("drop-group-distance", arena.get("dropGroupDistance", Integer.class, 100));
        arenaConfig.set("zombie-base-nightly-spawns", arena.get("zombieBaseNightlySpawns", Integer.class, 4));
        arenaConfig.set("zombie-nightly-spawn-increase", arena.get("zombieNightlySpawnIncrease", Integer.class, 3));
        arenaConfig.set("zombie-nightly-health-multiplier", arena.get("zombieNightlyHealthMultiplier", Double.class, 1.05d));
        arenaConfig.set("zombie-wave-size", arena.get("zombieWaveSize", Integer.class, 2));
        arenaConfig.set("zombie-wave-interval-seconds", arena.get("zombieWaveIntervalSeconds", Integer.class, 8));
        arenaConfig.set("zombie-spawn-radius-min", arena.get("zombieSpawnRadiusMin", Integer.class, 20));
        arenaConfig.set("zombie-spawn-radius-max", arena.get("zombieSpawnRadiusMax", Integer.class, 30));
        arenaConfig.set("blood-moon-chance", arena.get("bloodMoonChancePercent", Integer.class, 0));
        arenaConfig.set("blood-moon-zombie-spawn-multiplier", arena.get("bloodMoonZombieSpawnMultiplier", Double.class, 2.0d));
        arenaConfig.remove("blood-moon-baby-zombies");
        arenaConfig.set("blood-moon-baby-zombie-chance", arena.get("bloodMoonBabyZombieChancePercent", Integer.class, 20));
        arenaConfig.set("blood-moon-tnt-zombie-chance", arena.get("bloodMoonTntZombieChancePercent", Integer.class, 3));
        BloodMoonEscalation escalation = arena.get("bloodMoonEscalation", BloodMoonEscalation.class, BloodMoonEscalation.defaults());
        arenaConfig.set("blood-moon-tnt-chance-increase-per-night", escalation.tntIncreasePerNight());
        arenaConfig.set("blood-moon-tnt-maximum-chance", escalation.tntMaximumChance());
        arenaConfig.set("blood-moon-bucket-start-night", escalation.bucketStartNight());
        arenaConfig.set("blood-moon-sponge-start-night", escalation.spongeStartNight());
        arenaConfig.set("blood-moon-sponge-radius", escalation.spongeRadius());
        arenaConfig.set("blood-moon-builder-start-night", escalation.builderStartNight());
        arenaConfig.set("blood-moon-builder-source-removal-chance",
            nightfall.bloodMoonBuilderSourceRemovalChancePercent(arena));
        arenaConfig.set("blood-moon-axe-start-night", escalation.axeStartNight());
        arenaConfig.set("blood-moon-knockback-start-night", escalation.knockbackStartNight());
        arenaConfig.set("blood-moon-knockback-resistance", escalation.knockbackResistance());
        writeBloodMoonComets(arenaConfig, arena.get("bloodMoonComets", BloodMoonCometSettings.class,
            BloodMoonCometSettings.defaults()));
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

    private @NotNull List<Location> loadOptionalLocations(@NotNull ConfigSection section, @NotNull World world,
                                                           @NotNull String arenaId, @NotNull String key) {
        List<String> values = section.getStringList(key);
        List<Location> locations = new ArrayList<>();
        int index = 0;
        for (String value : values) {
            index++;
            Location location = LocationUtil.deserialize(value, world);
            if (location == null || location.getWorld() == null || !world.equals(location.getWorld())) {
                throw new MiniGameInvalidArenaConfigException(
                    "Location '" + key + "' #" + index + " for arena '" + arenaId + "' is invalid.");
            }
            locations.add(location);
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

    private @NotNull BloodMoonCometSettings loadBloodMoonComets(@NotNull ConfigSection arenaSection,
                                                                  @NotNull String arenaId) {
        BloodMoonCometSettings defaults = BloodMoonCometSettings.defaults();
        ConfigSection section = arenaSection.getSection("blood-moon-comets", false);
        if (section == null) return defaults;

        ConfigSection lootSection = section.getSection("loot", false);
        List<CometLoot> loot = new ArrayList<>();
        if (lootSection == null) {
            loot.addAll(defaults.loot());
        } else {
            for (String key : lootSection.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null || material.isAir() || !material.isBlock()) {
                    throw new MiniGameInvalidArenaConfigException(
                        "Arena '" + arenaId + "' has an invalid comet loot block '" + key + "'.");
                }
                int[] range = parseCometLootRange(lootSection.getString(key), arenaId, key);
                loot.add(new CometLoot(material, range[0], range[1]));
            }
        }

        int minimumDistance = Math.max(0, section.getInt("minimum-player-distance", defaults.minimumPlayerDistance()));
        int maximumDistance = Math.max(minimumDistance,
            section.getInt("maximum-player-distance", defaults.maximumPlayerDistance()));
        return new BloodMoonCometSettings(
            section.getBoolean("enabled", defaults.enabled()),
            Math.max(1, section.getInt("start-night", defaults.startNight())),
            Math.clamp(section.getInt("chance", defaults.chancePercent()), 0, 100),
            Math.max(0, section.getInt("chance-increase-per-night", defaults.chanceIncreasePerNight())),
            Math.clamp(section.getInt("maximum-chance", defaults.maximumChancePercent()), 0, 100),
            Math.max(0, section.getInt("maximum-per-night", defaults.maximumPerNight())),
            minimumDistance,
            maximumDistance,
            Math.max(0, section.getInt("arena-edge-buffer", defaults.arenaEdgeBuffer())),
            Math.max(1, section.getInt("path-safety-length", defaults.pathSafetyLength())),
            loot);
    }

    private int[] parseCometLootRange(String value, String arenaId, String material) {
        String[] parts = value == null ? new String[0] : value.trim().split("-", 2);
        try {
            int minimum = Integer.parseInt(parts[0].trim());
            int maximum = parts.length == 1 ? minimum : Integer.parseInt(parts[1].trim());
            if (minimum < 0 || maximum < minimum || maximum > 4096) throw new NumberFormatException();
            return new int[] {minimum, maximum};
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException exception) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId
                + "' has an invalid comet loot range for '" + material + "': '" + value + "'.");
        }
    }

    private void writeBloodMoonComets(ConfigSection arenaSection, BloodMoonCometSettings settings) {
        ConfigSection section = arenaSection.createSection("blood-moon-comets", true);
        section.set("enabled", settings.enabled());
        section.set("start-night", settings.startNight());
        section.set("chance", settings.chancePercent());
        section.set("chance-increase-per-night", settings.chanceIncreasePerNight());
        section.set("maximum-chance", settings.maximumChancePercent());
        section.set("maximum-per-night", settings.maximumPerNight());
        section.set("minimum-player-distance", settings.minimumPlayerDistance());
        section.set("maximum-player-distance", settings.maximumPlayerDistance());
        section.set("arena-edge-buffer", settings.arenaEdgeBuffer());
        section.set("path-safety-length", settings.pathSafetyLength());
        ConfigSection loot = section.createSection("loot", true);
        for (CometLoot entry : settings.loot()) {
            loot.set(entry.material().name(), entry.minimum() + "-" + entry.maximum());
        }
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
