/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.List;

/** Dialog description opened by a WebViewButton. */
public record WebViewDialog(@NotNull String title, @NotNull String submitLabel,
                            @NotNull List<WebViewField> fields, int columns) {
    public WebViewDialog(String title, String submitLabel, List<WebViewField> fields) {
        this(title, submitLabel, fields, 2);
    }
    public WebViewDialog {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (submitLabel == null || submitLabel.isBlank()) throw new IllegalArgumentException("submitLabel must not be blank");
        fields = fields == null ? List.of() : List.copyOf(fields);
        if (columns < 1 || columns > 6) throw new IllegalArgumentException("columns must be between 1 and 6");
    }
}
