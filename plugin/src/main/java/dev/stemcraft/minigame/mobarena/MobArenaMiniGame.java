package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import dev.stemcraft.minigame.bridge.BridgeConfig;
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

        minigame = createMiniGame(namespace, handler);

        config.onEnable(minigame);

        MiniGameHudConfigSupport.apply(minigame, config.config(), defaultHudDefinitions());

        command = new MobArenaCommand(api, this);
        command.onEnable();

        loadArenas();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
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

        arena.<Integer>set("spawner-configs.max", 0);
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

    public int startCountdownSeconds(MiniGameArena arena) {
        return 30; // TODO: Make this customisable.
    }
}
