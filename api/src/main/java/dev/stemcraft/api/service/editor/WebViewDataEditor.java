/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A recursive, typed editor for map and list data held in the parent WebView state.
 * It renders nested mappings and sequences as ordinary shared WebView controls.
 */
public record WebViewDataEditor(@NotNull String path, @NotNull String label, int columns)
        implements WebViewComponent {
    public WebViewDataEditor(String path, String label) {
        this(path, label, 2);
    }

    public WebViewDataEditor {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path must not be blank");
        if (label == null) label = "Configuration";
        if (columns < 1 || columns > 6) throw new IllegalArgumentException("columns must be between 1 and 6");
    }

    @Override public @NotNull String type() { return "data-editor"; }

    @Override public @NotNull Map<String, Object> properties() {
        return WebViewComponent.properties("path", path, "label", label, "columns", columns);
    }
}
