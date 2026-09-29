package dev.stemcraft.chunkgen;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnderhallsGeneratorTest {
    @BeforeAll static void start() { MockBukkit.mock(); }
    @AfterAll static void stop() { MockBukkit.unmock(); }
    private World world(long seed) {
        World world = mock(World.class);
        when(world.getSeed()).thenReturn(seed);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        return world;
    }
    @Test void allRoomsConnectAndNeighboringTilesHaveMatchingOpenings() {
        for (long seed : new long[]{0, 1, -2399, Long.MAX_VALUE}) for (int tx : new int[]{-3, 0, 4}) {
            var maze = new UnderhallsGenerator.Maze(seed, tx, -2);
            boolean[][] seen = new boolean[128][128];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{4, 4}); seen[4][4] = true;
            while (!queue.isEmpty()) {
                int[] pos = queue.remove();
                for (int[] step : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int x = pos[0] + step[0], z = pos[1] + step[1];
                    if (x < 0 || z < 0 || x >= 128 || z >= 128 || seen[x][z] || !maze.passage(x,z)) continue;
                    seen[x][z] = true; queue.add(new int[]{x,z});
                }
            }
            for (int x = 4; x < 128; x += 8) for (int z = 4; z < 128; z += 8) assertTrue(seen[x][z]);
            assertTrue(seen[0][68]); assertTrue(seen[68][0]);
            assertTrue(seen[127][68]); assertTrue(seen[68][127]);
            assertTrue(new UnderhallsGenerator.Maze(seed, tx + 1, -2).passage(0,68));
        }
    }
    @Test void generationIsDeterministicWithYellowWallsAndLitDoorwayRooms() {
        var generator = new UnderhallsGenerator();
        var first = new TerrainGenerationTest.MemoryChunk(-64,320);
        var second = new TerrainGenerationTest.MemoryChunk(-64,320);
        generator.generateNoise(world(901), new Random(1), -3, -5, first);
        generator.generateNoise(world(901), new Random(900), -3, -5, second);
        assertArrayEquals(first.blocks, second.blocks);
        assertTrue(Arrays.asList(first.blocks).contains(Material.END_STONE));
        var spawn = new TerrainGenerationTest.MemoryChunk(-64,320);
        generator.generateNoise(world(901), new Random(), 0,0,spawn);
        assertEquals(Material.SMOOTH_SANDSTONE, spawn.getType(4,64,4));
        assertEquals(Material.AIR, spawn.getType(4,65,4));
        assertEquals(Material.OCHRE_FROGLIGHT, spawn.getType(4,70,4));
        assertEquals(Material.SMOOTH_SANDSTONE, spawn.getType(12,70,4));
        assertEquals(Material.SMOOTH_SANDSTONE, spawn.getType(4,71,4));
        assertEquals(Material.AIR, spawn.getType(4,63,4));
        assertEquals(Material.AIR, spawn.getType(4,-64,4));
        var room = new TerrainGenerationTest.MemoryChunk(-64,320);
        generator.generateNoise(world(901), new Random(), 4,4,room);
        assertEquals(Material.AIR, room.getType(4,65,1));
        assertEquals(Material.AIR, room.getType(4,66,1));
        assertFalse(UnderhallsGenerator.exitTrigger(68,65));
        assertEquals(Material.DARK_OAK_DOOR, room.getType(4,65,2));
        assertEquals(Material.DARK_OAK_DOOR, room.getType(4,66,2));
        assertEquals(Material.AIR, room.getType(4,65,5));
        for (int x=2;x<=6;x++) for (int z=2;z<=6;z++) assertEquals(x == 4 && z == 4 ? Material.OCHRE_FROGLIGHT : Material.SMOOTH_SANDSTONE,room.getType(x,70,z));
    }
    @Test void everyDeadEndGetsADoorAndGeneratedRoomRemainsReachable() {
        for (long seed : new long[]{0,1,901,-2399}) {
            var maze = new UnderhallsGenerator.Maze(seed,0,0);
            int doors=0;
            boolean[][] open=new boolean[128][128];
            for(int x=0;x<128;x++)for(int z=0;z<128;z++) {
                int rx=x%8,rz=z%8;
                boolean wall=maze.doorCell(x/8,z/8) && rx>=2 && rx<=6 && rz>=2 && rz<=6 && (rx==2||rx==6||rz==2||rz==6);
                open[x][z]=maze.passage(x,z) && (!wall || rx==4 && rz==2);
            }
            boolean[][] visited=new boolean[128][128];
            ArrayDeque<int[]> queue=new ArrayDeque<>();queue.add(new int[]{68,65});visited[68][65]=true;
            while(!queue.isEmpty()) {
                var at=queue.remove();
                for(var step:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int x=at[0]+step[0],z=at[1]+step[1];
                    if(x>=0&&x<128&&z>=0&&z<128&&open[x][z]&&!visited[x][z]) {visited[x][z]=true;queue.add(new int[]{x,z});}
                }
            }
            for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                int degree=0;
                if(x>0 && maze.passage(x*8,z*8+4))degree++;
                if(x<15 && maze.passage((x+1)*8,z*8+4))degree++;
                if(z>0 && maze.passage(x*8+4,z*8))degree++;
                if(z<15 && maze.passage(x*8+4,(z+1)*8))degree++;
                if(x==0&&z==8||x==15&&z==8||z==0&&x==8||z==15&&x==8)degree++;
                if(degree==1) assertTrue(maze.doorCell(x,z));
                if(maze.doorCell(x,z)) {doors++;assertTrue(visited[x*8+4][z*8+3]);}
            }
            assertTrue(doors>=15,"Expected many doorways, got " + doors);
        }
    }

    @Test void negativeTilesKeepFixedExitAndArrivalReservations() {
        assertTrue(UnderhallsGenerator.exitTrigger(-60,-59));
        assertTrue(UnderhallsGenerator.reserved(-124,-124));
        assertTrue(UnderhallsGenerator.exitTrigger(-60,-60));
        assertTrue(UnderhallsGenerator.exitTrigger(-60,-61));
        assertFalse(UnderhallsGenerator.exitTrigger(-60,-62));
        assertTrue(UnderhallsGenerator.reserved(-60,-63));
        assertFalse(UnderhallsGenerator.reserved(12,12));
    }
}
