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

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Shared private web-editor hub used by quests, workshop items, loot, and packs. */
public interface EditorWebService {
    EditorWebService UNAVAILABLE = new EditorWebService() {
        @Override public void registerModule(@NotNull EditorWebModule module) { }
        @Override public @NotNull String createSession(@NotNull CommandSender sender) { return ""; }
    };

    /** Register or replace a service-owned editor module. */
    void registerModule(@NotNull EditorWebModule module);

    /** Create a private expiring editor URL containing only permitted modules. */
    @NotNull String createSession(@NotNull CommandSender sender);
}
