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

import java.util.List;

/** A list/card item rendered by the shared editor. */
public record EditorWebItem(
        @NotNull String id,
        @NotNull String title,
        @NotNull String description,
        @NotNull List<EditorWebBadge> badges,
        @Nullable EditorWebAction action
) {
    public EditorWebItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (title == null) title = "";
        if (description == null) description = "";
        badges = badges == null ? List.of() : List.copyOf(badges);
    }
}
