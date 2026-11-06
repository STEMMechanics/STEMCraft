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

/** A value/label pair used by shared editor select and suggestion controls. */
public record EditorWebOption(@NotNull String value, @NotNull String label) {
    public EditorWebOption {
        if (value == null) value = "";
        if (label == null) label = value;
    }
}
