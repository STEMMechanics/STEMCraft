package dev.stemcraft.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldEditPreviewTest {
    @Test void cylinderPreviewPreservesShapeAndIsDetachedFromSelection() throws Exception {
        var cylinder = new com.sk89q.worldedit.regions.CylinderRegion(null,
            BlockVector3.at(100, 70, -50),
            com.sk89q.worldedit.math.Vector2.at(8, 5), 60, 80);
        var selector = org.mockito.Mockito.mock(com.sk89q.worldedit.regions.RegionSelector.class);
        org.mockito.Mockito.when(selector.getRegion()).thenReturn(cylinder);
        var snapshot = WorldEditRegionSupport.previewSelection(selector, null);
        assertNotNull(snapshot);
        var copy = assertInstanceOf(com.sk89q.worldedit.regions.CylinderRegion.class, snapshot.getRegion());
        assertEquals(cylinder.getCenter(), copy.getCenter());
        assertEquals(cylinder.getRadius(), copy.getRadius());
        assertEquals(60, copy.getMinimumY());
        assertEquals(80, copy.getMaximumY());
        cylinder.shift(BlockVector3.at(1000, 10, 1000));
        assertEquals(copy.getCenter(), snapshot.getRegion().getCenter());
    }

    @Test void unsupportedCompleteRegionIsSkippedWithoutThrowing() throws Exception {
        var selector = org.mockito.Mockito.mock(com.sk89q.worldedit.regions.RegionSelector.class);
        org.mockito.Mockito.when(selector.getRegion()).thenReturn(
            org.mockito.Mockito.mock(com.sk89q.worldedit.regions.Region.class));
        assertNull(WorldEditRegionSupport.previewSelection(selector, null));
    }

    @Test void missingPrimaryIsSafeAcrossSelectionModes() throws Exception {
        assertNull(WorldEditRegionSupport.primaryPosition(null, null));
        var nullable = org.mockito.Mockito.mock(com.sk89q.worldedit.regions.RegionSelector.class);
        org.mockito.Mockito.when(nullable.getPrimaryPosition()).thenReturn(null);
        assertNull(WorldEditRegionSupport.primaryPosition(nullable, null));
        assertNull(WorldEditRegionSupport.primaryPosition(new CuboidRegionSelector(), null));
        var convex = new com.sk89q.worldedit.regions.selector.ConvexPolyhedralRegionSelector();
        assertNull(WorldEditRegionSupport.primaryPosition(convex, null));
        convex.selectPrimary(BlockVector3.at(-10, 85, 236), null);
        var primary = WorldEditRegionSupport.primaryPosition(convex, null);
        assertNotNull(primary);
        assertEquals(-9.5, primary.getX());
        assertEquals(85.5, primary.getY());
        assertEquals(236.5, primary.getZ());
        convex.clear();
        // WorldEdit retains the last primary after clearing this selector.
        assertEquals(primary, WorldEditRegionSupport.primaryPosition(convex, null));
    }

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
