package dev.stemcraft.minigame.boatrace;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.STEMCraftAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoatRaceCommandTest {
    private WorldMock world;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("boatrace-command");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void narrowestCheckpointHorizontalSpanUsesSmallerHorizontalAxis() {
        SCRegion region = new SCRegion(
            new CuboidRegion(BlockVector3.at(10, 60, 20), BlockVector3.at(12, 65, 23)),
            world
        );

        assertEquals(3, BoatRaceCommand.narrowestCheckpointHorizontalSpan(region));
    }

    @Test
    void narrowestCheckpointHorizontalSpanCountsInclusiveBlockWidth() {
        SCRegion region = new SCRegion(
            new CuboidRegion(BlockVector3.at(4, 60, 8), BlockVector3.at(7, 64, 8)),
            world
        );

        assertEquals(1, BoatRaceCommand.narrowestCheckpointHorizontalSpan(region));
    }

    @Test
    void refreshLiveRegionListenersUsesBoatRaceHandlerForLoadedArena() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        BoatRaceMiniGame boatRace = mock(BoatRaceMiniGame.class);
        MiniGame minigame = mock(MiniGame.class);
        BoatRaceArenaHandler handler = mock(BoatRaceArenaHandler.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(boatRace.minigame()).thenReturn(minigame);
        when(minigame.handler()).thenReturn(handler);
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);

        BoatRaceCommand command = new BoatRaceCommand(api, boatRace);
        command.refreshLiveRegionListeners(arena);

        verify(handler).refreshRegionListeners(arena);
    }

    @Test
    void refreshLiveRegionListenersSkipsSetupArena() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        BoatRaceMiniGame boatRace = mock(BoatRaceMiniGame.class);
        MiniGame minigame = mock(MiniGame.class);
        BoatRaceArenaHandler handler = mock(BoatRaceArenaHandler.class);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(boatRace.minigame()).thenReturn(minigame);
        when(minigame.handler()).thenReturn(handler);
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.SETUP);

        BoatRaceCommand command = new BoatRaceCommand(api, boatRace);
        command.refreshLiveRegionListeners(arena);

        verify(handler, never()).refreshRegionListeners(arena);
    }
}
