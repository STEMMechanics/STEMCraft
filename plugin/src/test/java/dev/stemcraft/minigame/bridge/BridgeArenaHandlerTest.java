package dev.stemcraft.minigame.bridge;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeArenaHandlerTest {
    @Test
    void onArenaCountdownTickShowsSharedStartingTitleForFinalFiveSeconds() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        BridgeMiniGame game = mock(BridgeMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.STARTING);
        when(arena.getOccupants()).thenReturn(List.of());

        BridgeArenaHandler handler = new BridgeArenaHandler(api, game);
        handler.onArenaCountdownTick(arena, 5);

        verify(arena).showStartingCountdownTitle(5);
    }

    @Test
    void onArenaCountdownEndUsesConfiguredEndingCountdown() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        BridgeMiniGame game = mock(BridgeMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.RUNNING);
        when(game.endingSeconds(arena)).thenReturn(37);

        BridgeArenaHandler handler = new BridgeArenaHandler(api, game);
        handler.onArenaCountdownEnd(arena);

        verify(arena).setStatus(MiniGameArena.ArenaStatus.ENDING, 37);
    }

    @Test
    void onPlayerJoinArenaUsesConfiguredStartCountdown() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        BridgeMiniGame game = mock(BridgeMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);
        when(arena.numPlayers()).thenReturn(2);
        when(arena.getMinPlayers()).thenReturn(2);
        when(arena.getLobbySpawn()).thenReturn(new Location(null, 0.0d, 0.0d, 0.0d));
        when(game.startCountdownSeconds(arena)).thenReturn(39);
        when(player.getInventory()).thenReturn(inventory);

        BridgeArenaHandler handler = new BridgeArenaHandler(api, game);
        handler.onPlayerJoinArena(arena, player);

        verify(arena).setStatus(MiniGameArena.ArenaStatus.STARTING, 39);
    }
}
