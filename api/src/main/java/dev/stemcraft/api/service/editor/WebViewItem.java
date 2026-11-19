/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** Clickable card or row supplied by a service. */
public record WebViewItem(@NotNull String id, @NotNull String title, @NotNull String description,
                          @NotNull List<WebViewBadge> badges, @Nullable WebViewButton action) {
    public WebViewItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (title == null) title = "";
        if (description == null) description = "";
        badges = badges == null ? List.of() : List.copyOf(badges);
    }
}
