package dev.stemcraft.capability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HasMetaImplTest {

    @Test
    void getMapReturnsTypedMapWhenContentsMatch() {
        TestMeta meta = new TestMeta();
        Map<String, Integer> stored = new LinkedHashMap<>();
        stored.put("one", 1);
        meta.set("scores", stored);

        Map<String, Integer> result = meta.getMap("scores", String.class, Integer.class);

        assertSame(stored, result);
        assertEquals(1, result.get("one"));
    }

    @Test
    void getMapReturnsDefaultWhenMissing() {
        TestMeta meta = new TestMeta();
        Map<String, Integer> fallback = new LinkedHashMap<>();

        Map<String, Integer> result = meta.getMap("scores", String.class, Integer.class, fallback);

        assertSame(fallback, result);
        assertNull(meta.getMap("scores", String.class, Integer.class));
    }

    @Test
    void getMapThrowsWhenKeyTypeDoesNotMatch() {
        TestMeta meta = new TestMeta();
        Map<Object, Integer> stored = new LinkedHashMap<>();
        stored.put(1, 2);
        meta.set("scores", stored);

        assertThrows(IllegalStateException.class, () -> meta.getMap("scores", String.class, Integer.class));
    }

    @Test
    void getMapThrowsWhenValueTypeDoesNotMatch() {
        TestMeta meta = new TestMeta();
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("one", "two");
        meta.set("scores", stored);

        assertThrows(IllegalStateException.class, () -> meta.getMap("scores", String.class, Integer.class));
    }

    @Test
    void getListReturnsTypedListWhenContentsMatch() {
        TestMeta meta = new TestMeta();
        List<String> stored = new ArrayList<>(List.of("a", "b"));
        meta.set("letters", stored);

        List<String> result = meta.getList("letters", String.class);

        assertSame(stored, result);
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void getListThrowsWhenElementTypeDoesNotMatch() {
        TestMeta meta = new TestMeta();
        meta.set("letters", List.of("a", 2));

        assertThrows(IllegalStateException.class, () -> meta.getList("letters", String.class));
    }

    @Test
    void getOrCreateMapCreatesStoresAndReturnsTypedMap() {
        TestMeta meta = new TestMeta();

        Map<String, Integer> result = meta.getOrCreateMap("scores", String.class, Integer.class, LinkedHashMap::new);
        result.put("one", 1);

        assertSame(result, meta.getMap("scores", String.class, Integer.class));
        assertEquals(1, meta.getMap("scores", String.class, Integer.class).get("one"));
    }

    @Test
    void getOrCreateListCreatesStoresAndReturnsTypedList() {
        TestMeta meta = new TestMeta();

        List<String> result = meta.getOrCreateList("letters", String.class, ArrayList::new);
        result.add("a");

        assertSame(result, meta.getList("letters", String.class));
        assertEquals(List.of("a"), meta.getList("letters", String.class));
    }

    private static final class TestMeta extends HasMetaImpl<TestMeta> {
    }
}
