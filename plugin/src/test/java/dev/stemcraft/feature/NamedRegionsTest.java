package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NamedRegionsTest {
    @TempDir Path tempDir;

    @Test void generatesUniqueNamesForEverySupportedFamily() {
        for (String type : Set.of("desert", "forest", "jungle", "ocean", "mountains", "snow", "swamp",
            "badlands", "savanna", "taiga", "mushroom-fields", "plains", "mineshaft", "village",
            "shipwreck", "ruined-portal", "ancient-city", "bastion-remnant", "buried-treasure",
            "desert-pyramid", "end-city", "fortress", "igloo", "jungle-pyramid", "mansion", "monument",
            "nether-fossil", "ocean-ruins", "pillager-outpost", "stronghold", "swamp-hut", "trail-ruins",
            "trial-chambers")) {
            var names = NamedRegions.defaultNames(type);
            assertEquals(1760, names.size(), type);
            assertEquals(1760, Set.copyOf(names).size(), type);
        }
    }

    @Test void groupsBiomeAndStructureVariants() {
        assertEquals("desert", NamedRegions.biomeFamily("desert"));
        assertEquals("mountains", NamedRegions.biomeFamily("jagged_peaks"));
        assertEquals("village", NamedRegions.structureFamily("village_plains"));
        assertEquals("ruined-portal", NamedRegions.structureFamily("ruined_portal_ocean"));
        assertEquals("mineshaft", NamedRegions.structureFamily("mineshaft_mesa"));
    }

    @Test void readsGeneratedChunksFromRegionHeader() throws Exception {
        byte[] header = new byte[4096];
        header[7] = 2;
        header[1023 * 4] = 1;
        Path region = tempDir.resolve("r.0.0.mca");
        Files.write(region, header);

        var generated = NamedRegions.generatedChunks(region);
        assertEquals(Set.of(1, 1023), generated.stream().boxed().collect(java.util.stream.Collectors.toSet()));
    }

    @Test void combinesAdjacentBiomeSamplesIntoOneBoundary() {
        var loops = NamedRegions.boundaryLoops(Set.of(NamedRegions.pack(0, 0), NamedRegions.pack(1, 0)));
        assertEquals(1, loops.size());
        assertTrue(loops.getFirst().size() >= 4);
    }

    @Test void throttlesDirtyMapSnapshotsUntilConfiguredInterval() {
        long builtAt = 1_000L;
        long fiveMinutes = 300_000L;

        assertFalse(NamedRegions.shouldRefreshMapSnapshot(false, builtAt, builtAt + fiveMinutes, fiveMinutes));
        assertFalse(NamedRegions.shouldRefreshMapSnapshot(true, builtAt, builtAt + fiveMinutes - 1, fiveMinutes));
        assertTrue(NamedRegions.shouldRefreshMapSnapshot(true, builtAt, builtAt + fiveMinutes, fiveMinutes));
        assertTrue(NamedRegions.shouldRefreshMapSnapshot(true, 0, builtAt, fiveMinutes));
        assertFalse(NamedRegions.shouldRefreshMapSnapshot(true, builtAt, builtAt - 1, fiveMinutes));
    }
}
