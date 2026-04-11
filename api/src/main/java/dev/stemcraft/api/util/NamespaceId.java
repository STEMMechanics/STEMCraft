/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.api.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Represents a namespaced identifier used throughout the STEMCraft API.
 *
 * A namespaced ID follows the format:
 *
 *     namespace:path
 *
 * where {namespace} identifies the owning plugin or system, and
 * {path} identifies a resource within that namespace.
 * Hierarchy
 * The {path} portion may be hierarchical using forward slashes ({/})
 * to represent sub-resources:
 *
 *     bridge:fortress
 *     bridge:fortress/lobby
 *     stemcraft:minigames/bedwars/arena-1
 *
 * Allowed Characters
 * Both the namespace and path must use lowercase characters and may contain:
 *   - letters {a-z}
 *   - numbers {0-9}
 *   - underscore ({_})
 *   - hyphen ({-})
 *   - period ({.})
 *   - forward slash ({/}) in the path only
 *
 * Design Notes
 *   - Commas, spaces, and special characters are not permitted
 *   - The format is intentionally similar to Minecraft and Bukkit resource locations
 *   - This class is API-safe and does not depend on Bukkit's {NamespacedKey}
 *
 * Namespaced IDs are commonly used to identify regions, listeners, commands,
 * holograms, and other extensible resources.
 */
public final class NamespaceId {
    private static final Pattern PATTERN =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-/]+");

    /**
     * Validates if the given string is a valid namespaced ID.
     *
     * @param id The namespaced ID string to validate.
     * @return True if the ID is valid, false otherwise.
     */
    public static boolean isValid(String id) {
        return id != null && PATTERN.matcher(id).matches();
    }

    /**
     * Checks if the given string is a valid namespaced ID and throws an exception if not.
     *
     * @param id The namespaced ID string to validate.
     * @throws IllegalArgumentException if the ID is not valid.
     */
    public static void checkValid(String id) {
        if (!isValid(id)) {
            throw new IllegalArgumentException("Invalid namespaced ID: " + id);
        }
    }

    /**
     * Creates a namespaced ID from the given namespace and key.
     *
     * @param namespace The namespace portion.
     * @param key The path portion.
     * @return The combined namespaced ID.
     */
    public static String of(String namespace, String key) {
        return namespace + ":" + key;
    }

    /**
     * Normalizes the given namespaced ID to lowercase.
     *
     * @param id The namespaced ID to normalize.
     * @return The normalized namespaced ID.
     */
    public static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    /**
     * Sanitizes a path fragment so it is safe to use in a namespaced ID path.
     * Invalid characters are replaced with underscores and the result is lowercased.
     *
     * @param path The raw path fragment.
     * @return A safe path fragment.
     */
    public static String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            return "id";
        }

        String sanitized = path.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.-/]+", "_")
            .replaceAll("_{2,}", "_")
            .replaceAll("/+", "/");

        sanitized = sanitized.replaceAll("^[/_.-]+", "");
        sanitized = sanitized.replaceAll("[/_.-]+$", "");
        return sanitized.isEmpty() ? "id" : sanitized;
    }

    /**
     * Extracts the namespace from the given namespaced ID.
     *
     * @param id The namespaced ID.
     * @return The namespace portion.
     */
    public static String getNamespace(String id) {
        if (!isValid(id)) {
            throw new IllegalArgumentException("Invalid namespaced ID: " + id);
        }
        return id.split(":", 2)[0];
    }

    /**
     * Extracts the path from the given namespaced ID.
     *
     * @param id The namespaced ID.
     * @return The path portion.
     */
    public static String getPath(String id) {
        if (!isValid(id)) {
            throw new IllegalArgumentException("Invalid namespaced ID: " + id);
        }
        return id.split(":", 2)[1];
    }

    /**
     * Extracts the path segments from the given namespaced ID.
     *
     * @param id The namespaced ID.
     * @return An array of path segments.
     */
    public static String[] getPathSegments(String id) {
        String path = getPath(id);
        return path.split("/");
    }
}
