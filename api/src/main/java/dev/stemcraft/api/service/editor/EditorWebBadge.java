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

/** A small semantic status badge rendered by the shared editor. */
public record EditorWebBadge(@NotNull String text, @NotNull String tone) {
    public EditorWebBadge {
        if (text == null) text = "";
        if (tone == null || tone.isBlank()) tone = "default";
    }
}
