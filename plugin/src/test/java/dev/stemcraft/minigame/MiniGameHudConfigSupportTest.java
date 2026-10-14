package dev.stemcraft.minigame;

import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.config.ConfigFileImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiniGameHudConfigSupportTest {
    @TempDir Path directory;

    private ConfigFileImpl config() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(directory.toFile(), "game.yml", true));
        return config;
    }

    @Test
    void preparationHudPersistsACountdownAndPreservesCustomOrDisabledLines() {
        var config = config();
        MiniGame game = mock(MiniGame.class);
        MiniGameHudConfigSupport.applyPreparation(game, config, "Volcano");
        var reloaded = config();
        assertTrue(reloaded.getStringList("hud.preparation.scoreboard.lines").stream()
            .anyMatch(line -> line.contains("{arena:time-remaining}")));
        reloaded.set("hud.preparation.scoreboard.lines", List.of());
        reloaded.set("hud.preparation.bossbar.lines", List.of("My preparation timer"));
        MiniGameHudConfigSupport.applyPreparation(game, reloaded, "Volcano");
        var saved = config();
        assertTrue(saved.getStringList("hud.preparation.scoreboard.lines").isEmpty());
        assertEquals(List.of("My preparation timer"), saved.getStringList("hud.preparation.bossbar.lines"));
    }

    @Test
    void floorStartDelayPersistsTheDefaultAndPreservesCustomAndDisabledValues() {
        var config = config();
        var arena = config.getSection("arenas.test");
        arena.set("countdown", 30);
        assertEquals(15, MiniGameConfigSupport.startDelaySeconds(arena));
        assertEquals(15, config().getInt("arenas.test.start-delay-seconds"));
        assertEquals(30, config().getInt("arenas.test.countdown"));
        arena.set("start-delay-seconds", 0);
        arena.save();
        assertEquals(0, MiniGameConfigSupport.startDelaySeconds(config().getSection("arenas.test")));
        arena.set("start-delay-seconds", 25);
        arena.save();
        assertEquals(25, MiniGameConfigSupport.startDelaySeconds(config().getSection("arenas.test")));
    }

    @Test
    void upgradesLegacyHudAndPersistsItAcrossReload() {
        var config = config();
        for (String state : List.of("waiting", "starting", "running", "ending")) {
            config.set("hud." + state + ".bossbar.lines", List.of("<gold>Floor Shuffle <white>{arena:time-remaining}", "<yellow>{arena:objective}"));
            config.set("hud." + state + ".bossbar.hold-updates", 2);
            config.set("hud." + state + ".bossbar.color", "YELLOW");
            config.set("hud." + state + ".scoreboard.lines", List.of("<gold>{arena:name}", "<white>{arena:status}", "Players: {arena:players}",
                    "Time: {arena:time-remaining}", "Score: {player:score}", "{player:progress}", "Winner: {arena:winner}"));
        }
        MiniGame game = mock(MiniGame.class);
        List<String> details = List.of(":steve: <white>Survivors</white> <green>{arena:players}</green>");
        MiniGameHudConfigSupport.applyTimedRound(game, config, "Floor Shuffle", details);
        var reloaded = config();
        var defaults = MiniGameHudConfigSupport.timedRoundDefaults("Floor Shuffle", details);
        for (var entry : defaults.entrySet()) {
            String path = "hud." + entry.getKey().name().toLowerCase(java.util.Locale.ROOT);
            assertEquals(entry.getValue().scoreboardLines(), reloaded.getStringList(path + ".scoreboard.lines"));
            assertEquals(entry.getValue().bossBarLines(), reloaded.getStringList(path + ".bossbar.lines"));
            assertEquals(5, reloaded.getInt(path + ".bossbar.hold-updates"));
            assertEquals("PURPLE", reloaded.getString(path + ".bossbar.color"));
            verify(game).registerHud(entry.getKey(), entry.getValue().bossBarLines(), entry.getValue().scoreboardLines(), 5, "PURPLE");
        }
        assertFalse(defaults.get(WAITING).scoreboardLines().toString().contains("time-remaining"));
        assertFalse(defaults.get(RUNNING).scoreboardLines().toString().contains("winner"));
        assertTrue(defaults.get(ENDING).scoreboardLines().toString().contains("winner"));
    }

    @Test
    void repairsFullyEmptyLegacyHudOnlyOnce() {
        var config = config();
        for (String state : List.of("waiting", "starting", "running", "ending")) {
            config.set("hud." + state + ".bossbar.lines", List.of());
            config.set("hud." + state + ".scoreboard.lines", List.of());
            config.set("hud." + state + ".bossbar.hold-updates", 5);
            config.set("hud." + state + ".bossbar.color", "PURPLE");
        }
        MiniGameHudConfigSupport.applyTimedRound(mock(MiniGame.class), config, "Floor Shuffle", List.of("Survivors"));
        var reloaded = config();
        for (String state : List.of("waiting", "starting", "running", "ending")) {
            assertFalse(reloaded.getStringList("hud." + state + ".bossbar.lines").isEmpty());
            assertFalse(reloaded.getStringList("hud." + state + ".scoreboard.lines").isEmpty());
            reloaded.set("hud." + state + ".bossbar.lines", List.of());
            reloaded.set("hud." + state + ".scoreboard.lines", List.of());
        }
        MiniGameHudConfigSupport.applyTimedRound(mock(MiniGame.class), reloaded, "Floor Shuffle", List.of("Survivors"));
        for (String state : List.of("waiting", "starting", "running", "ending")) {
            assertTrue(reloaded.getStringList("hud." + state + ".bossbar.lines").isEmpty());
            assertTrue(reloaded.getStringList("hud." + state + ".scoreboard.lines").isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Floor Shuffle", "Volcano", "Horse Racing", "Minecart Racing", "Mob Shooter", "Punch the Bat", "Minecraft Quake"})
    void freshConfigRegistersAndPersistsAllDefaultLines(String title) {
        var config = config();
        MiniGame game = mock(MiniGame.class);
        List<String> details = List.of("{player:progress}");
        MiniGameHudConfigSupport.applyTimedRound(game, config, title, details);
        var reloaded = config();
        for (var entry : MiniGameHudConfigSupport.timedRoundDefaults(title, details).entrySet()) {
            String path = "hud." + entry.getKey().name().toLowerCase(java.util.Locale.ROOT);
            assertEquals(entry.getValue().bossBarLines(), reloaded.getStringList(path + ".bossbar.lines"));
            assertEquals(entry.getValue().scoreboardLines(), reloaded.getStringList(path + ".scoreboard.lines"));
            verify(game).registerHud(entry.getKey(), entry.getValue().bossBarLines(), entry.getValue().scoreboardLines(), 5, "PURPLE");
        }
    }

    @Test
    void preservesCustomAndExplicitlyDisabledHud() {
        var config = config();
        config.set("hud.running.bossbar.lines", List.of("My custom HUD"));
        config.set("hud.running.bossbar.hold-updates", 7);
        config.set("hud.running.bossbar.color", "BLUE");
        config.set("hud.running.scoreboard.lines", List.of());
        MiniGameHudConfigSupport.applyTimedRound(mock(MiniGame.class), config, "Quake", List.of("Kills"));
        var reloaded = config();
        assertEquals(List.of("My custom HUD"), reloaded.getStringList("hud.running.bossbar.lines"));
        assertEquals(7, reloaded.getInt("hud.running.bossbar.hold-updates"));
        assertEquals("BLUE", reloaded.getString("hud.running.bossbar.color"));
        assertTrue(reloaded.getStringList("hud.running.scoreboard.lines").isEmpty());
        assertFalse(reloaded.getStringList("hud.waiting.scoreboard.lines").isEmpty());
        assertFalse(reloaded.getStringList("hud.starting.bossbar.lines").isEmpty());
    }

    @Test
    void migratesOriginalCountdownOnceAndPreservesSubsequentOverrides() {
        var config = config();
        var arena = config.getSection("arenas.test");
        arena.set("countdown", 10);
        assertEquals(30, MiniGameConfigSupport.timedRoundCountdown(arena));
        assertEquals(30, config().getInt("arenas.test.countdown"));
        arena.set("countdown", 10);
        assertEquals(10, MiniGameConfigSupport.timedRoundCountdown(arena));
        var custom = config.getSection("arenas.custom");
        custom.set("countdown", 45);
        assertEquals(45, MiniGameConfigSupport.timedRoundCountdown(custom));
    }
}
