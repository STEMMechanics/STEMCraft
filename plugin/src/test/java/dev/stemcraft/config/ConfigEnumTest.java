package dev.stemcraft.config;

import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigEnumTest {
    @TempDir Path tempDir;

    private enum Mode {
        SURVIVAL {
            @Override public String toString() { return "friendly survival"; }
        },
        CREATIVE
    }

    private ConfigFileImpl config() {
        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "config.yml", true));
        return config;
    }

    @Test
    void enumNamesRoundTripIncludingNestedContainers() throws Exception {
        ConfigFileImpl config = config();
        ConfigSection nested = config.getSection("player", true);
        nested.set("mode", Mode.SURVIVAL);
        nested.set("choices", List.of(Map.of("modes", new LinkedHashSet<>(List.of(Mode.CREATIVE, Mode.SURVIVAL)))));
        config.save();
        assertFalse(Files.readString(tempDir.resolve("config.yml")).contains("!!"));
        assertTrue(config.reload());
        assertEquals(Mode.SURVIVAL, config.getEnum("player.mode", Mode.class, Mode.CREATIVE));
        assertEquals("SURVIVAL", config.get("player.mode"));
        assertEquals(List.of(Map.of("modes", List.of("CREATIVE", "SURVIVAL"))), config.getList("player.choices"));
    }

    @Test
    void enumReadsHandleWhitespaceCaseInvalidValuesAndPathAliases() {
        ConfigFileImpl config = config();
        config.set("game-mode", "  cReAtIvE  ");
        config.set("invalid", "unknown");
        config.set("wrong-type", 42);
        config.save();
        assertEquals(Mode.CREATIVE, config.getEnum("game_mode", Mode.class, Mode.SURVIVAL));
        assertEquals(Mode.SURVIVAL, config.getEnum("invalid", Mode.class, Mode.SURVIVAL));
        assertEquals(Mode.SURVIVAL, config.getEnum("wrong-type", Mode.class, Mode.SURVIVAL));
        assertEquals("unknown", config.get("invalid"));
        assertFalse(config.isDirty());
    }

    @Test
    void defaultsUseNamesAndRespectSaveDefaults() {
        ConfigFileImpl config = config();
        assertEquals(Mode.SURVIVAL, config.getEnum("mode", Mode.class, Mode.SURVIVAL));
        config.getList("modes", List.of(Mode.CREATIVE));
        config.save();
        assertTrue(config.reload());
        assertEquals("SURVIVAL", config.get("mode"));
        assertEquals(List.of("CREATIVE"), config.getList("modes"));
        config.setSaveDefaults(false);
        assertEquals(Mode.CREATIVE, config.getEnum("missing", Mode.class, Mode.CREATIVE));
        assertFalse(config.contains("missing"));
        assertFalse(config.isDirty());
    }

    @Test
    void rejectsInvalidValuesBeforeMutationWithAbsolutePath() {
        ConfigFileImpl config = config();
        ConfigSection nested = config.getSection("player", true);
        nested.set("value", "original");
        config.save();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> nested.set("value", List.of(Map.of("bad", new Object()))));
        assertTrue(error.getMessage().contains("player.value[0].bad"));
        assertTrue(error.getMessage().contains("java.lang.Object"));
        assertEquals("original", nested.get("value"));
        assertFalse(config.isDirty());
        assertThrows(IllegalArgumentException.class, () -> config.set("bad", Map.of(1, "value")));
        assertThrows(IllegalArgumentException.class, () -> config.getList("bad-default", List.of(new Object())));
        assertFalse(config.contains("bad-default"));
        assertFalse(config.isDirty());
    }

    @Test
    void rejectsCyclesButAllowsSharedContainersAndCopiesInput() {
        ConfigFileImpl config = config();
        List<Object> cycle = new ArrayList<>();
        cycle.add(cycle);
        assertThrows(IllegalArgumentException.class, () -> config.set("cycle", cycle));
        List<Object> shared = new ArrayList<>(List.of(Mode.SURVIVAL));
        config.set("shared", List.of(shared, shared));
        shared.clear();
        assertEquals(List.of(List.of("SURVIVAL"), List.of("SURVIVAL")), config.getList("shared"));
    }

    @Test
    void supportedScalarsAndNullRemovalRoundTrip() {
        ConfigFileImpl config = config();
        config.set("values", List.of("text", true, 12, 13L, 1.5D));
        config.set("remove", Mode.CREATIVE);
        config.set("remove", null);
        config.save();
        assertTrue(config.reload());
        assertEquals(List.of("text", true, 12, 13, 1.5D), config.getList("values"));
        assertFalse(config.contains("remove"));
    }

    @Test
    void registeredSerializableStillRoundTrips() {
        ConfigurationSerialization.registerClass(SavedValue.class);
        try {
            ConfigFileImpl config = config();
            config.set("custom", new SavedValue("hello"));
            config.save();
            assertTrue(config.reload());
            assertEquals("hello", assertInstanceOf(SavedValue.class, config.get("custom")).value);
        } finally {
            ConfigurationSerialization.unregisterClass(SavedValue.class);
        }
    }

    public static class SavedValue implements ConfigurationSerializable {
        private final String value;
        public SavedValue(String value) { this.value = value; }
        @Override public Map<String, Object> serialize() { return Map.of("value", value); }
        public static SavedValue deserialize(Map<String, Object> values) {
            return new SavedValue((String) values.get("value"));
        }
    }
}
