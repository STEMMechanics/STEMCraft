package dev.stemcraft.minigame.boatrace;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.model.SCRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
