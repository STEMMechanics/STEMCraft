package dev.stemcraft.minigame.horserace;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import org.bukkit.World;


public final class HorseRaceMiniGame extends BaseMiniGame {
    public static final String NAMESPACE = "horserace";
    public static final String TITLE = "Horse Racing";
    public static final String INSTRUCTIONS = "Ride through checkpoints in order and jump obstacles. First to finish all laps wins.";
    MiniGame game;
    HorseRaceArenaHandler handler;
    private ConfigFile configFile;
    private HorseRaceConfig config;

    public HorseRaceMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onLoad() {
        handler = new HorseRaceArenaHandler(api, this);
        game = createMiniGame(NAMESPACE, handler);
        game.registerArenaPlaceholder("objective", (a, _, _) -> handler.objective(a));
        game.registerArenaPlaceholder("winner", (a, _, _) -> a == null ? "-" : a.get("winner", String.class, "-"));
        game.registerPlayerPlaceholder("progress", (_, _, p) -> p == null ? "-" : handler.progress(p.arena(), p.getPlayer()));
        configFile = api.config().load(NAMESPACE + ".yml");
        if (configFile == null) throw new IllegalStateException("Cannot load " + NAMESPACE + ".yml");
        configureRewards(game, configFile);
        MiniGameHudConfigSupport.apply(game, configFile, MiniGameHudConfigSupport.timedRoundDefaults(TITLE));
        handler.registerListeners();
        new HorseRaceCommand(api, this).register();
        config = new HorseRaceConfig(api, this, configFile);
        config.loadArenas();
    }

    @Override
    public ConfigSection getConfig() {
        return configFile;
    }

    HorseRaceArenaSettings settings(MiniGameArena arena) {
        return arena.getOrCreate("horseraceSettings", HorseRaceArenaSettings.class, HorseRaceArenaSettings::new);
    }

    MiniGameArena create(String id, World world) {
        MiniGameArena arena = game.createArena(id, world).setName(id).setMinPlayers(2).setMaxPlayers(8)
                .setLobbySpawn(world.getSpawnLocation()).setSpectatorSpawn(world.getSpawnLocation());
        settings(arena);
        return arena;
    }

    public MiniGame minigame() {
        return game;
    }

    void save(MiniGameArena arena) {
        config.save(arena);
    }

    void delete(MiniGameArena arena) {
        game.removeArena(arena.id());
        config.delete(arena.id());
    }
}
