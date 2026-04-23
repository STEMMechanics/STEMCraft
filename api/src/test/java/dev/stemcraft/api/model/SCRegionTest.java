package dev.stemcraft.api.model;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
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
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("region-tests");
        otherWorld = server.addSimpleWorld("other-world");
        player = server.addPlayer("Alex");
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
    void randomLocationAndGroundLocationStayWithinRegion() {
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
            }
        }

        SCRegion region = new SCRegion(cuboid(0, 64, 0, 1, 66, 1), world);

        Location random = region.getRandomLocation();
        assertNotNull(random);
        assertTrue(region.contains(random));

        Location ground = region.getRandomGroundLocation();
        assertNotNull(ground);
        assertEquals(world, ground.getWorld());
        assertTrue(region.contains(ground));
        assertEquals(Material.STONE, world.getBlockAt(ground.getBlockX(), ground.getBlockY() - 1, ground.getBlockZ()).getType());
    }

    @Test
    void polygonRegionsExposeVerticesPlayersAndSerializationHelpers() {
        SCRegion polygon = new SCRegion(polygon(List.of(
            BlockVector2.at(0, 0),
            BlockVector2.at(4, 0),
            BlockVector2.at(4, 4),
            BlockVector2.at(0, 4)
        )), world);

        player.teleport(new Location(world, 1.5, 61, 1.5));

        assertTrue(polygon.isPolygon());
        assertFalse(polygon.isCuboid());
        assertEquals(4, polygon.getPolygonVertices().size());
        assertTrue(polygon.containsPlayer(player));
        assertEquals(List.of((Player) player), polygon.getPlayers());

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
