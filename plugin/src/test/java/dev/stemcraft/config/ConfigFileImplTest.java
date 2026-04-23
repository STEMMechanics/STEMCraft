package dev.stemcraft.config;

import dev.stemcraft.api.config.ConfigSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileImplTest {
    @TempDir
    Path tempDir;

    @Test
    void savePreservesManualDiskChangesOnUntouchedPaths() throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, """
            alpha: 1
            beta: before
            """);

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "config.yml", true));

        Files.writeString(file, """
            alpha: 1
            beta: manual
            external: added
            """);

        config.set("alpha", 2);
        config.save();

        ConfigFileImpl reloaded = new ConfigFileImpl();
        assertTrue(reloaded.load(tempDir.toFile(), "config.yml", false));
        assertEquals(2, reloaded.getInt("alpha", 0));
        assertEquals("manual", reloaded.getString("beta", ""));
        assertEquals("added", reloaded.getString("external", ""));
    }

    @Test
    void saveTracksNestedSectionMutationsByAbsolutePath() throws IOException {
        Path file = tempDir.resolve("nested.yml");
        Files.writeString(file, """
            arenas:
              one:
                name: old
              two:
                enabled: true
            """);

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "nested.yml", true));

        ConfigSection arenaOne = config.getSection("arenas.one", false);
        assertNotNull(arenaOne);

        Files.writeString(file, """
            arenas:
              one:
                name: old
              two:
                enabled: false
                note: manual
            """);

        arenaOne.set("name", "new");
        arenaOne.save();

        ConfigFileImpl reloaded = new ConfigFileImpl();
        assertTrue(reloaded.load(tempDir.toFile(), "nested.yml", false));
        assertEquals("new", reloaded.getString("arenas.one.name", ""));
        assertFalse(reloaded.getBoolean("arenas.two.enabled", true));
        assertEquals("manual", reloaded.getString("arenas.two.note", ""));
    }

    @Test
    void savePreservesManualChangesWhenRemovingAnotherPath() throws IOException {
        Path file = tempDir.resolve("remove.yml");
        Files.writeString(file, """
            keep: before
            remove: value
            """);

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "remove.yml", true));

        Files.writeString(file, """
            keep: manual
            remove: value
            """);

        config.remove("remove");
        config.save();

        ConfigFileImpl reloaded = new ConfigFileImpl();
        assertTrue(reloaded.load(tempDir.toFile(), "remove.yml", false));
        assertEquals("manual", reloaded.getString("keep", ""));
        assertFalse(reloaded.contains("remove"));
    }
}
