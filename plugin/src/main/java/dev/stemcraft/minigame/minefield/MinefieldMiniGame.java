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
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
    static final String MINE_RATIO_KEY = "mineRatio";
    static final String HIDDEN_BLOCK_KEY = "hiddenBlock";
    static final String CLEAR_BLOCK_KEY = "clearBlock";
    static final String ADJACENT_BLOCK_KEY = "adjacentBlock";
    static final String TRIGGERED_MINE_BLOCK_KEY = "triggeredMineBlock";
    static final String COMPLETION_BONUS_KEY = "completionBonus";
    static final String WINNER_NAME_KEY = "winnerName";
    static final String RESULT_LINE_KEY = "resultLine";
    static final String MINE_COUNT_KEY = "mineCount";
    static final String REVEALED_SAFE_COUNT_KEY = "revealedSafeCount";

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "minefield";

    private MinefieldConfig config;

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
        MinefieldArenaHandler handler = new MinefieldArenaHandler(api, this);
        handler.initialize();

        minigame = createMiniGame(namespace, handler)
            .registerArenaPlaceholder("winner", (arena, team, player) -> arena == null ? "-" : winnerName(arena))
            .registerArenaPlaceholder("result", (arena, team, player) -> arena == null ? "-" : resultLine(arena))
            .registerArenaPlaceholder("mine-count", (arena, team, player) -> arena == null ? "0" : Integer.toString(mineCount(arena)))
            .registerArenaPlaceholder("safe-revealed", (arena, team, player) -> arena == null ? "0" : Integer.toString(revealedSafeCount(arena)))
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
                "<gradient:#3b82f6:#f97316><bold>{arena:name}</bold></gradient>",
                ":info_blue: <aqua>Waiting</aqua> <dark_gray>•</dark_gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
            ),
            List.of(
                "<gradient:#3b82f6:#f97316><bold>{arena:name}</bold></gradient>",
                "",
                ":question_blue: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":warning_yellow: <gray>Need</gray> <yellow>{arena:min-players}</yellow>",
                ":world: <gray>Map</gray> <gold>{arena:id}</gold>"
            ),
            3,
            "BLUE"
        ));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#3b82f6:#f97316><bold>{arena:name}</bold></gradient>",
                ":click_action_right: <gold>Starting in</gold> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>Progress</gray> <aqua>{player:progress}</aqua>"
            ),
            List.of(
                "<gradient:#3b82f6:#f97316><bold>{arena:name}</bold></gradient>",
                "",
                ":click_action_right: <gold>Starts In</gold> <yellow>{arena:time-remaining}</yellow>",
                ":question_blue: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":location: <gray>Progress</gray> <aqua>{player:progress}</aqua>"
            ),
            3,
            "GOLD"
        ));
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#3b82f6:#f97316><bold>{arena:name}</bold></gradient>",
                ":info_green: <gray>Revealed</gray> <white>{arena:safe-revealed}</white>",
                ":question_blue: <gray>Mines</gray> <blue>{arena:mine-count}</blue>",
                ":location: <gray>Progress</gray> <aqua>{player:progress}</aqua>"
            ),
            List.of(
                "<gradient:#3b82f6:#f97316><bold>{arena:name}</bold></gradient>",
                "",
                ":info_green: <gray>Revealed</gray> <white>{arena:safe-revealed}</white>",
                ":question_blue: <gray>Mines</gray> <blue>{arena:mine-count}</blue>",
                ":location: <gray>Progress</gray> <aqua>{player:progress}</aqua>",
                ":star: <gray>Score</gray> <gold>{player:score}</gold>",
                ":warning_yellow: <gray>Status</gray> <yellow>{player:state}</yellow>"
            ),
            2,
            "BLUE"
        ));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#3b82f6:#f97316><bold>{arena:name}</bold></gradient>",
                ":warning_yellow: <gold>{arena:result}</gold>",
                ":info_green: <gray>Reset in</gray> <yellow>{arena:time-remaining}</yellow>"
            ),
            List.of(
                "<gradient:#3b82f6:#f97316><bold>{arena:name}</bold></gradient>",
                "",
                ":warning_yellow: <gold>{arena:result}</gold>",
                ":info_green: <gray>Winner</gray> <yellow>{arena:winner}</yellow>",
                ":click_action_right: <gray>Reset In</gray> <yellow>{arena:time-remaining}</yellow>",
                ":star: <gray>Score</gray> <gold>{player:score}</gold>"
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
            .set(MINE_RATIO_KEY, MinefieldConfig.DEFAULT_MINE_RATIO)
            .set(HIDDEN_BLOCK_KEY, MinefieldConfig.DEFAULT_HIDDEN_BLOCK)
            .set(CLEAR_BLOCK_KEY, MinefieldConfig.DEFAULT_CLEAR_BLOCK)
            .set(ADJACENT_BLOCK_KEY, MinefieldConfig.DEFAULT_ADJACENT_BLOCK)
            .set(TRIGGERED_MINE_BLOCK_KEY, MinefieldConfig.DEFAULT_TRIGGERED_MINE_BLOCK)
            .set(COMPLETION_BONUS_KEY, MinefieldConfig.DEFAULT_COMPLETION_BONUS)
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
                    .set(MINE_RATIO_KEY, record.mineRatio())
                    .set(HIDDEN_BLOCK_KEY, record.hiddenBlock())
                    .set(CLEAR_BLOCK_KEY, record.clearBlock())
                    .set(ADJACENT_BLOCK_KEY, record.adjacentBlock())
                    .set(TRIGGERED_MINE_BLOCK_KEY, record.triggeredMineBlock())
                    .set(COMPLETION_BONUS_KEY, record.completionBonus())
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
        arena.setLobbySpawn(centerOfRegion(startRegion));
    }

    public int startCountdownSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get(START_COUNTDOWN_SECONDS_KEY, Integer.class, MinefieldConfig.DEFAULT_START_COUNTDOWN_SECONDS));
    }

    public int endingSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get(ENDING_SECONDS_KEY, Integer.class, MinefieldConfig.DEFAULT_ENDING_SECONDS));
    }

    public double mineRatio(@NotNull MiniGameArena arena) {
        return Math.max(0.0d, Math.min(0.9d, arena.get(MINE_RATIO_KEY, Double.class, MinefieldConfig.DEFAULT_MINE_RATIO)));
    }

    public int completionBonus(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get(COMPLETION_BONUS_KEY, Integer.class, MinefieldConfig.DEFAULT_COMPLETION_BONUS));
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

    public @NotNull Material triggeredMineBlock(@NotNull MiniGameArena arena) {
        return arena.get(TRIGGERED_MINE_BLOCK_KEY, Material.class, MinefieldConfig.DEFAULT_TRIGGERED_MINE_BLOCK);
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
        return switch (arena.getStatus()) {
            case WAITING -> "waiting";
            case STARTING -> "ready";
            case RUNNING -> "crossing";
            case ENDING -> "resetting";
            default -> "idle";
        };
    }

    private @NotNull Location centerOfRegion(@NotNull SCRegion region) {
        World world = region.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Region must have a world.");
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
        return fallback == null ? center : fallback;
    }
}
