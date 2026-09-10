package dev.stemcraft.chunkgen;

import dev.stemcraft.chunkgen.noise.*;
import dev.stemcraft.chunkgen.feature.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NoiseAndRegionsTest {
    @Test void seedsAreStableAndLayerSpecific() {
        assertEquals(SeedMixer.derive(-8234, "stemcraft:deep/v1/cavern"), SeedMixer.derive(-8234, "stemcraft:deep/v1/cavern"));
        assertNotEquals(SeedMixer.derive(42, "ab"), SeedMixer.derive(42, "ba"));
        assertNotEquals(SeedMixer.derive(42, "deep"), SeedMixer.derive(43, "deep"));
        assertNotEquals(SeedMixer.at(42, -1, 0, 1), SeedMixer.at(42, 1, 0, -1));
    }
    @Test void regionsUseFloorAtNegativeBoundaries() {
        assertEquals(-2, RegionFeature.coordinate(-769, 768));
        assertEquals(-1, RegionFeature.coordinate(-768, 768));
        assertEquals(-1, RegionFeature.coordinate(-1, 768));
        assertEquals(0, RegionFeature.coordinate(0, 768));
        assertEquals(1, RegionFeature.coordinate(768, 768));
        assertThrows(IllegalArgumentException.class, () -> RegionFeature.coordinate(1, 0));
    }
    @Test void noiseIsCoherentAndConcurrentOrderDoesNotMatter() throws Exception {
        NoiseSampler sampler = NoiseFactory.fractal(9843, .01, 4, .5, 2);
        double[] values = new double[120];
        for (int i = 0; i < values.length; i++) values[i] = sampler.sample(-i * 17.13, i * 3.2, i - 70.5);
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (int i = values.length - 1; i >= 0; i--) {
                final int index = i;
                assertEquals(values[i], executor.submit(() -> sampler.sample(-index * 17.13, index * 3.2, index - 70.5)).get());
                assertTrue(Math.abs(values[i]) <= 1);
            }
        }
        assertEquals(sampler.sample(-10, 3, 41), sampler.sample(-10.000001, 3, 41), .000001);
        assertNotEquals(sampler.sample(10, 3, 41), sampler.sample(10, 80, 41));
        assertThrows(IllegalArgumentException.class, () -> NoiseFactory.fractal(1, Double.NaN, 2, .5, 2));
    }
    @Test void craterBowlRimAndUpliftHaveExpectedSigns() {
        CraterFeature.Crater crater = new CraterFeature.Crater(0, 0, 200, true);
        var candidates = new CraterFeature.Crater[]{crater};
        assertTrue(CraterFeature.contribution(candidates, 0, 0) < 0);
        assertTrue(CraterFeature.contribution(candidates, 200, 0) > 0);
        assertEquals(0, CraterFeature.contribution(candidates, 250, 0));
        assertEquals(CraterFeature.contribution(candidates, -180, 20), CraterFeature.contribution(candidates, 180, -20));
    }
    @Test void regionalCraterCandidatesAgreeAcrossPositiveAndNegativeSeams() {
        CraterFeature field = new CraterFeature(92843);
        for (int boundary : new int[]{-1536, -768, 0, 768, 1536}) {
            var left = field.nearby(boundary - 1, -43);
            var right = field.nearby(boundary, -43);
            for (int x = boundary - 12; x <= boundary + 12; x++) {
                assertEquals(field.sample(x, -43), CraterFeature.contribution(left, x, -43), 1e-10);
                assertEquals(field.sample(x, -43), CraterFeature.contribution(right, x, -43), 1e-10);
            }
        }
        assertEquals(field.crater(-3, 2), new CraterFeature(92843).crater(-3, 2));
    }
}
