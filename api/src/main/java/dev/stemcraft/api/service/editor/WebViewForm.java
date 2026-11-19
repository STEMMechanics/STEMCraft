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

/** A data-driven form containing fields and any nested shared components. */
public record WebViewForm(@NotNull String id, @NotNull List<WebViewField> fields,
                          @NotNull List<WebViewComponent> components, @NotNull List<WebViewButton> actions,
                          int columns, @NotNull String payloadField, boolean submitState,
                          @NotNull String resultId) implements WebViewComponent {
    public WebViewForm(String id, List<WebViewField> fields, List<WebViewButton> actions) {
        this(id, fields, List.of(), actions, 2, "", false, "");
    }

    public WebViewForm {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        fields = fields == null ? List.of() : List.copyOf(fields);
        components = components == null ? List.of() : List.copyOf(components);
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (columns < 1 || columns > 6) throw new IllegalArgumentException("columns must be between 1 and 6");
        if (payloadField == null) payloadField = "";
        if (resultId == null) resultId = "";
    }

    @Override public @NotNull String type() { return "form"; }
    @Override public @NotNull Map<String, Object> properties() {
        return WebViewComponent.properties("id", id, "fields", fields,
            "children", WebViewComponent.definitions(components), "actions", actions,
            "columns", columns, "payloadField", payloadField, "submitState", submitState,
            "resultId", resultId);
    }
}
