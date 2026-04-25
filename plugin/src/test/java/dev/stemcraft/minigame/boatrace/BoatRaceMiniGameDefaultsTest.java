package dev.stemcraft.minigame.boatrace;

import dev.stemcraft.api.minigame.MiniGameArena;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoatRaceMiniGameDefaultsTest {
    @Test
    void lapsDefaultToOne() {
        MiniGameArena arena = mock(MiniGameArena.class);
        when(arena.get("laps", Integer.class, 1)).thenReturn(1);

        assertEquals(1, new BoatRaceMiniGame(null).laps(arena));
    }

    @Test
    void configuredLapsOverrideDefault() {
        MiniGameArena arena = mock(MiniGameArena.class);
        when(arena.get("laps", Integer.class, 1)).thenReturn(3);

        assertEquals(3, new BoatRaceMiniGame(null).laps(arena));
    }
}
