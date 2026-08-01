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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for font-related calculations.
 */
public final class FontUtil {
    private static final int DEFAULT_WIDTH = 6; // Default width for unknown characters
    private static final Set<Character> WIDTH_2 = new HashSet<>(Set.of('i', '!', ';', ':', '\'', ',', '.', '|')); // 2 px
    private static final Set<Character> WIDTH_3 = new HashSet<>(Set.of('l', '`'));       // 3 px
    private static final Set<Character> WIDTH_4 = new HashSet<>(Set.of('t', '*', '(', ')', '[', ']', '{', '}', '"', 'I', ' '));      // 4 px
    private static final Set<Character> WIDTH_5 = new HashSet<>(Set.of('f', 'k', '<', '>'));                 // 5 px
    private static final Set<Character> WIDTH_6 = new HashSet<>(Set.of('a', 'b', 'c', 'd', 'e', 'g', 'h', 'j',
            'm', 'n', 'o', 'p', 'q', 'r', 's', 'u',
            'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
            'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y',
            'Z', '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', '#', '$', '%', '^', '&',
            '-', '_', '=', '+', '/', '?', '\\', '■')); // 6 px
    private static final Set<Character> WIDTH_7 = new HashSet<>(Set.of('~', '@', '★')); // 7 px

    /**
     * Calculate the pixel width of a string based on the default minecraft font.
     *
     * @param text The text to calculate.
     * @return The pixel width.
     */
    public static int calculatePixelWidth(Component text) {
        return calculatePixelWidth(LegacyComponentSerializer.legacySection().serialize(text));
    }

    /**
     * Calculate the pixel width of a string based on the default minecraft font.
     *
     * @param text The text to calculate.
     * @return The pixel width.
     */
    public static int calculatePixelWidth(String text) {
        int width = 0;
        boolean bold = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (isLegacyColourCode(code) || code == 'r') {
                    bold = false;
                    i++;
                    continue;
                }
                if (code == 'l') {
                    bold = true;
                    i++;
                    continue;
                }
                if (isLegacyFormatCode(code)) {
                    i++;
                    continue;
                }
            }

            int charWidth = widthOf(c);
            width += charWidth;
            if (bold && c != ' ') {
                width += 1;
            }
        }
        return width;
    }

    private static int widthOf(char c) {
        if (WIDTH_2.contains(c)) return 2;
        if (WIDTH_3.contains(c)) return 3;
        if (WIDTH_4.contains(c)) return 4;
        if (WIDTH_5.contains(c)) return 5;
        if (WIDTH_6.contains(c)) return 6;
        if (WIDTH_7.contains(c)) return 7;
        return DEFAULT_WIDTH;
    }

    private static boolean isLegacyColourCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean isLegacyFormatCode(char code) {
        return code == 'k' || code == 'm' || code == 'n' || code == 'o';
    }
}
