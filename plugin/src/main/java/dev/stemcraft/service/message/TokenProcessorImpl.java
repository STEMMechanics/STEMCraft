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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class TokenProcessorImpl implements TokenProcessor {

    private final Map<Pattern, String> tokens = new HashMap<>();

    /**
     * Constructs a new TokenProcessor with the given configuration.
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
     */
    @Override
    public void add(String placeholder, String value) {
        if (placeholder == null || placeholder.isEmpty()) {
            return;
        }

        String safeValue = value == null ? "" : value;

        // remove the leading and trailing colons if present
        if (placeholder.startsWith(":") && placeholder.endsWith(":") && placeholder.length() > 2) {
            placeholder = placeholder.substring(1, placeholder.length() - 1);
        }

        tokens.put(Pattern.compile(Pattern.quote(":" + placeholder + ":")), safeValue);
    }

    /**
     * Removes a token binding.
     */
    @Override
    public void remove(String placeholder) {
        if (placeholder == null || placeholder.isEmpty()) {
            return;
        }

        // remove the leading and trailing colons if present
        if (placeholder.startsWith(":") && placeholder.endsWith(":") && placeholder.length() > 2) {
            placeholder = placeholder.substring(1, placeholder.length() - 1);
        }

        Pattern p = Pattern.compile(Pattern.quote(":" + placeholder + ":"));
        tokens.remove(p);
    }

    /**
     * Removes multiple token bindings.
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
     */
    public String apply(String str) {
        if (str == null || str.isEmpty() || tokens.isEmpty()) {
            return str;
        }
        String out = str;
        for (var entry : tokens.entrySet()) {
            out = entry.getKey().matcher(out).replaceAll(entry.getValue());
        }
        return out;
    }
}
