package dev.stemcraft.api.model;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SCRegionTest {
    private WorldMock world;
    private WorldMock otherWorld;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("region-tests");
        otherWorld = server.addSimpleWorld("other-world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cuboidRegionsSupportContainmentPathIntersectionAndCopying() {
        SCRegion outer = new SCRegion(cuboid(0, 60, 0, 4, 64, 4), world);
        SCRegion inner = new SCRegion(cuboid(1, 61, 1, 2, 62, 2), world);
        SCRegion overlap = new SCRegion(cuboid(3, 62, 3, 6, 65, 6), world);
        SCRegion other = new SCRegion(cuboid(0, 60, 0, 4, 64, 4), otherWorld);

        assertTrue(outer.isCuboid());
        assertFalse(outer.isPolygon());
        assertTrue(outer.contains(new Location(world, 1.2, 61.7, 1.9)));
        assertFalse(outer.contains(new Location(otherWorld, 1.2, 61.7, 1.9)));
        assertTrue(outer.intersectsPath(new Location(world, -2, 62, 2), new Location(world, 6, 62, 2)));
        assertFalse(outer.intersectsPath(new Location(world, -2, 70, 2), new Location(world, 6, 70, 2)));
        assertTrue(outer.contains(inner));
        assertFalse(inner.contains(outer));
        assertTrue(outer.intersects(overlap));
        assertFalse(outer.intersects(other));
        assertEquals(new Location(world, 0, 60, 0), outer.getMinimumLocation());
        assertEquals(new Location(world, 4, 64, 4), outer.getMaximumLocation());

        SCRegion copy = outer.copy();
        assertNotSame(outer, copy);
        assertEquals(outer.toString(), copy.toString());
        assertEquals(world, copy.getWorld());
    }

    @Test
    void polygonRegionsExposeVerticesAndSerializationHelpers() {
        SCRegion polygon = new SCRegion(polygon(List.of(
            BlockVector2.at(0, 0),
            BlockVector2.at(4, 0),
            BlockVector2.at(4, 4),
            BlockVector2.at(0, 4)
        )), world);

        assertTrue(polygon.isPolygon());
        assertFalse(polygon.isCuboid());
        assertEquals(4, polygon.getPolygonVertices().size());
        SCRegion deserialized = SCRegion.deserialize(polygon.serialize());
        assertNotNull(deserialized);
        assertEquals(polygon.toString(), deserialized.toString());

        SCRegion fromString = SCRegion.fromString(polygon.toString(), world);
        assertNotNull(fromString);
        assertEquals(polygon.toString(), fromString.toString());
    }

    private static CuboidRegion cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new CuboidRegion(
            BlockVector3.at(minX, minY, minZ),
            BlockVector3.at(maxX, maxY, maxZ)
        );
    }

    private static Polygonal2DRegion polygon(List<BlockVector2> points) {
        return new Polygonal2DRegion(null, points, 60, 65);
    }
}
