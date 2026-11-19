/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.condition;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Runtime values supplied to shared content-pack condition providers. */
public record ConditionContext(
        @Nullable Entity entity,
        @Nullable Entity target,
        @Nullable Player player,
        @Nullable Location location,
        @Nullable String biome,
        @Nullable String entityType,
        @Nullable String spawnReason,
        int distanceTier,
        Map<String, Object> values
) {
    public ConditionContext {
        values = Map.copyOf(values == null ? Map.of() : values);
        entityType = entityType == null ? "" : entityType;
        spawnReason = spawnReason == null ? "" : spawnReason;
        biome = biome == null ? "" : biome;
    }

    public static ConditionContext empty() {
        return new ConditionContext(null, null, null, null, "", "", "", -1, Map.of());
    }
}
