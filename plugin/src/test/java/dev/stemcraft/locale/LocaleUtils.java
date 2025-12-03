package dev.stemcraft.locale;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LocaleUtils {

    private LocaleUtils() {}

    public static Set<String> loadLocaleKeys(String resourcePath) {
        Yaml yaml = new Yaml();
        try (InputStream is = LocaleUtils.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Locale file not found: " + resourcePath);
            }

            Object root = yaml.load(is);
            Set<String> keys = new HashSet<>();
            collectKeys(root, keys);
            return keys;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load locale yaml", e);
        }
    }

    private static void collectKeys(Object node, Set<String> output) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                Object value = entry.getValue();

                if (value instanceof String) {
                    // Only count leaf keys that look like UPPER_UNDERSCORE
                    if (key.matches("[A-Z_]+")) {
                        output.add(key);
                    }
                } else {
                    collectKeys(value, output);
                }
            }
        } else if (node instanceof Iterable<?> it) {
            for (Object value : it) {
                collectKeys(value, output);
            }
        }
    }
}