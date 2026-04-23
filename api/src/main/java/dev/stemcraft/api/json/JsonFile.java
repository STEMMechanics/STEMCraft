package dev.stemcraft.api.json;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JsonFile {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path file;
    private ObjectNode root;

    public JsonFile(Path file) {
        this.file = file;
        this.root = MAPPER.createObjectNode();
    }

    public JsonFile(File parent, String name) {
        this(parent.toPath().resolve(name));
    }

    public Path path() { return file; }

    public ObjectNode root() { return root; }

    public JsonFile load() throws IOException {
        if (!Files.exists(file)) {
            this.root = MAPPER.createObjectNode();
            return this;
        }
        JsonNode n = MAPPER.readTree(file.toFile());
        if (n == null || n.isNull()) n = MAPPER.createObjectNode();
        if (!n.isObject()) throw new IOException("Expected JSON object: " + file);
        this.root = (ObjectNode) n;
        return this;
    }

    public JsonFile save() throws IOException {
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), root);
        return this;
    }

    // ---------- getters ----------
    public String getString(String ptr, String def) {
        JsonNode n = root.at(JsonPointer.compile(ptr));
        return (n.isTextual()) ? n.asText() : def;
    }

    public int getInt(String ptr, int def) {
        JsonNode n = root.at(JsonPointer.compile(ptr));
        return (n.isInt() || n.isLong() || n.isNumber()) ? n.asInt() : def;
    }

    public boolean getBoolean(String ptr, boolean def) {
        JsonNode n = root.at(JsonPointer.compile(ptr));
        return n.isBoolean() ? n.asBoolean() : def;
    }

    // ---------- setters ----------
    public JsonFile set(String ptr, String value) { setNode(ptr, TextNode.valueOf(value)); return this; }
    public JsonFile set(String ptr, int value) { setNode(ptr, IntNode.valueOf(value)); return this; }
    public JsonFile set(String ptr, boolean value) { setNode(ptr, BooleanNode.valueOf(value)); return this; }
    public JsonFile set(String ptr, JsonNode value) { setNode(ptr, value); return this; }

    public JsonFile remove(String ptr) {
        JsonPointer p = JsonPointer.compile(ptr);
        ObjectNode parent = ensureParentObject(p);
        parent.remove(unescape(p.last().toString().substring(1)));
        return this;
    }

    public JsonFile appendMap(String arrayPtr, Map<String, ?> map) {
        ArrayNode arr = ensureArray(arrayPtr);
        arr.add(MAPPER.valueToTree(map));
        return this;
    }

    public JsonFile appendObject(String arrayPtr, ObjectNode obj) {
        ArrayNode arr = ensureArray(arrayPtr);
        arr.add(obj);
        return this;
    }

    // ---------- internals ----------
    private void setNode(String ptr, JsonNode value) {
        JsonPointer p = JsonPointer.compile(ptr);
        ObjectNode parent = ensureParentObject(p);
        parent.set(unescape(p.last().toString().substring(1)), value);
    }

    private ObjectNode ensureParentObject(JsonPointer fullPtr) {
        JsonPointer parentPtr = fullPtr.head();
        JsonNode parent = ensureObjectPath(parentPtr);
        if (!(parent instanceof ObjectNode obj)) {
            throw new IllegalArgumentException("Pointer parent is not an object: " + fullPtr);
        }
        return obj;
    }

    private ArrayNode ensureArray(String ptr) {
        JsonPointer p = JsonPointer.compile(ptr);
        ObjectNode parent = ensureParentObject(p);
        String field = unescape(p.last().toString().substring(1));

        JsonNode existing = parent.get(field);
        if (existing == null || existing.isNull()) {
            ArrayNode created = MAPPER.createArrayNode();
            parent.set(field, created);
            return created;
        }
        if (!existing.isArray()) {
            throw new IllegalArgumentException("Target is not an array: " + ptr);
        }
        return (ArrayNode) existing;
    }

    private JsonNode ensureObjectPath(JsonPointer ptr) {
        if (ptr == null || ptr.matches()) return root;

        JsonNode current = root;
        JsonPointer p = ptr;

        while (!p.matches()) {
            if (!(current instanceof ObjectNode obj)) {
                throw new IllegalArgumentException("Non-object encountered while creating path: " + ptr);
            }

            String token = unescape(p.getMatchingProperty());
            JsonNode next = obj.get(token);

            if (next == null || next.isNull()) {
                ObjectNode created = MAPPER.createObjectNode();
                obj.set(token, created);
                next = created;
            } else if (!next.isObject()) {
                throw new IllegalArgumentException("Expected object at '" + token + "': " + ptr);
            }

            current = next;
            p = p.tail();
        }
        return current;
    }

    private static String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }
}