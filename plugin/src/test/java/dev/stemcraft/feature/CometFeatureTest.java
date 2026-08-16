package dev.stemcraft.feature;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CometFeatureTest {
    @Test
    void cometSphereContainsExactlyNinetySixUniqueMagmaPositions() {
        List<Vector> offsets = CometFeature.createSphereOffsets();

        assertEquals(96, offsets.size());
        assertEquals(96, new HashSet<>(offsets).size());
        assertTrue(offsets.stream().allMatch(offset -> offset.lengthSquared() <= 9.0d));
    }
}
