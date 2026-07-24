package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedWarsArenaHandlerTest {
    @Test
    void onArenaCountdownTickShowsSharedStartingTitleForFinalFiveSeconds() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BedWarsMiniGame game = mock(BedWarsMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(api.events()).thenReturn(events);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.STARTING);
        when(arena.getOccupants()).thenReturn(List.of());

        BedWarsArenaHandler handler = new BedWarsArenaHandler(api, game);
        handler.onArenaCountdownTick(arena, 5);

        verify(arena).showStartingCountdownTitle(5);
    }

    @Test
    void onArenaCountdownEndUsesConfiguredEndingCountdown() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BedWarsMiniGame game = mock(BedWarsMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(api.events()).thenReturn(events);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.RUNNING);
        when(game.endingSeconds(arena)).thenReturn(41);

        BedWarsArenaHandler handler = new BedWarsArenaHandler(api, game);
        handler.onArenaCountdownEnd(arena);

        verify(arena).setStatus(MiniGameArena.ArenaStatus.ENDING, 41);
    }

    @Test
    void onPlayerJoinArenaClearsInventoryAndReturnsLobbySpawn() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BedWarsMiniGame game = mock(BedWarsMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Location lobby = new Location(null, 0.0d, 0.0d, 0.0d);

        when(api.events()).thenReturn(events);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getLobbySpawn()).thenReturn(lobby);
        when(player.getInventory()).thenReturn(inventory);

        BedWarsArenaHandler handler = new BedWarsArenaHandler(api, game);
        Location joinSpawn = handler.onPlayerJoinArena(arena, player);

        assertEquals(lobby, joinSpawn);
        verify(inventory).clear();
        verify(player).updateInventory();
        verify(arena, never()).setStatus(any(), any(Integer.class));
    }

    @Test
    void captureParticipatingTeamsIgnoresConfiguredTeamsWithNoPlayers() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BedWarsMiniGame game = mock(BedWarsMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        MiniGameTeam activeTeam = mock(MiniGameTeam.class);
        MiniGameTeam unusedTeam = mock(MiniGameTeam.class);

        when(api.events()).thenReturn(events);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getOrCreate(eq("participatingTeams"), eq(Set.class), any())).thenReturn(new LinkedHashSet<String>());
        when(arena.getTeams()).thenReturn(List.of(activeTeam, unusedTeam));
        when(activeTeam.getName()).thenReturn("blue");
        when(unusedTeam.getName()).thenReturn("green");
        when(arena.getTeamPlayers("blue")).thenReturn(List.of(mock(Player.class)));
        when(arena.getTeamPlayers("green")).thenReturn(List.of());

        BedWarsArenaHandler handler = new BedWarsArenaHandler(api, game);
        handler.captureParticipatingTeams(arena);

        assertTrue(handler.isParticipatingTeam(arena, activeTeam));
        assertFalse(handler.isParticipatingTeam(arena, unusedTeam));
    }

    @Test
    void handleBedBreakAllowsUnusedTeamBedsWithoutRegisteringThem() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BedWarsMiniGame game = mock(BedWarsMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        MiniGameTeam activeTeam = mock(MiniGameTeam.class);
        MiniGameTeam unusedTeam = mock(MiniGameTeam.class);
        Player player = mock(Player.class);
        boolean[] cleared = {false};

        when(api.events()).thenReturn(events);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(arena.getOrCreate(eq("participatingTeams"), eq(Set.class), any())).thenReturn(new LinkedHashSet<>(Set.of("blue")));
        when(arena.getPlayerTeam(player)).thenReturn(activeTeam);
        when(activeTeam.getName()).thenReturn("blue");
        when(unusedTeam.getName()).thenReturn("yellow");
        when(unusedTeam.get("bedAlive", Boolean.class, true)).thenReturn(true);

        BedWarsArenaHandler handler = new BedWarsArenaHandler(api, game) {
            @Override
            void clearTeamBedBlocks(@NonNull MiniGameTeam team) {
                cleared[0] = true;
            }
        };
        BedWarsArenaHandler.HandlerEventResult result = handler.handleBedBreak(arena, player, Material.YELLOW_BED, unusedTeam);

        assertEquals(BedWarsArenaHandler.HandlerEventResult.ALLOW_NO_DROPS, result);
        assertTrue(cleared[0]);
        verify(unusedTeam, never()).set(eq("bedAlive"), eq(false));
        verify(game, never()).incrementStat(eq("beds_broken"), eq(arena), any());
        verify(arena, never()).broadcast(any());
    }

    @Test
    private MiniGameTeam team(String name) {
        MiniGameTeam team = mock(MiniGameTeam.class);
        when(team.getName()).thenReturn(name);
        return team;
    }

}
