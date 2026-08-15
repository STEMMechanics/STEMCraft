package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NightfallArenaHandlerTest {
    @Test
    void runningLateJoinIsInitializedAsSurvivalParticipant() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        TaskService tasks = mock(TaskService.class);
        NightfallMiniGame nightfall = mock(NightfallMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        MiniGamePlayer miniGamePlayer = mock(MiniGamePlayer.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Location spawn = new Location(null, 4.0d, 70.0d, 8.0d);

        when(api.events()).thenReturn(events);
        when(api.tasks()).thenReturn(tasks);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.RUNNING);
        when(arena.getPlayers()).thenReturn(List.of(player));
        when(arena.getPlayer(player)).thenReturn(miniGamePlayer);
        when(arena.getOrCreate(anyString(), eq(Map.class), any())).thenReturn(new LinkedHashMap<>());
        when(arena.getOrCreate(anyString(), eq(Set.class), any())).thenReturn(new LinkedHashSet<>());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(nightfall.playSpawn(arena)).thenReturn(spawn);

        NightfallArenaHandler handler = new NightfallArenaHandler(api, nightfall);

        assertEquals(spawn, handler.onPlayerJoinArena(arena, player));
        verify(player).setGameMode(GameMode.SURVIVAL);
        verify(inventory).clear();
        verify(miniGamePlayer).set("livesRemaining", 1);
        verify(miniGamePlayer).setDeaths(0);
    }
}
