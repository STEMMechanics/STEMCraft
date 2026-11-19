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
import java.util.Map;

/** Clickable list with optional client-side text filtering. */
public record WebViewList(@NotNull String label, @NotNull List<WebViewItem> items,
                          boolean searchable, @NotNull String searchPlaceholder,
                          @NotNull String empty) implements WebViewComponent {
    public WebViewList(String label, List<WebViewItem> items) {
        this(label, items, false, "Search", "Nothing found.");
    }

    public WebViewList {
        if (label == null) label = "";
        items = items == null ? List.of() : List.copyOf(items);
        if (searchPlaceholder == null) searchPlaceholder = "Search";
        if (empty == null) empty = "Nothing found.";
    }

    @Override public @NotNull String type() { return "list"; }
    @Override public @NotNull Map<String, Object> properties() {
        return WebViewComponent.properties("label", label, "items", items, "searchable", searchable,
            "searchPlaceholder", searchPlaceholder, "empty", empty);
    }
}
