package dev.stemcraft.feature;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class HarvestSupportTest {
    private record Position(int x, int y, int z) { }

    private static final class Forest {
        final World world = mock(World.class);
        final Map<Position, Block> blocks = new HashMap<>();
        Forest() {
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                block(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        }
        Block block(int x, int y, int z) {
            return blocks.computeIfAbsent(new Position(x, y, z), ignored -> {
                Block block = mock(Block.class);
                when(block.getX()).thenReturn(x);
                when(block.getY()).thenReturn(y);
                when(block.getZ()).thenReturn(z);
                when(block.getWorld()).thenReturn(world);
                when(block.getType()).thenReturn(Material.AIR);
                return block;
            });
        }
        Block place(int x, int y, int z, Material material) {
            Block block = block(x, y, z);
            when(block.getType()).thenReturn(material);
            return block;
        }
    }

    @Test void treesIncludeDiagonalBranchesButNeverLogsBelowTheCutOrOtherSpecies() {
        Forest forest = new Forest();
        Block root = forest.place(0, 65, 0, Material.OAK_LOG);
        Block branch = forest.place(1, 66, 1, Material.OAK_LOG);
        forest.place(0, 64, 0, Material.OAK_LOG);
        forest.place(2, 66, 1, Material.BIRCH_LOG);
        assertEquals(List.of(root, branch), HarvestSupport.connected(root, true, 128));
        assertFalse(HarvestSupport.hasNaturalCanopy(List.of(root, branch)));
        Leaves leaves = mock(Leaves.class);
        when(forest.block(1, 67, 1).getBlockData()).thenReturn(leaves);
        assertTrue(HarvestSupport.hasNaturalCanopy(List.of(root, branch)));
        when(leaves.isPersistent()).thenReturn(true);
        assertFalse(HarvestSupport.hasNaturalCanopy(List.of(root, branch)));
    }

    @Test void veinsFollowIdenticalOreInAllDirectionsAndRespectTheLimit() {
        Forest forest = new Forest();
        Block root = forest.place(0, 65, 0, Material.IRON_ORE);
        Block lower = forest.place(-1, 64, -1, Material.IRON_ORE);
        forest.place(1, 65, 0, Material.DEEPSLATE_IRON_ORE);
        forest.place(0, 66, 0, Material.GOLD_ORE);
        assertEquals(List.of(root, lower), HarvestSupport.connected(root, false, 64));
        assertEquals(List.of(root), HarvestSupport.connected(root, false, 1));
    }

    @Test void traversalDoesNotLoadNeighbouringChunks() {
        Forest forest = new Forest();
        Block root = forest.place(15, 65, 0, Material.DIAMOND_ORE);
        forest.place(16, 65, 0, Material.DIAMOND_ORE);
        when(forest.world.isChunkLoaded(1, 0)).thenReturn(false);
        assertEquals(List.of(root), HarvestSupport.connected(root, false, 64));
        verify(forest.world, never()).getBlockAt(16, 65, 0);
    }

    @Test void harvestMaterialsExcludeBuildingVariantsAndUseTheCorrectToolFamily() {
        assertTrue(HarvestSupport.isLog(Material.OAK_LOG));
        assertFalse(HarvestSupport.isLog(Material.STRIPPED_OAK_LOG));
        assertFalse(HarvestSupport.isLog(Material.OAK_WOOD));
        assertTrue(HarvestSupport.isOre(Material.ANCIENT_DEBRIS));
        assertFalse(HarvestSupport.isOre(Material.IRON_BLOCK));
        assertTrue(HarvestSupport.isCorrectTool(Material.DIAMOND_AXE, true));
        assertFalse(HarvestSupport.isCorrectTool(Material.DIAMOND_PICKAXE, true));
        assertTrue(HarvestSupport.isCorrectTool(Material.NETHERITE_PICKAXE, false));
    }
}
