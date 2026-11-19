/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.api.service.config;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A validated change applied to a live configuration file.
 *
 * <p>Values are snapshots. Mutating a returned map or collection cannot alter
 * the active configuration.</p>
 */
public final class ConfigChangeEvent {
    private final String fileName;
    private final ConfigChangeOrigin origin;
    private final Set<String> paths;
    private final Map<String, Object> oldValues;
    private final Map<String, Object> newValues;
    private final String revision;

    public ConfigChangeEvent(@NotNull String fileName,
                             @NotNull ConfigChangeOrigin origin,
                             @NotNull Set<String> paths,
                             @NotNull Map<String, Object> oldValues,
                             @NotNull Map<String, Object> newValues,
                             @NotNull String revision) {
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.paths = Collections.unmodifiableSet(new LinkedHashSet<>(paths));
        this.oldValues = immutableValues(oldValues);
        this.newValues = immutableValues(newValues);
        this.revision = Objects.requireNonNullElse(revision, "");
    }

    public @NotNull String fileName() { return fileName; }
    public @NotNull ConfigChangeOrigin origin() { return origin; }
    public @NotNull Set<String> paths() { return paths; }
    public @NotNull Set<String> changedPaths() { return paths; }
    public @NotNull Map<String, Object> oldValues() { return oldValues; }
    public @NotNull Map<String, Object> newValues() { return newValues; }
    public @NotNull String revision() { return revision; }

    /**
     * Returns whether this event changes the supplied path or a parent/child
     * path. An empty path means the entire file.
     */
    public boolean affects(String path) {
        String candidate = normalize(path);
        return paths.stream().anyMatch(changed -> overlaps(changed, candidate));
    }

    public Object oldValue(String path) { return oldValues.get(normalize(path)); }
    public Object newValue(String path) { return newValues.get(normalize(path)); }

    private static Map<String, Object> immutableValues(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> copy.put(key, immutableValue(child)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Collection<?> collection) {
            return Collections.unmodifiableList(collection.stream().map(ConfigChangeEvent::immutableValue).toList());
        }
        return value;
    }

    private static String normalize(String path) {
        if (path == null) return "";
        String value = path.trim();
        while (value.startsWith(".")) value = value.substring(1);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static boolean overlaps(String left, String right) {
        return left.isEmpty() || right.isEmpty() || left.equals(right)
            || left.startsWith(right + ".") || right.startsWith(left + ".");
    }
}
