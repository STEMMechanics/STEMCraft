package dev.stemcraft.service;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramServiceImplTest {
    private ServerMock server;
    private WorldMock world;
    private WorldMock otherWorld;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("hologram-tests");
        otherWorld = server.addSimpleWorld("other-world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void matchesLocationsInSameChunkWithoutResolvingChunk() {
        Location location = new Location(world, 31.9, 80, 47.9);

        assertTrue(HologramServiceImpl.isLocationInChunk(location, world, 1, 2));
        assertFalse(HologramServiceImpl.isLocationInChunk(location, world, 2, 2));
    }

    @Test
    void handlesNegativeChunkCoordinates() {
        Location location = new Location(world, -1.2, 80, -16.0);

        assertTrue(HologramServiceImpl.isLocationInChunk(location, world, -1, -1));
        assertFalse(HologramServiceImpl.isLocationInChunk(location, world, 0, -1));
    }

    @Test
    void rejectsNullAndWrongWorldLocations() {
        assertFalse(HologramServiceImpl.isLocationInChunk(null, world, 0, 0));
        assertFalse(HologramServiceImpl.isLocationInChunk(new Location(otherWorld, 0, 64, 0), world, 0, 0));
    }
}
