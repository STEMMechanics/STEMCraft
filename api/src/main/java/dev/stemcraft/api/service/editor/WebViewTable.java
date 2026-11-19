/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;

public record WebViewTable(@NotNull String label, @NotNull List<Column> columns,
                           @NotNull List<Map<String, Object>> rows) implements WebViewComponent {
    public WebViewTable { if (label == null) label = ""; columns = columns == null ? List.of() : List.copyOf(columns); rows = rows == null ? List.of() : List.copyOf(rows); }
    @Override public @NotNull String type() { return "table"; }
    @Override public @NotNull Map<String, Object> properties() { return WebViewComponent.properties("label", label, "columns", columns, "rows", rows); }
    public record Column(@NotNull String key, @NotNull String label) { }
}
