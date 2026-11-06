package dev.stemcraft.config;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.config.ConfigChangeOrigin;
import dev.stemcraft.api.service.config.ConfigSaveResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    @Test
    void conflictingSamePathChangeIsRejectedWithoutWriting() throws IOException {
        Path file = tempDir.resolve("conflict.yml");
        Files.writeString(file, "max-players: 10\nother: before\n");

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "conflict.yml", false));
        config.set("max-players", 20);
        Files.writeString(file, "max-players: 30\nother: external\n");

        ConfigSaveResult result = config.saveChecked();

        assertTrue(result.hasConflicts());
        assertTrue(result.conflictingPaths().contains("max-players"));
        assertFalse(result.success());
        assertEquals("max-players: 30\nother: external\n", Files.readString(file));
        assertEquals(20, config.getInt("max-players", 0));
    }

    @Test
    void identicalConcurrentChangeIsAccepted() throws IOException {
        Path file = tempDir.resolve("identical.yml");
        Files.writeString(file, "max-players: 10\nother: before\n");

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "identical.yml", false));
        config.set("max-players", 20);
        Files.writeString(file, "max-players: 20\nother: external\n");

        ConfigSaveResult result = config.saveChecked();

        assertTrue(result.success());
        ConfigFileImpl reloaded = new ConfigFileImpl();
        assertTrue(reloaded.load(tempDir.toFile(), "identical.yml", false));
        assertEquals(20, reloaded.getInt("max-players", 0));
        assertEquals("external", reloaded.getString("other", ""));
    }

    @Test
    void parentAndChildChangesConflict() throws IOException {
        Path file = tempDir.resolve("parent-child.yml");
        Files.writeString(file, "settings:\n  enabled: true\n  limit: 10\n");

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "parent-child.yml", false));
        config.set("settings", java.util.Map.of("enabled", false, "limit", 20));
        Files.writeString(file, "settings:\n  enabled: true\n  limit: 30\n");

        ConfigSaveResult result = config.saveChecked();

        assertTrue(result.hasConflicts());
        assertTrue(result.conflictingPaths().contains("settings"));
        ConfigFileImpl reloaded = new ConfigFileImpl();
        assertTrue(reloaded.load(tempDir.toFile(), "parent-child.yml", false));
        assertEquals(30, reloaded.getInt("settings.limit", 0));
    }

    @Test
    void externalDeletionAndUnrelatedLocalAdditionMerge() throws IOException {
        Path file = tempDir.resolve("deletion.yml");
        Files.writeString(file, "remove: old\nkeep: true\n");

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "deletion.yml", false));
        config.set("added", "local");
        Files.writeString(file, "keep: true\n");

        ConfigSaveResult result = config.saveChecked();

        assertTrue(result.success());
        ConfigFileImpl reloaded = new ConfigFileImpl();
        assertTrue(reloaded.load(tempDir.toFile(), "deletion.yml", false));
        assertFalse(reloaded.contains("remove"));
        assertEquals("local", reloaded.getString("added", ""));
    }

    @Test
    void invalidExternalYamlKeepsActiveConfiguration() throws IOException {
        Path file = tempDir.resolve("invalid.yml");
        Files.writeString(file, "value: valid\n");

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "invalid.yml", false));
        List<String> events = new ArrayList<>();
        config.onChange(event -> events.add(event.origin().name()));

        Files.writeString(file, "value: [invalid\n");

        assertFalse(config.reconcileExternal(true));
        assertEquals("valid", config.getString("value", ""));
        assertTrue(events.isEmpty());
    }

    @Test
    void changeEventsReportOriginsAndDoNotDuplicateUnchangedValues() throws IOException {
        Path file = tempDir.resolve("events.yml");
        Files.writeString(file, "value: one\n");

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "events.yml", false));
        List<ConfigChangeOrigin> origins = new ArrayList<>();
        config.onChange("value", event -> origins.add(event.origin()));

        config.set("value", "two");
        config.set("value", "two");
        assertEquals(List.of(ConfigChangeOrigin.INTERNAL), origins);
        assertTrue(config.saveChecked().success());

        Files.writeString(file, "value: three\n");
        assertTrue(config.reconcileExternal(true));
        assertEquals(List.of(ConfigChangeOrigin.INTERNAL, ConfigChangeOrigin.EXTERNAL), origins);

        Files.writeString(file, "value: four\n");
        assertTrue(config.reload());
        assertEquals(List.of(ConfigChangeOrigin.INTERNAL, ConfigChangeOrigin.EXTERNAL, ConfigChangeOrigin.RELOAD), origins);
    }

    @Test
    void editorYamlSaveMergesUnrelatedExternalChangesUsingItsRevision() throws IOException {
        Path file = tempDir.resolve("editor-merge.yml");
        Files.writeString(file, "alpha: 1\nbeta: before\n");

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "editor-merge.yml", false));
        String revision = config.revision();
        Files.writeString(file, "alpha: 1\nbeta: manual\n");

        ConfigSaveResult result = config.saveYaml("alpha: 2\nbeta: before\n", revision);

        assertTrue(result.success());
        ConfigFileImpl reloaded = new ConfigFileImpl();
        assertTrue(reloaded.load(tempDir.toFile(), "editor-merge.yml", false));
        assertEquals(2, reloaded.getInt("alpha", 0));
        assertEquals("manual", reloaded.getString("beta", ""));
    }

    @Test
    void editorYamlSaveRejectsAConflictingRevision() throws IOException {
        Path file = tempDir.resolve("editor-conflict.yml");
        Files.writeString(file, "alpha: 1\nbeta: before\n");

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "editor-conflict.yml", false));
        String revision = config.revision();
        Files.writeString(file, "alpha: 3\nbeta: before\n");

        ConfigSaveResult result = config.saveYaml("alpha: 2\nbeta: before\n", revision);

        assertTrue(result.hasConflicts());
        ConfigFileImpl reloaded = new ConfigFileImpl();
        assertTrue(reloaded.load(tempDir.toFile(), "editor-conflict.yml", false));
        assertEquals(3, reloaded.getInt("alpha", 0));
    }
}
