package dev.stemcraft.service.world;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldServiceImplDuplicateTest {
    @TempDir Path directory;
    private WorldServiceImpl service;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        STEMCraft plugin = mock(STEMCraft.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer().getWorldContainer()).thenReturn(directory.toFile());
        service = new WorldServiceImpl(plugin, mock(STEMCraftAPI.class));
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    @Test
    void duplicatesIntegratedCustomWorldWithoutCopyingWorldIdentityOrOtherDimensions() throws IOException {
        Path base = Files.createDirectories(directory.resolve("world"));
        Files.writeString(base.resolve("level.dat"), "shared world data");
        Path source = base.resolve("dimensions/minecraft/floorshuffle");
        write(source, "region/r.0.0.mca", "floor blocks");
        write(source, "entities/r.0.0.mca", "entities");
        write(source, "poi/r.0.0.mca", "points of interest");
        write(source, "data/minecraft/world_gen_settings.dat", "seed and generator");
        write(source, "data/paper/metadata.dat", "original UUID");
        write(source, "data/paper/level_overrides.dat", "world settings");
        write(source, "data/paper/persistent_data_container.dat", "plugin data");
        write(source, "paper-world.yml", "paper settings");
        write(source, "uid.dat", "legacy UUID");
        write(source, "session.lock", "lock");
        write(base, "dimensions/minecraft/volcano/region/r.0.0.mca", "other world");

        service.duplicateWorld("floorshuffle", "floorshuffle_copy");

        Path target = base.resolve("dimensions/minecraft/floorshuffle_copy");
        assertEquals("floor blocks", Files.readString(target.resolve("region/r.0.0.mca")));
        assertEquals("entities", Files.readString(target.resolve("entities/r.0.0.mca")));
        assertEquals("points of interest", Files.readString(target.resolve("poi/r.0.0.mca")));
        assertEquals("seed and generator", Files.readString(target.resolve("data/minecraft/world_gen_settings.dat")));
        assertEquals("world settings", Files.readString(target.resolve("data/paper/level_overrides.dat")));
        assertEquals("plugin data", Files.readString(target.resolve("data/paper/persistent_data_container.dat")));
        assertEquals("paper settings", Files.readString(target.resolve("paper-world.yml")));
        assertFalse(Files.exists(target.resolve("data/paper/metadata.dat")));
        assertFalse(Files.exists(target.resolve("uid.dat")));
        assertFalse(Files.exists(target.resolve("session.lock")));
        assertFalse(Files.exists(target.resolve("level.dat")));
        assertFalse(Files.exists(directory.resolve("floorshuffle_copy")));
        assertEquals("original UUID", Files.readString(source.resolve("data/paper/metadata.dat")));
        assertEquals(target, service.getWorldFolder("floorshuffle_copy"));
        assertTrue(service.listWorlds().contains("floorshuffle_copy"));
    }

    private static void write(Path root, String path, String contents) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }

    @Test
    void duplicateLegacyWorldStillCopiesTerrainAndOmitsIdentity() throws IOException {
        Path source = directory.resolve("legacy");
        write(source, "level.dat", "legacy metadata");
        write(source, "region/r.0.0.mca", "legacy terrain");
        write(source, "uid.dat", "UUID");
        service.duplicateWorld("legacy", "legacy_copy");
        assertEquals("legacy terrain", Files.readString(directory.resolve("legacy_copy/region/r.0.0.mca")));
        assertEquals("legacy metadata", Files.readString(directory.resolve("legacy_copy/level.dat")));
        assertFalse(Files.exists(directory.resolve("legacy_copy/uid.dat")));
    }

    @Test
    void existingIntegratedOrRootDestinationsAreNeverMergedOrOverwritten() throws IOException {
        Path base = directory.resolve("world");
        write(base, "level.dat", "shared");
        Path source = base.resolve("dimensions/minecraft/floorshuffle");
        write(source, "region/r.0.0.mca", "source");
        write(base, "dimensions/minecraft/existing/region/r.1.1.mca", "existing");
        assertThrows(IOException.class, () -> service.duplicateWorld("floorshuffle", "existing"));
        assertFalse(Files.exists(base.resolve("dimensions/minecraft/existing/region/r.0.0.mca")));
        assertEquals("existing", Files.readString(base.resolve("dimensions/minecraft/existing/region/r.1.1.mca")));
        write(directory, "legacy/level.dat", "legacy");
        assertThrows(IOException.class, () -> service.duplicateWorld("floorshuffle", "legacy"));
        assertFalse(Files.exists(base.resolve("dimensions/minecraft/legacy")));
        assertThrows(IOException.class, () -> service.duplicateWorld("floorshuffle", "floorshuffle"));
        assertEquals("source", Files.readString(source.resolve("region/r.0.0.mca")));
    }

    @Test
    void loadedDestinationIsNotUnloaded() throws IOException {
        var target = MockBukkit.getMock().addSimpleWorld("existing");
        write(directory, "legacy/level.dat", "legacy");
        assertThrows(IOException.class, () -> service.duplicateWorld("legacy", "existing"));
        assertSame(target, Bukkit.getWorld("existing"));
    }

    @Test
    void invalidOrMissingSourceDoesNotCreateDestination() throws IOException {
        write(directory, "legacy/level.dat", "legacy");
        assertThrows(IOException.class, () -> service.duplicateWorld("legacy", "../outside"));
        assertThrows(IOException.class, () -> service.duplicateWorld("legacy", "."));
        assertThrows(IOException.class, () -> service.duplicateWorld("missing", "copy"));
        assertFalse(Files.exists(directory.resolve("copy")));
    }

    @Test
    void integratedDimensionWithEnvironmentSuffixCanBeFoundAfterCopy() throws IOException {
        Path base = directory.resolve("world");
        write(base, "level.dat", "shared");
        write(base, "dimensions/minecraft/the_nether/region/r.0.0.mca", "nether terrain");
        service.duplicateWorld("world_nether", "arena_nether");
        Path target = base.resolve("dimensions/minecraft/arena_nether");
        assertEquals(target, service.getWorldFolder("arena_nether"));
        assertEquals("nether terrain", Files.readString(target.resolve("region/r.0.0.mca")));
    }

    @Test
    void failedCopyRemovesPartialDestinationAndLeavesSourceUntouched() throws IOException {
        Path source = directory.resolve("legacy");
        write(source, "level.dat", "legacy");
        Path external = directory.resolve("external.txt");
        Files.writeString(external, "do not copy through links");
        Files.createSymbolicLink(source.resolve("linked.txt"), external);
        assertThrows(IOException.class, () -> service.duplicateWorld("legacy", "copy"));
        assertFalse(Files.exists(directory.resolve("copy")));
        assertEquals("legacy", Files.readString(source.resolve("level.dat")));
        assertEquals("do not copy through links", Files.readString(external));
        assertTrue(Files.isSymbolicLink(source.resolve("linked.txt")));
    }
}
