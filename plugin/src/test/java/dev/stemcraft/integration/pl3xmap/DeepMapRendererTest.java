package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.api.service.world.generation.GeneratorMapRenderer;
import dev.stemcraft.chunkgen.generator.DeepGenerator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeepMapRendererTest {
    @Test void skipsRoofAndSelectsFirstFloorNotLowerCaves() {
        var policy = DeepGenerator.mapRenderer();
        assertEquals(70, policy.surfaceY(column(70, 110)));
        assertEquals(110, policy.surfaceY(column(110, 125)));
    }
    @Test void solidColumnsAreTransparentWithoutReadingOutsideHeightBounds() {
        var solid = column(320, 320);
        assertEquals(-65, DeepGenerator.mapRenderer().surfaceY(solid));
    }
    @Test void floorHeightControlsShading() {
        var policy = DeepGenerator.mapRenderer();
        var column = column(70, 110);
        int low = policy.color(column, 0, 0xffaaaaaa);
        int high = policy.color(column, 100, 0xffaaaaaa);
        assertTrue((low & 255) < (high & 255));
        assertEquals(255, low >>> 24);
    }
    private static GeneratorMapRenderer.Column column(int floor, int ceiling) {
        return new GeneratorMapRenderer.Column() {
            public int minY() { return -64; }
            public int maxY() { return 320; }
            public boolean isAir(int y) {
                assertTrue(y >= minY() && y < maxY());
                return y > floor && y < ceiling;
            }
            public boolean isVisible(int y) { return !isAir(y); }
        };
    }
}
