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

/** A service-defined field rendered by a shared editor form or dialog. */
public record EditorWebField(
        @NotNull String type,
        @NotNull String name,
        @NotNull String label,
        @NotNull String value,
        @NotNull String placeholder,
        boolean required,
        @Nullable String pattern,
        @NotNull List<EditorWebOption> options,
        @NotNull Map<String, String> attributes
) {
    public EditorWebField(@NotNull String type, @NotNull String name, @NotNull String label,
                          @NotNull String value, @NotNull String placeholder, boolean required,
                          @Nullable String pattern, @NotNull List<EditorWebOption> options) {
        this(type, name, label, value, placeholder, required, pattern, options, Map.of());
    }

    public EditorWebField {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (value == null) value = "";
        if (placeholder == null) placeholder = "";
        options = options == null ? List.of() : List.copyOf(options);
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public static @NotNull EditorWebField text(@NotNull String name, @NotNull String label,
                                                @NotNull String placeholder, boolean required,
                                                @Nullable String pattern) {
        return new EditorWebField("text", name, label, "", placeholder, required, pattern, List.of());
    }

    public static @NotNull EditorWebField select(@NotNull String name, @NotNull String label,
                                                  @NotNull List<EditorWebOption> options) {
        return new EditorWebField("select", name, label, "", "", false, null, options);
    }

    public static @NotNull EditorWebField suggestion(@NotNull String name, @NotNull String label,
                                                      @NotNull String placeholder,
                                                      @NotNull List<EditorWebOption> options,
                                                      boolean required) {
        return new EditorWebField("suggestion", name, label, "", placeholder, required, null, options);
    }

    public static @NotNull EditorWebField number(@NotNull String name, @NotNull String label,
                                                  @NotNull String value, @NotNull String placeholder,
                                                  boolean required, @Nullable String min,
                                                  @Nullable String max, @Nullable String step) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (min != null) attributes.put("min", min);
        if (max != null) attributes.put("max", max);
        if (step != null) attributes.put("step", step);
        return new EditorWebField("number", name, label, value, placeholder, required, null, List.of(), attributes);
    }

    public static @NotNull EditorWebField textarea(@NotNull String name, @NotNull String label,
                                                    @NotNull String value, boolean required) {
        return new EditorWebField("textarea", name, label, value, "", required, null, List.of());
    }
}
