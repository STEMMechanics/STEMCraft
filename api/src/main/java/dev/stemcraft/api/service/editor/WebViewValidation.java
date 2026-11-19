/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;

public record WebViewValidation(@NotNull List<String> errors, @NotNull String empty) implements WebViewComponent {
    public WebViewValidation { errors = errors == null ? List.of() : List.copyOf(errors); if (empty == null) empty = "No validation issues found."; }
    @Override public @NotNull String type() { return "validation"; }
    @Override public @NotNull Map<String, Object> properties() { return WebViewComponent.properties("errors", errors, "empty", empty); }
}
