package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.*;
import org.bukkit.block.data.type.Light;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeldLightFeatureTest {
    ServerMock server;
    World world;
    HeldLightFeature feature;
    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("survival_underhalls");
        var api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        var root = api.config().load("config.yml");
        when(root.isSection("held-light")).thenReturn(true);
        when(root.getSection("held-light", false).getBoolean("enabled", true)).thenReturn(true);
        feature = new HeldLightFeature(api);
    }
    @AfterEach void cleanup() {
        if (feature != null) feature.onDisable();
        MockBukkit.unmock();
    }
    @Test void restoresExactAirAndLeavesSolidsAndExistingLightsAlone() {
        var block = world.getBlockAt(8, 66, 8);
        block.setType(Material.CAVE_AIR);
        feature.illuminate(block, 14);
        assertEquals(14, ((Light) block.getBlockData()).getLevel());
        assertTrue(HeldLightFeature.isTemporaryLight(block));
        feature.restoreChunk(block.getChunk());
        assertEquals(Material.CAVE_AIR, block.getType());
        assertFalse(HeldLightFeature.isTemporaryLight(block));
        block.setType(Material.END_STONE);
        feature.illuminate(block, 15);
        assertEquals(Material.END_STONE, block.getType());
        block.setType(Material.LIGHT);
        feature.illuminate(block, 10);
        assertFalse(HeldLightFeature.isTemporaryLight(block));
    }
    @Test void recoveryAfterRestartPreservesReplacementBlocks() {
        var block = world.getBlockAt(-8, 66, -8);
        feature.illuminate(block, 14);
        var recovered = new HeldLightFeature(mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS));
        recovered.restoreChunk(block.getChunk());
        assertEquals(Material.AIR, block.getType());
        feature.illuminate(block, 14);
        block.setType(Material.COBBLESTONE);
        feature.restoreChunk(block.getChunk());
        assertEquals(Material.COBBLESTONE, block.getType());
        assertFalse(HeldLightFeature.isTemporaryLight(block));
    }
    @Test void eitherHandAndOverlappingPlayersUseStrongestLightAndCleanUp() {
        var first = server.addPlayer();
        var second = server.addPlayer();
        first.teleport(new Location(world, 8.5, 65, 8.5));
        second.teleport(first.getLocation());
        world.loadChunk(0, 0);
        first.getInventory().setItemInMainHand(new ItemStack(Material.TORCH));
        second.getInventory().setItemInOffHand(new ItemStack(Material.LANTERN));
        feature.tick(null);
        var block = first.getEyeLocation().getBlock();
        assertEquals(15, ((Light) block.getBlockData()).getLevel());
        feature.tick(second.getUniqueId());
        assertEquals(14, ((Light) block.getBlockData()).getLevel());
        second.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        first.teleport(first.getLocation().add(3, 0, 0));
        feature.tick(null);
        assertEquals(Material.AIR, block.getType());
        var moved = first.getEyeLocation().getBlock();
        assertEquals(Material.LIGHT, moved.getType());
        first.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        feature.tick(null);
        assertEquals(Material.AIR, moved.getType());
    }
    @Test void permanentPortalLightCanTakeOwnershipWithoutBeingErased() {
        var block = world.getBlockAt(8, 66, 8);
        feature.illuminate(block, 14);
        HeldLightFeature.releaseTemporaryLight(block);
        block.setType(Material.LIGHT);
        feature.onDisable();
        assertEquals(Material.LIGHT, block.getType());
        assertFalse(HeldLightFeature.isTemporaryLight(block));
    }

    @Test void soulLightsHaveLowerLevel() {
        assertEquals(10, HeldLightFeature.level(Material.SOUL_LANTERN));
        assertEquals(10, HeldLightFeature.level(Material.SOUL_TORCH));
        assertEquals(0, HeldLightFeature.level(Material.STICK));
    }
}
