package dev.stemcraft.chunkgen;

import dev.stemcraft.api.service.world.generation.*;
import dev.stemcraft.api.factory.ChunkGeneratorFactory;
import dev.stemcraft.chunkgen.generator.*;
import dev.stemcraft.chunkgen.terrain.TerrainModel;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TerrainGenerationTest {
    private static org.mockito.MockedStatic<Bukkit> blockData;

    @BeforeAll
    static void setup() {
        MockBukkit.mock();
        // MockBukkit does not implement glow lichen or cave-vine block data yet.
        blockData = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        var lichen = mock(org.bukkit.block.data.MultipleFacing.class);
        when(lichen.getMaterial()).thenReturn(Material.GLOW_LICHEN);
        blockData.when(() -> Bukkit.createBlockData(Material.GLOW_LICHEN)).thenReturn(lichen);
        var vines = mock(org.bukkit.block.data.type.CaveVines.class);
        when(vines.getMaterial()).thenReturn(Material.CAVE_VINES);
        blockData.when(() -> Bukkit.createBlockData(Material.CAVE_VINES)).thenReturn(vines);
    }

    @AfterAll
    static void cleanup() {
        blockData.close();
        MockBukkit.unmock();
    }

    private static final Map<String, Function<GenerationContext, TerrainModel>> MODELS = Map.of(
            "deep", DeepGenerator::new, "wasteland", WastelandGenerator::new,
            "skylands", SkylandsGenerator::new, "faraway", FarawayLandsGenerator::new);

    private StemChunkGenerator generator(String id) {
        return new StemChunkGenerator(new GeneratorDefinition(new NamespacedKey("stemcraft", id), id, "Test",
                GeneratorCategory.NORMAL, true, 1), MODELS.get(id));
    }

    private World world(long seed, int min, int max) {
        World world = mock(World.class);
        when(world.getSeed()).thenReturn(seed);
        when(world.getMinHeight()).thenReturn(min);
        when(world.getMaxHeight()).thenReturn(max);
        return world;
    }

    @Test
    void cavernLightsAreDeterministicAtNegativeCoordinatesAndUseSupportedSurfaces() {
        var feature = new dev.stemcraft.chunkgen.feature.CavernLightFeature();
        var context = new GenerationContext(9281, -64, 128, new NamespacedKey("stemcraft", "deep"), 1);
        Set<Material> found = new HashSet<>();
        for (int cx = -3; cx <= 0; cx++) {
            var first = litCavern();
            var second = litCavern();
            feature.generate(context, cx, -2, first);
            feature.generate(context, 10, 8, litCavern());
            feature.generate(context, cx, -2, second);
            assertArrayEquals(first.blocks, second.blocks);
            found.addAll(Arrays.asList(first.blocks));
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    for (int y = -30; y < 60; y++) {
                        Material material = first.getType(x, y, z);
                        if (material == Material.CAVE_VINES) assertTrue(first.getType(x, y + 1, z).isSolid());
                        if (material == Material.GLOW_LICHEN) assertTrue(first.getType(x, y - 1, z).isSolid());
                    }
                }
        }
        assertTrue(found.containsAll(List.of(Material.GLOWSTONE, Material.GLOW_LICHEN, Material.CAVE_VINES)));
    }

    private MemoryChunk litCavern() {
        var data = new MemoryChunk(-64, 128);
        data.setRegion(0, -31, 0, 16, -30, 16, Material.DEEPSLATE);
        data.setRegion(0, 60, 0, 16, 61, 16, Material.STONE);
        return data;
    }

    @Test
    void chunksAreOrderIndependentConcurrentAndMatchHeightQueries() throws Exception {
        World world = world(83423, -64, 320);
        for (String id : MODELS.keySet()) {
            var generator = generator(id);
            MemoryChunk first = new MemoryChunk(-64, 320);
            generator.generateNoise(world, new Random(1), -1, 0, first);
            try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
                List<Callable<MemoryChunk>> tasks = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    final int coordinate = i - 1;
                    tasks.add(() -> {
                        MemoryChunk data = new MemoryChunk(-64, 320);
                        generator.generateNoise(world, new Random(987), coordinate, 0, data);
                        return data;
                    });
                }
                var chunks = pool.invokeAll(tasks);
                assertArrayEquals(first.blocks, chunks.getFirst().get().blocks, id);
            }
            for (int x = 0; x < 16; x += 3)
                for (int z = 0; z < 16; z += 3) {
                    int expected = -64;
                    for (int y = 319; y >= -64; y--)
                        if (first.getType(x, y, z) != Material.AIR) {
                            expected = y + 1;
                            break;
                        }
                    assertEquals(expected, generator.getBaseHeight(world, new Random(99), x - 16, z, HeightMap.WORLD_SURFACE_WG), id);
                }
            var provider = generator.getDefaultBiomeProvider(world);
            for (int x = -5000; x < 5000; x += 250)
                assertTrue(provider.getBiomes(world).contains(provider.getBiome(world, x, 20, -x)));
        }
    }

    @Test
    void everyGeneratorHasSolidSpawnFloorAndHeadroomAcrossSeedsAndHeightRanges() {
        for (String id : MODELS.keySet())
            for (long seed : new long[]{0, -1, 29381})
                for (int[] range : new int[][]{{-64, 320}, {0, 256}}) {
                    var generator = generator(id);
                    World world = world(seed, range[0], range[1]);
                    MemoryChunk data = new MemoryChunk(range[0], range[1]);
                    generator.generateNoise(world, new Random(), 0, 0, data);
                    int feet = generator.getFixedSpawnLocation(world, new Random()).getBlockY();
                    assertTrue(data.getType(0, feet - 1, 0).isSolid(), id);
                    for (int y = feet; y <= feet + 2; y++) assertEquals(Material.AIR, data.getType(0, y, 0), id);
                    if (id.equals("skylands")) for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++)
                            assertEquals(Material.AIR, data.getType(x, range[0], z), "Skylands must have no ground plane");
                }
    }

    @Test
    void skylandsOreFeatureIncludesAllRequiredResourceTypes() {
        var data = new MemoryChunk(-64, 320);
        Arrays.fill(data.blocks, Material.STONE);
        new dev.stemcraft.chunkgen.feature.OreFeature().generate(new GenerationContext(8329, -64, 320,
                new NamespacedKey("stemcraft", "skylands"), 1), -1, -1, data);
        Set<Material> found = new HashSet<>(Arrays.asList(data.blocks));
        assertTrue(found.containsAll(List.of(Material.DIAMOND_ORE, Material.COAL_ORE, Material.COPPER_ORE,
                Material.REDSTONE_ORE, Material.IRON_ORE)));
    }

    @Test
    void surfaceTreesAreDeterministicAndStayInsideTheirPlacementCell() {
        var feature = new dev.stemcraft.chunkgen.feature.SurfaceVegetationFeature();
        var context = new GenerationContext(83423, -64, 320, new NamespacedKey("stemcraft", "skylands"), 1);
        int woodedChunks = 0;
        for (int chunk = -2; chunk <= 2; chunk++) {
            MemoryChunk first = new MemoryChunk(-64, 320), second = new MemoryChunk(-64, 320);
            first.biome = Biome.FOREST;
            second.biome = Biome.FOREST;
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    first.setBlock(x, 90, z, Material.GRASS_BLOCK);
                    second.setBlock(x, 90, z, Material.GRASS_BLOCK);
                }
            feature.generate(context, chunk, -1, first);
            feature.generate(context, chunk, -1, second);
            assertArrayEquals(first.blocks, second.blocks);
            if (Arrays.asList(first.blocks).contains(Material.OAK_LOG)) woodedChunks++;
            for (int x = 0; x < 16; x++)
                for (int y = 91; y < 100; y++) {
                    assertEquals(Material.AIR, first.getType(x, y, 0));
                    assertEquals(Material.AIR, first.getType(x, y, 15));
                    assertEquals(Material.AIR, first.getType(0, y, x));
                    assertEquals(Material.AIR, first.getType(15, y, x));
                }
        }
        assertTrue(woodedChunks > 0);
    }

    @Test
    void terrainFieldsHaveStableCurrentFingerprints() {
        Map<String, Long> hashes = new TreeMap<>();

        for (String id : MODELS.keySet()) {
            TerrainModel model = MODELS.get(id).apply(
                    new GenerationContext(
                            83423,
                            -64,
                            320,
                            new NamespacedKey("stemcraft", id),
                            1
                    )
            );

            long hash = 1;

            for (int x = -3072; x <= 3072; x += 384) {
                for (int z = -2048; z <= 2048; z += 512) {
                    var column = model.column(x, z);

                    for (int y = -64; y < 320; y += 24) {
                        double density = column.density(y);
                        long stableDensity = Math.round(density * 1_000_000_000L);
                        hash = hash * 31 + stableDensity;
                    }
                }
            }

            hashes.put(id, hash);
        }

        assertEquals(
                Map.of(
                        "deep", -8830327102494030133L,
                        "faraway", 77673409813281125L,
                        "skylands", 6418876078609021152L,
                        "wasteland", -3801747636682544639L
                ),
                hashes
        );
    }

    @Test
    void deepHasAnUnbrokenLowerRoofAndSafeUndergroundSpawn() {
        for (long seed : new long[]{0, -1, 83423}) {
            var context = new GenerationContext(seed, -64, 320, new NamespacedKey("stemcraft", "deep"), 1);
            var model = new DeepGenerator(context);
            assertTrue(model.spawnFloor() < 100);
            for (int x = -2048; x <= 2048; x += 128)
                for (int z = -1024; z <= 1024; z += 128) {
                    var column = model.column(x, z);
                    for (int y = 182; y < 320; y++) assertTrue(column.density(y) > 0, "Roof gap");
                    assertEquals(Material.BEDROCK, column.material(181, 0));
                }
            var generator = new StemChunkGenerator(new GeneratorDefinition(context.generatorKey(), "Deep", "Test",
                    GeneratorCategory.CAVE, true, 1), DeepGenerator::new);
            var data = new MemoryChunk(-64, 320);
            generator.generateNoise(world(seed, -64, 320), new Random(), 0, 0, data);
            int floor = model.spawnFloor();
            assertTrue(data.getType(0, floor, 0).isSolid());
            assertEquals(Material.AIR, data.getType(0, floor + 1, 0));
            assertEquals(Material.BEDROCK, data.getType(0, 181, 0));
        }
    }

    @Test
    void skylandsHasWideVoidGapsAndExplorableIslandGroups() {
        int land = 0, sampled = 0;
        for (long seed : new long[]{0, 83423}) {
            var context = new GenerationContext(seed, -64, 320, new NamespacedKey("stemcraft", "skylands"), 1);
            var model = new SkylandsGenerator(context);
            for (int x = -4096; x <= 4096; x += 128)
                for (int z = -4096; z <= 4096; z += 128) {
                    boolean solid = false;
                    var column = model.column(x, z);
                    for (int y = -32; y < 280; y += 8) solid |= column.density(y) > 0;
                    if (solid) land++;
                    sampled++;
                }
        }
        assertTrue(land > 50, "Keep explorable island groups");
        assertTrue(land < sampled * .5, "Most columns should be open void, got " + land + "/" + sampled);
    }

    @Test
    void detailsPopulateDryGroundAndCaveFloorsDeterministically() {
        var feature = new dev.stemcraft.chunkgen.feature.SurfaceDetailFeature();
        for (Biome biome : List.of(Biome.DESERT, Biome.LUSH_CAVES)) {
            var a = new MemoryChunk(-64, 320);
            var b = new MemoryChunk(-64, 320);
            a.biome = biome;
            b.biome = biome;
            Material floor = biome == Biome.DESERT ? Material.SAND : Material.STONE;
            a.setRegion(0, 50, 0, 16, 51, 16, floor);
            b.setRegion(0, 50, 0, 16, 51, 16, floor);
            var context = new GenerationContext(83423, -64, 320, new NamespacedKey("stemcraft", "deep"), 1);
            feature.generate(context, -20, 4, a);
            feature.generate(context, -20, 4, b);
            assertArrayEquals(a.blocks, b.blocks);
            assertTrue(Arrays.asList(a.blocks).contains(biome == Biome.DESERT ? Material.DEAD_BUSH : Material.MOSS_BLOCK));
        }
    }

    @Test
    void oasesProvideWaterAndFertileLand() {
        var model = new WastelandGenerator(new GenerationContext(83423, -64, 320, new NamespacedKey("stemcraft", "wasteland"), 2));
        int water = 0, fertile = 0;
        for (int x = -4096; x <= 4096; x += 64)
            for (int z = -4096; z <= 4096; z += 64) {
                if (model.biome(x, 100, z) != Biome.FOREST) continue;
                var column = model.column(x, z);
                for (int y = 318; y > -64; y--) {
                    if (column.density(y) > 0) {
                        if (column.material(y, 0) == Material.GRASS_BLOCK) fertile++;
                        if (column.fluid(y + 1) == Material.WATER) water++;
                        break;
                    }
                }
            }
        assertTrue(fertile > 0);
        assertTrue(water > 0);
    }

    @Test
    void chunksAndSurfaceDetailsAreOrderIndependent() throws Exception {
        Map<String, Function<GenerationContext, TerrainModel>> models = Map.of(
                "deep", DeepGenerator::new, "skylands", SkylandsGenerator::new,
                "wasteland", WastelandGenerator::new, "faraway", FarawayLandsGenerator::new);
        World world = world(83423, -64, 320);
        for (var entry : models.entrySet()) {
            var generator = new StemChunkGenerator(new GeneratorDefinition(new NamespacedKey("stemcraft", entry.getKey()),
                    entry.getKey(), "Test", GeneratorCategory.NORMAL, true, 1), entry.getValue());
            Callable<MemoryChunk> generate = () -> {
                var data = new MemoryChunk(-64, 320);
                generator.generateNoise(world, new Random(), -32, 19, data);
                generator.generateSurface(world, new Random(), -32, 19, data);
                return data;
            };
            MemoryChunk expected = generate.call();
            try (var pool = Executors.newFixedThreadPool(2)) {
                var results = pool.invokeAll(List.of(generate, generate));
                for (var result : results) assertArrayEquals(expected.blocks, result.get().blocks);
            }
        }
    }

    @Test
    void vegetationReachesLowerIslandBandsAndKeepsHeadroom() {
        var feature = new dev.stemcraft.chunkgen.feature.SurfaceVegetationFeature();
        var context = new GenerationContext(83423, -64, 320, new NamespacedKey("stemcraft", "skylands"), 1);
        int wooded = 0;
        for (int chunk = -5; chunk < 5; chunk++) {
            var data = new MemoryChunk(-64, 320);
            data.biome = Biome.FOREST;
            for (int y : new int[]{40, 130, 220}) data.setRegion(0, y, 0, 16, y + 1, 16, Material.GRASS_BLOCK);
            feature.generate(context, chunk, -2, data);
            int[] logs = new int[3];
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    if (data.getType(x, 41, z) == Material.OAK_LOG) logs[0]++;
                    if (data.getType(x, 131, z) == Material.OAK_LOG) logs[1]++;
                    if (data.getType(x, 221, z) == Material.OAK_LOG) logs[2]++;
                }
            assertEquals(logs[2], logs[0]);
            assertEquals(logs[2], logs[1]);
            wooded += logs[0];
        }
        assertTrue(wooded > 0);
    }

    @Test
    void builtInsRegisterOneCurrentImplementationPerType() {
        var api = mock(dev.stemcraft.api.STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        when(api.config().load("config.yml")).thenReturn(null);
        var generation = api.worlds().generator();
        Map<String, StemChunkGenerator> registered = new HashMap<>();
        doAnswer(call -> {
            GeneratorDefinition definition = call.getArgument(1);
            ChunkGeneratorFactory factory = call.getArgument(2);
            registered.put(definition.key().getKey() + ":" + definition.version(), (StemChunkGenerator) factory.create(""));
            return null;
        }).when(generation).registerGenerator(any(), any(), any());
        BuiltInGenerators.register(mock(org.bukkit.plugin.Plugin.class), api);
        assertEquals(Set.of("deep:1", "skylands:1", "wasteland:1", "faraway:1"), registered.keySet());
        World world = world(83423, -64, 320);
        for (String id : MODELS.keySet()) {
            var actual = registered.get(id + ":1").model(world);
            var expected = MODELS.get(id).apply(new GenerationContext(83423, -64, 320, new NamespacedKey("stemcraft", id), 1));
            assertEquals(expected.getClass(), actual.getClass());
            for (int y = -64; y < 320; y += 8)
                assertEquals(expected.column(-100, 200).density(y), actual.column(-100, 200).density(y));
            assertEquals(1, registered.get(id + ":1").getDefaultPopulators(world).size());
        }
    }

    // ChunkData still requires these legacy methods on test implementations.
    @SuppressWarnings("removal")
    static final class MemoryChunk implements ChunkGenerator.ChunkData {
        final int min, max;
        final Material[] blocks;
        Biome biome = Biome.PLAINS;

        MemoryChunk(int min, int max) {
            this.min = min;
            this.max = max;
            blocks = new Material[256 * (max - min)];
            Arrays.fill(blocks, Material.AIR);
        }

        private int index(int x, int y, int z) {
            return ((x * 16 + z) * (max - min)) + y - min;
        }

        @Override
        public int getMinHeight() {
            return min;
        }

        @Override
        public int getMaxHeight() {
            return max;
        }

        @Override
        public @NotNull Material getType(int x, int y, int z) {
            return blocks[index(x, y, z)];
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull Material material) {
            assertTrue(x >= 0 && x < 16 && z >= 0 && z < 16 && y >= min && y < max);
            blocks[index(x, y, z)] = material;
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull BlockData data) {
            setBlock(x, y, z, data.getMaterial());
        }

        @Override
        @Deprecated
        public void setBlock(
                int x, int y, int z,
                @NotNull org.bukkit.material.MaterialData data) {
            setBlock(x, y, z, data.getItemType());
        }

        @Override
        public void setRegion(
                int x0, int y0, int z0,
                int x1, int y1, int z1,
                @NotNull Material material) {
            for (int x = x0; x < x1; x++)
                for (int y = y0; y < y1; y++)
                    for (int z = z0; z < z1; z++)
                        setBlock(x, y, z, material);
        }

        @Override
        public void setRegion(
                int x0, int y0, int z0,
                int x1, int y1, int z1,
                @NotNull BlockData data) {
            setRegion(x0, y0, z0, x1, y1, z1, data.getMaterial());
        }

        @Override
        @Deprecated
        public void setRegion(
                int x0, int y0, int z0,
                int x1, int y1, int z1,
                @NotNull org.bukkit.material.MaterialData data) {
            setRegion(x0, y0, z0, x1, y1, z1, data.getItemType());
        }

        @Override
        public @NotNull Biome getBiome(int x, int y, int z) {
            return biome;
        }

        @Override
        public @NotNull BlockData getBlockData(int x, int y, int z) {
            return getType(x, y, z).createBlockData();
        }

        @Override
        @Deprecated
        public @NotNull org.bukkit.material.MaterialData getTypeAndData(int x, int y, int z) {
            return new org.bukkit.material.MaterialData(getType(x, y, z));
        }

        @Override
        @Deprecated
        public byte getData(int x, int y, int z) {
            return 0;
        }

        @Override
        public int getHeight(@NotNull HeightMap map, int x, int z) {
            throw new UnsupportedOperationException();
        }
    }
}
