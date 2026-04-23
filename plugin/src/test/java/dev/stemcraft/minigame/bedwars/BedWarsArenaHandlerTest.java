package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedWarsArenaHandlerTest {
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
    void findRandomDropLocationUsesHighestAllowedConfiguredSurface() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BedWarsMiniGame game = mock(BedWarsMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("bedwars-drop-test");
            world.getBlockAt(0, 64, 0).setType(Material.YELLOW_BED);
            world.getBlockAt(0, 73, 0).setType(Material.GRASS_BLOCK);
            world.getBlockAt(1, 73, 0).setType(Material.STONE);
            world.getBlockAt(-1, 73, 0).setType(Material.STONE);
            world.getBlockAt(0, 73, 1).setType(Material.STONE);
            world.getBlockAt(0, 73, -1).setType(Material.STONE);
            world.getBlockAt(1, 73, 1).setType(Material.STONE);
            world.getBlockAt(1, 73, -1).setType(Material.STONE);
            world.getBlockAt(-1, 73, 1).setType(Material.STONE);
            world.getBlockAt(-1, 73, -1).setType(Material.STONE);
            world.getBlockAt(0, 74, 0).setType(Material.AIR);
            world.getBlockAt(0, 75, 0).setType(Material.AIR);

            when(api.events()).thenReturn(events);
            when(events.register(any(), any())).thenReturn(mock(Listener.class));
            when(game.dropSurfaceMaterials(arena)).thenReturn(List.of(Material.GRASS_BLOCK));

            BedWarsArenaHandler handler = new BedWarsArenaHandler(api, game);
            Location dropLocation = handler.findRandomDropLocation(
                new Location(world, 0.0d, 70.0d, 0.0d),
                new Location(world, 0.0d, 75.0d, 0.0d),
                location -> true,
                game.dropSurfaceMaterials(arena),
                new ZeroRandom(),
                1
            );

            assertNotNull(dropLocation);
            assertEquals(world, dropLocation.getWorld());
            assertEquals(0.5d, dropLocation.getX(), 0.0001d);
            assertEquals(74.15d, dropLocation.getY(), 0.0001d);
            assertEquals(0.5d, dropLocation.getZ(), 0.0001d);
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void findRandomDropLocationRejectsColumnWhenHighestBlockIsNotAllowed() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BedWarsMiniGame game = mock(BedWarsMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("bedwars-drop-test");
            world.getBlockAt(0, 72, 0).setType(Material.GRASS_BLOCK);
            world.getBlockAt(0, 73, 0).setType(Material.STONE);
            world.getBlockAt(0, 74, 0).setType(Material.AIR);
            world.getBlockAt(0, 75, 0).setType(Material.AIR);

            when(api.events()).thenReturn(events);
            when(events.register(any(), any())).thenReturn(mock(Listener.class));
            when(game.dropSurfaceMaterials(arena)).thenReturn(List.of(Material.GRASS_BLOCK));

            BedWarsArenaHandler handler = new BedWarsArenaHandler(api, game);
            Location dropLocation = handler.findRandomDropLocation(
                new Location(world, 0.0d, 70.0d, 0.0d),
                new Location(world, 0.0d, 75.0d, 0.0d),
                location -> true,
                game.dropSurfaceMaterials(arena),
                new ZeroRandom(),
                1
            );

            assertNull(dropLocation);
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void findRandomDropLocationRejectsColumnWhenSurroundingBlocksAreNotSolid() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        BedWarsMiniGame game = mock(BedWarsMiniGame.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("bedwars-drop-test");
            world.getBlockAt(0, 73, 0).setType(Material.GRASS_BLOCK);
            world.getBlockAt(1, 73, 0).setType(Material.STONE);
            world.getBlockAt(-1, 73, 0).setType(Material.STONE);
            world.getBlockAt(0, 73, 1).setType(Material.STONE);
            world.getBlockAt(0, 73, -1).setType(Material.AIR);
            world.getBlockAt(1, 73, 1).setType(Material.STONE);
            world.getBlockAt(1, 73, -1).setType(Material.STONE);
            world.getBlockAt(-1, 73, 1).setType(Material.STONE);
            world.getBlockAt(-1, 73, -1).setType(Material.STONE);
            world.getBlockAt(0, 74, 0).setType(Material.AIR);
            world.getBlockAt(0, 75, 0).setType(Material.AIR);

            when(api.events()).thenReturn(events);
            when(events.register(any(), any())).thenReturn(mock(Listener.class));
            when(game.dropSurfaceMaterials(arena)).thenReturn(List.of(Material.GRASS_BLOCK));

            BedWarsArenaHandler handler = new BedWarsArenaHandler(api, game);
            Location dropLocation = handler.findRandomDropLocation(
                new Location(world, 0.0d, 70.0d, 0.0d),
                new Location(world, 0.0d, 75.0d, 0.0d),
                location -> true,
                game.dropSurfaceMaterials(arena),
                new ZeroRandom(),
                1
            );

            assertNull(dropLocation);
        } finally {
            MockBukkit.unmock();
        }
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
            void clearTeamBedBlocks(MiniGameTeam team) {
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

    private static final class ZeroRandom extends Random {
        private static final long serialVersionUID = 1L;

        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }
}
