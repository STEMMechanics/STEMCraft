/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.editor;

import dev.stemcraft.api.service.web.WebServiceRequest;
import org.jetbrains.annotations.NotNull;

/** A service-owned module displayed inside the shared STEMCraft editor hub. */
public interface EditorWebModule {
    /** Stable identifier used in editor URLs and the sidebar. */
    @NotNull String id();

    /** Child-friendly label shown in the sidebar. */
    @NotNull String title();

    /** Small icon or emoji shown beside the label. */
    default @NotNull String icon() { return "✦"; }

    /** Short explanation shown before the module is opened. */
    default @NotNull String description() { return "A STEMCraft editor."; }

    /** Permission required for the module to appear in a private session. */
    @NotNull String readPermission();

    /** Permission required for save and mutating actions. */
    default @NotNull String writePermission() { return readPermission(); }

    /**
     * Handle a request below {@code /editor/<token>/api/<module-id>}.
     * The path is relative to the module and starts with {@code /}.
     */
    @NotNull EditorWebResponse handle(@NotNull EditorWebContext context, @NotNull String path,
                                      @NotNull WebServiceRequest request);
}
