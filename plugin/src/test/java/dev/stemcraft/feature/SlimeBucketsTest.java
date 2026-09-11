package dev.stemcraft.feature;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlimeBucketsTest {
    @Test
    void detectsChunkBoundariesWithoutReactingInsideChunk() {
        assertFalse(SlimeBuckets.crossedChunk(
            new Location(null, 1, 64, 1), new Location(null, 15, 70, 15)));
        assertTrue(SlimeBuckets.crossedChunk(
            new Location(null, 15, 64, 15), new Location(null, 16, 64, 15)));
        assertTrue(SlimeBuckets.crossedChunk(
            new Location(null, 0, 64, 0), new Location(null, -1, 64, 0)));
    }
}
