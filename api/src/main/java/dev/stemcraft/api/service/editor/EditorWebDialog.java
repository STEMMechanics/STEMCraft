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

/** A service-defined short form rendered by the shared dialog component. */
public record EditorWebDialog(
        @NotNull String title,
        @NotNull String submitLabel,
        @NotNull List<EditorWebField> fields
) {
    public EditorWebDialog {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (submitLabel == null || submitLabel.isBlank()) throw new IllegalArgumentException("submitLabel must not be blank");
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
