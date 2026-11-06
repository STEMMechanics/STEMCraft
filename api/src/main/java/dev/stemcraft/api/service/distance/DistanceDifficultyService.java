/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.distance;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Public distance-tier information; consumers do not read private PDC keys. */
public interface DistanceDifficultyService {
    DistanceDifficultyService UNAVAILABLE = new DistanceDifficultyService() {
        @Override public boolean isAvailable() { return false; }
        @Override public int tier(@NotNull Location location) { return 0; }
        @Override public int storedTier(@NotNull Entity entity) { return 0; }
        @Override public double multiplier(@NotNull Entity entity) { return 1.0; }
        @Override public boolean reloadContentPack(@NotNull String packId) { return false; }
        @Override public void unloadContentPack(@NotNull String packId) { }
        @Override public boolean selectWorldProfile(@NotNull String world, @NotNull String profileId) { return false; }
        @Override public @NotNull Map<String, String> worldProfiles() { return Map.of(); }
    };

    boolean isAvailable();

    int tier(@NotNull Location location);

    /** Return the spawn-recorded tier, with compatibility fallback for older mobs. */
    int storedTier(@NotNull Entity entity);

    double multiplier(@NotNull Entity entity);

    /** Load distance profiles owned by one content pack without selecting one. */
    boolean reloadContentPack(@NotNull String packId);

    /** Unregister distance profiles owned by one content pack. */
    void unloadContentPack(@NotNull String packId);

    /** Explicitly select a registered profile for a world. */
    boolean selectWorldProfile(@NotNull String world, @NotNull String profileId);

    /** Current explicit world-to-profile selections. */
    @NotNull Map<String, String> worldProfiles();
}
