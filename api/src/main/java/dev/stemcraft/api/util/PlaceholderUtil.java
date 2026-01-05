package dev.stemcraft.api.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderUtil {

    /**
     * Replaces case-insensitive placeholders (e.g. <code>%key%</code>) using
     * alternating key/value arguments.
     */
    public static String apply(String text, String... values) {
        if (values == null || values.length == 0) {
            return text;
        }

        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }

        return apply(text, map);
    }

    public static String apply(String text, Map<String, String> values) {
        if (text == null || text.isEmpty() || values == null || values.isEmpty()) {
            return text;
        }

        String result = text;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String replacement = entry.getValue();

            if (key == null || key.isEmpty()) continue;
            if (replacement == null) replacement = "";

            if (StringUtil.isAllUpper(key)) {
                replacement = replacement.toUpperCase(Locale.ROOT);
            } else if (StringUtil.isTitleCase(key)) {
                replacement = StringUtil.toTitleCase(replacement);
            }

            String pattern = "(?i)\\{" + Pattern.quote(key) + "}";
            result = result.replaceAll(pattern, Matcher.quoteReplacement(replacement));
        }

        return result;
    }

    public static String apply(String text, Object... values) {
        if (values == null || values.length == 0) {
            return text;
        }

        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            String key = String.valueOf(values[i]);
            String value = String.valueOf(values[i + 1]);
            map.put(key, value);
        }

        return apply(text, map);
    }
}