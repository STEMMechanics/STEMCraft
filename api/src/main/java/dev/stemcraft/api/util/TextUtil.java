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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Utility class for text formatting and colour handling using Adventure API.
 */
public final class TextUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /**
     * Converts a formatted string into an Adventure {@link Component}.
     *
     * @param input the formatted text (MiniMessage or legacy), may be null.
     * @return a non-null {@link Component} representing the formatted text.
     */
    public static Component colourise(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        boolean hasAngleTag = input.indexOf('<') != -1 && input.indexOf('>') != -1;
        boolean hasLegacy = input.indexOf('&') != -1 || input.indexOf('§') != -1;

        if (hasAngleTag || hasLegacy) {
            try {
                return MM.deserialize(translateLegacyToMiniMessage(input));
            } catch (Exception ignored) {
                // fall through
            }
        }
        // Legacy: choose the correct serializer based on which legacy character is present.
        if (input.indexOf('§') != -1 && input.indexOf('&') == -1) {
            return LEGACY_SECTION.deserialize(input);
        }
        return LEGACY_AMP.deserialize(input);
    }

    private static String translateLegacyToMiniMessage(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);

        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);

            if (current == '&' && i + 1 < input.length()) {
                if (appendShortHexColour(input, i, out)) {
                    i += 7;
                    continue;
                }
                if (appendExpandedHexColour(input, i, out)) {
                    i += 13;
                    continue;
                }
                String replacement = legacyCodeToMiniMessage(input.charAt(i + 1));
                if (replacement != null) {
                    out.append(replacement);
                    i++;
                    continue;
                }
            }

            if (current == '§' && i + 1 < input.length()) {
                if (appendExpandedHexColour(input, i, out)) {
                    i += 13;
                    continue;
                }
                String replacement = legacyCodeToMiniMessage(input.charAt(i + 1));
                if (replacement != null) {
                    out.append(replacement);
                    i++;
                    continue;
                }
            }

            out.append(current);
        }

        return out.toString();
    }

    private static boolean appendShortHexColour(String input, int index, StringBuilder out) {
        if (index + 7 >= input.length() || input.charAt(index) != '&' || input.charAt(index + 1) != '#') {
            return false;
        }

        String hex = input.substring(index + 2, index + 8);
        if (!isHex(hex)) {
            return false;
        }

        out.append("<#").append(hex).append('>');
        return true;
    }

    private static boolean appendExpandedHexColour(String input, int index, StringBuilder out) {
        if (index + 13 >= input.length() || Character.toLowerCase(input.charAt(index + 1)) != 'x') {
            return false;
        }

        StringBuilder hex = new StringBuilder(6);
        for (int i = index + 2; i <= index + 13; i += 2) {
            char marker = input.charAt(i);
            if (marker != '&' && marker != '§') {
                return false;
            }
            char digit = input.charAt(i + 1);
            if (!isHexDigit(digit)) {
                return false;
            }
            hex.append(digit);
        }

        out.append("<#").append(hex).append('>');
        return true;
    }

    private static String legacyCodeToMiniMessage(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!isHexDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(char value) {
        return (value >= '0' && value <= '9')
            || (value >= 'a' && value <= 'f')
            || (value >= 'A' && value <= 'F');
    }

    public static Component colourise(Component input) {
        String raw = PLAIN.serialize(input);
        return LEGACY_AMP.deserialize(raw);
    }

    public static String[] colourise(String... inputs) {
        if (inputs == null || inputs.length == 0) {
            return new String[0];
        }

        String[] out = new String[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            out[i] = colouriseToSection(inputs[i]);
        }
        return out;
    }

    /**
     * Converts formatted text to a legacy string using ampersand colour codes
     * (for example, {@code &a} and {@code &7}).
     *
     * @param input the formatted text, may be null.
     * @return the legacy-formatted string with ampersand colour codes.
     */
    public static String colouriseToAmpersand(String input) {
        return LEGACY_AMP.serialize(colourise(input));
    }

    /**
     * Converts formatted text to a legacy string using section colour codes (e.g., §a, §7).
     * Useful for APIs that still require section-coded legacy strings.
     *
     * @param input the formatted text, may be null.
     * @return the legacy-formatted string with section colour codes.
     */
    public static String colouriseToSection(String input) {
        return LEGACY_SECTION.serialize(colourise(input));
    }


    /**
     * Removes all colour and formatting from a MiniMessage or legacy-formatted string, returning plain text.
     *
     * @param input the formatted text, may be null.
     * @return plain unformatted text.
    */
    public static String stripColour(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return stripColour(colourise(input));
    }

    public static String stripColour(Component component) {
        if (component == null) return "";
        return PLAIN.serialize(component);
    }

    /**
     * Helper method to return the text length of a component.
     *
     * @param text The text to calculate.
     * @return The text length.
     */
    public static int componentLength(Component text) {
        String t = LegacyComponentSerializer.legacySection().serialize(text);
        return t.replaceAll("§[0-9a-fk-or]", "").length();
    }

    /**
     * Converts a Component to plain text.
     *
     * @param c The component.
     * @return The plain text.
     */
    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /**
     * Reverts section sign (§) colour codes back to ampersand ({@code &}) codes in a string.
     *
     * @param input the string with section colour codes, may be null.
     * @return the string with ampersand colour codes.
     */
    public static String untranslateCodes(String input) {
        if (input == null) {
            return null;
        }
        return input.replace('§', '&');
    }

    public static String untranslateCodesStr(Component input) {
        if (input == null) {
            return null;
        }

        return LEGACY_SECTION.serialize(input).replace('§', '&');
    }

    public static Component untranslateCodes(Component input) {
        if (input == null) {
            return null;
        }

        return Component.text(LEGACY_SECTION.serialize(input).replace('§', '&'));
    }
}
