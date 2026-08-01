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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.service.playerstats.PlayerStatValue;
import dev.stemcraft.api.service.playerstats.PlayerStatsRecord;
import dev.stemcraft.api.service.playerstats.PlayerStatsService;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerStatsServiceImpl extends BaseService implements PlayerStatsService {
    private static final int MAX_BUCKET_DAYS = 370;
    private static final List<String> BUILT_IN_STATS = List.of(
        "damage_dealt",
        "damage_taken",
        "deaths",
        "player_kills",
        "mob_kills",
        "zombie_kills",
        "skeleton_kills",
        "dragon_kills",
        "fish_caught",
        "play_time",
        "jumps",
        "distance_walked_km",
        "distance_sprinted_km",
        "distance_boated_km",
        "distance_swam_km",
        "distance_fallen_m",
        "distance_flown_km",
        "distance_horse_km",
        "distance_minecart_km",
        "distance_pig_km",
        "distance_strider_km",
        "distance_total_moved_km",
        "food_eaten",
        "potions_drank",
        "spears_thrown",
        "times_slept",
        "seeds_planted",
        "bucket_fills",
        "blocks_placed_survival",
        "blocks_placed_creative",
        "blocks_broken_survival",
        "blocks_broken_creative"
    );
    private static final Map<String, RawStatisticDefinition> RAW_STAT_DEFINITIONS = Map.ofEntries(
        Map.entry("damage_dealt", new RawStatisticDefinition("damage_dealt", Statistic.DAMAGE_DEALT, "Damage Dealt", "Total damage dealt by the player.", 1.0)),
        Map.entry("damage_taken", new RawStatisticDefinition("damage_taken", Statistic.DAMAGE_TAKEN, "Damage Taken", "Total damage received by the player.", 1.0)),
        Map.entry("deaths", new RawStatisticDefinition("deaths", Statistic.DEATHS, "Deaths", "Number of times the player has died.", 1.0)),
        Map.entry("player_kills", new RawStatisticDefinition("player_kills", Statistic.PLAYER_KILLS, "Player Kills", "Number of player kills made by the player.", 1.0)),
        Map.entry("mob_kills", new RawStatisticDefinition("mob_kills", Statistic.MOB_KILLS, "Mob Kills", "Number of mob kills made by the player.", 1.0)),
        Map.entry("fish_caught", new RawStatisticDefinition("fish_caught", Statistic.FISH_CAUGHT, "Fish Caught", "Number of fish caught by the player.", 1.0)),
        Map.entry("food_eaten", new RawStatisticDefinition("food_eaten", Statistic.CAKE_SLICES_EATEN, "Food Eaten", "Number of food items eaten by the player, including cake slices.", 1.0)),
        Map.entry("play_time", new RawStatisticDefinition("play_time", Statistic.PLAY_ONE_MINUTE, "Play Time", "Total play time recorded by the server in hours.", 1.0 / 72000.0)),
        Map.entry("jumps", new RawStatisticDefinition("jumps", Statistic.JUMP, "Jumps", "Number of jumps made by the player.", 1.0)),
        Map.entry("distance_walked_km", new RawStatisticDefinition("distance_walked_km", Statistic.WALK_ONE_CM, "Distance Walked", "Distance walked by the player in kilometers.", 0.00001)),
        Map.entry("distance_sprinted_km", new RawStatisticDefinition("distance_sprinted_km", Statistic.SPRINT_ONE_CM, "Distance Sprinted", "Distance sprinted by the player in kilometers.", 0.00001)),
        Map.entry("distance_boated_km", new RawStatisticDefinition("distance_boated_km", Statistic.BOAT_ONE_CM, "Distance Boated", "Distance traveled by boat in kilometers.", 0.00001)),
        Map.entry("distance_swam_km", new RawStatisticDefinition("distance_swam_km", Statistic.SWIM_ONE_CM, "Distance Swum", "Distance swum by the player in kilometers.", 0.00001)),
        Map.entry("distance_fallen_m", new RawStatisticDefinition("distance_fallen_m", Statistic.FALL_ONE_CM, "Distance Fallen", "Distance fallen by the player in meters.", 0.01)),
        Map.entry("distance_flown_km", new RawStatisticDefinition("distance_flown_km", Statistic.FLY_ONE_CM, "Distance Flown", "Distance flown by the player in kilometers.", 0.00001)),
        Map.entry("distance_horse_km", new RawStatisticDefinition("distance_horse_km", Statistic.HORSE_ONE_CM, "Distance Ridden on Horse", "Distance traveled by horse in kilometers.", 0.00001)),
        Map.entry("distance_minecart_km", new RawStatisticDefinition("distance_minecart_km", Statistic.MINECART_ONE_CM, "Distance Traveled by Minecart", "Distance traveled by minecart in kilometers.", 0.00001)),
        Map.entry("distance_pig_km", new RawStatisticDefinition("distance_pig_km", Statistic.PIG_ONE_CM, "Distance Ridden on Pig", "Distance traveled by pig in kilometers.", 0.00001)),
        Map.entry("distance_strider_km", new RawStatisticDefinition("distance_strider_km", Statistic.STRIDER_ONE_CM, "Distance Ridden on Strider", "Distance traveled by strider in kilometers.", 0.00001))
    );

    private static final Map<String, PlayerStatDefinition> EVENT_STAT_DEFINITIONS = Map.ofEntries(
        Map.entry("zombie_kills", new PlayerStatDefinition("zombie_kills", "Zombie Kills", "Number of zombie-type mobs killed by the player.")),
        Map.entry("skeleton_kills", new PlayerStatDefinition("skeleton_kills", "Skeleton Kills", "Number of skeleton-type mobs killed by the player.")),
        Map.entry("dragon_kills", new PlayerStatDefinition("dragon_kills", "Dragon Kills", "Number of Ender Dragons killed by the player.")),
        Map.entry("bucket_fills", new PlayerStatDefinition("bucket_fills", "Bucket Fills", "Number of times the player filled a bucket with water or lava.")),
        Map.entry("distance_total_moved_km", new PlayerStatDefinition("distance_total_moved_km", "Total Distance Moved", "Combined travel distance in kilometers across walking, sprinting, swimming, boating, flying, minecart, horse, pig, and strider movement.")),
        Map.entry("potions_drank", new PlayerStatDefinition("potions_drank", "Potions Drank", "Number of drinkable potions consumed by the player.")),
        Map.entry("spears_thrown", new PlayerStatDefinition("spears_thrown", "Spears Thrown", "Number of tridents thrown by the player.")),
        Map.entry("times_slept", new PlayerStatDefinition("times_slept", "Times Slept", "Number of successful bed entries by the player.")),
        Map.entry("seeds_planted", new PlayerStatDefinition("seeds_planted", "Seeds Planted", "Number of seed/crop block placements by the player.")),
        Map.entry("blocks_placed_survival", new PlayerStatDefinition("blocks_placed_survival", "Blocks Placed in Survival", "Number of blocks placed while in survival mode.")),
        Map.entry("blocks_placed_creative", new PlayerStatDefinition("blocks_placed_creative", "Blocks Placed in Creative", "Number of blocks placed while in creative mode.")),
        Map.entry("blocks_broken_survival", new PlayerStatDefinition("blocks_broken_survival", "Blocks Broken in Survival", "Number of blocks broken while in survival mode.")),
        Map.entry("blocks_broken_creative", new PlayerStatDefinition("blocks_broken_creative", "Blocks Broken in Creative", "Number of blocks broken while in creative mode."))
    );
    private static final Set<Material> SEED_PLANT_BLOCKS = Set.of(
        Material.WHEAT,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOTS,
        Material.NETHER_WART,
        Material.MELON_STEM,
        Material.ATTACHED_MELON_STEM,
        Material.PUMPKIN_STEM,
        Material.ATTACHED_PUMPKIN_STEM,
        Material.COCOA,
        Material.TORCHFLOWER_CROP,
        Material.PITCHER_CROP
    );

    private final ConcurrentMap<String, PlayerStatDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PlayerStatsState> players = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, Long>> rawBaselines = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TimeInDefinition> timeInDefinitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Instant> lastObservedAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> lastObservedWorld = new ConcurrentHashMap<>();
    private List<String> enabledBuiltInKeys = List.of();

    public PlayerStatsServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
        setConfigKey("player_stats");
    }

    @Override
    public void onEnable() {
        configureDefaults();
        ensureStorage();
        loadPersistedData();
        loadBuiltInDefinitions();
        loadTimeInDefinitions();
        registerEventCollectors();

        long autosaveTicks = Math.max(20L, getConfigSection().getLong("autosave_ticks", 1200L));
        api.tasks().repeating(autosaveTicks, this::captureOnlinePlayers);
        captureOnlinePlayers();
    }

    @Override
    public void onDisable() {
        captureOnlinePlayers();
    }

    @Override
    public void register(@NotNull PlayerStatDefinition definition) {
        String key = normalizeKey(definition.key());
        if (key == null) {
            throw new IllegalArgumentException("Invalid player stat key: " + definition.key());
        }
        definitions.put(key, new PlayerStatDefinition(
            key,
            definition.title().trim(),
            definition.description().trim(),
            definition.namespace(),
            definition.scope(),
            definition.scopeId()
        ));
    }

    @Override
    public void unregister(@NotNull String name) {
        String key = normalizeKey(name);
        if (key != null) {
            definitions.remove(key);
        }
    }

    @Override
    public @Nullable PlayerStatDefinition getDefinition(@NotNull String key) {
        String normalized = normalizeKey(key);
        return normalized == null ? null : definitions.get(normalized);
    }

    @Override
    public @NotNull List<PlayerStatDefinition> getDefinitions() {
        return definitions.values().stream()
            .sorted(Comparator.comparing(PlayerStatDefinition::key))
            .toList();
    }

    @Override
    public void increment(@NotNull UUID playerUuid, @Nullable String username, @NotNull String key, double amount) {
        if (amount == 0.0d) {
            return;
        }

        String normalizedKey = normalizeKey(key);
        if (normalizedKey == null) {
            throw new IllegalArgumentException("Invalid player stat key: " + key);
        }

        PlayerStatDefinition definition = definitions.get(normalizedKey);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown player stat: " + normalizedKey);
        }

        Instant now = Instant.now();
        LocalDate bucketDate = LocalDate.now(ZoneOffset.UTC);
        PlayerStatsState state = players.computeIfAbsent(playerUuid, ignored -> new PlayerStatsState(playerUuid));
        state.username = username != null && !username.isBlank() ? username : state.username;
        state.updatedAt = now;

        StatValueState value = state.stats.computeIfAbsent(normalizedKey, ignored -> new StatValueState());
        value.total += amount;
        value.updatedAt = now;
        value.dailyBuckets.merge(bucketDate, amount, Double::sum);
        pruneBuckets(value.dailyBuckets, bucketDate.minusDays(MAX_BUCKET_DAYS));
    }

    @Override
    public void set(@NotNull UUID playerUuid, @Nullable String username, @NotNull String key, double value) {
        String normalizedKey = normalizeTrackedKey(key);
        Instant now = Instant.now();
        LocalDate bucketDate = LocalDate.now(ZoneOffset.UTC);
        PlayerStatsState state = players.computeIfAbsent(playerUuid, ignored -> new PlayerStatsState(playerUuid));
        state.username = username != null && !username.isBlank() ? username : state.username;
        state.updatedAt = now;

        StatValueState statValue = state.stats.computeIfAbsent(normalizedKey, ignored -> new StatValueState());
        statValue.total = value;
        statValue.updatedAt = now;
        statValue.dailyBuckets.clear();
        if (value != 0.0d) {
            statValue.dailyBuckets.put(bucketDate, value);
        }
    }

    @Override
    public double total(@NotNull UUID playerUuid, @NotNull String key) {
        String normalizedKey = normalizeTrackedKey(key);
        PlayerStatsState state = players.get(playerUuid);
        if (state == null) {
            return 0.0d;
        }
        StatValueState statValue = state.stats.get(normalizedKey);
        return statValue == null ? 0.0d : statValue.total;
    }

    @Override
    public void captureOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            capturePlayerStats(player);
        }
        saveStats();
    }

    @Override
    public @NotNull List<PlayerStatsRecord> list(@Nullable String uuidText, @Nullable String username, @Nullable String statKey, @Nullable String period) {
        captureOnlinePlayers();
        String normalizedKey = normalizeKey(statKey);
        QueryWindow window = QueryWindow.parse(period);

        return players.values().stream()
            .filter(state -> matchesPlayer(state, uuidText, username))
            .sorted(Comparator.comparing(PlayerStatsState::username, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .map(state -> toRecord(state, normalizedKey, window))
            .toList();
    }

    @Override
    public @NotNull List<PlayerStatsRecord> top(@NotNull String statKey, int limit, @Nullable String period) {
        captureOnlinePlayers();

        String normalizedKey = normalizeKey(statKey);
        if (normalizedKey == null) {
            return List.of();
        }
        if (!definitions.containsKey(normalizedKey)) {
            return List.of();
        }

        QueryWindow window = QueryWindow.parse(period);
        int maxResults = Math.clamp(limit, 1, 100);

        return players.values().stream()
            .sorted(Comparator
                .comparingDouble((PlayerStatsState state) -> derivedValueForWindow(state, normalizedKey, window))
                .reversed()
                .thenComparing(PlayerStatsState::username, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(state -> state.uuid.toString()))
            .filter(state -> derivedValueForWindow(state, normalizedKey, window) > 0.0d)
            .limit(maxResults)
            .map(state -> toRecord(state, normalizedKey, window))
            .toList();
    }

    private void configureDefaults() {
        ConfigSection section = getConfigSection();
        boolean changed = false;
        if (!section.contains("autosave_ticks")) {
            section.set("autosave_ticks", 1200L);
            changed = true;
        }
        if (!section.isSection("descriptors")) {
            section.createSection("descriptors");
            changed = true;
        }
        if (section.contains("built_in")) {
            section.remove("built_in");
            changed = true;
        }
        if (changed) {
            section.save();
        }
    }

    private void loadBuiltInDefinitions() {
        List<String> enabled = new ArrayList<>();
        for (String normalizedKey : BUILT_IN_STATS) {
            RawStatisticDefinition raw = RAW_STAT_DEFINITIONS.get(normalizedKey);
            if (raw != null) {
                register(raw.definition);
                enabled.add(normalizedKey);
                continue;
            }

            PlayerStatDefinition eventDefinition = EVENT_STAT_DEFINITIONS.get(normalizedKey);
            if (eventDefinition != null) {
                register(eventDefinition);
                enabled.add(normalizedKey);
                continue;
            }

            plugin.getLogger().warning("Ignoring unknown built-in player stat: " + normalizedKey);
        }
        enabledBuiltInKeys = List.copyOf(enabled);
    }

    private void registerEventCollectors() {
        api.events().register(PlayerJoinEvent.class, event -> capturePlayerStats(event.getPlayer()));
        api.events().register(PlayerQuitEvent.class, event -> {
            Player player = event.getPlayer();
            capturePlayerStats(player);
            UUID uuid = player.getUniqueId();
            lastObservedAt.remove(uuid);
            lastObservedWorld.remove(uuid);
        });
        api.events().register(PlayerChangedWorldEvent.class, event ->
            capturePlayerStats(event.getPlayer(), event.getFrom().getName())
        );
        api.events().register(PlayerBucketFillEvent.class, event -> {
            if (!isEnabled("bucket_fills")) {
                return;
            }
            increment(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "bucket_fills", 1.0d);
        });
        api.events().register(PlayerItemConsumeEvent.class, event -> {
            if (isEnabled("food_eaten") && event.getItem().getType().isEdible()) {
                increment(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "food_eaten", 1.0d);
            }

            if (isEnabled("potions_drank") && event.getItem().getType() == Material.POTION) {
                increment(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "potions_drank", 1.0d);
            }
        });
        api.events().register(PlayerBedEnterEvent.class, event -> {
            if (!isEnabled("times_slept")) {
                return;
            }
            if (event.useBed() == org.bukkit.event.Event.Result.ALLOW) {
                increment(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "times_slept", 1.0d);
            }
        });
        api.events().register(BlockPlaceEvent.class, event -> {
            if (event.isCancelled()) {
                return;
            }
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && isEnabled("blocks_placed_creative")) {
                increment(player.getUniqueId(), player.getName(), "blocks_placed_creative", 1.0d);
            } else if (player.getGameMode() == GameMode.SURVIVAL && isEnabled("blocks_placed_survival")) {
                increment(player.getUniqueId(), player.getName(), "blocks_placed_survival", 1.0d);
            }

            if (isEnabled("seeds_planted") && SEED_PLANT_BLOCKS.contains(event.getBlockPlaced().getType())) {
                increment(player.getUniqueId(), player.getName(), "seeds_planted", 1.0d);
            }
        });
        api.events().register(BlockBreakEvent.class, event -> {
            if (event.isCancelled()) {
                return;
            }
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && isEnabled("blocks_broken_creative")) {
                increment(player.getUniqueId(), player.getName(), "blocks_broken_creative", 1.0d);
            } else if (player.getGameMode() == GameMode.SURVIVAL && isEnabled("blocks_broken_survival")) {
                increment(player.getUniqueId(), player.getName(), "blocks_broken_survival", 1.0d);
            }
        });
        api.events().register(ProjectileLaunchEvent.class, event -> {
            if (!isEnabled("spears_thrown")) {
                return;
            }
            if (!(event.getEntity() instanceof Trident trident)) {
                return;
            }
            if (!(trident.getShooter() instanceof Player player)) {
                return;
            }
            increment(player.getUniqueId(), player.getName(), "spears_thrown", 1.0d);
        });
        api.events().register(EntityDeathEvent.class, event -> {
            if (event.getEntity() instanceof EnderDragon dragon) {
                if (isEnabled("dragon_kills")) {
                    Player killer = dragon.getKiller();
                    if (killer != null) {
                        increment(killer.getUniqueId(), killer.getName(), "dragon_kills", 1.0d);
                    }
                }
                return;
            }

            Player killer = event.getEntity().getKiller();
            if (killer == null) {
                return;
            }

            String entityTypeName = event.getEntityType().name();
            if (isEnabled("zombie_kills") && entityTypeName.contains("ZOMBIE")) {
                increment(killer.getUniqueId(), killer.getName(), "zombie_kills", 1.0d);
            }
            if (isEnabled("skeleton_kills") && entityTypeName.contains("SKELETON")) {
                increment(killer.getUniqueId(), killer.getName(), "skeleton_kills", 1.0d);
            }
        });
    }

    private boolean isEnabled(String key) {
        return enabledBuiltInKeys.contains(key);
    }

    private void capturePlayerStats(Player player) {
        capturePlayerStats(player, null);
    }

    private void capturePlayerStats(Player player, @Nullable String elapsedWorldOverride) {
        UUID uuid = player.getUniqueId();
        String username = player.getName();
        Instant now = Instant.now();
        String currentWorld = player.getWorld().getName();

        Instant previousObservedAt = lastObservedAt.put(uuid, now);
        String previousWorld = elapsedWorldOverride != null ? elapsedWorldOverride : lastObservedWorld.get(uuid);
        if (previousObservedAt != null && previousWorld != null) {
            long elapsedMillis = Math.max(0L, now.toEpochMilli() - previousObservedAt.toEpochMilli());
            if (elapsedMillis > 0L) {
                recordTimeInWorld(uuid, username, previousWorld, elapsedMillis / 3_600_000.0d);
            }
        }
        lastObservedWorld.put(uuid, currentWorld);

        rawBaselines.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());

        for (String key : enabledBuiltInKeys) {
            RawStatisticDefinition definition = RAW_STAT_DEFINITIONS.get(key);
            if (definition == null) {
                continue;
            }

            long currentValue = player.getStatistic(definition.statistic);
            Long previousValue = rawBaselines.get(uuid).put(key, currentValue);
            if (previousValue == null) {
                continue;
            }

            long delta = currentValue - previousValue;
            if (delta > 0L) {
                increment(uuid, username, key, delta * definition.multiplier);
            }
        }

        PlayerStatsState state = players.computeIfAbsent(uuid, ignored -> new PlayerStatsState(uuid));
        state.username = username;
        state.updatedAt = now;
    }

    private void loadPersistedData() {
        players.clear();
        rawBaselines.clear();
        definitions.clear();

        loadConfiguredDefinitions();
        api.database().queryEach(
            "SELECT player_uuid, state_yaml FROM player_stats_state",
            null,
            rs -> {
                String uuidKey = rs.getString("player_uuid");
                String stateYaml = rs.getString("state_yaml");
                YamlConfiguration persisted = new YamlConfiguration();
                try {
                    persisted.loadFromString(stateYaml == null ? "" : stateYaml);
                } catch (Exception ignored) {
                    return;
                }
                ConfigurationSection playerSection = persisted.getConfigurationSection("player");
                if (playerSection == null) {
                    return;
                }
            try {
                UUID uuid = UUID.fromString(uuidKey);
                PlayerStatsState state = new PlayerStatsState(uuid);
                state.username = playerSection.getString("username", "");
                state.updatedAt = parseInstant(playerSection.getString("updated_at", ""));

                ConfigurationSection statsSection = playerSection.getConfigurationSection("stats");
                if (statsSection != null) {
                    for (String key : statsSection.getKeys(false)) {
                        ConfigurationSection statSection = statsSection.getConfigurationSection(key);
                        if (statSection == null) {
                            continue;
                        }
                        String normalizedKey = normalizeKey(key);
                        if (normalizedKey == null) {
                            continue;
                        }
                        StatValueState value = new StatValueState();
                        value.total = statSection.getDouble("total", 0.0d);
                        value.updatedAt = parseInstant(statSection.getString("updated_at", ""));

                        ConfigurationSection bucketsSection = statSection.getConfigurationSection("buckets");
                        if (bucketsSection != null) {
                            for (String dayKey : bucketsSection.getKeys(false)) {
                                try {
                                    value.dailyBuckets.put(LocalDate.parse(dayKey), bucketsSection.getDouble(dayKey, 0.0d));
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        state.stats.put(normalizedKey, value);
                    }
                }

                ConfigurationSection rawSection = playerSection.getConfigurationSection("raw");
                if (rawSection != null) {
                    ConcurrentMap<String, Long> baseline = new ConcurrentHashMap<>();
                    for (String key : rawSection.getKeys(false)) {
                        String normalizedKey = normalizeKey(key);
                        if (normalizedKey != null) {
                            baseline.put(normalizedKey, rawSection.getLong(key, 0L));
                        }
                    }
                    rawBaselines.put(uuid, baseline);
                }

                players.put(uuid, state);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid player stats UUID: " + uuidKey);
            }
            }
        );
    }

    private void saveStats() {
        saveDefinitionsToConfig();
        api.database().update("DELETE FROM player_stats_state", null);
        List<PlayerStatsState> ordered = players.values().stream()
            .sorted(Comparator.comparing(PlayerStatsState::username, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();

        for (PlayerStatsState state : ordered) {
            YamlConfiguration persisted = new YamlConfiguration();
            String basePath = "player";
            persisted.set(basePath + ".username", state.username);
            persisted.set(basePath + ".updated_at", state.updatedAt == null ? null : state.updatedAt.toString());
            persisted.set(basePath + ".stats", null);
            for (Map.Entry<String, StatValueState> entry : state.stats.entrySet()) {
                String statBase = basePath + ".stats." + entry.getKey();
                persisted.set(statBase + ".total", entry.getValue().total);
                persisted.set(statBase + ".updated_at", entry.getValue().updatedAt == null ? null : entry.getValue().updatedAt.toString());
                persisted.set(statBase + ".buckets", null);
                for (Map.Entry<LocalDate, Double> bucket : entry.getValue().dailyBuckets.entrySet()) {
                    persisted.set(statBase + ".buckets." + bucket.getKey(), bucket.getValue());
                }
            }
            persisted.set(basePath + ".raw", null);
            Map<String, Long> rawValues = rawBaselines.getOrDefault(state.uuid, new ConcurrentHashMap<>());
            for (Map.Entry<String, Long> rawEntry : rawValues.entrySet()) {
                persisted.set(basePath + ".raw." + rawEntry.getKey(), rawEntry.getValue());
            }
            String stateYaml = persisted.saveToString();
            api.database().update(
                "INSERT INTO player_stats_state (player_uuid, state_yaml, updated_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET state_yaml = excluded.state_yaml, updated_at = excluded.updated_at",
                ps -> {
                    ps.setString(1, state.uuid.toString());
                    ps.setString(2, stateYaml);
                    ps.setLong(3, System.currentTimeMillis());
                }
            );
        }
    }

    private void ensureStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS player_stats_state (" +
            "player_uuid TEXT PRIMARY KEY," +
            "state_yaml TEXT NOT NULL," +
            "updated_at INTEGER NOT NULL" +
            ");"
        );
        api.database().execute("CREATE INDEX IF NOT EXISTS player_stats_state_updated_at ON player_stats_state(updated_at);");
    }

    private void loadConfiguredDefinitions() {
        ConfigSection definitionsSection = getConfigSection().getSection("descriptors", false);
        if (definitionsSection == null) {
            return;
        }

        for (String key : definitionsSection.getKeys(false)) {
            ConfigSection definitionSection = definitionsSection.getSection(key, false);
            if (definitionSection == null) {
                continue;
            }
            String normalizedKey = normalizeKey(key);
            if (normalizedKey == null) {
                continue;
            }
            definitions.put(normalizedKey, new PlayerStatDefinition(
                normalizedKey,
                definitionSection.getString("title", normalizedKey),
                definitionSection.getString("description", ""),
                blankToNull(definitionSection.getString("namespace", "")),
                blankToNull(definitionSection.getString("scope", "")),
                blankToNull(definitionSection.getString("scope_id", ""))
            ));
        }
    }

    private void loadTimeInDefinitions() {
        timeInDefinitions.clear();
        ConfigSection timeInSection = getConfigSection().getSection("time_in", false);
        if (timeInSection == null) {
            return;
        }

        for (String bucketKey : timeInSection.getKeys(false)) {
            ConfigSection bucketSection = timeInSection.getSection(bucketKey, false);
            if (bucketSection == null) {
                continue;
            }

            String normalizedBucket = normalizeKey(bucketKey);
            if (normalizedBucket == null) {
                plugin.getLogger().warning("Ignoring invalid player_stats.time_in key: " + bucketKey);
                continue;
            }

            String statKey = normalizeKey("time_in_" + normalizedBucket);
            if (statKey == null) {
                continue;
            }

            String title = bucketSection.getString("title", "Time in " + bucketKey);
            String description = bucketSection.getString("description", "");
            register(new PlayerStatDefinition(statKey, title, description));

            Set<String> worlds = new LinkedHashSet<>();
            for (String worldName : bucketSection.getStringList("worlds")) {
                if (worldName == null) {
                    continue;
                }
                String normalizedWorld = worldName.trim().toLowerCase(Locale.ROOT);
                if (!normalizedWorld.isBlank()) {
                    worlds.add(normalizedWorld);
                }
            }

            if (worlds.isEmpty()) {
                plugin.getLogger().warning("Ignoring player_stats.time_in." + bucketKey + " with no worlds configured.");
                continue;
            }

            timeInDefinitions.put(statKey, new TimeInDefinition(statKey, worlds));
        }
    }

    private void recordTimeInWorld(@NotNull UUID uuid, @Nullable String username, @NotNull String worldName, double hours) {
        if (hours <= 0.0d || timeInDefinitions.isEmpty()) {
            return;
        }

        String normalizedWorld = worldName.toLowerCase(Locale.ROOT);
        for (TimeInDefinition definition : timeInDefinitions.values()) {
            if (definition.worlds().contains(normalizedWorld)) {
                increment(uuid, username, definition.statKey(), hours);
            }
        }
    }

    private void saveDefinitionsToConfig() {
        ConfigSection section = getConfigSection();
        section.remove("descriptors");
        ConfigSection descriptorsSection = section.createSection("descriptors");
        for (PlayerStatDefinition definition : getDefinitions()) {
            descriptorsSection.set(definition.key() + ".title", definition.title());
            descriptorsSection.set(definition.key() + ".description", definition.description());
            descriptorsSection.set(definition.key() + ".namespace", definition.namespace());
            descriptorsSection.set(definition.key() + ".scope", definition.scope());
            descriptorsSection.set(definition.key() + ".scope_id", definition.scopeId());
        }
        section.save();
    }

    private PlayerStatsRecord toRecord(PlayerStatsState state, @Nullable String statKey, QueryWindow window) {
        List<PlayerStatValue> values = new ArrayList<>();
        Collection<PlayerStatDefinition> definitionsToUse = statKey == null ? getDefinitions() : getSelectedDefinitions(statKey);
        for (PlayerStatDefinition definition : definitionsToUse) {
            StatValueState valueState = state.stats.get(definition.key());
            double value = derivedValueForWindow(state, definition.key(), window);
            Instant updatedAt = valueState == null ? null : valueState.updatedAt;
            if ("distance_total_moved_km".equals(definition.key())) {
                updatedAt = state.updatedAt;
            }
            values.add(new PlayerStatValue(definition.key(), definition.title(), definition.description(), value, updatedAt));
        }
        return new PlayerStatsRecord(
            state.uuid,
            blankToNull(state.username),
            PlayerUtil.isBedrock(state.uuid) ? "bedrock" : "java",
            state.updatedAt,
            values
        );
    }

    private double derivedValueForWindow(PlayerStatsState state, String key, QueryWindow window) {
        if ("distance_total_moved_km".equals(key)) {
            return statWindowValue(state, "distance_walked_km", window)
                + statWindowValue(state, "distance_sprinted_km", window)
                + statWindowValue(state, "distance_swam_km", window)
                + statWindowValue(state, "distance_boated_km", window)
                + statWindowValue(state, "distance_flown_km", window)
                + statWindowValue(state, "distance_horse_km", window)
                + statWindowValue(state, "distance_minecart_km", window)
                + statWindowValue(state, "distance_pig_km", window)
                + statWindowValue(state, "distance_strider_km", window);
        }
        return statWindowValue(state, key, window);
    }

    private double statWindowValue(PlayerStatsState state, String key, QueryWindow window) {
        StatValueState valueState = state.stats.get(key);
        return valueState == null ? 0.0d : valueForWindow(valueState, window);
    }

    private List<PlayerStatDefinition> getSelectedDefinitions(@Nullable String statKey) {
        if (statKey == null || statKey.isBlank()) {
            return getDefinitions();
        }
        PlayerStatDefinition definition = definitions.get(statKey);
        return definition == null ? List.of() : List.of(definition);
    }

    private double valueForWindow(StatValueState state, QueryWindow window) {
        if (window.days == null) {
            return state.total;
        }

        LocalDate threshold = LocalDate.now(ZoneOffset.UTC).minusDays(window.days - 1L);
        double value = 0.0d;
        for (Map.Entry<LocalDate, Double> entry : state.dailyBuckets.entrySet()) {
            if (!entry.getKey().isBefore(threshold)) {
                value += entry.getValue();
            }
        }
        return value;
    }

    private boolean matchesPlayer(PlayerStatsState state, @Nullable String uuidText, @Nullable String username) {
        boolean matchesUuid = uuidText != null && !uuidText.isBlank() && state.uuid.toString().equalsIgnoreCase(uuidText);
        boolean matchesUsername = username != null && !username.isBlank() && state.username != null && state.username.equalsIgnoreCase(username);
        if ((uuidText == null || uuidText.isBlank()) && (username == null || username.isBlank())) {
            return true;
        }
        return matchesUuid || matchesUsername;
    }

    private Map<String, Object> toDefinitionMap(PlayerStatDefinition definition) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("key", definition.key());
        response.put("title", definition.title());
        response.put("description", definition.description());
        if (definition.namespace() != null) {
            response.put("namespace", definition.namespace());
        }
        if (definition.scope() != null) {
            response.put("scope", definition.scope());
        }
        if (definition.scopeId() != null) {
            response.put("scope_id", definition.scopeId());
        }
        return response;
    }

    private Map<String, Object> toResponseMap(PlayerStatsRecord record) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("uuid", record.uuid().toString());
        response.put("username", record.username());
        response.put("updated_at", record.updatedAt() == null ? null : record.updatedAt().toString());
        response.put("stats", record.stats().stream().map(value -> {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("key", value.key());
            stat.put("title", value.title());
            stat.put("description", value.description());
            stat.put("value", value.value());
            stat.put("updated_at", value.updatedAt() == null ? null : value.updatedAt().toString());
            return stat;
        }).toList());
        return response;
    }

    private @Nullable String normalizeKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        for (char c : normalized.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                continue;
            }
            return null;
        }
        return normalized;
    }

    private @NotNull String normalizeTrackedKey(@NotNull String key) {
        String normalizedKey = normalizeKey(key);
        if (normalizedKey == null) {
            throw new IllegalArgumentException("Invalid player stat key: " + key);
        }

        PlayerStatDefinition definition = definitions.get(normalizedKey);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown player stat: " + normalizedKey);
        }
        return normalizedKey;
    }

    private @Nullable Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void pruneBuckets(Map<LocalDate, Double> buckets, LocalDate oldestAllowed) {
        buckets.keySet().removeIf(date -> date.isBefore(oldestAllowed));
    }

    private @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class RawStatisticDefinition {
        private final Statistic statistic;
        private final PlayerStatDefinition definition;
        private final double multiplier;

        private RawStatisticDefinition(String key, Statistic statistic, String title, String description, double multiplier) {
            this.statistic = statistic;
            this.multiplier = multiplier;
            this.definition = new PlayerStatDefinition(
                key,
                title,
                description
            );
        }
    }

    private static final class PlayerStatsState {
        private final UUID uuid;
        private String username;
        private Instant updatedAt;
        private final Map<String, StatValueState> stats = new LinkedHashMap<>();

        private PlayerStatsState(UUID uuid) {
            this.uuid = uuid;
        }

        private String username() {
            return username;
        }
    }

    private static final class StatValueState {
        private double total;
        private Instant updatedAt;
        private final Map<LocalDate, Double> dailyBuckets = new LinkedHashMap<>();
    }

    private record QueryWindow(String key, Integer days) {
            private QueryWindow(String key, @Nullable Integer days) {
                this.key = key;
                this.days = days;
            }

            private static QueryWindow parse(@Nullable String raw) {
                if (raw == null || raw.isBlank()) {
                    return new QueryWindow("all", null);
                }

                return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                    case "day", "today", "1d" -> new QueryWindow("day", 1);
                    case "week", "last_week", "7d" -> new QueryWindow("week", 7);
                    case "month", "last_month", "30d" -> new QueryWindow("month", 30);
                    case "year", "last_year", "365d" -> new QueryWindow("year", 365);
                    default -> new QueryWindow("all", null);
                };
            }
        }

    private record TimeInDefinition(String statKey, Set<String> worlds) {}
}
