package dev.stemcraft.minigame.volcano;

import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.config.ConfigFileImpl;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.RUNNING;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VolcanoMiniGameTest {
    @TempDir Path directory;

    private ConfigFileImpl config() {
        var config = new ConfigFileImpl();
        assertTrue(config.load(directory.toFile(), "volcano.yml", true));
        return config;
    }

    @Test
    void upgradesGeneratedRunningHudToSurvivalTimeAndPersistsIt() {
        var config = config();
        var old = MiniGameHudConfigSupport.timedRoundDefaults("Volcano", List.of(
                ":warning_yellow: <yellow>{arena:objective}</yellow>",
                ":steve: <white>Survivors</white> <green>{arena:players}</green>")).get(RUNNING);
        config.set("hud.running.bossbar.lines", old.bossBarLines());
        config.set("hud.running.scoreboard.lines", old.scoreboardLines());
        config.set("hud.running.bossbar.hold-updates", 7);
        config.set("hud.running.bossbar.color", "RED");
        VolcanoMiniGame.applyHud(mock(MiniGame.class), config);
        var saved = config();
        assertTrue(saved.getStringList("hud.running.scoreboard.lines").stream().anyMatch(l -> l.contains("{player:survival-time}")));
        assertTrue(saved.getStringList("hud.running.bossbar.lines").stream().anyMatch(l -> l.contains("{player:survival-time}")));
        assertEquals(7, saved.getInt("hud.running.bossbar.hold-updates"));
        assertEquals("RED", saved.getString("hud.running.bossbar.color"));
    }

    @Test
    void preservesCustomAndDisabledRunningHud() {
        var config = config();
        config.set("hud.running.bossbar.lines", List.of("My volcano HUD"));
        config.set("hud.running.scoreboard.lines", List.of());
        VolcanoMiniGame.applyHud(mock(MiniGame.class), config);
        var saved = config();
        assertEquals(List.of("My volcano HUD"), saved.getStringList("hud.running.bossbar.lines"));
        assertTrue(saved.getStringList("hud.running.scoreboard.lines").isEmpty());
    }
}
