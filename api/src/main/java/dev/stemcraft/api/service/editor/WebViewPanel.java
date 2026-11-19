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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Panel with an explicit column count and typed child components. */
public record WebViewPanel(
        @NotNull String title,
        @NotNull String description,
        int columns,
        @NotNull List<WebViewComponent> components,
        @NotNull List<WebViewButton> actions,
        @NotNull String meta
) implements WebViewComponent {
    public WebViewPanel(@NotNull String title, int columns, List<WebViewComponent> components) {
        this(title, "", columns, components, List.of(), "");
    }

    public WebViewPanel(@NotNull String title, int columns, List<WebViewComponent> components,
                        List<WebViewButton> actions, String meta) {
        this(title, "", columns, components, actions, meta);
    }

    public WebViewPanel(@NotNull String title, @NotNull String description, int columns,
                        List<WebViewComponent> components) {
        this(title, description, columns, components, List.of(), "");
    }

    public WebViewPanel {
        if (title == null) title = "";
        if (description == null) description = "";
        if (columns < 1 || columns > 6) throw new IllegalArgumentException("columns must be between 1 and 6");
        components = components == null ? List.of() : List.copyOf(new ArrayList<>(components));
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (meta == null) meta = "";
    }

    @Override public @NotNull String type() { return "panel"; }

    @Override public @NotNull Map<String, Object> properties() {
        return WebViewComponent.properties(
            "title", title, "description", description, "columns", columns,
            "children", WebViewComponent.definitions(components), "actions", actions, "meta", meta);
    }
}
