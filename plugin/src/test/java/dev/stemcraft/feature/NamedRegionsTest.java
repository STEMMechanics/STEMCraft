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
            assertTrue(names.size() >= 200, type);
            assertEquals(names.size(), Set.copyOf(names).size(), type);
            assertTrue(names.stream().allMatch(name -> name.split("\\s+").length <= 2), type);
        }
    }

    @Test void offersNaturalTwoWordChoicesForExistingLongNames() {
        assertEquals(Set.of("Far Pearl", "Far Deep", "Pearl Deep"),
            Set.copyOf(NamedRegions.shortNameCandidates("Far Pearl Deep")));
        assertEquals(Set.of("Western Sunfield", "Western Plains", "Sunfield Plains"),
            Set.copyOf(NamedRegions.shortNameCandidates("Western Sunfield Plains")));
        assertEquals(Set.of("Woods Elder"), Set.copyOf(NamedRegions.shortNameCandidates("The Woods of Elder")));
    }

    @Test void includesWarcraftRegionNamesInMatchingPools() {
        assertTrue(NamedRegions.defaultNames("forest").contains("Elwynn Forest"));
        assertTrue(NamedRegions.defaultNames("mountains").contains("Redridge Mountains"));
        assertTrue(NamedRegions.defaultNames("snow").contains("Howling Fjord"));
        assertTrue(NamedRegions.defaultNames("swamp").contains("Dustwallow Marsh"));
        assertTrue(NamedRegions.defaultNames("plains").contains("Ohn'ahran Plains"));
        assertTrue(NamedRegions.defaultNames("ancient-city").contains("Hallowfall"));
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
}
