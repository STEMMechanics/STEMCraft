package dev.stemcraft.minigame.parkour;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.hologram.HologramTypeHandler;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import dev.stemcraft.minigame.TimedRecordLeaderboard;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ParkourMiniGame extends BaseMiniGame {
    private static final int DEFAULT_CAPACITY = 512;
    private static final String RECORD_HOLOGRAM_TYPE = "parkour_records";
    private static final String RUN_START_MILLIS_KEY = "runStartMillis";
    private static final String LAST_RUN_MILLIS_KEY = "lastRunMillis";
    private static final String RUN_ARMED_KEY = "runArmed";

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "parkour";

    private final ParkourConfig config;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;

    public ParkourMiniGame(STEMCraftAPI api) {
        super(api);
        this.config = new ParkourConfig(api, this);
    }

    @Override
    public void onLoad() {
        ParkourArenaHandler handler = new ParkourArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
            .registerArenaPlaceholder("record-time", (arena, team, player) -> arena == null ? "-" : formatMillis(arenaBestMillis(arena)))
            .registerArenaPlaceholder("record-holder", (arena, team, player) -> arena == null ? "-" : arenaBestHolder(arena))
            .registerPlayerPlaceholder("run-state", (arena, team, player) -> player == null ? "idle" : runState(player))
            .registerPlayerPlaceholder("run-time", (arena, team, player) -> player == null ? "-" : displayRunTime(player))
            .registerPlayerPlaceholder("best-time", (arena, team, player) -> player == null ? "-" : formatMillis(bestTimeMillis(player)));

        configFile = api.config().load("parkour.yml");
        if (configFile == null) {
            api.messages().warn("Parkour config could not be loaded.");
            return;
        }
        configFile.setAutoSave(true);
        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());
        loadArenas();
        registerRecordHolograms();
        new ParkourCommand(api, this).onEnable();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "Parkour: {arena:name}",
                "Run: {player:run-time}"
            ),
            List.of(
                "<gold>Parkour: <white>{arena:name}",
                "",
                "Players: {arena:joined-players}",
                "State: {player:run-state}",
                "Run: {player:run-time}",
                "Best: {player:best-time}",
                "Record: {arena:record-time}",
                "Holder: {arena:record-holder}"
            )
        ));
        return definitions;
    }

    public @Nullable MiniGameArena createArena(@NotNull String arenaId, @NotNull World world) {
        if (minigame.arena(arenaId) != null) {
            return null;
        }

        return minigame.createArena(arenaId, world)
            .setName(StringUtil.beautify(arenaId))
            .setLobbySpawn(world.getSpawnLocation())
            .setMinPlayers(1)
            .setMaxPlayers(DEFAULT_CAPACITY)
            .set("lobbyRegion", singleBlockRegion(world.getSpawnLocation()))
            .set("bestTimes", new LinkedHashMap<UUID, ParkourArenaRecord.BestTime>());
    }

    public void deleteArena(@NotNull String arenaId) {
        MiniGameArena arena = minigame.arena(arenaId);
        if (arena != null) {
            minigame.removeArena(arenaId);
        }
        config.deleteArena(arenaId);
        api.holograms().delete(RECORD_HOLOGRAM_TYPE, arenaId);
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        config.saveArena(arena);
        api.holograms().update(RECORD_HOLOGRAM_TYPE, arena.id());
    }

    public void persistArenaEnabled(@NotNull MiniGameArena arena, boolean enabled) {
        if (enabled && !config.hasArena(arena.id())) {
            saveArena(arena);
            return;
        }
        config.setArenaEnabled(arena.id(), enabled);
    }

    public boolean reloadFromConfig() {
        if (!reloadConfigFile(configFile)) {
            return false;
        }

        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());
        unloadArenas(minigame);
        loadArenas();
        api.holograms().update(RECORD_HOLOGRAM_TYPE, null);
        return true;
    }

    public void loadArenas() {
        ConfigSection arenas = config.getSection("arenas");
        if (arenas == null) {
            return;
        }

        for (String arenaId : arenas.getKeys(false)) {
            ConfigSection arenaSection = arenas.getSection(arenaId, false);
            if (arenaSection == null) {
                continue;
            }

            try {
                ParkourArenaRecord arenaDef = config.load(arenaId, arenaSection);
                MiniGameArena arena = minigame.createArena(arenaId, arenaDef.world())
                    .setName(arenaDef.name())
                    .setRegion(arenaDef.arenaRegion())
                    .setMinPlayers(1)
                    .setMaxPlayers(DEFAULT_CAPACITY)
                    .set("lobbyRegion", arenaDef.lobbyRegion() == null ? null : arenaDef.lobbyRegion().copy())
                    .set("arenaRegion", arenaDef.arenaRegion())
                    .set("finishRegion", arenaDef.finishRegion())
                    .set("bestTimes", new LinkedHashMap<>(arenaDef.bestTimes()));

                syncLobbyRegion(arena);

                ArenaValidationResult result = arena.validate();
                if (result.hasErrors()) {
                    api.messages().error("Parkour arena '" + arenaId + "' has validation errors and will be disabled:");
                    for (String error : result.getErrors()) {
                        api.messages().error(" - " + error);
                    }
                    arena.setStatus(MiniGameArena.ArenaStatus.DISABLED);
                    continue;
                }

                arena.setStatus(arenaDef.enabled()
                    ? MiniGameArena.ArenaStatus.WAITING
                    : MiniGameArena.ArenaStatus.DISABLED);
            } catch (MiniGameInvalidArenaConfigException exception) {
                createPlaceholderArena(arenaId, arenaSection, exception.getMessage());
            }
        }
    }

    public void syncLobbyRegion(@NotNull MiniGameArena arena) {
        SCRegion lobbyRegion = arena.get("lobbyRegion", SCRegion.class);
        if (lobbyRegion == null) {
            arena.remove("startRegion");
            return;
        }
        arena.setLobbySpawn(lobbySpawnFor(lobbyRegion));
        arena.set("startRegion", lobbyRegion.copy());
    }

    public boolean startRun(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer == null || isRunning(mgPlayer) || !isArmed(mgPlayer)) {
            return false;
        }

        mgPlayer.set(RUN_START_MILLIS_KEY, System.currentTimeMillis());
        mgPlayer.remove(LAST_RUN_MILLIS_KEY);
        mgPlayer.set(RUN_ARMED_KEY, false);
        mgPlayer.setScore(0);
        player.setFallDistance(0.0f);
        arena.info(player, "Run started.");
        return true;
    }

    public void resetRun(@NotNull MiniGameArena arena, @NotNull Player player, @Nullable String reason) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer == null) {
            return;
        }

        mgPlayer.remove(RUN_START_MILLIS_KEY);
        mgPlayer.remove(LAST_RUN_MILLIS_KEY);
        mgPlayer.set(RUN_ARMED_KEY, true);
        player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.teleport(arena.getLobbySpawn());
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        if (reason != null && !reason.isBlank()) {
            arena.warn(player, reason);
        }
    }

    public void completeRun(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer == null || !isRunning(mgPlayer)) {
            return;
        }

        long duration = currentRunMillis(mgPlayer);
        mgPlayer.remove(RUN_START_MILLIS_KEY);
        mgPlayer.set(LAST_RUN_MILLIS_KEY, duration);
        mgPlayer.set(RUN_ARMED_KEY, false);

        ParkourArenaRecord.BestTime existing = bestTimes(arena).get(player.getUniqueId());
        ParkourArenaRecord.BestTime arenaRecord = arenaBest(arena);
        boolean personalBest = existing == null || duration < existing.timeMillis();
        boolean arenaBest = arenaRecord == null || duration < arenaRecord.timeMillis();
        if (personalBest) {
            bestTimes(arena).put(player.getUniqueId(), new ParkourArenaRecord.BestTime(player.getUniqueId(), player.getName(), duration));
            saveArena(arena);
        }

        if (arenaBest) {
            arena.startCelebration("parkour-record-" + player.getUniqueId(), player.getLocation(), 4,
                Color.AQUA, Color.BLUE, Color.WHITE);
            arena.success(player, "Finished in " + formatMillis(duration) + ". New arena record!");
        } else if (personalBest) {
            arena.startCelebration("parkour-best-" + player.getUniqueId(), player.getLocation(), 3,
                Color.LIME, Color.YELLOW, Color.ORANGE);
            arena.success(player, "Finished in " + formatMillis(duration) + ". New personal best!");
        } else {
            arena.success(player, "Finished in " + formatMillis(duration) + ". Personal best: " + formatMillis(existing.timeMillis()));
        }
    }

    public void clearRun(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.remove(RUN_START_MILLIS_KEY);
            mgPlayer.remove(LAST_RUN_MILLIS_KEY);
            mgPlayer.remove(RUN_ARMED_KEY);
        }
    }

    public void armRun(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.set(RUN_ARMED_KEY, true);
        }
    }

    public boolean isRunning(@NotNull MiniGamePlayer player) {
        return player.contains(RUN_START_MILLIS_KEY);
    }

    public boolean isArmed(@NotNull MiniGamePlayer player) {
        return player.get(RUN_ARMED_KEY, Boolean.class, false);
    }

    public long currentRunMillis(@NotNull MiniGamePlayer player) {
        long started = player.get(RUN_START_MILLIS_KEY, Long.class, 0L);
        if (started <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - started);
    }

    public long bestTimeMillis(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return 0L;
        }
        ParkourArenaRecord.BestTime bestTime = bestTimes(arena).get(player.getPlayer().getUniqueId());
        return bestTime == null ? 0L : bestTime.timeMillis();
    }

    public long lastRunMillis(@NotNull MiniGamePlayer player) {
        return player.get(LAST_RUN_MILLIS_KEY, Long.class, 0L);
    }

    public String runState(@NotNull MiniGamePlayer player) {
        if (isRunning(player)) {
            return "running";
        }
        return lastRunMillis(player) > 0L ? "finished" : "ready";
    }

    public String displayRunTime(@NotNull MiniGamePlayer player) {
        if (isRunning(player)) {
            return formatSeconds(currentRunMillis(player));
        }

        long lastRun = lastRunMillis(player);
        if (lastRun > 0L) {
            return formatMillis(lastRun);
        }
        return "-";
    }

    public String formatMillis(long durationMillis) {
        return TimedRecordLeaderboard.formatMillis(durationMillis);
    }

    public String formatSeconds(long durationMillis) {
        if (durationMillis <= 0L) {
            return "0s";
        }

        long totalSeconds = durationMillis / 1000L;
        if (totalSeconds < 60L) {
            return totalSeconds + "s";
        }

        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    public long arenaBestMillis(@NotNull MiniGameArena arena) {
        ParkourArenaRecord.BestTime bestTime = arenaBest(arena);
        return bestTime == null ? 0L : bestTime.timeMillis();
    }

    public String arenaBestHolder(@NotNull MiniGameArena arena) {
        ParkourArenaRecord.BestTime bestTime = arenaBest(arena);
        return bestTime == null ? "-" : bestTime.playerName();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, ParkourArenaRecord.BestTime> bestTimes(MiniGameArena arena) {
        return arena.getOrCreate("bestTimes", Map.class, LinkedHashMap::new);
    }

    private @Nullable ParkourArenaRecord.BestTime arenaBest(@NotNull MiniGameArena arena) {
        return bestTimes(arena).values().stream()
            .min(java.util.Comparator.comparingLong(ParkourArenaRecord.BestTime::timeMillis))
            .orElse(null);
    }

    private void registerRecordHolograms() {
        api.holograms().registerType(RECORD_HOLOGRAM_TYPE, new HologramTypeHandler() {
            @Override
            public List<String> list(@NotNull String type) {
                return minigame.arenas().stream()
                    .map(MiniGameArena::id)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            }

            @Override
            public @NotNull List<String> lines(@NotNull String type, @NotNull String context, int id, @NotNull List<String> data) {
                return renderRecordHologram(context, data);
            }
        });
    }

    private @NotNull List<String> renderRecordHologram(@NotNull String arenaId, @NotNull List<String> data) {
        MiniGameArena arena = minigame.arena(arenaId);
        if (arena == null) {
            return List.of("<red>Unknown Parkour arena:</red> <yellow>" + arenaId + "</yellow>");
        }

        List<TimedRecordLeaderboard.Entry> entries = bestTimes(arena).values().stream()
            .map(bestTime -> new TimedRecordLeaderboard.Entry(bestTime.playerName(), bestTime.timeMillis()))
            .toList();

        return TimedRecordLeaderboard.render("<gold>" + arena.getName() + " Records</gold>", data, entries);
    }

    private void createPlaceholderArena(@NotNull String arenaId, @NotNull ConfigSection arenaSection, @NotNull String error) {
        World world = resolvePlaceholderWorld(arenaSection);
        if (world == null || minigame.arena(arenaId) != null) {
            api.messages().error("Failed to load Parkour arena '" + arenaId + "': " + error);
            return;
        }

        MiniGameArena arena = minigame.createArena(arenaId, world)
            .setName(arenaSection.getString("name", StringUtil.beautify(arenaId)))
            .setLobbySpawn(world.getSpawnLocation())
            .setMinPlayers(1)
            .setMaxPlayers(DEFAULT_CAPACITY)
            .set("loadError", error)
            .set("bestTimes", new LinkedHashMap<UUID, ParkourArenaRecord.BestTime>());

        SCRegion lobbyRegion = loadRegion(arenaSection, world, "lobby");
        if (lobbyRegion == null) {
            lobbyRegion = loadRegion(arenaSection, world, "start");
        }
        if (lobbyRegion == null) {
            Location configuredSpawn = loadLocation(arenaSection, world);
            if (configuredSpawn != null) {
                lobbyRegion = singleBlockRegion(configuredSpawn);
            }
        }
        if (lobbyRegion != null) {
            arena.set("lobbyRegion", lobbyRegion.copy());
        }

        SCRegion arenaRegion = loadRegion(arenaSection, world, "arena");
        if (arenaRegion != null) {
            arena.setRegion(arenaRegion);
            arena.set("arenaRegion", arenaRegion.copy());
        }

        SCRegion finishRegion = loadRegion(arenaSection, world, "finish");
        if (finishRegion != null) {
            arena.set("finishRegion", finishRegion.copy());
        }

        syncLobbyRegion(arena);
        arena.setStatus(MiniGameArena.ArenaStatus.SETUP);
        api.messages().error("Failed to load Parkour arena '" + arenaId + "': " + error + " Loaded in setup mode.");
    }

    private @Nullable World resolvePlaceholderWorld(@NotNull ConfigSection arenaSection) {
        String worldName = arenaSection.getString("world");
        if (!worldName.isBlank()) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                return world;
            }
        }

        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.getFirst();
    }

    private @Nullable Location loadLocation(@NotNull ConfigSection section, @NotNull World world) {
        String serialized = section.getString("spawn");
        if (serialized.isBlank()) {
            return null;
        }
        return dev.stemcraft.api.util.LocationUtil.deserialize(serialized, world);
    }

    private @Nullable SCRegion loadRegion(@NotNull ConfigSection section, @NotNull World world, @NotNull String key) {
        String serialized = section.getString(key);
        if (serialized.isBlank()) {
            return null;
        }
        return SCRegion.fromString(serialized, world);
    }

    private @NotNull SCRegion singleBlockRegion(@NotNull Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Lobby location must have a world.");
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        SCRegion region = SCRegion.fromString("CUBOID:" + x + "," + y + "," + z + "," + x + "," + y + "," + z, world);
        if (region == null) {
            throw new IllegalStateException("Failed to build parkour lobby region.");
        }
        return region;
    }

    private @NotNull Location lobbySpawnFor(@NotNull SCRegion region) {
        World world = region.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Lobby region must have a world.");
        }

        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        Location center = new Location(
            world,
            (min.getBlockX() + max.getBlockX() + 1) / 2.0d,
            min.getBlockY(),
            (min.getBlockZ() + max.getBlockZ() + 1) / 2.0d
        );
        if (region.contains(center)) {
            return center;
        }

        Location ground = region.getRandomGroundLocation();
        if (ground != null) {
            return ground;
        }

        Location fallback = region.getRandomLocation();
        if (fallback != null) {
            return fallback;
        }

        return center;
    }
}
