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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveStorageSupportTest {
    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
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
        Block primary = supportedBlock(0, 65, 0);
        Block partner = supportedBlock(1, 65, 0);

        Chest chest = GraveStorageSupport.placeStorageChest(primary, partner);
        assertNotNull(chest);

        List<ItemStack> overflow = GraveStorageSupport.fillStorage(chest, partner, nonStackableDrops(28));

        assertTrue(overflow.isEmpty());
        assertEquals(Material.CHEST, primary.getType());
        assertEquals(Material.CHEST, partner.getType());
        assertEquals(27, chest.getBlockInventory().getSize());
        assertTrue(partner.getState() instanceof Chest);
        Chest partnerChest = (Chest) partner.getState();
        assertEquals(28, occupiedSlots(chest) + occupiedSlots(partnerChest));
    }

    @Test
    void findDoubleChestPartnerSkipsLiquidForLandGraves() {
        Block primary = supportedBlock(0, 65, 0);
        primary.getRelative(BlockFace.NORTH).setType(Material.WATER);
        primary.getRelative(BlockFace.SOUTH).setType(Material.AIR);

        Block partner = GraveStorageSupport.findDoubleChestPartner(primary, false);

        assertNotNull(partner);
        assertEquals(primary.getRelative(BlockFace.SOUTH).getLocation(), partner.getLocation());
    }

    @Test
    void findDoubleChestPartnerAllowsLiquidForLiquidGraves() {
        Block primary = supportedBlock(0, 65, 0);
        primary.getRelative(BlockFace.NORTH).setType(Material.WATER);
        primary.getRelative(BlockFace.SOUTH).setType(Material.AIR);

        Block partner = GraveStorageSupport.findDoubleChestPartner(primary, true);

        assertNotNull(partner);
        assertEquals(primary.getRelative(BlockFace.NORTH).getLocation(), partner.getLocation());
    }

    private Block supportedBlock(int x, int y, int z) {
        world.getBlockAt(x, y - 1, z).setType(Material.STONE);
        return world.getBlockAt(x, y, z);
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
