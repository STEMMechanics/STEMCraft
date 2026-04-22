package dev.stemcraft.api.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility for safely extracting typed values from Map-shaped data.
 * <p>
 * Designed for deserialisation of YAML, JSON, or Bukkit-serialised structures
 * where values are provided as {@code Map<String, Object>} and runtime type
 * safety is required.
 * <p>
 * All failures result in {@link IllegalArgumentException} with a precise
 * logical path to aid debugging.
 */
public final class MapParse {

    private MapParse() {}

    /**
     * Ensures the value is a {@code Map<String, Object>} with string keys.
     *
     * @param value the value to inspect.
     * @param path logical path for error reporting.
     * @return the validated map.
     * @throws IllegalArgumentException if the value is not a map or has non-string keys.
     */
    public static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> m)) {
            throw typeError(path, "map", value);
        }
        for (Object k : m.keySet()) {
            if (!(k instanceof String)) {
                throw new IllegalArgumentException(
                        "Expected string keys at " + path + ", got key: " + safeType(k)
                );
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) m;
        return out;
    }

    /**
     * Ensures the value is a list.
     *
     * @param value the value to inspect.
     * @param path logical path for error reporting.
     * @return the list, or an empty list if {@code value} is null.
     * @throws IllegalArgumentException if the value is not a list.
     */
    public static List<?> list(Object value, String path) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> l)) {
            throw typeError(path, "list", value);
        }
        return l;
    }

    /**
     * Ensures the value is a list of {@code Map<String, Object>}.
     *
     * @param value the value to inspect.
     * @param path logical path for error reporting.
     * @return a list of validated maps.
     * @throws IllegalArgumentException if any element is not a map.
     */
    public static List<Map<String, Object>> listOfMaps(Object value, String path) {
        List<?> l = list(value, path);
        List<Map<String, Object>> out = new ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) {
            out.add(map(l.get(i), path + "[" + i + "]"));
        }
        return out;
    }

    /**
     * Reads a string value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the string value, or null if not present.
     * @throws IllegalArgumentException if the value exists but is not a string.
     */
    public static String string(Map<String, Object> map, String key, String path) {
        Object v = map == null ? null : map.get(key);
        if (v == null) return null;
        if (v instanceof String s) return s;
        throw typeError(pathKey(path, key), "string", v);
    }

    /**
     * Reads an integer value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the integer value, or null if not present.
     * @throws IllegalArgumentException if the value exists but is not numeric.
     */
    public static Integer integer(Map<String, Object> map, String key, String path) {
        Object v = map == null ? null : map.get(key);
        return switch (v) {
            case null -> null;
            case Number n -> n.intValue();
            case String s -> parseIntString(s, pathKey(path, key));
            default -> throw typeError(pathKey(path, key), "number", v);
        };
    }

    /**
     * Reads a long value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the long value, or null if not present.
     * @throws IllegalArgumentException if the value exists but is not numeric.
     */
    public static Long longValue(Map<String, Object> map, String key, String path) {
        Object v = map == null ? null : map.get(key);
        return switch (v) {
            case null -> null;
            case Number n -> n.longValue();
            case String s -> parseLongString(s, pathKey(path, key));
            default -> throw typeError(pathKey(path, key), "number", v);
        };
    }

    /**
     * Reads a double value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the double value, or null if not present.
     * @throws IllegalArgumentException if the value exists but is not numeric.
     */
    public static Double doubleValue(Map<String, Object> map, String key, String path) {
        Object v = map == null ? null : map.get(key);
        return switch (v) {
            case null -> null;
            case Number n -> n.doubleValue();
            case String s -> parseDoubleString(s, pathKey(path, key));
            default -> throw typeError(pathKey(path, key), "number", v);
        };
    }

    /**
     * Reads a boolean value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the boolean value, or null if not present.
     * @throws IllegalArgumentException if the value exists but is not boolean-like.
     */
    public static Boolean bool(Map<String, Object> map, String key, String path) {
        Object v = map == null ? null : map.get(key);
        return switch (v) {
            case null -> null;
            case Boolean b -> b;
            case String s -> parseBoolString(s, pathKey(path, key));
            default -> throw typeError(pathKey(path, key), "boolean", v);
        };
    }

    /**
     * Reads a UUID value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the UUID value, or null if not present.
     * @throws IllegalArgumentException if the value exists but is not a valid UUID.
     */
    public static UUID uuid(Map<String, Object> map, String key, String path) {
        Object v = map == null ? null : map.get(key);
        switch (v) {
            case null -> {
                return null;
            }
            case UUID u -> {
                return u;
            }
            case String s -> {
                try {
                    return UUID.fromString(s);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Invalid UUID at " + pathKey(path, key) + ": " + s
                    );
                }
            }
            default -> {
            }
        }
        throw typeError(pathKey(path, key), "uuid", v);
    }

    /**
     * Reads a required string value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the string value.
     * @throws IllegalArgumentException if missing or not a string.
     */
    public static String requireString(Map<String, Object> map, String key, String path) {
        String s = string(map, key, path);
        if (s == null) throw missingError(pathKey(path, key));
        return s;
    }

    /**
     * Reads a required integer value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the integer value.
     * @throws IllegalArgumentException if missing or not numeric.
     */
    public static int requireInt(Map<String, Object> map, String key, String path) {
        Integer i = integer(map, key, path);
        if (i == null) throw missingError(pathKey(path, key));
        return i;
    }

    /**
     * Reads a required map value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the map value.
     * @throws IllegalArgumentException if missing or not a map.
     */
    public static Map<String, Object> requireMap(Map<String, Object> map, String key, String path) {
        Object v = map == null ? null : map.get(key);
        if (v == null) throw missingError(pathKey(path, key));
        return map(v, pathKey(path, key));
    }

    /**
     * Reads a required list value.
     *
     * @param map the map to read from.
     * @param key the key to read.
     * @param path logical path for error reporting.
     * @return the list value.
     * @throws IllegalArgumentException if missing or not a list.
     */
    public static List<?> requireList(Map<String, Object> map, String key, String path) {
        Object v = map == null ? null : map.get(key);
        if (v == null) throw missingError(pathKey(path, key));
        return list(v, pathKey(path, key));
    }

    /**
     * Constructs a missing value error.
     * @param path the logical path.
     * @return the exception.
     */
    private static IllegalArgumentException missingError(String path) {
        return new IllegalArgumentException("Missing value at " + path);
    }

    /**
     * Constructs a type error message.
     * @param path the logical path.
     * @param expected the expected type.
     * @param actual the actual value.
     * @return the exception.
     */
    private static IllegalArgumentException typeError(String path, String expected, Object actual) {
        return new IllegalArgumentException(
                "Expected " + expected + " at " + path + ", got " + safeType(actual)
        );
    }

    /**
     * Safe type name for an object.
     * @param o the object.
     * @return the type name or "null".
     */
    private static String safeType(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    /**
     * Constructs a path key.
     * @param path the base path.
     * @param key the key.
     * @return the combined path.
     */
    private static String pathKey(String path, String key) {
        if (path == null || path.isBlank()) return key;
        return path + "." + key;
    }

    /**
     * Parses an integer from a string.
     * @param s the string.
     * @param path the logical path.
     * @return the integer.
     */
    private static Integer parseIntString(String s, String path) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid int at " + path + ": " + s);
        }
    }

    /**
     * Parses a long from a string.
     * @param s the string.
     * @param path the logical path.
     * @return the long.
     */
    private static Long parseLongString(String s, String path) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long at " + path + ": " + s);
        }
    }

    /**
     * Parses a double from a string.
     * @param s the string.
     * @param path the logical path.
     * @return the double.
     */
    private static Double parseDoubleString(String s, String path) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double at " + path + ": " + s);
        }
    }

    /**
     * Parses a boolean from a string.
     * @param s the string.
     * @param path the logical path.
     * @return the boolean.
     */
    private static Boolean parseBoolString(String s, String path) {
        String t = s.trim().toLowerCase();
        return switch (t) {
            case "true", "yes", "y", "1", "on" -> true;
            case "false", "no", "n", "0", "off" -> false;
            default -> throw new IllegalArgumentException(
                    "Invalid boolean at " + path + ": " + s
            );
        };
    }
}