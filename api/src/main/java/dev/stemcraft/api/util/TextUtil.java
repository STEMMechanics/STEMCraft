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

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /**
     * Converts a formatted string into an Adventure {@link Component}.
     *
     * @param input the formatted text (MiniMessage or legacy), may be null
     * @return a non-null {@link Component} representing the formatted text
     */
    public static Component colourise(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        boolean hasAngleTag = input.indexOf('<') != -1 && input.indexOf('>') != -1;
        boolean hasLegacy = input.indexOf('&') != -1 || input.indexOf('§') != -1;

        if (hasAngleTag && !hasLegacy) {
            try {
                return MM.deserialize(input);
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
     * Converts formatted text to a legacy string using ampersand colour codes (e.g., &a, &7).
     */
    public static String colouriseToAmpersand(String input) {
        return LEGACY_AMP.serialize(colourise(input));
    }

    /**
     * Converts formatted text to a legacy string using section colour codes (e.g., §a, §7).
     * Useful for APIs that still require section-coded legacy strings.
     */
    public static String colouriseToSection(String input) {
        return LEGACY_SECTION.serialize(colourise(input));
    }


    /**
     * Removes all colour and formatting from a MiniMessage or legacy-formatted string, returning plain text.
     *
     * @param input the formatted text, may be null
     * @return plain unformatted text
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
     * Helper method to return the text length of a component
     *
     * @param text The text to calculate
     * @return The text length
     */
    public static int componentLength(Component text) {
        String t = LegacyComponentSerializer.legacySection().serialize(text);
        return t.replaceAll("§[0-9a-fk-or]", "").length();
    }

    /**
     * Converts a Component to plain text
     *
     * @param c The component
     * @return The plain text
     */
    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /**
     * Reverts section sign (§) colour codes back to ampersand (&) codes in a string.
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