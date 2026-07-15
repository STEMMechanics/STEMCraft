package dev.stemcraft.service.world;

import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldServiceImplStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void discoverWorldNamesIncludesIntegratedPaperDimensions() throws IOException {
        Path worldRoot = tempDir.resolve("world");
        Files.createDirectories(worldRoot);
        Files.createFile(worldRoot.resolve("level.dat"));
        Files.createDirectories(WorldServiceImpl.paperDimensionPath(worldRoot, World.Environment.NETHER).resolve("region"));
        Files.createDirectories(WorldServiceImpl.paperDimensionPath(worldRoot, World.Environment.THE_END).resolve("region"));

        Set<String> names = WorldServiceImpl.discoverWorldNames(tempDir);

        assertEquals(Set.of("world", "world_nether", "world_the_end"), names);
    }

    @Test
    void discoverWorldNamesStillRecognisesLegacyStandaloneWorldFolders() throws IOException {
        Path worldRoot = tempDir.resolve("arena_nether");
        Files.createDirectories(worldRoot.resolve("DIM-1").resolve("region"));

        Set<String> names = WorldServiceImpl.discoverWorldNames(tempDir);

        assertEquals(Set.of("arena_nether"), names);
    }

    @Test
    void recognizedWorldRootIncludesPaperDimensionsDirectory() throws IOException {
        Path worldRoot = tempDir.resolve("survival");
        Files.createDirectories(worldRoot.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region"));

        assertTrue(WorldServiceImpl.isRecognizedWorldRoot(worldRoot));
    }

    @Test
    void integratedDimensionPathDetectionMatchesPaperLayout() throws IOException {
        Path worldRoot = tempDir.resolve("survival");
        Path netherPath = WorldServiceImpl.paperDimensionPath(worldRoot, World.Environment.NETHER);
        Path endPath = WorldServiceImpl.paperDimensionPath(worldRoot, World.Environment.THE_END);

        assertTrue(WorldServiceImpl.isIntegratedDimensionPath(netherPath));
        assertTrue(WorldServiceImpl.isIntegratedDimensionPath(endPath));
        assertFalse(WorldServiceImpl.isIntegratedDimensionPath(worldRoot));
    }
}
