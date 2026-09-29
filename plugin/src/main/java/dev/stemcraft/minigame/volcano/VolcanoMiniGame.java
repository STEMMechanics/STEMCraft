package dev.stemcraft.minigame.volcano;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import org.bukkit.World;


public final class VolcanoMiniGame extends BaseMiniGame {
    public static final String NAMESPACE = "volcano";
    public static final String TITLE = "Volcano";
    public static final String INSTRUCTIONS = "Avoid marked eruption sites and magma. The ground breaks away; last survivor wins.";
    MiniGame game;
    VolcanoArenaHandler handler;
    private ConfigFile configFile;
    private VolcanoConfig config;

    public VolcanoMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onLoad() {
        handler = new VolcanoArenaHandler(api, this);
        game = createMiniGame(NAMESPACE, handler);
        game.registerArenaPlaceholder("objective", (a, _, _) -> handler.objective(a));
        game.registerArenaPlaceholder("winner", (a, _, _) -> a == null ? "-" : a.get("winner", String.class, "-"));
        game.registerPlayerPlaceholder("progress", (_, _, p) -> p == null ? "-" : handler.progress(p.arena()));
        configFile = api.config().load(NAMESPACE + ".yml");
        if (configFile == null) throw new IllegalStateException("Cannot load " + NAMESPACE + ".yml");
        configureRewards(game, configFile);
        MiniGameHudConfigSupport.apply(game, configFile, MiniGameHudConfigSupport.timedRoundDefaults(TITLE));
        handler.registerListeners();
        new VolcanoCommand(api, this).register();
        config = new VolcanoConfig(api, this, configFile);
        config.loadArenas();
    }

    @Override
    public ConfigSection getConfig() {
        return configFile;
    }

    VolcanoArenaSettings settings(MiniGameArena arena) {
        return arena.getOrCreate("volcanoSettings", VolcanoArenaSettings.class, VolcanoArenaSettings::new);
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
