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

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for string manipulation and formatting.
 */
public final class StringUtil {
    private static final Map<String, String> IRREGULAR = Map.ofEntries(
            Map.entry("child", "children"),
            Map.entry("person", "people"),
            Map.entry("man", "men"),
            Map.entry("woman", "women"),
            Map.entry("mouse", "mice"),
            Map.entry("goose", "geese"),
            Map.entry("tooth", "teeth"),
            Map.entry("foot", "feet")
    );

    /**
     * Checks if a string is entirely in uppercase.
     *
     * @param s the string to check.
     * @return true if the string is all uppercase, false otherwise.
     */
    public static boolean isAllUpper(String s) {
        return s.equals(s.toUpperCase(Locale.ROOT));
    }

    /**
     * Checks if a string is in title case.
     *
     * @param s the string to check.
     * @return true if the string is in title case, false otherwise.
     */
    public static boolean isTitleCase(String s) {
        return !s.isEmpty() &&
                Character.isUpperCase(s.charAt(0)) &&
                s.substring(1).equals(s.substring(1).toLowerCase(Locale.ROOT));
    }

    /**
     * Converts a string into title case.
     *
     * @param s the string to convert.
     * @return the string in title case.
     */
    public static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Converts a string into a slug suitable for filenames or identifiers.
     * Lowercases the string, replaces spaces with underscores, and removes
     * special characters.
     *
     * @param input the input string.
     * @return the slugified string.
     */
    public static String slugify(String input) {
        if (input == null) return null;
        return input.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s_]", "") // Remove invalid chars
                .replaceAll("\\s+", "_")         // Replace whitespace with hyphens
                .replaceAll("_{2,}", "_");
    }


    /**
     * Convert a camelCase or PascalCase string to snake_case.
     *
     * @param string The string to convert.
     * @return The converted string.
     */
    public static String toSnakeCase(String string) {
        return string
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    /**
     * Convert a list of objects to strings (or null).
     *
     * @param placeholders the objects to convert.
     * @return array of strings.
     */
    public static String[] toStrings(Object... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return new String[0];
        }

        String[] out = new String[placeholders.length];
        for (int i = 0; i < placeholders.length; i++) {
            Object o = placeholders[i];
            out[i] = (o == null ? "null" : String.valueOf(o));
        }
        return out;
    }

    /**
     * Capitalizes the first letter of each word in a string.
     *
     * @param str The input string.
     * @param ignoreColors Whether to ignore color codes (e.g., &a, &b).
     * @return The capitalized string.
     */
    public static String capitalize(String str, Boolean ignoreColors) {
        if (str != null && !str.isEmpty()) {
            final int strLen = str.length();
            final StringBuilder buffer = new StringBuilder(strLen);
            boolean capitalizeNext = true;

            for (int i = 0; i < strLen; ++i) {
                final char ch = str.charAt(i);

                if (Character.isWhitespace(ch)) {
                    buffer.append(ch);

                    capitalizeNext = true;
                } else if (ch == '&' && ignoreColors && i + 1 < strLen
                        && "0123456789abcdefklmnor".indexOf(str.charAt(i + 1)) != -1) {
                    buffer.append(ch).append(str.charAt(i + 1));
                    i++;

                } else if (capitalizeNext) {
                    buffer.append(Character.toTitleCase(ch));

                    capitalizeNext = false;
                } else {
                    buffer.append(ch);
                }
            }

            return buffer.toString();
        }

        return str;
    }

    public static String capitalize(String str) {
        return capitalize(str, false);
    }

    /**
     * Beautifies a string by replacing underscores with spaces and converting to lowercase.
     *
     * @param str The input string.
     * @return The beautified string.
     */
    public static String beautify(String str) {
        return str.toLowerCase().replace("_", " ");
    }

    /**
     * Converts a camelCase string to snake_case.
     *
     * @param s The camelCase string.
     * @return The snake_case string.
     */
    public static String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Converts a camelCase string to kebab-case.
     *
     * @param s The camelCase string.
     * @return The kebab-case string.
     */
    public static String camelToKebab(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * Converts a string to its plural form.
     * <p>
     * Rules:
     * - If the string already appears plural, it is returned unchanged
     * - If multiple words exist, only the last word is pluralised
     * - Handles common English irregulars and suffix rules
     * - Preserves original casing
     * <p>
     * Examples:
     * - "world" -> "worlds"
     * - "worlds" -> "worlds"
     * - "game world" -> "game worlds"
     * - "City" -> "Cities".
     *
     * @param text input text.
     * @return pluralised form, or original text if unchanged.
     */
    public static String toPlural(String text) {
        if (text == null || text.isBlank()) return text;

        int s = text.lastIndexOf(' ');
        int d = text.lastIndexOf('-');
        int u = text.lastIndexOf('_');
        int i = Math.max(s, Math.max(d, u));

        if (i >= 0 && i < text.length() - 1) {
            String prefix = text.substring(0, i + 1);
            String tail = text.substring(i + 1);
            return prefix + pluraliseWord(tail);
        }

        return pluraliseWord(text);
    }

    /**
     * Parses a boolean value from a string.
     * Accepts "true", "yes", "1" (case insensitive) as true.
     * All other values (including null) are false.
     *
     * @param value input string.
     * @return parsed boolean.
     */
    public static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.equals("true") || trimmed.equals("yes") || trimmed.equals("1");
    }

    /**
     * Joins a list of Components into a single string, stripping color codes.
     *
     * @param components list of Components.
     * @param separator separator string.
     * @return joined string.
     */
    public static String joinPlainText(List<Component> components, String separator) {
        if (components == null || components.isEmpty()) {
            return "";
        }
        return components.stream()
                .map(TextUtil::stripColour)
                .collect(Collectors.joining(separator));
    }

    /**
     * Pluralises a single word.
     * If the word already appears plural, it is returned unchanged.
     *
     * @param word single word.
     * @return pluralised word.
     */
    private static String pluraliseWord(String word) {
        if (word.isEmpty()) return word;

        String lower = word.toLowerCase(Locale.ROOT);

        // Already plural (cheap heuristic)
        if (isLikelyPlural(lower)) {
            return word;
        }

        // Irregulars
        if (IRREGULAR.containsKey(lower)) {
            return matchCase(word, IRREGULAR.get(lower));
        }

        // y -> ies (consonant+y)
        if (lower.endsWith("y") && lower.length() > 1
                && !isVowel(lower.charAt(lower.length() - 2))) {
            return word.substring(0, word.length() - 1) + "ies";
        }

        // s, x, z, ch, sh -> es
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("ch") || lower.endsWith("sh")) {
            return word + "es";
        }

        // f / fe -> ves
        if (lower.endsWith("fe")) {
            return word.substring(0, word.length() - 2) + "ves";
        }
        if (lower.endsWith("f")) {
            return word.substring(0, word.length() - 1) + "ves";
        }

        return word + "s";
    }

    /**
     * Heuristic check to determine if a word is already plural.
     * Avoids double-pluralisation like "worlds" -> "worldss".
     *
     * @param lower lowercase word.
     * @return true if the word likely represents a plural form.
     */
    private static boolean isLikelyPlural(String lower) {
        return lower.endsWith("s")
                || IRREGULAR.containsValue(lower);
    }

    /**
     * Checks whether a character is a vowel.
     *
     * @param c character to test.
     * @return true if vowel.
     */
    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }

    /**
     * Applies the original word's casing to the plural form.
     *
     * @param original original word.
     * @param plural plural form in lowercase.
     * @return plural with matched casing.
     */
    private static String matchCase(String original, String plural) {
        if (original.equals(original.toUpperCase(Locale.ROOT))) {
            return plural.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(plural.charAt(0)) + plural.substring(1);
        }
        return plural;
    }

    /**
     * Checks if a string can be parsed as an integer.
     *
     * @param s the string to check.
     * @return true if the string is an integer, false otherwise.
     */
    public static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Escapes special characters in a string for JSON formatting.
     *
     * @param s the string to escape.
     * @return the escaped string.
     */
    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}