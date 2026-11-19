/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.Map;

public record WebViewNotice(@NotNull String text) implements WebViewComponent {
    @Override public @NotNull String type() { return "notice"; }
    @Override public @NotNull Map<String, Object> properties() { return WebViewComponent.properties("text", text); }
}
