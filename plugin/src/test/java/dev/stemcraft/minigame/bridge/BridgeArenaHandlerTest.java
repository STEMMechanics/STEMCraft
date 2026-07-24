package dev.stemcraft.minigame.bridge;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeArenaHandlerTest {
    @Test
    void onArenaCountdownTickShowsSharedStartingTitleForFinalFiveSeconds() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BridgeMiniGame game = mock(BridgeMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(api.events()).thenReturn(events);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.STARTING);
        when(arena.getOccupants()).thenReturn(List.of());

        BridgeArenaHandler handler = new BridgeArenaHandler(api, game);
        handler.onArenaCountdownTick(arena, 5);

        verify(arena).showStartingCountdownTitle(5);
    }

    @Test
    void onArenaCountdownEndUsesConfiguredEndingCountdown() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BridgeMiniGame game = mock(BridgeMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(api.events()).thenReturn(events);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.RUNNING);
        when(game.endingSeconds(arena)).thenReturn(37);

        BridgeArenaHandler handler = new BridgeArenaHandler(api, game);
        handler.onArenaCountdownEnd(arena);

        verify(arena).setStatus(MiniGameArena.ArenaStatus.ENDING, 37);
    }

    @Test
    void onPlayerJoinArenaClearsInventoryAndReturnsLobbySpawn() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BridgeMiniGame game = mock(BridgeMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Location lobby = new Location(null, 0.0d, 0.0d, 0.0d);

        when(api.events()).thenReturn(events);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getLobbySpawn()).thenReturn(lobby);
        when(player.getInventory()).thenReturn(inventory);

        BridgeArenaHandler handler = new BridgeArenaHandler(api, game);
        Location joinSpawn = handler.onPlayerJoinArena(arena, player);

        verify(inventory).clear();
        verify(player).updateInventory();
        verify(arena, never()).setStatus(any(), any(Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(lobby, joinSpawn);
    }
}
