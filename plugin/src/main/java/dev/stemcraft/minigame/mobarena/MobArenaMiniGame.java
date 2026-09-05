package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import dev.stemcraft.minigame.MiniGameHudConfigSupport.HudDefinition;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>The Mob Arena mini-game.</p>
 */
public class MobArenaMiniGame extends BaseMiniGame {
    @Getter
    @Accessors(fluent = true)
    private static final @NotNull String KILLS_TOTAL_STAT_KEY = "mobarena_kills_total";
    @Getter
    @Accessors(fluent = true)
    private static final @NotNull String HIGHEST_ROUND_STAT_KEY = "mobarena_highest_round";

    static final int DEFAULT_START_COUNTDOWN_SECONDS = 30;
    static final int DEFAULT_ENDING_SECONDS = 20;

    private static final int HUD_LINE_HOLD_UPDATES = 3;

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "mobarena";

    private MobArenaConfig config = null;
    private MobArenaCommand command = null;
    private MobArenaArenaHandler handler = null;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame = null;

    /**
     * <p>Creates a new {@code MobArenaMiniGame}.</p>
     *
     * @param api The {@link STEMCraftAPI} to use.
     */
    public MobArenaMiniGame(@NotNull final STEMCraftAPI api) {
        super(api);
    }

    /**
     * <p>Handles whenever the minigame is told to load in.</p>
     */
    @Override
    public void onLoad() {
        config = new MobArenaConfig(api);
        handler = new MobArenaArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
                .registerArenaPlaceholder("mobs-left", (arena, team, player) -> String.valueOf(handler.getTrackedMobsForMinigame(arena)))
                .registerArenaPlaceholder("round", (arena, team, player) -> String.valueOf(handler.getWaveForArena(arena)));

        registerStats();

        command = new MobArenaCommand(api, this);

        config.onEnable(minigame);
        handler.onEnable();
        command.onEnable();

        MiniGameHudConfigSupport.apply(minigame, config.config(), defaultHudDefinitions());

        loadArenas();
    }

