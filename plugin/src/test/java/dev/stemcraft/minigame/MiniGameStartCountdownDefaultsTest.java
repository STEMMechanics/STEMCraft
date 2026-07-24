package dev.stemcraft.minigame;

import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.bedwars.BedWarsMiniGame;
import dev.stemcraft.minigame.boatrace.BoatRaceMiniGame;
import dev.stemcraft.minigame.bridge.BridgeMiniGame;
import dev.stemcraft.minigame.minefield.MinefieldMiniGame;
import dev.stemcraft.minigame.nightfall.NightfallMiniGame;
import dev.stemcraft.minigame.tntrun.TntRunMiniGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniGameStartCountdownDefaultsTest {
    @Test
    void startCountdownDefaultsToThirtySecondsAcrossMinigames() {
        MiniGameArena arena = mock(MiniGameArena.class);
        when(arena.get("startCountdownSeconds", Integer.class, 30)).thenReturn(30);

        assertEquals(30, new BedWarsMiniGame(null).startCountdownSeconds(arena));
        assertEquals(30, new BridgeMiniGame(null).startCountdownSeconds(arena));
        assertEquals(30, new BoatRaceMiniGame(null).startCountdownSeconds(arena));
        assertEquals(30, new MinefieldMiniGame(null).startCountdownSeconds(arena));
        assertEquals(30, new NightfallMiniGame(null).startCountdownSeconds(arena));
        assertEquals(30, new TntRunMiniGame(null).startCountdownSeconds(arena));
    }

    @Test
    void configuredStartCountdownOverridesDefault() {
        MiniGameArena arena = mock(MiniGameArena.class);
        when(arena.get("startCountdownSeconds", Integer.class, 30)).thenReturn(45);

        assertEquals(45, new BedWarsMiniGame(null).startCountdownSeconds(arena));
        assertEquals(45, new BridgeMiniGame(null).startCountdownSeconds(arena));
        assertEquals(45, new BoatRaceMiniGame(null).startCountdownSeconds(arena));
        assertEquals(45, new MinefieldMiniGame(null).startCountdownSeconds(arena));
        assertEquals(45, new NightfallMiniGame(null).startCountdownSeconds(arena));
        assertEquals(45, new TntRunMiniGame(null).startCountdownSeconds(arena));
    }
}
