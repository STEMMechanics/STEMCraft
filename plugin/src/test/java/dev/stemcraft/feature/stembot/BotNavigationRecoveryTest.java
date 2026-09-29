package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyInt;

class BotNavigationRecoveryTest {
    private final World world = mock(World.class);
    private final Map<String, List<Double>> floors = new HashMap<>();
    private final BotNavigationRecovery.Terrain terrain = new BotNavigationRecovery.Terrain() {
        public List<Double> surfaces(int x, int z, double low, double high) {
            return floors.getOrDefault(x + ":" + z, List.of()).stream()
                .filter(y -> y >= low && y <= high).toList();
        }
        public boolean clear(int x, int z, double feet, double top) { return true; }
    };

    private void floor(int x, int z, double y) {
        floors.computeIfAbsent(x + ":" + z, key -> new ArrayList<>()).add(y);
    }

    private List<Location> route(Location from, Location to) {
        var search = new BotNavigationRecovery.Search(from, to, terrain);
        for(int i = 0; i < 2000; i++) {
            List<Location> result = search.advance();
            if(result != null) return result;
        }
        fail("Search did not terminate within its budget");
        return List.of();
    }

    @Test
    void exitsBackwardsUpStaircaseInsteadOfChoosingForwardAlcove() {
        // Dead end toward the target on the lower floor.
        for(int x = -3; x <= 3; x++) floor(x, 0, 64);
        // Only exit is behind the spawn, rising more than the old six-block scan limit.
        for(int z = 1; z <= 10; z++) floor(-3, z, 64 + z);
        for(int x = -2; x <= 10; x++) floor(x, 10, 74);
        List<Location> route = route(new Location(world,0,64,0),new Location(world,10,74,10));
        assertFalse(route.isEmpty());
        assertEquals(-2.5, route.getFirst().getX());
        assertTrue(route.stream().anyMatch(p -> p.getX() == -2.5 && p.getY() == 74));
        assertEquals(new Location(world,10.5,74,10.5), route.getLast());
    }

    @Test
    void keepsTightLandingBetweenTwoPerpendicularStairFlights() {
        floor(0,0,60);
        floor(0,1,61);
        floor(0,2,62);
        floor(0,3,62);
        floor(1,3,63);
        floor(2,3,64);
        floor(3,3,64);
        var route = route(new Location(world,.5,60,.5), new Location(world,3.5,64,3.5));
        assertEquals(List.of(
            new Location(world,.5,61,1.5),
            new Location(world,.5,62,2.5),
            new Location(world,.5,62,3.5),
            new Location(world,1.5,63,3.5),
            new Location(world,2.5,64,3.5),
            new Location(world,3.5,64,3.5)), route);
    }

    @Test
    void splitsLongRouteIntoShortConnectedSegments() {
        for(int x = 0; x <= 240; x++) floor(x, 0, 64);
        var route = route(new Location(world,.5,64,.5),new Location(world,240,64,0));
        assertTrue(route.size() >= 30);
        Location previous = new Location(world,.5,64,.5);
        for(Location point : route) {
            assertTrue(previous.distance(point) <= 8);
            previous = point;
        }
        assertEquals(240.5, route.getLast().getX());
    }

    @Test
    void unreachableElevatedTargetDoesNotReturnAnAlcove() {
        for(int x = 0; x <= 5; x++) floor(x, 0, 64);
        floor(5,0,74);
        assertTrue(route(new Location(world,0,64,0),new Location(world,5,74,0)).isEmpty());
    }

    @Test
    void acceptsHalfBlockStepsButRejectsLargeDrops() {
        floor(0,0,64);
        floor(1,0,64.5);
        floor(2,0,65);
        assertFalse(route(new Location(world,0,64,0),new Location(world,2,65,0)).isEmpty());
        floor(3,0,60);
        assertTrue(route(new Location(world,0,64,0),new Location(world,3,60,0)).isEmpty());
    }

    @Test
    void collisionSurfacesRespectSlabsHeadroomAndHazards() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        Block air = block(Material.AIR);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air);
        Block slab = block(Material.STONE_SLAB, new BoundingBox(0,0,0,1,.5,1));
        when(world.getBlockAt(0,63,0)).thenReturn(slab);
        var blocks = new BotNavigationRecovery.BlockTerrain(world);
        assertTrue(blocks.surfaces(0,0,63,65).contains(63.5));
        assertTrue(blocks.clear(0,0,63.5,65.3));
        Block stone = block(Material.STONE,new BoundingBox(0,0,0,1,1,1));
        when(world.getBlockAt(0,65,0)).thenReturn(stone);
        assertFalse(blocks.clear(0,0,63.5,65.3));
        Block magma = block(Material.MAGMA_BLOCK,new BoundingBox(0,0,0,1,1,1));
        when(world.getBlockAt(0,63,0)).thenReturn(magma);
        assertFalse(blocks.surfaces(0,0,63,65).contains(64D));
        assertFalse(blocks.clear(0,0,64,65.8));
    }

    @Test
    void stairCollisionUsesTheUpperTreadAndRejectsStandingInsideIt() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        Block air = block(Material.AIR);
        Block stair = block(Material.STONE_STAIRS, new BoundingBox(0,0,0,1,.5,1),
            new BoundingBox(0,.5,.5,1,1,1));
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air);
        when(world.getBlockAt(0,63,0)).thenReturn(stair);
        var blocks = new BotNavigationRecovery.BlockTerrain(world);
        assertTrue(blocks.surfaces(0,0,63,65).contains(64D));
        assertFalse(blocks.clear(0,0,63.5,65.3));
        assertTrue(blocks.clear(0,0,64,65.8));
    }

    private Block block(Material material, BoundingBox... boxes) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        VoxelShape shape = mock(VoxelShape.class);
        when(shape.getBoundingBoxes()).thenReturn(List.of(boxes));
        when(shape.overlaps(any())).thenAnswer(call -> Arrays.stream(boxes)
            .anyMatch(box -> box.overlaps(call.getArgument(0))));
        when(block.getCollisionShape()).thenReturn(shape);
        return block;
    }

    @Test
    void doesNotReadBlocksInUnloadedChunksOrAcrossWorlds() {
        var search = new BotNavigationRecovery.Search(new Location(world,0,64,0),new Location(world,10,70,0));
        List<Location> result;
        do { result = search.advance(); } while(result == null);
        assertTrue(result.isEmpty());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        assertTrue(Objects.requireNonNull(new BotNavigationRecovery.Search(new Location(world, 0, 64, 0),
                new Location(mock(World.class), 10, 70, 0)).advance()).isEmpty());
    }
}
