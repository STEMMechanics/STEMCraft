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

import java.util.List;

/**
 * Declarative view supplied by an editor service.
 *
 * <p>The view describes data and behaviour only.  The shared editor owns the
 * HTML, CSS and browser event handling for every component.</p>
 */
public record EditorWebView(
        @NotNull String kind,
        @NotNull String title,
        @NotNull String description,
        boolean writable,
        @NotNull List<EditorWebComponent> components
) {
    public EditorWebView {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (description == null) description = "";
        components = components == null ? List.of() : List.copyOf(components);
    }
}
