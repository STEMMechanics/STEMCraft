/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Opens a related editor record in a separate browser tab using the current field value.
 * A value prefix can limit the link to matching IDs; {@code *:} matches custom namespaced IDs
 * and removes their namespace before opening the target editor.
 */
public record WebViewLink(@NotNull String module, @NotNull String parameter,
                          @NotNull String label, @NotNull Map<String, String> query,
                          @Nullable String valuePrefix) {
    public WebViewLink(String module, String parameter, String label) {
        this(module, parameter, label, Map.of(), null);
    }

    public WebViewLink(String module, String parameter, String label, Map<String, String> query) {
        this(module, parameter, label, query, null);
    }

    public WebViewLink {
        if (module == null || module.isBlank()) throw new IllegalArgumentException("module must not be blank");
        if (parameter == null || parameter.isBlank()) throw new IllegalArgumentException("parameter must not be blank");
        if (label == null || label.isBlank()) label = "Open related record";
        query = query == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(query));
        if (valuePrefix != null && valuePrefix.isBlank()) valuePrefix = null;
    }
}
