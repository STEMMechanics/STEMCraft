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

/** HTTP response returned by an {@link EditorWebModule}. */
public record EditorWebResponse(int status, @NotNull String contentType, @NotNull String body) {
    public EditorWebResponse {
        if (status < 100 || status > 599) throw new IllegalArgumentException("status must be an HTTP status");
        if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("contentType must not be blank");
        if (body == null) body = "";
    }

    public static @NotNull EditorWebResponse text(@NotNull String body) {
        return new EditorWebResponse(200, "text/plain; charset=utf-8", body);
    }
}
