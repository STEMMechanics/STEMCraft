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
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Action metadata consumed by the shared editor.
 *
 * <p>An action can navigate to another declarative view, submit a request, or
 * open a service-defined dialog.  It contains no browser markup.</p>
 */
public record EditorWebAction(
        @NotNull String type,
        @NotNull String id,
        @NotNull String label,
        @NotNull String variant,
        @Nullable String endpoint,
        @NotNull String method,
        @NotNull Map<String, Object> payload,
        @NotNull Map<String, String> query,
        @Nullable EditorWebDialog dialog
) {
    public EditorWebAction {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (variant == null || variant.isBlank()) variant = "default";
        if (method == null || method.isBlank()) method = "POST";
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        query = query == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(query));
    }

    public static @NotNull EditorWebAction navigate(@NotNull String id, @NotNull String label,
                                                     @NotNull Map<String, String> query) {
        return new EditorWebAction("navigate", id, label, "default", null, "GET", Map.of(), query, null);
    }

    public static @NotNull EditorWebAction request(@NotNull String id, @NotNull String label,
                                                    @NotNull String variant, @NotNull String endpoint,
                                                    @NotNull Map<String, Object> payload) {
        return new EditorWebAction("request", id, label, variant, endpoint, "POST", payload, Map.of(), null);
    }

    public static @NotNull EditorWebAction dialog(@NotNull String id, @NotNull String label,
                                                   @NotNull String variant, @NotNull String endpoint,
                                                   @NotNull Map<String, Object> payload,
                                                   @NotNull EditorWebDialog dialog) {
        return new EditorWebAction("dialog", id, label, variant, endpoint, "POST", payload, Map.of(), dialog);
    }
}
