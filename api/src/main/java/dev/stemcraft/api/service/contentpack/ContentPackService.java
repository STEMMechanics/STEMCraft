/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.contentpack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Lifecycle and discovery service for STEMCraft content packs. */
public interface ContentPackService {
    ContentPackService UNAVAILABLE = new ContentPackService() {
        @Override public @NotNull List<ContentPackInfo> packs() { return List.of(); }
        @Override public @Nullable ContentPackInfo find(@NotNull String id) { return null; }
        @Override public @NotNull ContentPackValidation validate(@NotNull String id) {
            return new ContentPackValidation(id, false, List.of("Content packs are unavailable."), List.of());
        }
        @Override public boolean reload(@NotNull String id) { return false; }
        @Override public boolean enable(@NotNull String id) { return false; }
        @Override public boolean disable(@NotNull String id) { return false; }
        @Override public boolean delete(@NotNull String id) { return false; }
        @Override public boolean create(@NotNull String id, @Nullable String template) { return false; }
        @Override public @NotNull List<String> texturePaths(@NotNull String id) { return List.of(); }
    };

    @NotNull List<ContentPackInfo> packs();

    @Nullable ContentPackInfo find(@NotNull String id);

    @NotNull ContentPackValidation validate(@NotNull String id);

    boolean reload(@NotNull String id);

    boolean enable(@NotNull String id);

    boolean disable(@NotNull String id);

    /** Disable a pack and move it to recoverable trash. */
    boolean delete(@NotNull String id);

    /** Create a valid starter pack directory. */
    boolean create(@NotNull String id, @Nullable String template);

    /** Return PNG texture paths available in a pack's textures directory. */
    @NotNull List<String> texturePaths(@NotNull String id);
}
