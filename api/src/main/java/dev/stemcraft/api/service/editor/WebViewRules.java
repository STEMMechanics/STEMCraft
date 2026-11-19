/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;

public record WebViewRules(@NotNull List<Rule> rules) implements WebViewComponent {
    public WebViewRules { rules = rules == null ? List.of() : List.copyOf(rules); }
    @Override public @NotNull String type() { return "rules"; }
    @Override public @NotNull Map<String, Object> properties() { return WebViewComponent.properties("rules", rules); }
    public record Rule(@NotNull String drops, @NotNull String conditions, @NotNull String operation) { }
}
