package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildTarget;
import dev.stemcraft.api.service.resourcepack.ResourcePackWriter;
import dev.stemcraft.service.resourcepack.ResourcePackServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackMetaGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generateWritesNewAndLegacyMetadataWhenRangeCrossesFormat65() throws Exception {
        ResourcePackServiceImpl service = mock(ResourcePackServiceImpl.class);
        ConfigSectionView config = mock(ConfigSectionView.class);

        when(service.getConfig()).thenReturn(config);
        when(service.overlayBuildPlan()).thenReturn(List.of());
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        TestResourcePackWriter writer = new TestResourcePackWriter(
            tempDir,
            new PackFormatRange(32, 88),
            null
        );
        new PackMetaGenerator(service).generate(new ResourcePackBuildContext(
            new ResourcePackBuildTarget("26.2", 88),
            writer,
            config
        ));

        JsonObject pack = readPack(tempDir);
        assertEquals(32, pack.get("min_format").getAsInt());
        assertEquals(88, pack.get("max_format").getAsInt());
        assertEquals(64, pack.get("pack_format").getAsInt());

        JsonArray supportedFormats = pack.getAsJsonArray("supported_formats");
        assertEquals(32, supportedFormats.get(0).getAsInt());
        assertEquals(64, supportedFormats.get(1).getAsInt());
    }

    @Test
    void generateOmitsLegacyMetadataWhenOnlyNewFormatRangeIsSupported() throws Exception {
        ResourcePackServiceImpl service = mock(ResourcePackServiceImpl.class);
        ConfigSectionView config = mock(ConfigSectionView.class);

        when(service.getConfig()).thenReturn(config);
        when(service.overlayBuildPlan()).thenReturn(List.of());
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        Path output = tempDir.resolve("new-format-only");
        Files.createDirectories(output);
        new PackMetaGenerator(service).generate(new ResourcePackBuildContext(
            new ResourcePackBuildTarget("1.21.9", 69),
            new TestResourcePackWriter(output, new PackFormatRange(65, 69), null),
            config
        ));

        JsonObject pack = readPack(output);
        assertEquals(65, pack.get("min_format").getAsInt());
        assertEquals(69, pack.get("max_format").getAsInt());
        assertFalse(pack.has("pack_format"));
        assertFalse(pack.has("supported_formats"));
    }

    @Test
    void generateWritesLegacyMetadataOnlyWhenTargetRangeIsPre1219() throws Exception {
        ResourcePackServiceImpl service = mock(ResourcePackServiceImpl.class);
        ConfigSectionView config = mock(ConfigSectionView.class);

        when(service.getConfig()).thenReturn(config);
        when(service.overlayBuildPlan()).thenReturn(List.of());
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        Path output = tempDir.resolve("legacy-only");
        Files.createDirectories(output);
        new PackMetaGenerator(service).generate(new ResourcePackBuildContext(
            new ResourcePackBuildTarget("1.21.7", 64),
            new TestResourcePackWriter(output, new PackFormatRange(32, 64), null),
            config
        ));

        JsonObject pack = readPack(output);
        assertEquals(64, pack.get("pack_format").getAsInt());
        JsonArray supportedFormats = pack.getAsJsonArray("supported_formats");
        assertEquals(32, supportedFormats.get(0).getAsInt());
        assertEquals(64, supportedFormats.get(1).getAsInt());
        assertFalse(pack.has("min_format"));
        assertFalse(pack.has("max_format"));
    }

    @Test
    void generateWritesOverlayEntriesFromBuildPlan() throws Exception {
        ResourcePackServiceImpl service = mock(ResourcePackServiceImpl.class);
        ConfigSectionView config = mock(ConfigSectionView.class);

        when(service.getConfig()).thenReturn(config);
        when(service.overlayBuildPlan()).thenReturn(List.of(
            new ResourcePackServiceImpl.OverlayBuildPlanEntry("overlay_89_92", new PackFormatRange(89, 92))
        ));
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        Path output = tempDir.resolve("with-overlay");
        Files.createDirectories(output);
        new PackMetaGenerator(service).generate(new ResourcePackBuildContext(
            new ResourcePackBuildTarget("26.2", 88),
            new TestResourcePackWriter(output, new PackFormatRange(64, 88), null),
            config
        ));

        JsonObject root = readRoot(output);
        JsonObject overlays = root.getAsJsonObject("overlays");
        JsonArray entries = overlays.getAsJsonArray("entries");
        JsonObject entry = entries.get(0).getAsJsonObject();
        assertEquals("overlay_89_92", entry.get("directory").getAsString());
        assertEquals(89, entry.get("min_format").getAsInt());
        assertEquals(92, entry.get("max_format").getAsInt());
    }

    private JsonObject readPack(Path resourcePackDir) throws Exception {
        return readRoot(resourcePackDir).getAsJsonObject("pack");
    }

    private JsonObject readRoot(Path resourcePackDir) throws Exception {
        String raw = Files.readString(resourcePackDir.resolve("pack.mcmeta"));
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static final class TestResourcePackWriter implements ResourcePackWriter {
        private final Path root;
        private final PackFormatRange supportedRange;
        private final String overlayDirectory;
        private final ConfigSection manifest = mock(ConfigSection.class);

        private TestResourcePackWriter(Path root, PackFormatRange supportedRange, @Nullable String overlayDirectory) {
            this.root = root;
            this.supportedRange = supportedRange;
            this.overlayDirectory = overlayDirectory;
        }

        @Override
        public @NotNull Path root() {
            return root;
        }

        @Override
        public @NotNull PackFormatRange supportedRange() {
            return supportedRange;
        }

        @Override
        public boolean overlay() {
            return overlayDirectory != null;
        }

        @Override
        public @Nullable String overlayDirectory() {
            return overlayDirectory;
        }

        @Override
        public @NotNull ConfigSection manifest() {
            return manifest;
        }

        @Override
        public @NotNull Path resolve(@NotNull String relativePath) {
            return root.resolve(relativePath);
        }

        @Override
        public void writeString(@NotNull String relativePath, @NotNull String content) throws java.io.IOException {
            Path output = resolve(relativePath);
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, content);
        }

        @Override
        public void copyFile(@NotNull Path source, @NotNull String relativePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void copyDirectory(@NotNull Path sourceDir, @NotNull String relativePath) {
            throw new UnsupportedOperationException();
        }
    }
}
