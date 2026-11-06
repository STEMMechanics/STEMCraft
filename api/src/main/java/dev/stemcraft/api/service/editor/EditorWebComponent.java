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
import java.util.Map;

/** A shared-editor component definition.  Services provide data, never HTML. */
public record EditorWebComponent(@NotNull String type, @NotNull Map<String, Object> properties) {
    public EditorWebComponent {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        properties = properties == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(properties));
    }

    public static @NotNull EditorWebComponent of(@NotNull String type, @NotNull Map<String, Object> properties) {
        return new EditorWebComponent(type, properties);
    }
}
