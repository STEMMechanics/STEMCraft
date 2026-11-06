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

/** The permissions captured when a private editor link is issued. */
public record EditorWebContext(@NotNull String token, boolean writable) {
    public EditorWebContext {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("token must not be blank");
    }
}
