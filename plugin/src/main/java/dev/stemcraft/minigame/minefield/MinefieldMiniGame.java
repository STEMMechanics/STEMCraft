package dev.stemcraft.minigame.minefield;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import dev.stemcraft.minigame.TimedRecordLeaderboard;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MinefieldMiniGame extends BaseMiniGame {
    static final String START_REGION_KEY = "startRegion";
    static final String FIELD_REGION_KEY = "fieldRegion";
    static final String FINISH_REGION_KEY = "finishRegion";
    static final String ARENA_REGION_KEY = "arenaRegion";
    static final String START_COUNTDOWN_SECONDS_KEY = "startCountdownSeconds";
    static final String ENDING_SECONDS_KEY = "endingSeconds";
    static final String CONFIGURED_MINE_COUNT_KEY = "configuredMineCount";
    static final String LIVES_KEY = "lives";
    static final String HIDDEN_BLOCK_KEY = "hiddenBlock";
    static final String CLEAR_BLOCK_KEY = "clearBlock";
    static final String ADJACENT_BLOCK_KEY = "adjacentBlock";
    static final String MARKER_BLOCK_KEY = "markerBlock";
    static final String TRIGGERED_MINE_BLOCK_KEY = "triggeredMineBlock";
    static final String BEST_TIME_MILLIS_KEY = "bestTimeMillis";
    static final String WINNER_NAME_KEY = "winnerName";
    static final String RESULT_LINE_KEY = "resultLine";
    static final String MINE_COUNT_KEY = "mineCount";
    static final String REVEALED_SAFE_COUNT_KEY = "revealedSafeCount";
    static final String PLAYER_LIVES_LEFT_KEY = "livesLeft";
    static final String PLAYER_ELIMINATED_KEY = "eliminated";
    static final String PLAYER_RUN_START_MILLIS_KEY = "runStartMillis";
    static final String PLAYER_RUN_TIME_MILLIS_KEY = "runTimeMillis";

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "minefield";

    private MinefieldConfig config;
    private MinefieldArenaHandler handler;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;

    public MinefieldMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onLoad() {
        config = new MinefieldConfig(api, this);
        handler = new MinefieldArenaHandler(api, this);
        handler.initialize();

        minigame = createMiniGame(namespace, handler)
            .registerArenaPlaceholder("winner", (arena, team, player) -> arena == null ? "-" : winnerName(arena))
            .registerArenaPlaceholder("result", (arena, team, player) -> arena == null ? "-" : resultLine(arena))
            .registerArenaPlaceholder("best-time", (arena, team, player) -> arena == null ? "-" : formatMillis(bestTimeMillis(arena)))
            .registerArenaPlaceholder("mine-count", (arena, team, player) -> arena == null ? "0" : Integer.toString(mineCount(arena)))
            .registerArenaPlaceholder("configured-mine-count", (arena, team, player) -> arena == null ? "0" : Integer.toString(configuredMineCount(arena)))
            .registerArenaPlaceholder("safe-revealed", (arena, team, player) -> arena == null ? "0" : Integer.toString(revealedSafeCount(arena)))
            .registerPlayerPlaceholder("lives", (arena, team, player) -> player == null ? "0" : Integer.toString(livesLeft(player)))
            .registerPlayerPlaceholder("time", (arena, team, player) -> player == null ? "-" : displayRunTime(player))
            .registerPlayerPlaceholder("progress", (arena, team, player) -> player == null ? "0%" : progressDisplay(player))
            .registerPlayerPlaceholder("state", (arena, team, player) -> player == null ? "idle" : playerState(player));

        configFile = api.config().load("minefield.yml");
        if (configFile == null) {
            api.messages().warn("Minefield config could not be loaded.");
            return;
        }

        configFile.setAutoSave(true);
        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());
        configureRewards(minigame, configFile);

        new MinefieldCommand(api, this).onEnable();
        loadArenas();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                ":info_blue: <aqua>Waiting</aqua> <dark_gray>•</dark_gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
            ),
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":warning_yellow: <gold>Minefield</gold> <dark_gray>•</dark_gray> <aqua>Lobby</aqua>",
                ":info_green: <gray>Joined</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":question_blue: <gray>Need</gray> <yellow>{arena:min-players}</yellow> <gray>players</gray>",
                ":question_blue: <gray>Mines</gray> <blue>{arena:configured-mine-count}</blue>",
                ":clock: <gray>Record</gray> <gold>{arena:best-time}</gold>",
                ":world: <gray>Map ID</gray> <gold>{arena:id}</gold>"
            ),
            3,
            "BLUE"
        ));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                ":click_action_right: <gold>Starting in</gold> <yellow>{arena:time-remaining}</yellow>",
                ":heart: <gray>Lives</gray> <red>{player:lives}</red>"
            ),
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":click_action_right: <gold>Starts In</gold> <yellow>{arena:time-remaining}</yellow>",
                ":info_green: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":question_blue: <gray>Need</gray> <yellow>{arena:min-players}</yellow>",
                ":question_blue: <gray>Mines</gray> <blue>{arena:configured-mine-count}</blue>",
                ":heart: <gray>Lives</gray> <red>{player:lives}</red>",
                ":clock: <gray>Record</gray> <gold>{arena:best-time}</gold>",
                ":world: <gray>Map ID</gray> <gold>{arena:id}</gold>"
            ),
            3,
            "GOLD"
        ));
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                ":location: <gray>Progress</gray> <aqua>{player:progress}</aqua>",
                ":clock: <gray>Time</gray> <gold>{player:time}</gold>",
                ":heart: <gray>Lives</gray> <red>{player:lives}</red>"
            ),
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":warning_yellow: <gold>Minefield</gold> <dark_gray>•</dark_gray> <aqua>Run</aqua>",
                ":question_blue: <gray>Mines</gray> <blue>{arena:mine-count}</blue>",
                ":info_green: <gray>Revealed</gray> <white>{arena:safe-revealed}</white>",
                ":location: <gray>Progress</gray> <aqua>{player:progress}</aqua>",
                ":heart: <gray>Lives</gray> <red>{player:lives}</red>",
                ":clock: <gray>Time</gray> <gold>{player:time}</gold>",
                ":clock: <gray>Record</gray> <gold>{arena:best-time}</gold>",
                ":warning_yellow: <gray>Status</gray> <yellow>{player:state}</yellow>"
            ),
            2,
            "BLUE"
        ));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                ":warning_yellow: <gold>{arena:result}</gold>",
                ":click_action_right: <gray>Reset in</gray> <yellow>{arena:time-remaining}</yellow>"
            ),
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":warning_yellow: <gold>{arena:result}</gold>",
                ":info_green: <gray>Winner</gray> <yellow>{arena:winner}</yellow>",
                ":clock: <gray>Time</gray> <gold>{player:time}</gold>",
                ":clock: <gray>Record</gray> <gold>{arena:best-time}</gold>",
                ":click_action_right: <gray>Reset In</gray> <yellow>{arena:time-remaining}</yellow>",
                ":heart: <gray>Lives</gray> <red>{player:lives}</red>"
            ),
            2,
            "GREEN"
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
            .setSpectatorSpawn(world.getSpawnLocation())
            .setMinPlayers(1)
            .setMaxPlayers(12)
            .set(ARENA_REGION_KEY, null)
            .set(START_REGION_KEY, null)
            .set(FIELD_REGION_KEY, null)
            .set(FINISH_REGION_KEY, null)
            .set(START_COUNTDOWN_SECONDS_KEY, MinefieldConfig.DEFAULT_START_COUNTDOWN_SECONDS)
            .set(ENDING_SECONDS_KEY, MinefieldConfig.DEFAULT_ENDING_SECONDS)
            .set(CONFIGURED_MINE_COUNT_KEY, MinefieldConfig.DEFAULT_CONFIGURED_MINE_COUNT)
            .set(LIVES_KEY, MinefieldConfig.DEFAULT_LIVES)
            .set(HIDDEN_BLOCK_KEY, MinefieldConfig.DEFAULT_HIDDEN_BLOCK)
            .set(CLEAR_BLOCK_KEY, MinefieldConfig.DEFAULT_CLEAR_BLOCK)
            .set(ADJACENT_BLOCK_KEY, MinefieldConfig.DEFAULT_ADJACENT_BLOCK)
            .set(MARKER_BLOCK_KEY, MinefieldConfig.DEFAULT_MARKER_BLOCK)
            .set(TRIGGERED_MINE_BLOCK_KEY, MinefieldConfig.DEFAULT_TRIGGERED_MINE_BLOCK)
            .set(BEST_TIME_MILLIS_KEY, 0L)
            .set(WINNER_NAME_KEY, "-")
            .set(RESULT_LINE_KEY, "Waiting for players")
            .set(MINE_COUNT_KEY, 0)
            .set(REVEALED_SAFE_COUNT_KEY, 0);
    }

    public void deleteArena(@NotNull String arenaId) {
        MiniGameArena arena = minigame.arena(arenaId);
        if (arena != null) {
            minigame.removeArena(arenaId);
        }
        config.deleteArena(arenaId);
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        config.saveArena(arena);
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
        configureRewards(minigame, configFile);
        unloadArenas(minigame);
        loadArenas();
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
                MinefieldArenaRecord record = config.load(arenaId, arenaSection);
                MiniGameArena arena = minigame.createArena(arenaId, record.world())
                    .setName(record.name())
                    .setLobbySpawn(record.startSpawn())
                    .setSpectatorSpawn(record.spectator())
                    .setRegion(record.arenaRegion())
                    .setMinPlayers(record.minPlayers())
                    .setMaxPlayers(record.maxPlayers())
                    .set(ARENA_REGION_KEY, record.arenaRegion())
                    .set(START_REGION_KEY, record.startRegion())
                    .set(FIELD_REGION_KEY, record.fieldRegion())
                    .set(FINISH_REGION_KEY, record.finishRegion())
                    .set(START_COUNTDOWN_SECONDS_KEY, record.startCountdownSeconds())
                    .set(ENDING_SECONDS_KEY, record.endingSeconds())
                    .set(CONFIGURED_MINE_COUNT_KEY, record.configuredMineCount())
                    .set(LIVES_KEY, record.lives())
                    .set(HIDDEN_BLOCK_KEY, record.hiddenBlock())
                    .set(CLEAR_BLOCK_KEY, record.clearBlock())
                    .set(ADJACENT_BLOCK_KEY, record.adjacentBlock())
                    .set(MARKER_BLOCK_KEY, record.markerBlock())
                    .set(TRIGGERED_MINE_BLOCK_KEY, record.triggeredMineBlock())
                    .set(BEST_TIME_MILLIS_KEY, record.bestTimeMillis())
                    .set(WINNER_NAME_KEY, "-")
                    .set(RESULT_LINE_KEY, "Waiting for players")
                    .set(MINE_COUNT_KEY, 0)
                    .set(REVEALED_SAFE_COUNT_KEY, 0);

                syncStartRegion(arena);

                ArenaValidationResult result = arena.validate();
                if (result.hasErrors()) {
                    api.messages().error("Minefield arena '" + arenaId + "' has validation errors and will be disabled:");
                    for (String error : result.getErrors()) {
                        api.messages().error(" - " + error);
                    }
                    arena.setStatus(MiniGameArena.ArenaStatus.DISABLED);
                    continue;
                }

                arena.setStatus(record.enabled()
                    ? MiniGameArena.ArenaStatus.WAITING
                    : MiniGameArena.ArenaStatus.DISABLED);
            } catch (MiniGameInvalidArenaConfigException exception) {
                api.messages().error("Failed to load Minefield arena '" + arenaId + "': " + exception.getMessage());
            }
        }
    }

    public void syncStartRegion(@NotNull MiniGameArena arena) {
        SCRegion startRegion = arena.get(START_REGION_KEY, SCRegion.class);
        if (startRegion == null) {
            return;
        }
        arena.setLobbySpawn(resolveSpawn(startRegion, arena.getLobbySpawn()));
    }

    public void refreshArenaRuntime(@NotNull MiniGameArena arena) {
        syncStartRegion(arena);
        if (handler != null) {
            handler.refreshArena(arena);
        }
    }

    public int startCountdownSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get(START_COUNTDOWN_SECONDS_KEY, Integer.class, MinefieldConfig.DEFAULT_START_COUNTDOWN_SECONDS));
    }

    public int endingSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get(ENDING_SECONDS_KEY, Integer.class, MinefieldConfig.DEFAULT_ENDING_SECONDS));
    }

    public int configuredMineCount(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get(CONFIGURED_MINE_COUNT_KEY, Integer.class, MinefieldConfig.DEFAULT_CONFIGURED_MINE_COUNT));
    }

    public int lives(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get(LIVES_KEY, Integer.class, MinefieldConfig.DEFAULT_LIVES));
    }

    public @NotNull Material hiddenBlock(@NotNull MiniGameArena arena) {
        return arena.get(HIDDEN_BLOCK_KEY, Material.class, MinefieldConfig.DEFAULT_HIDDEN_BLOCK);
    }

    public @NotNull Material clearBlock(@NotNull MiniGameArena arena) {
        return arena.get(CLEAR_BLOCK_KEY, Material.class, MinefieldConfig.DEFAULT_CLEAR_BLOCK);
    }

    public @NotNull Material adjacentBlock(@NotNull MiniGameArena arena) {
        return arena.get(ADJACENT_BLOCK_KEY, Material.class, MinefieldConfig.DEFAULT_ADJACENT_BLOCK);
    }

    public @NotNull Material markerBlock(@NotNull MiniGameArena arena) {
        return arena.get(MARKER_BLOCK_KEY, Material.class, MinefieldConfig.DEFAULT_MARKER_BLOCK);
    }

    public @NotNull Material triggeredMineBlock(@NotNull MiniGameArena arena) {
        return arena.get(TRIGGERED_MINE_BLOCK_KEY, Material.class, MinefieldConfig.DEFAULT_TRIGGERED_MINE_BLOCK);
    }

    public long bestTimeMillis(@NotNull MiniGameArena arena) {
        return Math.max(0L, arena.get(BEST_TIME_MILLIS_KEY, Long.class, 0L));
    }

    public void setBestTimeMillis(@NotNull MiniGameArena arena, long value) {
        arena.set(BEST_TIME_MILLIS_KEY, Math.max(0L, value));
    }

    public @NotNull String winnerName(@NotNull MiniGameArena arena) {
        return arena.get(WINNER_NAME_KEY, String.class, "-");
    }

    public void setWinner(@NotNull MiniGameArena arena, @Nullable Player player) {
        arena.set(WINNER_NAME_KEY, player == null ? "-" : player.getName());
    }

    public @NotNull String resultLine(@NotNull MiniGameArena arena) {
        return arena.get(RESULT_LINE_KEY, String.class, "Waiting for players");
    }

    public void setResultLine(@NotNull MiniGameArena arena, @NotNull String line) {
        arena.set(RESULT_LINE_KEY, line);
    }

    public int mineCount(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get(MINE_COUNT_KEY, Integer.class, 0));
    }

    public void setMineCount(@NotNull MiniGameArena arena, int value) {
        arena.set(MINE_COUNT_KEY, Math.max(0, value));
    }

    public int revealedSafeCount(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get(REVEALED_SAFE_COUNT_KEY, Integer.class, 0));
    }

    public void setRevealedSafeCount(@NotNull MiniGameArena arena, int value) {
        arena.set(REVEALED_SAFE_COUNT_KEY, Math.max(0, value));
    }

    public int livesLeft(@NotNull dev.stemcraft.api.minigame.MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return 0;
        }

        int configuredLives = lives(arena);
        if (configuredLives == 0) {
            return 0;
        }
        return Math.max(0, player.get(PLAYER_LIVES_LEFT_KEY, Integer.class, configuredLives));
    }

    public @NotNull String displayRunTime(@NotNull dev.stemcraft.api.minigame.MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "-";
        }

        long recorded = player.get(PLAYER_RUN_TIME_MILLIS_KEY, Long.class, 0L);
        if (recorded > 0L) {
            return formatMillis(recorded);
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            long startedAt = player.get(PLAYER_RUN_START_MILLIS_KEY, Long.class, 0L);
            if (startedAt > 0L) {
                return formatMillis(Math.max(0L, System.currentTimeMillis() - startedAt));
            }
        }

        return "-";
    }

    private @NotNull String progressDisplay(@NotNull dev.stemcraft.api.minigame.MiniGamePlayer player) {
        return player.get("progressPercent", Integer.class, 0) + "%";
    }

    private @NotNull String playerState(@NotNull dev.stemcraft.api.minigame.MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "idle";
        }
        if (arena.hasSpectator(player.getPlayer())) {
            return "spectating";
        }
        if (player.get(PLAYER_ELIMINATED_KEY, Boolean.class, false)) {
            return "out";
        }
        return switch (arena.getStatus()) {
            case WAITING -> "waiting";
            case STARTING -> "ready";
            case RUNNING -> "crossing";
            case ENDING -> "resetting";
            default -> "idle";
        };
    }

    public static @NotNull String formatMillis(long durationMillis) {
        return TimedRecordLeaderboard.formatMillis(durationMillis);
    }

    static @Nullable Location resolveSpawn(@NotNull SCRegion region, @Nullable Location fallbackLocation) {
        World world = region.getWorld();
        if (world == null) {
            return fallbackLocation == null ? null : fallbackLocation.clone();
        }

        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        Location center = new Location(
            world,
            (min.getBlockX() + max.getBlockX() + 1) / 2.0d,
            max.getBlockY() + 1.0d,
            (min.getBlockZ() + max.getBlockZ() + 1) / 2.0d
        );
        if (isSafeStandLocation(center)) {
            return center;
        }

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                Location candidate = new Location(world, x + 0.5d, max.getBlockY() + 1.0d, z + 0.5d);
                if (isSafeStandLocation(candidate)) {
                    return candidate;
                }
            }
        }

        Location ground = region.getRandomGroundLocation();
        if (ground != null) {
            return ground;
        }

        Location random = region.getRandomLocation();
        if (random != null && isSafeStandLocation(random)) {
            return random;
        }

        return fallbackLocation == null ? center.clone() : fallbackLocation.clone();
    }

    private static boolean isSafeStandLocation(@NotNull Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        Block feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Block head = world.getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        Block ground = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());
        return !ground.isPassable() && feet.isPassable() && head.isPassable();
    }
}
