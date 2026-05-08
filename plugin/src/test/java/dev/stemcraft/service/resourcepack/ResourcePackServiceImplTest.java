package dev.stemcraft.service.resourcepack;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.message.TokenProcessor;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.generator.AbstractResourcePackGenerator;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.config.ConfigFileImpl;
import dev.stemcraft.service.resourcepack.generators.GlyphGenerator;
import dev.stemcraft.service.resourcepack.generators.MinecraftPackGenerator;
import dev.stemcraft.service.resourcepack.generators.PackMetaGenerator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourcePackServiceImplTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void teardownServer() {
        MockBukkit.unmock();
    }

    @Test
    void resolveResourcePackFormatMatchesKnownVersionThresholds() {
        assertEquals(32, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 20, 6}));
        assertEquals(34, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 0}));
        assertEquals(42, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 3}));
        assertEquals(46, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 4}));
        assertEquals(55, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 5}));
        assertEquals(63, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 6}));
        assertEquals(64, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 8}));
        assertEquals(69, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 10}));
        assertEquals(75, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 11}));
    }

    @Test
    void resolveResourcePackFormatFallsBackToEarliestKnownFormat() {
        assertEquals(32, ResourcePackServiceImpl.resolveResourcePackFormat(null));
        assertEquals(32, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 19, 4}));
        assertEquals(32, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {0, 0, 0}));
    }

    @Test
    void resolveSupportedVersionRangeStartsAtEarliestKnownAndClampsToCurrentVersion() {
        assertEquals(32, ResourcePackServiceImpl.resolveSupportedVersionRange(new int[] {1, 21, 11})[0]);
        assertEquals(75, ResourcePackServiceImpl.resolveSupportedVersionRange(new int[] {1, 21, 11})[1]);
        assertEquals(32, ResourcePackServiceImpl.resolveSupportedVersionRange(new int[] {1, 21, 3})[0]);
        assertEquals(42, ResourcePackServiceImpl.resolveSupportedVersionRange(new int[] {1, 21, 3})[1]);
    }

    @Test
    void resolveSupportedVersionRangeUsesConfiguredMinButKeepsCurrentMinecraftAsMax() {
        ConfigSectionView config = mock(ConfigSectionView.class);
        when(config.getInt("min_pack_format", 32)).thenReturn(65);
        when(config.getInt("max_pack_format", 75)).thenReturn(69);

        List<String> warnings = new ArrayList<>();
        int[] supportedRange = ResourcePackServiceImpl.resolveSupportedVersionRange(
            new int[] {1, 21, 11},
            config,
            warnings::add
        );

        assertEquals(65, supportedRange[0]);
        assertEquals(75, supportedRange[1]);
        assertEquals(1, warnings.size());
    }

    @Test
    void resolveSupportedVersionRangeClampsConfiguredMinToCurrentMinecraftFormat() {
        ConfigSectionView config = mock(ConfigSectionView.class);
        when(config.getInt("min_pack_format", 32)).thenReturn(80);
        when(config.getInt("max_pack_format", 75)).thenReturn(75);

        List<String> warnings = new ArrayList<>();
        int[] supportedRange = ResourcePackServiceImpl.resolveSupportedVersionRange(
            new int[] {1, 21, 11},
            config,
            warnings::add
        );

        assertEquals(75, supportedRange[0]);
        assertEquals(75, supportedRange[1]);
        assertEquals(1, warnings.size());
    }

    @Test
    void planFutureSegmentsSplitsRangesWhereGeneratorCompatibilityChanges() {
        List<PackFormatRange> plannedRanges = ResourcePackServiceImpl.planFutureSegments(
            75,
            List.of(
                new PackFormatRange(64, 75),
                new PackFormatRange(80, 85),
                new PackFormatRange(64, 82)
            )
        );

        assertEquals(3, plannedRanges.size());
        assertEquals(new PackFormatRange(76, 79), plannedRanges.get(0));
        assertEquals(new PackFormatRange(80, 82), plannedRanges.get(1));
        assertEquals(new PackFormatRange(83, 85), plannedRanges.get(2));
    }

    @Test
    void registerGeneratorLoadsGeneratorSpecificConfigForExternalImplementation() {
        TestHarness harness = new TestHarness();
        RecordingGenerator generator = new RecordingGenerator("external");

        harness.service.registerGenerator(generator);

        assertSame(harness.externalConfig, generator.loadedConfig);
        assertTrue(harness.service.hasGenerator("external"));
    }

    @Test
    void duplicateGeneratorIdsAreRejected() {
        TestHarness harness = new TestHarness();

        harness.service.registerGenerator(new RecordingGenerator("duplicate"));

        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> harness.service.registerGenerator(new RecordingGenerator("duplicate"))
        );
        assertEquals("Duplicate resource-pack generator id: duplicate", thrown.getMessage());
    }

    @Test
    void blankGeneratorIdsAreRejected() {
        TestHarness harness = new TestHarness();

        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> harness.service.registerGenerator(new RecordingGenerator("   "))
        );
        assertEquals("Resource-pack generator id must not be blank", thrown.getMessage());
    }

    @Test
    void incompatibleGeneratorIsSkippedForCurrentTargetAndPlannedForCompatibleFutureTarget() {
        TestHarness harness = new TestHarness();
        FutureOnlyGenerator generator = new FutureOnlyGenerator("future-only");

        harness.service.registerGenerator(generator);

        assertEquals(2, harness.service.buildPlan().size());
        assertEquals(75, harness.service.buildPlan().getFirst().packFormat());
        assertEquals(82, harness.service.buildPlan().get(1).packFormat());
        assertFalse(generator.supports(harness.service.buildPlan().getFirst()));
        assertTrue(generator.supports(harness.service.buildPlan().get(1)));
    }

    @Test
    void abstractResourcePackGeneratorStoresAndExposesConfig() {
        ConfigSectionView config = mock(ConfigSectionView.class);
        TestAbstractGenerator generator = new TestAbstractGenerator();

        generator.onLoad(config);

        assertSame(config, generator.exposedConfig());
        assertEquals("abstract-test", generator.id());
    }

    @Test
    void internalGeneratorsRegisterCleanlyUnderNewContract() {
        TestHarness harness = new TestHarness();

        assertDoesNotThrow(() -> harness.service.registerGenerator(new PackMetaGenerator(harness.service)));
        assertDoesNotThrow(() -> harness.service.registerGenerator(new GlyphGenerator(harness.service)));
        assertDoesNotThrow(() -> harness.service.registerGenerator(new MinecraftPackGenerator(harness.service)));
        assertTrue(harness.service.hasGenerator("pack-meta"));
        assertTrue(harness.service.hasGenerator("glyphs"));
        assertTrue(harness.service.hasGenerator("minecraft"));
    }

    @Test
    void directInterfaceImplementationCanRegisterAndGenerateSuccessfully() throws Exception {
        GenerationHarness harness = new GenerationHarness(tempDir.resolve("direct-interface"));
        RecordingWritingGenerator generator = new RecordingWritingGenerator("direct-generator");
        ConfigSection generators = harness.config.getSection("generators");
        assertNotNull(generators);
        ConfigSection directGeneratorConfig = generators.getSection("direct-generator");
        assertNotNull(directGeneratorConfig);
        directGeneratorConfig.set("marker", "ready");
        harness.config.save();

        harness.service.registerGenerator(generator);
        harness.service.generatePack(null);

        assertNotNull(generator.loadedConfig);
        assertEquals("ready", generator.loadedConfig.getString("marker"));
        assertEquals(List.of(75), generator.generatedFormats);
        assertZipContains(harness.resourcePackZip(), "direct.txt");
    }

    @Test
    void onLoadReceivesGeneratorConfigSection() {
        TestHarness harness = new TestHarness();
        RecordingGenerator generator = new RecordingGenerator("external");

        harness.service.registerGenerator(generator);

        assertSame(harness.externalConfig, generator.loadedConfig);
    }

    @Test
    void unsupportedGeneratorsAreSkippedForIncompatibleTargets() throws Exception {
        GenerationHarness harness = new GenerationHarness(tempDir.resolve("unsupported"));
        FutureWritingGenerator generator = new FutureWritingGenerator("future-writer");

        harness.service.registerGenerator(generator);
        harness.service.generatePack(null);

        assertEquals(List.of(82), generator.generatedFormats);
        try (ZipFile zip = new ZipFile(harness.resourcePackZip(), StandardCharsets.UTF_8)) {
            assertNull(zip.getEntry("future-writer.txt"), "Unexpected zip entry: future-writer.txt");
        }
        assertZipContains(harness.resourcePackZip(), "overlays/overlay_80_82/future-writer.txt");
    }

    @Test
    void bundledGeneratorsGenerateExpectedFilesThroughGeneratePack() throws Exception {
        GenerationHarness harness = new GenerationHarness(tempDir.resolve("bundled"));
        harness.createSampleDataPack();

        harness.service.registerGenerator(new PackMetaGenerator(harness.service));
        harness.service.registerGenerator(new GlyphGenerator(harness.service));
        harness.service.registerGenerator(new MinecraftPackGenerator(harness.service));

        harness.service.generatePack(null);

        File zip = harness.resourcePackZip();
        assertNotNull(zip);
        assertTrue(zip.exists());
        assertZipContains(zip, "pack.mcmeta");
        assertZipContains(zip, "assets/testns/example.txt");
        assertZipContains(zip, "assets/testns/textures/font/icon.png");
        assertZipContains(zip, "assets/minecraft/font/default.json");
    }

    private static void assertZipContains(File zipFile, String entryName) throws Exception {
        try (ZipFile zip = new ZipFile(zipFile, StandardCharsets.UTF_8)) {
            assertNotNull(zip.getEntry(entryName), "Missing zip entry: " + entryName);
        }
    }

    private static final class TestHarness {
        private final ConfigSectionView externalConfig = mock(ConfigSectionView.class);
        private final TestService service;

        private TestHarness() {
            MockBukkit.mock();
            STEMCraft plugin = mock(STEMCraft.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("resource-pack-test"));
            when(plugin.getDataFolder()).thenReturn(new File("build/tmp/resource-pack-service-test"));

            STEMCraftAPI api = mock(STEMCraftAPI.class);
            ConfigService configService = mock(ConfigService.class);
            ConfigSectionView config = mock(ConfigSectionView.class);
            ConfigSectionView generatorsConfig = mock(ConfigSectionView.class);
            when(api.config()).thenReturn(configService);
            when(config.getSection("generators")).thenReturn(generatorsConfig);
            when(generatorsConfig.getSection("external")).thenReturn(externalConfig);
            when(generatorsConfig.getSection("duplicate")).thenReturn(mock(ConfigSectionView.class));
            when(generatorsConfig.getSection("future-only")).thenReturn(mock(ConfigSectionView.class));
            when(generatorsConfig.getSection("pack-meta")).thenReturn(mock(ConfigSectionView.class));
            when(generatorsConfig.getSection("glyphs")).thenReturn(mock(ConfigSectionView.class));
            when(generatorsConfig.getSection("minecraft")).thenReturn(mock(ConfigSectionView.class));
            when(config.getInt("min_pack_format", 32)).thenReturn(32);
            when(config.getInt("max_pack_format", 75)).thenReturn(75);

            service = new TestService(plugin, api, config);
        }
    }

    private static final class GenerationHarness {
        private final Path root;
        private final Path dataPacksDir;
        private final ConfigFile config;
        private final TestService service;

        private GenerationHarness(Path root) throws Exception {
            MockBukkit.mock();
            this.root = root;
            this.dataPacksDir = root.resolve("data-packs");
            Files.createDirectories(dataPacksDir);

            STEMCraft plugin = mock(STEMCraft.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("resource-pack-generation-test"));
            when(plugin.getDataFolder()).thenReturn(root.toFile());

            STEMCraftAPI api = mock(STEMCraftAPI.class);
            MessageService messages = mock(MessageService.class);
            TokenProcessor tokens = mock(TokenProcessor.class);
            when(messages.tokens()).thenReturn(tokens);
            when(api.messages()).thenReturn(messages);

            LocalConfigService configService = new LocalConfigService(root);
            when(api.config()).thenReturn(configService);

            config = configService.load("config.yml", true);
            assertNotNull(config);
            config.set("description", "Test Pack");
            config.set("bedrock.enabled", false);
            config.set("min_pack_format", 32);
            config.set("max_pack_format", 75);
            config.save();

            service = new TestService(plugin, api, config);
            setDataPacksDir(service, dataPacksDir.toFile());
        }

        private void createSampleDataPack() throws Exception {
            Path packRoot = dataPacksDir.resolve("sample-pack");
            Path namespaceRoot = packRoot.resolve("contents").resolve("testns");
            Files.createDirectories(namespaceRoot.resolve("textures").resolve("font"));

            Files.writeString(namespaceRoot.resolve("example.txt"), "hello");

            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0xFFFFFFFF);
            ImageIO.write(image, "png", namespaceRoot.resolve("textures").resolve("font").resolve("icon.png").toFile());

            Files.writeString(
                packRoot.resolve("config.yml"),
                """
                namespace: testns
                name_prefix: ui_
                glyphs:
                  accept:
                    char: "\\uE100"
                    file: font/icon.png
                    ascent: 8
                    height: 8
                """.stripIndent()
            );
        }

        private File resourcePackZip() {
            return root.resolve("resource-pack.zip").toFile();
        }

        private static void setDataPacksDir(ResourcePackServiceImpl service, File dataPacksDir) throws Exception {
            Field field = ResourcePackServiceImpl.class.getDeclaredField("dataPacksDir");
            field.setAccessible(true);
            field.set(service, dataPacksDir);
        }
    }

    private static final class LocalConfigService implements ConfigService {
        private final Path root;

        private LocalConfigService(Path root) {
            this.root = root;
        }

        @Override
        public ConfigFile load(@NotNull String name, boolean createIfNotExist) {
            return load(root.toFile(), name, createIfNotExist);
        }

        @Override
        public ConfigFile load(@NotNull File parent, @NotNull String name, boolean createIfNotExist) {
            ConfigFileImpl configFile = new ConfigFileImpl();
            return configFile.load(parent, name, createIfNotExist) ? configFile : null;
        }

        @Override
        public ConfigFile load(@NotNull File file, boolean createIfNotExist) {
            ConfigFileImpl configFile = new ConfigFileImpl();
            return configFile.load(file, createIfNotExist) ? configFile : null;
        }
    }

    private static final class TestService extends ResourcePackServiceImpl {
        private final ConfigSectionView config;

        private TestService(STEMCraft plugin, STEMCraftAPI api, ConfigSectionView config) {
            super(plugin, api);
            this.config = config;
        }

        @Override
        public @NotNull ConfigSectionView getConfig() {
            return config;
        }
    }

    private static class RecordingGenerator implements ResourcePackGenerator {
        private final String id;
        private ConfigSectionView loadedConfig;

        private RecordingGenerator(String id) {
            this.id = id;
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public void onLoad(@NotNull ConfigSectionView config) {
            loadedConfig = config;
        }

        @Override
        public void generate(@NotNull ResourcePackBuildContext context) {
        }
    }

    private static final class RecordingWritingGenerator implements ResourcePackGenerator {
        private final String id;
        private ConfigSectionView loadedConfig;
        private final List<Integer> generatedFormats = new ArrayList<>();

        private RecordingWritingGenerator(String id) {
            this.id = id;
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public void onLoad(@NotNull ConfigSectionView config) {
            loadedConfig = config;
        }

        @Override
        public void generate(@NotNull ResourcePackBuildContext context) throws IOException {
            generatedFormats.add(context.target().packFormat());
            context.writer().writeString("direct.txt", "generated");
        }
    }

    private static final class FutureOnlyGenerator extends RecordingGenerator {
        private FutureOnlyGenerator(String id) {
            super(id);
        }

        @Override
        public @NotNull PackFormatRange supportedFormats() {
            return new PackFormatRange(80, 82);
        }
    }

    private static final class FutureWritingGenerator implements ResourcePackGenerator {
        private final String id;
        private final List<Integer> generatedFormats = new ArrayList<>();

        private FutureWritingGenerator(String id) {
            this.id = id;
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public @NotNull PackFormatRange supportedFormats() {
            return new PackFormatRange(80, 82);
        }

        @Override
        public void generate(@NotNull ResourcePackBuildContext context) throws IOException {
            generatedFormats.add(context.target().packFormat());
            context.writer().writeString("future-writer.txt", Integer.toString(context.target().packFormat()));
        }
    }

    private static final class TestAbstractGenerator extends AbstractResourcePackGenerator {
        private TestAbstractGenerator() {
            super("abstract-test");
        }

        @Override
        public void generate(@NotNull ResourcePackBuildContext context) {
        }

        private ConfigSectionView exposedConfig() {
            return config();
        }
    }
}
