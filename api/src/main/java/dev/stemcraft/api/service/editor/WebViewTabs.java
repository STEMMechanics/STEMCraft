/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;

public record WebViewTabs(@NotNull List<Tab> tabs) implements WebViewComponent {
    public WebViewTabs { tabs = tabs == null ? List.of() : List.copyOf(tabs); }
    @Override public @NotNull String type() { return "tabs"; }
    @Override public @NotNull Map<String, Object> properties() { return WebViewComponent.properties("items", tabs); }
    public record Tab(@NotNull String id, @NotNull String label, @NotNull WebViewButton action, boolean active) { }
}
