/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.Map;

public record WebViewSubview(@NotNull String title, @NotNull WebViewButton back) implements WebViewComponent {
    @Override public @NotNull String type() { return "subview"; }
    @Override public @NotNull Map<String, Object> properties() { return WebViewComponent.properties("title", title, "back", back); }
}
