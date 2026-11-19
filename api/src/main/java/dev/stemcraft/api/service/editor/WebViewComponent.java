/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed, data-only building block rendered by the shared WebService UI. */
public interface WebViewComponent {
    @NotNull String type();
    @NotNull Map<String, Object> properties();

    default @NotNull Map<String, Object> definition() {
        return Map.of("type", type(), "properties", properties());
    }

    static @NotNull List<Map<String, Object>> definitions(List<? extends WebViewComponent> components) {
        return components == null ? List.of() : components.stream().map(WebViewComponent::definition).toList();
    }

    static @NotNull Map<String, Object> properties(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            values.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return Map.copyOf(values);
    }
}
