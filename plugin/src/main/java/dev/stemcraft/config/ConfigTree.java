/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.config;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Small, dependency-free snapshots and path operations used by ConfigFileImpl.
 *
 * <p>A Bukkit ConfigurationSection is a live, mutable view. Keeping an
 * ordinary tree snapshot gives ConfigFileImpl a stable baseline for its
 * three-way merge and prevents event payloads from changing underneath
 * listeners.</p>
 */
final class ConfigTree {
    static final Object MISSING = new Object();

    private ConfigTree() {}

    static Map<String, Object> snapshot(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                result.put(key, snapshot(child));
            } else {
                result.put(key, cloneValue(section.get(key)));
            }
        }

        return result;
    }

    static Object valueAt(Map<String, Object> root, String path) {
        String normalized = normalize(path);
        if (normalized.isEmpty()) return root;

        Object current = root;
        for (String part : normalized.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return MISSING;
            }
            current = map.get(part);
        }

        return current;
    }

    static void applyPath(Map<String, Object> root, String path, Object value) {
        String normalized = normalize(path);
        if (normalized.isEmpty()) {
            root.clear();
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        root.put(key, cloneValue(entry.getValue()));
                    }
                }
            }
            return;
        }

        String[] parts = normalized.split("\\.");
        Map<String, Object> current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            Object child = current.get(parts[index]);
            if (!(child instanceof Map<?, ?>)) {
                if (value == MISSING) return;
                Map<String, Object> replacement = new LinkedHashMap<>();
                current.put(parts[index], replacement);
                current = replacement;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) child;
                current = childMap;
            }
        }

        String leaf = parts[parts.length - 1];
        if (value == MISSING) current.remove(leaf);
        else current.put(leaf, cloneValue(value));
    }

    static Set<String> diffPaths(Object before, Object after) {
        Set<String> paths = new LinkedHashSet<>();
        diffPaths(before, after, "", paths);
        return paths;
    }

    private static void diffPaths(Object before, Object after, String path, Set<String> paths) {
        if (equal(before, after)) return;

        if (before instanceof Map<?, ?> beforeMap && after instanceof Map<?, ?> afterMap) {
            Set<String> keys = new LinkedHashSet<>();
            beforeMap.keySet().forEach(key -> {
                if (key instanceof String stringKey) keys.add(stringKey);
            });
            afterMap.keySet().forEach(key -> {
                if (key instanceof String stringKey) keys.add(stringKey);
            });

            for (String key : keys) {
                Object oldValue = beforeMap.containsKey(key) ? beforeMap.get(key) : MISSING;
                Object newValue = afterMap.containsKey(key) ? afterMap.get(key) : MISSING;
                diffPaths(oldValue, newValue, childPath(path, key), paths);
            }
            return;
        }

        paths.add(path);
    }

    static boolean overlaps(String left, String right) {
        String first = normalize(left);
        String second = normalize(right);
        return first.isEmpty() || second.isEmpty() || first.equals(second)
            || first.startsWith(second + ".") || second.startsWith(first + ".");
    }

    static Set<String> reducePaths(Collection<String> paths) {
        List<String> ordered = paths.stream()
            .map(ConfigTree::normalize)
            .distinct()
            .sorted((left, right) -> Integer.compare(left.length(), right.length()))
            .toList();

        Set<String> reduced = new LinkedHashSet<>();
        for (String candidate : ordered) {
            boolean covered = reduced.stream().anyMatch(existing -> overlaps(existing, candidate));
            if (!covered) reduced.add(candidate);
        }
        return reduced;
    }

    static boolean equal(Object left, Object right) {
        if (left == right) return true;
        if (left == MISSING || right == MISSING) return false;
        if (left instanceof Number first && right instanceof Number second) {
            try {
                return new BigDecimal(first.toString()).compareTo(new BigDecimal(second.toString())) == 0;
            } catch (NumberFormatException ignored) {
                return Double.compare(first.doubleValue(), second.doubleValue()) == 0;
            }
        }
        if (left instanceof Map<?, ?> firstMap && right instanceof Map<?, ?> secondMap) {
            if (firstMap.size() != secondMap.size()) return false;
            for (Map.Entry<?, ?> entry : firstMap.entrySet()) {
                if (!secondMap.containsKey(entry.getKey())
                    || !equal(entry.getValue(), secondMap.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof Collection<?> firstCollection && right instanceof Collection<?> secondCollection) {
            if (firstCollection.size() != secondCollection.size()) return false;
            var firstIterator = firstCollection.iterator();
            var secondIterator = secondCollection.iterator();
            while (firstIterator.hasNext() && secondIterator.hasNext()) {
                if (!equal(firstIterator.next(), secondIterator.next())) return false;
            }
            return true;
        }
        return Objects.equals(left, right);
    }

    static Object cloneValue(Object value) {
        if (value == MISSING || value == null) return value;

        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), cloneValue(entry.getValue()));
            }
            return copy;
        }

        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) copy.add(cloneValue(item));
            return copy;
        }

        return value;
    }

    static String normalize(String path) {
        if (path == null) return "";
        String value = path.trim();
        while (value.startsWith(".")) value = value.substring(1);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String childPath(String parent, String child) {
        return parent.isEmpty() ? child : parent + "." + child;
    }
}
