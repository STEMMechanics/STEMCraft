package dev.stemcraft.feature;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NamedRegionLookupIndexTest {
    @Test
    void indexesNegativeChunkBoundariesAndRemovesReplacedStructures() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("survival");
        NamedRegions.Area structure = new NamedRegions.Area(
            "structure:one", "survival", "structure", "village", "Village",
            -17, 10, -17, -1, 20, -1, false, 1L, true);
        NamedRegions.StructureIndex index = new NamedRegions.StructureIndex();

        index.add(structure);

        assertTrue(index.candidates("survival", -17, -17).contains("structure:one"));
        assertTrue(index.candidates("survival", -1, -1).contains("structure:one"));
        assertFalse(index.candidates("other", -1, -1).contains("structure:one"));
        assertTrue(structure.contains(new Location(world, -1, 20, -1)));
        assertFalse(structure.contains(new Location(world, -1, 21, -1)));
        assertFalse(structure.contains(new Location(world, 0, 20, -1)));

        NamedRegions.Area moved = new NamedRegions.Area(
            "structure:one", "survival", "structure", "village", "Village",
            32, 10, 32, 47, 20, 47, false, 1L, true);
        index.add(moved);

        assertFalse(index.candidates("survival", -1, -1).contains("structure:one"));
        assertTrue(index.candidates("survival", 32, 32).contains("structure:one"));
    }

    @Test
    void overlappingStructuresRemainCandidatesForExactContainmentResolution() {
        NamedRegions.StructureIndex index = new NamedRegions.StructureIndex();
        NamedRegions.Area first = new NamedRegions.Area(
            "structure:first", "survival", "structure", "fort", "First",
            0, 0, 0, 32, 32, 32, false, 1L, true);
        NamedRegions.Area second = new NamedRegions.Area(
            "structure:second", "survival", "structure", "tower", "Second",
            16, 10, 16, 48, 40, 48, false, 2L, true);

        index.add(first);
        index.add(second);

        Set<String> candidates = index.candidates("survival", 20, 20);
        assertTrue(candidates.containsAll(Set.of("structure:first", "structure:second")));
        assertTrue(index.bucketCount() > 0);
    }
}
