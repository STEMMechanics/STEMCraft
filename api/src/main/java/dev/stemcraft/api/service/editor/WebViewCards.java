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

/** Responsive collection of clickable WebView items. */
public record WebViewCards(@NotNull List<WebViewItem> items, int columns, @NotNull String empty)
        implements WebViewComponent {
    public WebViewCards(List<WebViewItem> items, int columns) { this(items, columns, "Nothing found."); }

    public WebViewCards {
        items = items == null ? List.of() : List.copyOf(items);
        if (columns < 1 || columns > 6) throw new IllegalArgumentException("columns must be between 1 and 6");
        if (empty == null) empty = "Nothing found.";
    }

    @Override public @NotNull String type() { return "cards"; }
    @Override public @NotNull Map<String, Object> properties() {
        return WebViewComponent.properties("items", items, "columns", columns, "empty", empty);
    }
}