    /**
     * <p>Gets all default HUD definitions.</p>
     *
     * @return The default Mob Arena HUD definitions.
     */
    private @NotNull Map<@NotNull ArenaStatus, @NotNull HudDefinition> defaultHudDefinitions() {
        @NotNull final Map<ArenaStatus, HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(ArenaStatus.WAITING, new HudDefinition(
                List.of(
                        "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                        ":info_blue: <aqua>Waiting for players</aqua> <dark_gray>•</dark_gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
                ),
                List.of(
                        "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                        "",
                        ":skeleton_head_front: <gold>Mob Arena</gold> <dark_gray>•</dark_gray> <aqua>Lobby</aqua>",
                        ":info_green: <gray>Joined</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                        ":question_blue: <gray>Need</gray> <yellow>{arena:min-players}</yellow> <gray>players</gray>",
                        ":world: <gray>Map ID</gray> <gold>{arena:id}</gold>"
                ),
                HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(ArenaStatus.STARTING, new HudDefinition(
                List.of(
                        "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                        ":click_action_right: <gold>Starting in</gold> <yellow>{arena:time-remaining}</yellow>",
                        ":info_green: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
                ),
                List.of(
                        "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                        "",
                        ":click_action_right: <gold>Starts In</gold> <yellow>{arena:time-remaining}</yellow>",
                        ":info_green: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                        ":question_blue: <gray>Need</gray> <yellow>{arena:min-players}</yellow>",
                        ":world: <gray>Map ID</gray> <gold>{arena:id}</gold>"
                ),
                HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(ArenaStatus.RUNNING, new HudDefinition(
                List.of(
                        "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                        ":skeleton_head_front: <gray>Mobs</gray> {arena:mobs-left}",
                        ":ladder: <gray>Round</gray> <aqua>{arena:round}</aqua>"
                ),
                List.of(
                        "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                        "",
                        ":skeleton_head_front: <gray>Mobs</gray> <red>{arena:mobs-left}</red>",
                        ":ladder: <gray>Round</gray> <aqua>{arena:round}</aqua>",
                        "",
                        ":click_action_left: <gray>Kills</gray> <red>{player:kills}</red>"
                ),
                HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(ArenaStatus.ENDING, new HudDefinition(
                List.of(
                        "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                        ":warning_yellow: <gold>Round ends in</gold> <yellow>{arena:time-remaining}</yellow>"
                ),
                List.of(
                        "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                        "",
                        ":ladder: <gray>Round</gray> <aqua>{arena:round}</aqua>",
                        ":warning_yellow: <gold>Round ends in</gold> <yellow>{arena:time-remaining}</yellow>",
                        "",
                        ":click_action_left: <gray>Kills</gray> <red>{player:kills}</red>"
                ),
                HUD_LINE_HOLD_UPDATES
        ));
        return definitions;
    }

    /**
     * <p>Creates a new Mob Arena arena.</p>
     *
     * @param arenaId The Arena ID of the new arena to create.
     * @param world   The world that contains the new arena.
     * @return        The instance of the new arena.
     */
    public @Nullable MiniGameArena createArena(@NotNull final String arenaId, @NotNull final World world) {
        if (minigame.arena(arenaId) != null) {
            return null;
        }

        @NotNull final MiniGameArena arena = minigame.createArena(arenaId, world)
                .setName(StringUtil.beautify(arenaId))
                .setLobbySpawn(world.getSpawnLocation())
                .setSpectatorSpawn(world.getSpawnLocation())
                .setMinPlayers(1)
                .setMaxPlayers(16);

        arena.set("spawner-configs.max", 0);
        arena.<Map<String,SCRegion>>set("zones", new HashMap<>());

        return arena;
    }

    /**
     * <p>Deletes an arena via it's ID.</p>
     *
     * @param arenaId The ID of the arena to delete.
     */
    public void deleteArena(@NotNull final String arenaId) {
        @NotNull final MiniGameArena arena = minigame.arena(arenaId);
        //noinspection VariableNotUsedInsideIf // Used as a check to see if the arena exists.
        if (arena != null) {
            minigame.removeArena(arenaId);
        }
        //unregisterArenaStats(arenaId);
        config.deleteArena(arenaId);
    }

    /**
     * <p>Saves an arena.</p>
     *
     * @param arena The arena to save.
     */
    public void saveArena(@NotNull final MiniGameArena arena) {
        //registerArenaStats(arena);
        config.saveArena(arena.id(), new MobArenaArenaRecord(arena));
    }

    /**
     * <p>Save the given arena's status.</p>
     *
     * @param arena   The arena whose status to persist.
     * @param enabled Whether the arena is enabled or not.
     */
    public void persistArenaEnabled(@NotNull final MiniGameArena arena, final boolean enabled) {
        if (enabled && !config.hasArena(arena.id())) {
            saveArena(arena);
            return;
        }
        config.setArenaEnabled(arena.id(), enabled);
    }

    /**
     * <p>Reloads the Mob Arena config from the config file.</p>
     *
     * @return Whether the arena config was loaded or not.
     */
    // TODO: Merge this into a static class, code taken from Bridge impl. - ProjectHSI
    public boolean reloadFromConfig() {
        if (!reloadConfigFile(config.config())) {
            return false;
        }

        config.onEnable(minigame);
        MiniGameHudConfigSupport.apply(minigame, config.config(), defaultHudDefinitions());
        //unloadArenas(minigame, arena -> unregisterArenaStats(arena.id()));
        unloadArenas(minigame);
        loadArenas();
        return true;
    }

    /**
     * <p>Loads all arenas from the config.</p>
     */
    private void loadArenas() {
        @NotNull @Unmodifiable final List<MobArenaArenaRecord> arenaRecordList = config.loadArenas();

        arenaRecordList.forEach(this::loadArena);
    }

    /**
     * <p>Loads an arena via it's ID.</p>
     *
     * @param arenaId The Arena ID to load in.
     */
    private void loadArena(@NotNull final String arenaId) {
        loadArena(config.loadArena(arenaId));
    }

    /**
     * <p>Loads an arena record into it's Arena form.</p>
     *
     * @param arenaRecord The {@link MobArenaArenaRecord} to load in from.
     */
    private void loadArena(@NotNull final MobArenaArenaRecord arenaRecord) {
        @NotNull final MiniGameArena arena = minigame.createArena(arenaRecord.arenaId(), arenaRecord.world())
                .setName(arenaRecord.name())
                .setRegion(arenaRecord.arenaRegion())
                .setLobbySpawn(arenaRecord.lobby())
                .setSpectatorSpawn(arenaRecord.spectator())
                .setMinPlayers(arenaRecord.minPlayers())
                .setMaxPlayers(arenaRecord.maxPlayers());

        arena.set("spawner-configs.max", arenaRecord.spawnerConfigs().size());

        for (int i = 0; i < arenaRecord.spawnerConfigs().size(); i++) {
            @NotNull final String spawnerConfigPrefix = "spawner-configs." + i + ".";
            final MobArenaSpawnerRecord spawnerRecord = arenaRecord.spawnerConfigs().get(i);

            arena.set(spawnerConfigPrefix + "entityType", spawnerRecord.entityType());
            arena.set(spawnerConfigPrefix + "initialAmount", spawnerRecord.initialAmount());
            arena.set(spawnerConfigPrefix + "incrementAmount", spawnerRecord.incrementAmount());
            arena.set(spawnerConfigPrefix + "incrementType", spawnerRecord.incrementType());
            arena.set(spawnerConfigPrefix + "initialWave", spawnerRecord.initialWave());
            arena.set(spawnerConfigPrefix + "spawnZone", spawnerRecord.spawnZone());
            arena.set(spawnerConfigPrefix + "countTowardsMobCount", spawnerRecord.countTowardsMobCount());
        }

        arena.set("zones", arenaRecord.zones());

        @NotNull final ArenaValidationResult result = arena.validate();
        if (result.hasErrors()) {
            api.messages().error("Mob Arena arena '" + arenaRecord.arenaId() + "' has validation errors and will be disabled:");
            for (final String error : result.getErrors()) {
                api.messages().error(" - " + error);
            }
            arena.setStatus(ArenaStatus.DISABLED);
        } else {
            arena.setStatus(arenaRecord.enabled()
                    ? ArenaStatus.WAITING
                    : ArenaStatus.DISABLED);
        }
    }

    /**
     * <p>Registers global stats for Mob Arena.</p>
     */
    private void registerStats() {
        api.playerStats().register(new PlayerStatDefinition(
                KILLS_TOTAL_STAT_KEY,
                "Mob Arena Kills (All Arenas)",
                "Total number of mobs slain by the player across all Mob Arena arenas.",
                namespace,
                "minigame",
                namespace
        ));
        api.playerStats().register(new PlayerStatDefinition(
                HIGHEST_ROUND_STAT_KEY,
                "Highest Mob Arena Round (All Arenas)",
                "Highest round achieved by the player in any Mob Arena arena.",
                namespace,
                "minigame",
                namespace
        ));
    }

    /**
     * <p>Gets the amount of time it should take for an arena to start for a given arena.</p>
     *
     * @param arena The arena to get the time it takes for an arena to start.
     * @return The time it should take for the arena to start.
     */
    public int startCountdownSeconds(final @NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("startCountdownSeconds", Integer.class, DEFAULT_START_COUNTDOWN_SECONDS));
    }

    /**
     * <p>Gets the amount of time it should take for an arena to reset from an ending for a given arena.</p>
     *
     * @param arena The arena to get the time it takes for an arena to reset from an ending.
     * @return The time it should take for the arena to start.
     */
    public int endingSeconds(final @NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("endingSeconds", Integer.class, DEFAULT_ENDING_SECONDS));
    }
}
