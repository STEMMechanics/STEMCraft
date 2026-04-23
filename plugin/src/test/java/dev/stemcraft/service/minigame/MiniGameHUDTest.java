package dev.stemcraft.service.minigame;

import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniGameHUDTest {
    @Test
    void arenaPlaceholdersReceiveViewingPlayerContext() {
        MiniGameImpl game = mock(MiniGameImpl.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        MiniGamePlayer player = mock(MiniGamePlayer.class);

        when(player.arena()).thenReturn(arena);
        when(game.renderArenaPlaceholder(arena, "team-line-1", player)).thenReturn("viewer-aware");

        MiniGameHUD hud = new MiniGameHUD(game, List.of(), List.of("{arena:team-line-1}"));

        assertEquals(List.of("viewer-aware"), hud.scoreboard(player));
    }
}
