package dev.stemcraft.feature;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TravelLodestoneSupportTest {
    private TestWorld world;

    @BeforeEach
    void setUp() {
        world = new TestWorld("travel-lodestones-test");
    }

    @Test
    void detectStructureFindsValidStackFromAnyChangedBlock() {
        Block lower = world.blockAt(0, 64, 0);
        Block upper = world.blockAt(0, 65, 0);
        Block lodestone = world.blockAt(0, 66, 0);
        lower.setType(Material.EMERALD_BLOCK);
        upper.setType(Material.EMERALD_BLOCK);
        lodestone.setType(Material.LODESTONE);

        TravelLodestoneSupport.Structure fromLower = TravelLodestoneSupport.detectStructure(lower);
        TravelLodestoneSupport.Structure fromUpper = TravelLodestoneSupport.detectStructure(upper);
        TravelLodestoneSupport.Structure fromTop = TravelLodestoneSupport.detectStructure(lodestone);

        assertNotNull(fromLower);
        assertNotNull(fromUpper);
        assertNotNull(fromTop);
        assertEquals(Material.EMERALD_BLOCK, fromTop.supportMaterial());
        assertEquals(lodestone.getLocation(), fromTop.lodestoneBlock().getLocation());
    }

    @Test
    void findClosestSameWorldDestinationIgnoresDifferentTypesAndWorlds() {
        TestWorld otherWorld = new TestWorld("travel-lodestones-other");
        TravelLodestoneSupport.TravelLodestoneRecord source = record(world, 0, 70, 0, Material.GOLD_BLOCK);
        TravelLodestoneSupport.TravelLodestoneRecord nearest = record(world, 5, 70, 0, Material.GOLD_BLOCK);
        TravelLodestoneSupport.TravelLodestoneRecord farther = record(world, 10, 70, 0, Material.GOLD_BLOCK);
        TravelLodestoneSupport.TravelLodestoneRecord wrongType = record(world, 1, 70, 0, Material.DIAMOND_BLOCK);
        TravelLodestoneSupport.TravelLodestoneRecord otherWorldRecord = record(otherWorld, 1, 70, 0, Material.GOLD_BLOCK);

        TravelLodestoneSupport.TravelLodestoneRecord result = TravelLodestoneSupport.findClosestSameWorldDestination(
                source,
                List.of(source, farther, wrongType, otherWorldRecord, nearest)
        );

        assertEquals(nearest, result);
    }

    @Test
    void findSafeTeleportLocationSkipsLodestoneYLevelAndAllowsOpenAir() {
        placeLodestone(world, 10, 10, Material.DIAMOND_BLOCK);
        TravelLodestoneSupport.TravelLodestoneRecord destination = record(world, 10, 66, 10, Material.DIAMOND_BLOCK);

        Location location = TravelLodestoneSupport.findSafeTeleportLocation(world.mock, destination, 5);

        assertNotNull(location);
        assertNotEquals(destination.y(), location.getBlockY());
    }

    @Test
    void findSafeTeleportLocationReturnsNullWhenAllNearbySpaceIsBlocked() {
        placeLodestone(world, 0, 0, Material.EMERALD_BLOCK);
        TravelLodestoneSupport.TravelLodestoneRecord destination = record(world, 0, 66, 0, Material.EMERALD_BLOCK);

        int radius = 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dy == 0) {
                    continue;
                }

                for (int dz = -radius; dz <= radius; dz++) {
                    world.blockAt(destination.x() + dx, destination.y() + dy, destination.z() + dz).setType(Material.STONE);
                    world.blockAt(destination.x() + dx, destination.y() + dy + 1, destination.z() + dz).setType(Material.STONE);
                }
            }
        }

        Location location = TravelLodestoneSupport.findSafeTeleportLocation(world.mock, destination, radius);

        assertNull(location);
    }

    private TravelLodestoneSupport.TravelLodestoneRecord record(TestWorld world, int x, int y, int z, Material material) {
        return new TravelLodestoneSupport.TravelLodestoneRecord(world.uid, world.name, x, y, z, material);
    }

    private void placeLodestone(TestWorld world, int x, int z, Material supportMaterial) {
        world.blockAt(x, 64, z).setType(supportMaterial);
        world.blockAt(x, 64 + 1, z).setType(supportMaterial);
        world.blockAt(x, 64 + 2, z).setType(Material.LODESTONE);
    }

    private static final class TestWorld {
        private final UUID uid = UUID.randomUUID();
        private final String name;
        private final World mock = mock(World.class);
        private final Map<String, BlockState> blocks = new HashMap<>();

        private TestWorld(String name) {
            this.name = name;
            when(mock.getUID()).thenReturn(uid);
            when(mock.getName()).thenReturn(name);
            when(mock.getMinHeight()).thenReturn(-64);
            when(mock.getMaxHeight()).thenReturn(320);
            when(mock.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                    blockAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))
            );
        }

        private Block blockAt(int x, int y, int z) {
            return blocks.computeIfAbsent(key(x, y, z), unused -> createBlockState(x, y, z)).block;
        }

        private BlockState createBlockState(int x, int y, int z) {
            BlockState state = new BlockState();
            state.block = mock(Block.class);
            when(state.block.getWorld()).thenReturn(mock);
            when(state.block.getX()).thenReturn(x);
            when(state.block.getY()).thenReturn(y);
            when(state.block.getZ()).thenReturn(z);
            when(state.block.getLocation()).thenAnswer(unused -> new Location(mock, x, y, z));
            when(state.block.getType()).thenAnswer(unused -> state.type);
            when(state.block.getRelative(any(BlockFace.class))).thenAnswer(invocation -> {
                BlockFace face = invocation.getArgument(0);
                return blockAt(x + face.getModX(), y + face.getModY(), z + face.getModZ());
            });
            when(state.block.getRelative(any(BlockFace.class), anyInt())).thenAnswer(invocation -> {
                BlockFace face = invocation.getArgument(0);
                int distance = invocation.getArgument(1);
                return blockAt(x + face.getModX() * distance, y + face.getModY() * distance, z + face.getModZ() * distance);
            });
            doAnswer(invocation -> {
                state.type = invocation.getArgument(0);
                return null;
            }).when(state.block).setType(any(Material.class));
            doAnswer(invocation -> {
                state.type = invocation.getArgument(0);
                return null;
            }).when(state.block).setType(any(Material.class), anyBoolean());
            return state;
        }

        private static @NotNull String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }

    private static final class BlockState {
        private Block block;
        private Material type = Material.AIR;
    }
}
