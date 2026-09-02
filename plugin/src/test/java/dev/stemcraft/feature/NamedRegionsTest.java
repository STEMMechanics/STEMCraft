package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NamedRegionsTest {
    @TempDir Path tempDir;

    @Test void offersNaturalTwoWordChoicesForExistingLongNames() {
        assertEquals(Set.of("Far Pearl", "Far Deep", "Pearl Deep"),
            Set.copyOf(NamedRegions.shortNameCandidates("Far Pearl Deep")));
        assertEquals(Set.of("Western Sunfield", "Western Plains", "Sunfield Plains"),
            Set.copyOf(NamedRegions.shortNameCandidates("Western Sunfield Plains")));
        assertEquals(Set.of("Woods Elder"), Set.copyOf(NamedRegions.shortNameCandidates("The Woods of Elder")));
    }

    @Test void recognisesProductionNumberedFallbackNamesForMigration() {
        assertTrue(NamedRegions.isNumberedFallback("Foxglove 1150"));
        assertTrue(NamedRegions.isNumberedFallback("Mosslight 1701"));
        assertFalse(NamedRegions.isNumberedFallback("Pearl Deep"));
        assertFalse(NamedRegions.isNumberedFallback("Area 52 East"));
    }

    @Test void packagedConfigProvidesSourcesAndFormsForEveryBiomeFamily() {
        var stream=getClass().getResourceAsStream("/config.yml");assertNotNull(stream);
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
        for(String type:Set.of("desert","forest","jungle","ocean","mountains","snow","swamp","badlands",
            "savanna","taiga","mushroom-fields","plains")){
            assertFalse(config.getStringList("named-regions.names.sources."+type).isEmpty(),type+" sources");
            assertFalse(config.getStringList("named-regions.names.forms."+type).isEmpty(),type+" forms");
        }
    }

    @Test void packagedConfigProvidesEditableSourcesForEveryBiomeFamily() {
        var stream = getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        for (String type : Set.of("desert", "forest", "jungle", "ocean", "mountains", "snow", "swamp",
            "badlands", "savanna", "taiga", "mushroom-fields", "plains")) {
            var sources = config.getStringList("named-regions.names.sources." + type);
            assertFalse(sources.isEmpty(), type);
            var generated = NamedRegions.generateNames(sources,
                config.getStringList("named-regions.names.forms." + type), List.of());
            assertTrue(generated.size() >= 100, type);
            assertTrue(generated.stream().allMatch(name -> name.split("\\s+").length <= 2), type);
        }
    }

    @Test void recognisesOnlyTheDeterministicFallbackForARegion() {
        String id = "region:3a187fee-d2b3-31a8-aed6-e16c0c98b34f";
        String expected = "Plains " + letterCode(id.hashCode());
        assertTrue(NamedRegions.isFallbackName(expected, "plains", id));
        assertFalse(NamedRegions.isFallbackName("Greenfield Plains", "plains", id));
    }

    private static String letterCode(int value) {
        long remaining = Integer.toUnsignedLong(value);
        StringBuilder out = new StringBuilder();
        do { out.append((char) ('a' + remaining % 26)); remaining /= 26; } while (remaining > 0);
        return out.reverse().toString();
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

    @Test void parsesOpaqueAndTranslucentMapPaletteColours() {
        assertEquals(0xFF54A0FF, NamedRegions.mapColourValue("#54A0FF", "#FFFF9800"));
        assertEquals(0x4054A0FF, NamedRegions.mapColourValue("0x4054A0FF", "#FFFF9800"));
        assertEquals(0xFFFF9800, NamedRegions.mapColourValue("not-a-colour", "#FFFF9800"));
    }
}
