package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// TODO: [Overall] Add in player stats

public class MobArenaMiniGame extends BaseMiniGame {
    @Getter
    @Accessors(fluent = true)
    private static final @NotNull String KILLS_TOTAL_STAT_KEY = "mobarena_kills_total";
    @Getter
    @Accessors(fluent = true)
    private static final @NotNull String HIGHEST_ROUND_STAT_KEY = "mobarena_highest_round";

    private static final int HUD_LINE_HOLD_UPDATES = 3;

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "mobarena";

    private MobArenaConfig config;
    private MobArenaCommand command;
    private MobArenaArenaHandler handler;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    public MobArenaMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onLoad() {
        config = new MobArenaConfig(api);
        handler = new MobArenaArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
                .registerArenaPlaceholder("mobs-left", (arena, team, player) -> String.valueOf(handler.getTrackedMobsForMinigame(arena)))
                .registerArenaPlaceholder("round", (arena, team, player) -> String.valueOf(handler.getRoundForArena(arena)));

        registerStats();

        command = new MobArenaCommand(api, this);

        config.onEnable(minigame);
        handler.onEnable();
        command.onEnable();

        MiniGameHudConfigSupport.apply(minigame, config.config(), defaultHudDefinitions());

        loadArenas();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
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
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
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
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
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
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
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

    public @Nullable MiniGameArena createArena(@NotNull String arenaId, @NotNull World world) {
        if (minigame.arena(arenaId) != null) {
            return null;
        }

        MiniGameArena arena = minigame.createArena(arenaId, world)
                .setName(StringUtil.beautify(arenaId))
                .setLobbySpawn(world.getSpawnLocation())
                .setSpectatorSpawn(world.getSpawnLocation())
                .setMinPlayers(2)
                .setMaxPlayers(16);

        // TODO: Initialise everything else.

        arena.set("spawner-configs.max", 0);
        arena.<Map<String,SCRegion>>set("zones", new HashMap<>());

        return arena;
    }

    public void deleteArena(@NotNull String arenaId) {
        MiniGameArena arena = minigame.arena(arenaId);
        if (arena != null) {
            minigame.removeArena(arenaId);
        }
        //unregisterArenaStats(arenaId);
        config.deleteArena(arenaId);
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        //registerArenaStats(arena);
        config.saveArena(arena.id(), new MobArenaArenaRecord(arena));
    }

    public void persistArenaEnabled(@NotNull MiniGameArena arena, boolean enabled) {
        if (enabled && !config.hasArena(arena.id())) {
            saveArena(arena);
            return;
        }
        config.setArenaEnabled(arena.id(), enabled);
    }

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

    private void loadArenas() {
        List<MobArenaArenaRecord> arenaRecordList = config.loadArenas();

        arenaRecordList.forEach(this::loadArena);
    }

    private void loadArena(String arenaId) {
        loadArena(config.loadArena(arenaId));
    }

    private void loadArena(MobArenaArenaRecord arenaRecord) {
        MiniGameArena arena = minigame.createArena(arenaRecord.arenaId(), arenaRecord.world())
                .setName(arenaRecord.name())
                .setRegion(arenaRecord.arenaRegion())
                .setLobbySpawn(arenaRecord.lobby())
                .setSpectatorSpawn(arenaRecord.spectator())
                .setMinPlayers(arenaRecord.minPlayers())
                .setMaxPlayers(arenaRecord.maxPlayers());

        arena.set("spawner-configs.max", arenaRecord.spawnTicketList().size());

        for (int i = 0; i < arenaRecord.spawnTicketList().size(); i++) {
            final String spawnerConfigPrefix = "spawner-configs." + i + ".";
            final MobArenaArenaRecord.SpawnerRecord spawnerRecord = arenaRecord.spawnTicketList().get(i);

            arena.set(spawnerConfigPrefix + "entityType", spawnerRecord.entityType());
            arena.set(spawnerConfigPrefix + "initialAmount", spawnerRecord.initialAmount());
            arena.set(spawnerConfigPrefix + "incrementAmount", spawnerRecord.incrementAmount());
            arena.set(spawnerConfigPrefix + "incrementType", spawnerRecord.incrementType());
            arena.set(spawnerConfigPrefix + "initialWave", spawnerRecord.initialWave());
            arena.set(spawnerConfigPrefix + "spawnZone", spawnerRecord.spawnZone());
            arena.set(spawnerConfigPrefix + "countTowardsMobCount", spawnerRecord.countTowardsMobCount());
        }

        arena.set("zones", arenaRecord.zones());

        ArenaValidationResult result = arena.validate();
        if (result.hasErrors()) {
            api.messages().error("Mob Arena arena '" + arenaRecord.arenaId() + "' has validation errors and will be disabled:");
            for (String error : result.getErrors()) {
                api.messages().error(" - " + error);
            }
            arena.setStatus(MiniGameArena.ArenaStatus.DISABLED);
        } else {
            arena.setStatus(arenaRecord.enabled()
                    ? MiniGameArena.ArenaStatus.WAITING
                    : MiniGameArena.ArenaStatus.DISABLED);
        }
    }

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

    public int startCountdownSeconds(MiniGameArena arena) {
        return 30; // TODO: Make this customisable.
    }
}
