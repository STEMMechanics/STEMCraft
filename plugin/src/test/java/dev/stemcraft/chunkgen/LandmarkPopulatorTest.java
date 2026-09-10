package dev.stemcraft.chunkgen;

import dev.stemcraft.api.service.world.generation.*;
import dev.stemcraft.chunkgen.feature.LandmarkPopulator;
import dev.stemcraft.chunkgen.noise.SeedMixer;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.generator.*;
import org.bukkit.loot.LootTable;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LandmarkPopulatorTest {
    @BeforeAll static void setup() { MockBukkit.mock(); }
    @AfterAll static void cleanup() { MockBukkit.unmock(); }
    record Position(int x, int y, int z) {}

    @Test void oneCandidatePer512BlockRegionIncludingNegativeCoordinates() {
        for (int rx = -2; rx <= 2; rx++) for (int rz = -2; rz <= 2; rz++) {
            int count = 0;
            for (int x = rx * 32; x < rx * 32 + 32; x++) for (int z = rz * 32; z < rz * 32 + 32; z++)
                if (LandmarkPopulator.candidate(98, x, z)) count++;
            assertEquals(1, count);
        }
    }
    @Test void fossilsAndDifferentRuinsAreDeterministicBoundedAndUsuallyHaveNoLoot() {
        var key = new NamespacedKey("stemcraft", "wasteland");
        var definition = new GeneratorDefinition(key, "Waste", "Test", GeneratorCategory.HOSTILE, true, 1);
        var populator = new LandmarkPopulator(definition, true);
        long seed = SeedMixer.derive(83, key + "/v1/landmarks");
        int bones = 0, camps = 0, buried = 0, walls = 0, chests = 0;
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getLootTable(any(NamespacedKey.class))).thenReturn(mock(LootTable.class));
            for (int rx = -5; rx < 5; rx++) for (int rz = -5; rz < 5; rz++) {
                int[] candidate = candidate(seed, rx, rz);
                Map<Position, Material> a = generate(populator, candidate, false);
                assertEquals(a, generate(populator, candidate, false));
                if (a.containsValue(Material.BONE_BLOCK)) {
                    bones++;
                    int width = a.keySet().stream().mapToInt(Position::x).max().orElseThrow() - a.keySet().stream().mapToInt(Position::x).min().orElseThrow();
                    int length = a.keySet().stream().mapToInt(Position::z).max().orElseThrow() - a.keySet().stream().mapToInt(Position::z).min().orElseThrow();
                    assertTrue(Math.max(width, length) >= 11);
                    assertFalse(a.containsValue(Material.CHEST));
                } else if (a.containsValue(Material.BROWN_CARPET)) camps++;
                else if (a.keySet().stream().anyMatch(p -> p.y() >= 67)) walls++;
                else buried++;
                if (a.containsValue(Material.CHEST)) chests++;
            }
        }
        assertTrue(bones > 10); assertTrue(camps > 0); assertTrue(buried > 0); assertTrue(walls > 0);
        assertTrue(chests > 0 && chests < 30);
    }
    @Test void fossilsAreOptInAndUnsupportedTerrainIsUntouched() {
        var key = new NamespacedKey("stemcraft", "skylands");
        var populator = new LandmarkPopulator(new GeneratorDefinition(key, "Sky", "Test", GeneratorCategory.FLOATING, true, 1), false);
        long seed = SeedMixer.derive(83, key + "/v1/landmarks");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getLootTable(any(NamespacedKey.class))).thenReturn(mock(LootTable.class));
            for (int rx = -3; rx < 3; rx++) {
                int[] candidate = candidate(seed, rx, -2);
                assertFalse(generate(populator, candidate, false).containsValue(Material.BONE_BLOCK));
                assertTrue(generate(populator, candidate, true).isEmpty());
            }
        }
    }
    private static int[] candidate(long seed, int rx, int rz) {
        for (int x = rx * 32; x < rx * 32 + 32; x++) for (int z = rz * 32; z < rz * 32 + 32; z++)
            if (LandmarkPopulator.candidate(seed, x, z)) return new int[]{x, z};
        throw new AssertionError("No candidate");
    }
    private static Map<Position, Material> generate(LandmarkPopulator populator, int[] chunk, boolean voidWorld) {
        WorldInfo info = mock(WorldInfo.class);
        when(info.getSeed()).thenReturn(83L); when(info.getMinHeight()).thenReturn(-64); when(info.getMaxHeight()).thenReturn(320);
        Map<Position, Material> blocks = new HashMap<>();
        LimitedRegion region = mock(LimitedRegion.class);
        when(region.getType(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2);
            assertEquals(chunk[0], Math.floorDiv(x, 16)); assertEquals(chunk[1], Math.floorDiv(z, 16));
            return blocks.getOrDefault(new Position(x, y, z), !voidWorld && y < 65 ? Material.SAND : Material.AIR);
        });
        when(region.getBiome(anyInt(), anyInt(), anyInt())).thenReturn(Biome.DESERT);
        Chest chest = mock(Chest.class);
        when(region.getBlockState(anyInt(), anyInt(), anyInt())).thenReturn(chest);
        doAnswer(call -> {
            int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2);
            assertEquals(chunk[0], Math.floorDiv(x, 16)); assertEquals(chunk[1], Math.floorDiv(z, 16));
            blocks.put(new Position(x, y, z), call.getArgument(3)); return null;
        }).when(region).setType(anyInt(), anyInt(), anyInt(), any());
        populator.populate(info, new Random(), chunk[0], chunk[1], region);
        verify(region, never()).getWorld();
        if (blocks.containsValue(Material.CHEST)) { verify(chest).setSeed(anyLong()); verify(chest).update(true, false); }
        return blocks;
    }
}
