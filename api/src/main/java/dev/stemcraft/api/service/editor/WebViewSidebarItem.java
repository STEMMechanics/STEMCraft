/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;

/** A typed quick link displayed beneath its module in the shared editor sidebar. */
public record WebViewSidebarItem(@NotNull String id, @NotNull String label, @NotNull String icon,
                                 @NotNull WebViewButton action, boolean active) {
    public WebViewSidebarItem(String id, String label, String icon, WebViewButton action) {
        this(id, label, icon, action, false);
    }

    public WebViewSidebarItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (icon == null || icon.isBlank()) icon = "circle";
        if (action == null) throw new IllegalArgumentException("action must not be null");
    }
}
