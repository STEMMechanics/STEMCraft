package dev.stemcraft.feature;

import dev.stemcraft.config.ConfigFileImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomCommandsTest {
    @TempDir
    Path tempDir;

    @Test
    void writeEntryOmitsDefaultPermissionButReadEntryRestoresIt() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "custom-commands.yml", true));

        CustomCommands.writeEntry(config, new CustomCommands.CustomCommandEntry(
                "survival",
                "survival",
                CustomCommands.defaultPermission("survival"),
                false,
                List.of("tpworld survival")
        ));

        assertTrue(config.isSection("survival"));
        assertEquals("survival", config.getString("survival.command", ""));
        assertFalse(config.contains("survival.permission"));
        assertEquals(List.of("tpworld survival"), config.getStringList("survival.run"));

        CustomCommands.CustomCommandEntry entry = CustomCommands.readEntry("survival", config.getSection("survival", false));
        assertEquals("survival", entry.label());
        assertEquals(CustomCommands.defaultPermission("survival"), entry.permission());
        assertFalse(entry.explicitPermission());
    }

    @Test
    void readEntryAcceptsSingleStringRunAndExplicitPermission() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "custom-commands-string-run.yml", true));

        config.set("spawn.command", "spawn");
        config.set("spawn.permission", "stemcraft.command.spawn");
        config.set("spawn.run", "/tpworldspawn world");

        CustomCommands.CustomCommandEntry entry = CustomCommands.readEntry("spawn", config.getSection("spawn", false));

        assertEquals("spawn", entry.label());
        assertEquals("stemcraft.command.spawn", entry.permission());
        assertTrue(entry.explicitPermission());
        assertEquals(List.of("tpworldspawn world"), entry.runCommands());
    }

    @Test
    void readEntryPreservesExplicitPublicPermission() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "custom-commands-public.yml", true));

        config.set("nightfall.command", "nightfall");
        config.set("nightfall.permission", "");
        config.set("nightfall.run", List.of("tpworld nightfall"));

        CustomCommands.CustomCommandEntry entry = CustomCommands.readEntry("nightfall", config.getSection("nightfall", false));

        assertEquals("nightfall", entry.label());
        assertTrue(entry.explicitPermission());
        assertEquals("", entry.permission());
        assertEquals(List.of("tpworld nightfall"), entry.runCommands());
    }

    @Test
    void writeEntryPreservesPublicCommandWithNoConfiguredCommands() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "custom-commands-empty.yml", true));

        CustomCommands.writeEntry(config, new CustomCommands.CustomCommandEntry(
                "nightfall",
                "nightfall",
                "",
                true,
                List.of()
        ));

        assertTrue(config.isSection("nightfall"));
        assertTrue(config.contains("nightfall.permission"));
        assertEquals("", config.getString("nightfall.permission", "missing"));
        assertEquals(List.of(), config.getStringList("nightfall.run"));

        CustomCommands.CustomCommandEntry entry = CustomCommands.readEntry("nightfall", config.getSection("nightfall", false));
        assertEquals("nightfall", entry.label());
        assertTrue(entry.explicitPermission());
        assertEquals("", entry.permission());
        assertEquals(List.of(), entry.runCommands());
    }

    @Test
    void normalizeHelpersStripLeadingSlashes() {
        assertEquals("survival", CustomCommands.normalizeConfigId(" /Survival "));
        assertEquals("survival", CustomCommands.normalizeLabel("//Survival"));
        assertEquals("tpworld survival", CustomCommands.normalizeRunCommand(" //tpworld survival "));
    }
}
