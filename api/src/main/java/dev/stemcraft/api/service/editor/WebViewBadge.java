/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;

/** Semantic status marker displayed by shared views. */
public record WebViewBadge(@NotNull String text, @NotNull String tone) {
    public WebViewBadge {
        if (text == null) text = "";
        if (tone == null || tone.isBlank()) tone = "default";
    }
}
