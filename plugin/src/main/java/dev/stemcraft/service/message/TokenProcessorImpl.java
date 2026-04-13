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

package dev.stemcraft.service.message;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.message.TokenProcessor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of the TokenProcessor interface.
 */
public class TokenProcessorImpl implements TokenProcessor {

    private final Map<String, String> tokens = new LinkedHashMap<>();

    /**
     * Constructs a new TokenProcessor with the given configuration.
     *
     * @param config The configuration section containing token definitions.
     */
    public TokenProcessorImpl(ConfigSection config) {
        ConfigSection sec = config.getSection("tokens");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                add(key, sec.getString(key));
            }
        }
    }

    /**
     * Adds a token binding.
     *
     * @param placeholder The placeholder token (without colons).
     * @param value The value to bind to the token.
     */
    @Override
    public void add(String placeholder, String value) {
        String marker = markerFor(placeholder);
        if (marker == null) {
            return;
        }

        String safeValue = value == null ? "" : value;
        tokens.put(marker, safeValue);
    }

    /**
     * Removes a token binding.
     *
     * @param placeholder The placeholder token (without colons).
     */
    @Override
    public void remove(String placeholder) {
        String marker = markerFor(placeholder);
        if (marker == null) {
            return;
        }
        tokens.remove(marker);
    }

    /**
     * Removes multiple token bindings.
     *
     * @param placeholders An iterable of placeholder tokens to remove.
     */
    @Override
    public void remove(Iterable<String> placeholders) {
        if (placeholders == null) {
            return;
        }
        for (String placeholder : placeholders) {
            remove(placeholder);
        }
    }

    /**
     * Processes the input string, replacing all tokens with their bound values.
     *
     * @param str The input string to process.
     * @return The processed string with tokens replaced.
     */
    public String apply(String str) {
        if (str == null || str.isEmpty() || tokens.isEmpty()) {
            return str;
        }
        String out = str;
        for (var entry : tokens.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static String markerFor(String placeholder) {
        if (placeholder == null || placeholder.isEmpty()) {
            return null;
        }

        String normalized = placeholder;
        if (normalized.startsWith(":") && normalized.endsWith(":") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        if (normalized.isEmpty()) {
            return null;
        }

        return ":" + normalized + ":";
    }
}
