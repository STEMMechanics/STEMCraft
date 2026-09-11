package dev.stemcraft.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldEditPreviewTest {
    @Test void unfinishedCornersNeverProduceAnOriginRegion() {
        var selector = new CuboidRegionSelector();
        assertNull(WorldEditRegionSupport.previewSelection(selector, null));
        selector.selectPrimary(BlockVector3.at(1000, 80, 1000), null);
        assertNull(WorldEditRegionSupport.previewSelection(selector, null));
        selector.selectSecondary(BlockVector3.at(1010, 85, 1010), null);
        var complete = WorldEditRegionSupport.previewSelection(selector, null);
        assertNotNull(complete);
        assertEquals(BlockVector3.at(1000, 80, 1000), complete.getRegion().getMinimumPoint());
        selector.clear();
        selector.selectSecondary(BlockVector3.at(1010, 85, 1010), null);
        assertNull(WorldEditRegionSupport.previewSelection(selector, null));
    }
}
