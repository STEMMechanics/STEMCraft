package dev.stemcraft.service.resourcepack.generators;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinecraftPackGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generateMigratesRenamedPillarTexturesFor26_2() throws Exception {
        Path dataPack = tempDir.resolve("data-packs/source");
        Path blocks = dataPack.resolve("contents/minecraft/textures/block");
        Files.createDirectories(blocks);
        Files.writeString(blocks.resolve("quartz_pillar.png"), "quartz");
        Files.writeString(blocks.resolve("purpur_pillar.png"), "purpur");

        ResourcePackServiceImpl service = mock(ResourcePackServiceImpl.class);
        when(service.dataPackDirectories()).thenReturn(new File[] {dataPack.toFile()});

        Path output = tempDir.resolve("output");
        TestWriter writer = new TestWriter(output);
        new MinecraftPackGenerator(service).generate(new ResourcePackBuildContext(
            new ResourcePackBuildTarget("26.2", 88),
            writer,
            mock(ConfigSectionView.class)
        ));

        Path outputBlocks = output.resolve("assets/minecraft/textures/block");
        assertTrue(Files.isRegularFile(outputBlocks.resolve("quartz_pillar_side.png")));
        assertTrue(Files.isRegularFile(outputBlocks.resolve("purpur_pillar_side.png")));
        assertFalse(Files.exists(outputBlocks.resolve("quartz_pillar.png")));
        assertFalse(Files.exists(outputBlocks.resolve("purpur_pillar.png")));
    }

    private static final class TestWriter implements ResourcePackWriter {
        private final Path root;

        private TestWriter(Path root) {
            this.root = root;
        }

        @Override public @NotNull Path root() { return root; }
        @Override public @NotNull PackFormatRange supportedRange() { return new PackFormatRange(32, 88); }
        @Override public boolean overlay() { return false; }
        @Override public String overlayDirectory() { return null; }
        @Override public @NotNull ConfigSection manifest() { return mock(ConfigSection.class); }
        @Override public @NotNull Path resolve(@NotNull String relativePath) { return root.resolve(relativePath); }

        @Override
        public void writeString(@NotNull String relativePath, @NotNull String content) throws IOException {
            Path output = resolve(relativePath);
            Files.createDirectories(output.getParent());
            Files.writeString(output, content);
        }

        @Override
        public void copyFile(@NotNull Path source, @NotNull String relativePath) throws IOException {
            Path output = resolve(relativePath);
            Files.createDirectories(output.getParent());
            Files.copy(source, output);
        }

        @Override
        public void copyDirectory(@NotNull Path sourceDir, @NotNull String relativePath) throws IOException {
            try (var paths = Files.walk(sourceDir)) {
                for (Path source : paths.toList()) {
                    Path output = resolve(relativePath).resolve(sourceDir.relativize(source));
                    if (Files.isDirectory(source)) Files.createDirectories(output);
                    else Files.copy(source, output);
                }
            }
        }
    }
}
