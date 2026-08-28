package dev.stemcraft.feature;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraveStorageSupportTest {
    private WorldMock world;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("grave-tests");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void requiresDoubleChestReturnsFalseWhenDropsFitSingleChest() {
        assertFalse(GraveStorageSupport.requiresDoubleChest(nonStackableDrops(27)));
    }

    @Test
    void requiresDoubleChestReturnsTrueWhenDropsOverflowSingleChest() {
        assertTrue(GraveStorageSupport.requiresDoubleChest(nonStackableDrops(28)));
    }

    @Test
    void fillChestUsesDoubleChestCapacityWhenPartnerPlaced() {
        Block primary = supportedBlock(0);
        Block partner = supportedBlock(1);

        Chest chest = GraveStorageSupport.placeStorageChest(primary, partner);
        assertNotNull(chest);

        List<ItemStack> overflow = GraveStorageSupport.fillStorage(chest, partner, nonStackableDrops(28));

        assertTrue(overflow.isEmpty());
        assertEquals(Material.CHEST, primary.getType());
        assertEquals(Material.CHEST, partner.getType());
        assertEquals(27, chest.getBlockInventory().getSize());
        assertInstanceOf(Chest.class, partner.getState());
        Chest partnerChest = (Chest) partner.getState();
        assertEquals(28, occupiedSlots(chest) + occupiedSlots(partnerChest));
    }

    @Test
    void findDoubleChestPartnerSkipsLiquidForLandGraves() {
        Block primary = supportedBlock(0);
        primary.getRelative(BlockFace.NORTH).setType(Material.WATER);
        primary.getRelative(BlockFace.SOUTH).setType(Material.AIR);

        Block partner = GraveStorageSupport.findDoubleChestPartner(primary, false);

        assertNotNull(partner);
        assertEquals(primary.getRelative(BlockFace.SOUTH).getLocation(), partner.getLocation());
    }

    @Test
    void findDoubleChestPartnerAllowsLiquidForLiquidGraves() {
        Block primary = supportedBlock(0);
        primary.getRelative(BlockFace.NORTH).setType(Material.WATER);
        primary.getRelative(BlockFace.SOUTH).setType(Material.AIR);

        Block partner = GraveStorageSupport.findDoubleChestPartner(primary, true);

        assertNotNull(partner);
        assertEquals(primary.getRelative(BlockFace.NORTH).getLocation(), partner.getLocation());
    }

    @Test
    void graveWaypointUsesEightWayWorldDirections() {
        assertEquals("N", Graves.compassDirection(0, -10));
        assertEquals("NE", Graves.compassDirection(10, -10));
        assertEquals("E", Graves.compassDirection(10, 0));
        assertEquals("SE", Graves.compassDirection(10, 10));
        assertEquals("S", Graves.compassDirection(0, 10));
        assertEquals("SW", Graves.compassDirection(-10, 10));
        assertEquals("W", Graves.compassDirection(-10, 0));
        assertEquals("NW", Graves.compassDirection(-10, -10));
        assertEquals("Here", Graves.compassDirection(0, 0));
    }

    private Block supportedBlock(int x) {
        world.getBlockAt(x, 65 - 1, 0).setType(Material.STONE);
        return world.getBlockAt(x, 65, 0);
    }

    private List<ItemStack> nonStackableDrops(int count) {
        List<ItemStack> drops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drops.add(new ItemStack(Material.DIAMOND_SWORD));
        }
        return drops;
    }

    private int occupiedSlots(Chest chest) {
        int occupied = 0;
        for (ItemStack stack : chest.getBlockInventory().getContents()) {
            if (stack != null && stack.getType() != Material.AIR) {
                occupied++;
            }
        }
        return occupied;
    }
}
