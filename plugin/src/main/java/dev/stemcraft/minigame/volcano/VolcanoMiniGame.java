package dev.stemcraft.minigame.volcano;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import org.bukkit.World;

import java.util.List;


public final class VolcanoMiniGame extends BaseMiniGame {
    public static final String NAMESPACE = "volcano";
    public static final String TITLE = "Volcano";
    public static final String INSTRUCTIONS = "Survive as long as you can! Smoking vents erupt into lava; dodge the flows and holes. Last survivor wins.";
    private static final List<String> OLD_RUNNING_DETAILS = List.of(
            ":warning_yellow: <yellow>{arena:objective}</yellow>",
            ":steve: <white>Survivors</white> <green>{arena:players}</green>");
    private static final List<String> RUNNING_DETAILS = List.of(
            ":clock: <white>Survived</white> <gold>{player:survival-time}</gold>",
            ":steve: <white>Survivors</white> <green>{arena:players}</green>",
            ":warning_yellow: <yellow>{arena:objective}</yellow>");
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
        game.registerPlayerPlaceholder("progress", (_, _, p) -> p == null ? "-" : handler.progress(p.arena(), p.getPlayer()));
        game.registerPlayerPlaceholder("survival-time", (_, _, p) -> p == null ? "00:00" : handler.survivalTime(p.arena(), p.getPlayer()));
        configFile = api.config().load(NAMESPACE + ".yml");
        if (configFile == null) throw new IllegalStateException("Cannot load " + NAMESPACE + ".yml");
        configureRewards(game, configFile);
        applyHud(game, configFile);
        handler.registerListeners();
        new VolcanoCommand(api, this).register();
        config = new VolcanoConfig(api, this, configFile);
        config.loadArenas();
    }

    static void applyHud(MiniGame game, ConfigSection config) {
        var old = MiniGameHudConfigSupport.timedRoundDefaults(TITLE, OLD_RUNNING_DETAILS).get(MiniGameArena.ArenaStatus.RUNNING);
        var updated = MiniGameHudConfigSupport.timedRoundDefaults(TITLE, RUNNING_DETAILS).get(MiniGameArena.ArenaStatus.RUNNING);
        ConfigSection running = config.getSection("hud.running");
        ConfigSection bossbar = running.getSection("bossbar");
        if (bossbar.contains("lines") && bossbar.getStringList("lines").equals(old.bossBarLines())) {
            bossbar.set("lines", updated.bossBarLines());
        }
        ConfigSection scoreboard = running.getSection("scoreboard");
        if (scoreboard.contains("lines") && scoreboard.getStringList("lines").equals(old.scoreboardLines())) {
            scoreboard.set("lines", updated.scoreboardLines());
        }
        MiniGameHudConfigSupport.applyTimedRound(game, config, TITLE, RUNNING_DETAILS);
        MiniGameHudConfigSupport.applyPreparation(game, config, TITLE);
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
