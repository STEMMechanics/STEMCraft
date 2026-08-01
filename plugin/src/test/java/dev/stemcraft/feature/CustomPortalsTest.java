package dev.stemcraft.feature;

import dev.stemcraft.config.ConfigFileImpl;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomPortalsTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void writePortalRoundTripsDefinition() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "custom-portals.yml", true));

        CustomPortals.PortalDefinition definition = new CustomPortals.PortalDefinition(
            "creative",
            new CustomPortals.PortalDestination(
                "creative",
                CustomPortals.PortalDestinationMode.EXACT,
                10.5,
                64.0,
                -23.25,
                90.0f,
                0.0f
            ),
            Set.of(
                new CustomPortals.PortalBlockKey("lobby", 100, 64, 200),
                new CustomPortals.PortalBlockKey("lobby", 100, 65, 200)
            )
        );

        CustomPortals.writePortal(config.getSection("portals"), definition);

        CustomPortals.PortalDefinition reloaded = CustomPortals.readPortal(
            "creative",
            config.getSection("portals.creative", false)
        );

        assertNotNull(reloaded);
        assertEquals(definition.id(), reloaded.id());
        assertEquals(definition.destination(), reloaded.destination());
        assertEquals(definition.blocks(), reloaded.blocks());
    }

    @Test
    void writePortalRoundTripsWorldDefaultDestination() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "custom-portals.yml", true));

        CustomPortals.PortalDefinition definition = new CustomPortals.PortalDefinition(
            "survival",
            new CustomPortals.PortalDestination(
                "survival",
                CustomPortals.PortalDestinationMode.WORLD_DEFAULT,
                0.0d,
                0.0d,
                0.0d,
                0.0f,
                0.0f
            ),
            Set.of(new CustomPortals.PortalBlockKey("lobby", 100, 64, 200))
        );

        CustomPortals.writePortal(config.getSection("portals"), definition);

        CustomPortals.PortalDefinition reloaded = CustomPortals.readPortal(
            "survival",
            config.getSection("portals.survival", false)
        );

        assertNotNull(reloaded);
        assertEquals(definition.destination(), reloaded.destination());
    }

    @Test
    void deserializeLegacyExactDestinationStillWorks() {
        CustomPortals.PortalDestination destination = CustomPortals.PortalDestination.deserialize(
            "creative,10.5,64.0,-23.25,90.0,0.0"
        );

        assertNotNull(destination);
        assertEquals(
            new CustomPortals.PortalDestination(
                "creative",
                CustomPortals.PortalDestinationMode.EXACT,
                10.5,
                64.0,
                -23.25,
                90.0f,
                0.0f
            ),
            destination
        );
    }

    @Test
    void collectPortalBlocksTraversesConnectedCustomShape() {
        ServerMock server = MockBukkit.mock();
        World world = server.addSimpleWorld("lobby");

        Block lowerLeft = world.getBlockAt(10, 64, 10);
        Block upperLeft = world.getBlockAt(10, 65, 10);
        Block upperRight = world.getBlockAt(11, 65, 10);
        Block topTail = world.getBlockAt(11, 66, 10);
        Block disconnected = world.getBlockAt(15, 64, 10);

        lowerLeft.setType(Material.NETHER_PORTAL);
        upperLeft.setType(Material.NETHER_PORTAL);
        upperRight.setType(Material.NETHER_PORTAL);
        topTail.setType(Material.NETHER_PORTAL);
        disconnected.setType(Material.NETHER_PORTAL);

        Set<CustomPortals.PortalBlockKey> blocks = CustomPortals.collectPortalBlocks(lowerLeft);

        assertEquals(4, blocks.size());
        assertTrue(blocks.contains(new CustomPortals.PortalBlockKey("lobby", 10, 64, 10)));
        assertTrue(blocks.contains(new CustomPortals.PortalBlockKey("lobby", 10, 65, 10)));
        assertTrue(blocks.contains(new CustomPortals.PortalBlockKey("lobby", 11, 65, 10)));
        assertTrue(blocks.contains(new CustomPortals.PortalBlockKey("lobby", 11, 66, 10)));
    }
}
