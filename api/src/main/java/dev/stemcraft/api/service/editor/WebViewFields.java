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

/** A shared field grid with a service-declared column count. */
public record WebViewFields(@NotNull List<WebViewField> fields, int columns) implements WebViewComponent {
    public WebViewFields(List<WebViewField> fields) { this(fields, 2); }

    public WebViewFields {
        fields = fields == null ? List.of() : List.copyOf(fields);
        if (columns < 1 || columns > 6) throw new IllegalArgumentException("columns must be between 1 and 6");
    }

    @Override public @NotNull String type() { return "fields"; }
    @Override public @NotNull Map<String, Object> properties() {
        return WebViewComponent.properties("fields", fields, "columns", columns);
    }
}
