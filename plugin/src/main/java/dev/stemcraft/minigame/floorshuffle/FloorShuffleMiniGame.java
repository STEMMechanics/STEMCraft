package dev.stemcraft.minigame.floorshuffle;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import org.bukkit.World;

import java.util.List;


public final class FloorShuffleMiniGame extends BaseMiniGame {
    public static final String NAMESPACE = "floorshuffle";
    public static final String TITLE = "Floor Shuffle";
    public static final String INSTRUCTIONS = "Stand on the announced colour before the other tiles disappear. Last survivor wins.";
    MiniGame game;
    FloorShuffleArenaHandler handler;
    private ConfigFile configFile;
    private FloorShuffleConfig config;
    FloorShuffleTextFormat textFormat = FloorShuffleTextFormat.defaults();

    public FloorShuffleMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onLoad() {
        handler = new FloorShuffleArenaHandler(api, this);
        game = createMiniGame(NAMESPACE, handler);
        game.registerArenaPlaceholder("objective", (a, _, _) -> handler.objective(a));
        game.registerArenaPlaceholder("winner", (a, _, _) -> a == null ? "-" : a.get("winner", String.class, "-"));
        game.registerPlayerPlaceholder("progress", (_, _, p) -> p == null ? "-" : handler.progress(p.arena()));
        configFile = api.config().load(NAMESPACE + ".yml");
        if (configFile == null) throw new IllegalStateException("Cannot load " + NAMESPACE + ".yml");
        textFormat = FloorShuffleTextFormat.load(configFile);
        configureRewards(game, configFile);
        MiniGameHudConfigSupport.applyTimedRound(game, configFile, TITLE, List.of(
                ":warning_yellow: <yellow>{arena:objective}</yellow>",
                ":steve: <white>Survivors</white> <green>{arena:players}</green>"));
        MiniGameHudConfigSupport.applyPreparation(game, configFile, TITLE);
        handler.registerListeners();
        new FloorShuffleCommand(api, this).register();
        config = new FloorShuffleConfig(api, this, configFile);
        config.loadArenas();
    }

    @Override
    public ConfigSection getConfig() {
        return configFile;
    }

    FloorShuffleArenaSettings settings(MiniGameArena arena) {
        return arena.getOrCreate("floorshuffleSettings", FloorShuffleArenaSettings.class, FloorShuffleArenaSettings::new);
    }

    MiniGameArena create(String id, World world) {
        MiniGameArena arena = game.createArena(id, world).setName(id).setMinPlayers(2).setMaxPlayers(8)
                .setLobbySpawn(world.getSpawnLocation()).setSpectatorSpawn(world.getSpawnLocation());
        settings(arena);
        arena.set("allowPreparationJoin", true);
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
