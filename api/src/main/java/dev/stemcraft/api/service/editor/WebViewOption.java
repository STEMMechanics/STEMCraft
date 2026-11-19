/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;

/** Value and label for a select or suggestion field. */
public record WebViewOption(@NotNull String value, @NotNull String label) {
    public WebViewOption {
        if (value == null) value = "";
        if (label == null) label = value;
    }
}
