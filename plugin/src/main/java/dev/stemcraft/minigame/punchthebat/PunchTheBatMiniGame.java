package dev.stemcraft.minigame.punchthebat;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import org.bukkit.World;


public final class PunchTheBatMiniGame extends BaseMiniGame {
    public static final String NAMESPACE = "punchthebat";
    public static final String TITLE = "Punch the Bat";
    public static final String INSTRUCTIONS = "Punch bats to score. Glowing bats are worth 5 points. Highest score wins.";
    MiniGame game;
    PunchTheBatArenaHandler handler;
    private ConfigFile configFile;
    private PunchTheBatConfig config;

    public PunchTheBatMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onLoad() {
        handler = new PunchTheBatArenaHandler(api, this);
        game = createMiniGame(NAMESPACE, handler);
        game.registerArenaPlaceholder("objective", (a, _, _) -> handler.objective(a));
        game.registerArenaPlaceholder("winner", (a, _, _) -> a == null ? "-" : a.get("winner", String.class, "-"));
        game.registerPlayerPlaceholder("progress", (_, _, p) -> p == null ? "-" : handler.progress(p.arena(), p.getPlayer()));
        configFile = api.config().load(NAMESPACE + ".yml");
        if (configFile == null) throw new IllegalStateException("Cannot load " + NAMESPACE + ".yml");
        configureRewards(game, configFile);
        MiniGameHudConfigSupport.apply(game, configFile, MiniGameHudConfigSupport.timedRoundDefaults(TITLE));
        handler.registerListeners();
        new PunchTheBatCommand(api, this).register();
        config = new PunchTheBatConfig(api, this, configFile);
        config.loadArenas();
    }

    @Override
    public ConfigSection getConfig() {
        return configFile;
    }

    PunchTheBatArenaSettings settings(MiniGameArena arena) {
        return arena.getOrCreate("punchthebatSettings", PunchTheBatArenaSettings.class, PunchTheBatArenaSettings::new);
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
