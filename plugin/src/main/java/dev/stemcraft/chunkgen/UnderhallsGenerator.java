/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.chunkgen;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** A seeded maze of 16x16 rooms per tile, with fixed connections between tiles. */
public final class UnderhallsGenerator extends ChunkGenerator {
    public static final int CELL = 8;
    public static final int SIZE = 16;
    public static final int TILE = CELL * SIZE;
    public static final int ROOM = 8 * CELL;
    private record TileKey(long seed, int x, int z) { }
    private final Map<TileKey, Maze> cache = Collections.synchronizedMap(new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TileKey, Maze> entry) { return size() > 128; }
    });

    public static int floor(WorldInfo world) {
        return Math.max(world.getMinHeight() + 2, Math.min(64, world.getMaxHeight() - 9));
    }

    public static boolean exitRoom(int x, int z) {
        int lx = Math.floorMod(x, TILE), lz = Math.floorMod(z, TILE);
        return lx >= ROOM + 2 && lx <= ROOM + 6 && lz >= ROOM + 2 && lz <= ROOM + 6;
    }

    public static boolean exitTrigger(int x, int z) {
        int lx = Math.floorMod(x, TILE), lz = Math.floorMod(z, TILE);
        return lx >= ROOM + 3 && lx <= ROOM + 5 && lz >= ROOM + 3 && lz <= ROOM + 5;
    }

    /** Keep the arrival square and exit chambers clear of player construction. */
    public static boolean reserved(int x, int z) {
        int lx = Math.floorMod(x, TILE), lz = Math.floorMod(z, TILE);
        return exitRoom(x, z) || (lx >= ROOM + 3 && lx <= ROOM + 5 && lz == ROOM + 1) || (lx >= 2 && lx <= 5 && lz >= 2 && lz <= 5);
    }

    public static final class Maze {
        private final boolean[] east = new boolean[SIZE * SIZE];
        private final boolean[] south = new boolean[SIZE * SIZE];
        public Maze(long seed, int tileX, int tileZ) {
            carve(new Random(seed ^ ((long) tileX * 341873128712L) ^ ((long) tileZ * 132897987541L)));
        }
        private void carve(Random random) {
            boolean[] visited = new boolean[SIZE * SIZE];
            int[] stack = new int[SIZE * SIZE];
            int top = 0;
            visited[0] = true;
            while (top >= 0) {
                int current = stack[top], x = current % SIZE, z = current / SIZE;
                int[] candidates = new int[4];
                int count = 0;
                if (x > 0 && !visited[current - 1]) candidates[count++] = current - 1;
                if (x < SIZE - 1 && !visited[current + 1]) candidates[count++] = current + 1;
                if (z > 0 && !visited[current - SIZE]) candidates[count++] = current - SIZE;
                if (z < SIZE - 1 && !visited[current + SIZE]) candidates[count++] = current + SIZE;
                if (count == 0) { top--; continue; }
                int next = candidates[random.nextInt(count)];
                if (Math.abs(next - current) == 1) east[Math.min(current, next)] = true;
                else south[Math.min(current, next)] = true;
                visited[next] = true;
                stack[++top] = next;
            }
        }
        public boolean passage(int x, int z) {
            int cx = x / CELL, cz = z / CELL, dx = x % CELL, dz = z % CELL;
            if (dx != 0 && dz != 0) return true;
            if (dx == 0 && dz >= 3 && dz <= 5) return cx == 0 ? cz == SIZE / 2 : east[cz * SIZE + cx - 1];
            if (dz == 0 && dx >= 3 && dx <= 5) return cz == 0 ? cx == SIZE / 2 : south[(cz - 1) * SIZE + cx];
            return false;
        }
    }

    @Override public void generateNoise(@NotNull WorldInfo info, @NotNull Random random, int chunkX, int chunkZ,
                                        @NotNull ChunkData data) {
        int floor = floor(info);
        int tileX = Math.floorDiv(chunkX, TILE / 16), tileZ = Math.floorDiv(chunkZ, TILE / 16);
        Maze maze = cache.computeIfAbsent(new TileKey(info.getSeed(), tileX, tileZ), key -> new Maze(key.seed, key.x, key.z));
        data.setRegion(0, data.getMinHeight(), 0, 16, floor, 16, Material.BEDROCK);
        data.setRegion(0, floor, 0, 16, floor + 1, 16, Material.SMOOTH_SANDSTONE);
        data.setRegion(0, floor + 7, 0, 16, floor + 8, 16, Material.BEDROCK);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
            int lx = Math.floorMod(wx, TILE), lz = Math.floorMod(wz, TILE);
            boolean room = exitRoom(wx, wz);
            boolean roomWall = room && (lx == ROOM + 2 || lx == ROOM + 6 || lz == ROOM + 2 || lz == ROOM + 6);
            boolean door = lx == ROOM + 4 && lz == ROOM + 2;
            boolean wall = !maze.passage(lx, lz) || roomWall;
            Material brick = Math.floorMod(wx * 31 + wz * 17, 9) == 0 ? Material.END_STONE_BRICKS : Material.END_STONE;
            for (int y = 1; y <= 5; y++) {
                data.setBlock(x, floor + y, z, wall ? brick : Material.AIR);
                if (door && y <= 2) {
                    Door block = (Door) Material.DARK_OAK_DOOR.createBlockData();
                    block.setFacing(BlockFace.NORTH);
                    block.setHalf(y == 1 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
                    data.setBlock(x, floor + y, z, block);
                }
            }
            boolean lamp = !room && lx % CELL >= 3 && lx % CELL <= 4 && lz % CELL == 4 &&
                ((lx / CELL + lz / CELL) % 3 == 0);
            data.setBlock(x, floor + 6, z, lamp ? Material.OCHRE_FROGLIGHT : Material.SMOOTH_SANDSTONE);
        }
    }

    @Override public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, ROOM + 4.5, floor(world) + 1, ROOM + 1.5);
    }
    @Override public int getBaseHeight(@NotNull WorldInfo world, @NotNull Random random, int x, int z, @NotNull HeightMap map) {
        return floor(world) + 1;
    }
    @Override public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo world) {
        return new BiomeProvider() {
            @Override public @NotNull Biome getBiome(@NotNull WorldInfo info, int x, int y, int z) { return Biome.DEEP_DARK; }
            @Override public @NotNull List<Biome> getBiomes(@NotNull WorldInfo info) { return List.of(Biome.DEEP_DARK); }
        };
    }
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
}
