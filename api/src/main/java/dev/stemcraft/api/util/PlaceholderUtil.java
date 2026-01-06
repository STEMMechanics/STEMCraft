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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for applying placeholders in strings.
 */
public final class PlaceholderUtil {

    /**
     * Replaces case-insensitive placeholders (e.g. <code>%key%</code>) using
     * alternating key/value arguments.
     *
     * @param text The text containing placeholders.
     * @param values Alternating keys and values.
     * @return The text with placeholders replaced.
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