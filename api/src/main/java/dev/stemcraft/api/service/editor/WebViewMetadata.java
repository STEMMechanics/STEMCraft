/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.editor;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;

public record WebViewMetadata(@NotNull List<String> dependencies, boolean resourcePackRebuildRequired)
        implements WebViewComponent {
    public WebViewMetadata { dependencies = dependencies == null ? List.of() : List.copyOf(dependencies); }
    @Override public @NotNull String type() { return "meta"; }
    @Override public @NotNull Map<String, Object> properties() { return WebViewComponent.properties("dependencies", dependencies, "resourcePackRebuildRequired", resourcePackRebuildRequired); }
}
