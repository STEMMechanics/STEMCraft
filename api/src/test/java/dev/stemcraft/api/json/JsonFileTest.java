package dev.stemcraft.api.json;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFileTest {
    @TempDir
    Path tempDir;

    @Test
    void loadSaveSetRemoveAndAppendRoundTrip() throws IOException {
        JsonFile file = new JsonFile(tempDir.resolve("nested/config.json"));

        file.load()
            .set("/name", "stemcraft")
            .set("/count", 5)
            .set("/enabled", true)
            .appendMap("/items", Map.of("id", "one"))
            .appendObject("/items", JsonNodeFactory.instance.objectNode().put("id", "two"))
            .save();

        JsonFile reloaded = new JsonFile(tempDir.resolve("nested/config.json")).load();
        assertEquals("stemcraft", reloaded.getString("/name", ""));
        assertEquals(5, reloaded.getInt("/count", 0));
        assertTrue(reloaded.getBoolean("/enabled", false));
        assertEquals(2, reloaded.root().withArray("items").size());

        reloaded.remove("/name").save();
        JsonFile afterRemove = new JsonFile(tempDir.resolve("nested/config.json")).load();
        assertEquals("missing", afterRemove.getString("/name", "missing"));
    }

    @Test
    void loadMissingFileStartsWithEmptyRoot() throws IOException {
        JsonFile file = new JsonFile(tempDir.resolve("missing.json")).load();

        assertFalse(Files.exists(file.path()));
        assertTrue(file.root().isObject());
        assertEquals("", file.getString("/name", ""));
    }

    @Test
    void loadRejectsNonObjectJson() throws IOException {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "[1,2,3]");

        assertThrows(IOException.class, () -> new JsonFile(file).load());
    }

    @Test
    void appendRejectsNonArrayTargets() throws IOException {
        JsonFile file = new JsonFile(tempDir.resolve("config.json")).load();
        file.set("/items", "not-an-array");

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> file.appendMap("/items", Map.of("id", "value"))
        );
        assertEquals("Target is not an array: /items", error.getMessage());
    }

    @Test
    void setRejectsPointersWhoseParentIsNotAnObject() throws IOException {
        JsonFile file = new JsonFile(tempDir.resolve("config.json")).load();
        file.set("/items", JsonNodeFactory.instance.arrayNode());

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> file.set("/items/name", "value")
        );
        assertEquals("Expected object at 'items': /items", error.getMessage());
    }
}
