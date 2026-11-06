/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.itemeditor;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Draft and live-preview workflow for configuration-backed custom items. */
public interface ItemEditorService {
    ItemEditorService UNAVAILABLE = new ItemEditorService() {
        @Override public boolean open(@NotNull Player player, @NotNull String itemId) { return false; }
        @Override public boolean create(@NotNull Player player, @NotNull String itemId, @NotNull String material, @NotNull String packId) { return false; }
        @Override public boolean set(@NotNull Player player, @NotNull String field, @NotNull String value) { return false; }
        @Override public boolean undo(@NotNull Player player) { return false; }
        @Override public boolean redo(@NotNull Player player) { return false; }
        @Override public boolean cancel(@NotNull Player player) { return false; }
        @Override public boolean save(@NotNull Player player) { return false; }
        @Override public boolean test(@NotNull Player editor, @Nullable Player target, int amount) { return false; }
        @Override public @Nullable String selected(@NotNull Player player) { return null; }
    };

    boolean open(@NotNull Player player, @NotNull String itemId);

    boolean create(@NotNull Player player, @NotNull String itemId, @NotNull String material, @NotNull String packId);

    boolean set(@NotNull Player player, @NotNull String field, @NotNull String value);

    boolean undo(@NotNull Player player);

    boolean redo(@NotNull Player player);

    boolean cancel(@NotNull Player player);

    boolean save(@NotNull Player player);

    boolean test(@NotNull Player editor, @Nullable Player target, int amount);

    @Nullable String selected(@NotNull Player player);
}
