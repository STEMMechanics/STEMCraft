/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic editor for an array in the view state. */
public record WebViewRepeater(@NotNull String path, @NotNull String title,
                              @NotNull String itemLabel, @NotNull String addLabel,
                              @NotNull List<WebViewField> fields, @NotNull Map<String, Object> defaultItem,
                              int columns, boolean reorderable) implements WebViewComponent {
    public WebViewRepeater {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path must not be blank");
        if (title == null) title = "";
        if (itemLabel == null) itemLabel = "Item";
        if (addLabel == null) addLabel = "Add item";
        fields = fields == null ? List.of() : List.copyOf(fields);
        defaultItem = defaultItem == null ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(defaultItem));
        if (columns < 1 || columns > 6) throw new IllegalArgumentException("columns must be between 1 and 6");
    }
    @Override public @NotNull String type() { return "repeater"; }
    @Override public @NotNull Map<String, Object> properties() {
        return WebViewComponent.properties("path", path, "title", title, "itemLabel", itemLabel,
            "addLabel", addLabel, "fields", fields, "defaultItem", defaultItem,
            "columns", columns, "reorderable", reorderable);
    }
}
