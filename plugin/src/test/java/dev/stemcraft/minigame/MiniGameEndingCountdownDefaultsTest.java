package dev.stemcraft.minigame;

import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.bedwars.BedWarsMiniGame;
import dev.stemcraft.minigame.boatrace.BoatRaceMiniGame;
import dev.stemcraft.minigame.bridge.BridgeMiniGame;
import dev.stemcraft.minigame.nightfall.NightfallMiniGame;
import dev.stemcraft.minigame.tntrun.TntRunMiniGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniGameEndingCountdownDefaultsTest {
    @Test
    void endingCountdownDefaultsToTwentySecondsAcrossMinigames() {
        MiniGameArena arena = mock(MiniGameArena.class);
        when(arena.get("endingSeconds", Integer.class, 20)).thenReturn(20);

        assertEquals(20, new BedWarsMiniGame(null).endingSeconds(arena));
        assertEquals(20, new BridgeMiniGame(null).endingSeconds(arena));
        assertEquals(20, new BoatRaceMiniGame(null).endingSeconds(arena));
        assertEquals(20, new NightfallMiniGame(null).endingSeconds(arena));
        assertEquals(20, new TntRunMiniGame(null).endingSeconds(arena));
    }

    @Test
    void configuredEndingCountdownOverridesDefault() {
        MiniGameArena arena = mock(MiniGameArena.class);
        when(arena.get("endingSeconds", Integer.class, 20)).thenReturn(45);

        assertEquals(45, new BedWarsMiniGame(null).endingSeconds(arena));
        assertEquals(45, new BridgeMiniGame(null).endingSeconds(arena));
        assertEquals(45, new BoatRaceMiniGame(null).endingSeconds(arena));
        assertEquals(45, new NightfallMiniGame(null).endingSeconds(arena));
        assertEquals(45, new TntRunMiniGame(null).endingSeconds(arena));
    }
}
