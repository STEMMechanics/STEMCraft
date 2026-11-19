/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.ability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Reusable runtime ability service for configuration-backed custom items. */
public interface ItemAbilityService {
    ItemAbilityService UNAVAILABLE = new ItemAbilityService() {
        @Override public boolean reloadContentPack(@NotNull String packId) { return false; }
        @Override public void unloadContentPack(@NotNull String packId) { }
        @Override public void refresh(@NotNull Player player) { }
        @Override public void registerPreview(@NotNull String token, @NotNull String itemId,
                                              @NotNull Map<String, Object> definition) { }
        @Override public void unregisterPreview(@NotNull String token) { }
    };

    boolean reloadContentPack(@NotNull String packId);

    void unloadContentPack(@NotNull String packId);

    /** Recalculate only this player's workshop equipment effects. */
    void refresh(@NotNull Player player);

    /** Register the current in-memory definition used by tagged editor previews. */
    void registerPreview(@NotNull String token, @NotNull String itemId,
                         @NotNull Map<String, Object> definition);

    /** Remove a tagged editor preview definition. */
    void unregisterPreview(@NotNull String token);
}
