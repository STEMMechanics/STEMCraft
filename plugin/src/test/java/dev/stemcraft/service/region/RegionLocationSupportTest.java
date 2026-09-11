package dev.stemcraft.service.region;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionLocationSupportTest {
    private WorldMock world;

    @BeforeEach
    void setUp() {
        world = MockBukkit.mock().addSimpleWorld("region-location-tests");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void findsRandomAndGroundLocationsInsideRegion() {
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++)
            world.getBlockAt(x, 63, z).setType(Material.STONE);
        SCRegion region = new SCRegion(new CuboidRegion(
            BlockVector3.at(0, 64, 0), BlockVector3.at(1, 66, 1)), world);

        Location random = RegionLocationSupport.randomLocation(region);
        assertNotNull(random);
        assertTrue(region.contains(random));

        Location ground = RegionLocationSupport.randomGroundLocation(region);
        assertNotNull(ground);
        assertEquals(world, ground.getWorld());
        assertTrue(region.contains(ground));
        assertEquals(Material.STONE,
            world.getBlockAt(ground.getBlockX(), ground.getBlockY() - 1, ground.getBlockZ()).getType());
    }
}
