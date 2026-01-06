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

package dev.stemcraft.api.message;

/**
 * Interface for processing tokens in strings.
 */
public interface TokenProcessor {
    /**
     * Adds a token binding.
     *
     * @param placeholder The token placeholder to bind (e.g., "{username}").
     * @param value The value to replace the token with.
     */
    void add(String placeholder, String value);

    /**
     * Removes a token binding.
     *
     * @param placeholder The token placeholder to remove.
     */
    void remove(String placeholder);

    /**
     * Removes multiple token bindings.
     *
     * @param placeholders An iterable of token placeholders to remove.
     */
    void remove(Iterable<String> placeholders);

    /**
     * Processes the input string, replacing all tokens with their bound values.
     *
     * @param str The input string containing tokens.
     * @return The processed string with tokens replaced.
     */
    String apply(String str);
}
