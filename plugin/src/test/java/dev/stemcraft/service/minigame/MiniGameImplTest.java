package dev.stemcraft.service.minigame;

import dev.stemcraft.api.minigame.MiniGameArena;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiniGameImplTest {
    @Test
    void findPlayerUsesMiniGameNamespace() {
        MiniGameServiceImpl service = mock(MiniGameServiceImpl.class);
        Player player = mock(Player.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(service.findPlayerArena(player, "bedwars")).thenReturn(arena);

        MiniGameImpl miniGame = new MiniGameImpl(service, "bedwars");

        assertSame(arena, miniGame.findPlayer(player));
        verify(service).findPlayerArena(player, "bedwars");
    }
}
