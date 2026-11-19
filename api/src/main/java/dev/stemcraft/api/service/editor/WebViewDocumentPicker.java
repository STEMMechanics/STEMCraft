/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;

/** Selects a configuration file and opens the owning module's component-rendered editor view. */
public record WebViewDocumentPicker(@NotNull String module, @NotNull String pack,
                                    @NotNull List<WebViewOption> options) implements WebViewComponent {
    public WebViewDocumentPicker { if (module == null) module = ""; if (pack == null) pack = ""; options = options == null ? List.of() : List.copyOf(options); }
    @Override public @NotNull String type() { return "document-picker"; }
    @Override public @NotNull Map<String, Object> properties() { return WebViewComponent.properties("module", module, "pack", pack, "options", options); }
}
