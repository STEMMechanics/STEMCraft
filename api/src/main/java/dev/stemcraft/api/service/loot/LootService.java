/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package dev.stemcraft.api.service.loot;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Reusable loot-table evaluation and simulation service. */
public interface LootService {
    LootService UNAVAILABLE = new LootService() {
        @Override public @NotNull List<LootDrop> simulate(@NotNull String tableId, int rolls, @NotNull LootContext context, @NotNull Random random) { return List.of(); }
        @Override public @NotNull List<String> tableIds() { return List.of(); }
    };

    @NotNull List<LootDrop> simulate(@NotNull String tableId, int rolls, @NotNull LootContext context, @NotNull Random random);

    @NotNull List<String> tableIds();

    record LootContext(
            @Nullable String world,
            @Nullable EntityType entityType,
            @Nullable String biome,
            @Nullable String spawnReason,
            boolean playerKill,
            int distanceTier,
            @NotNull Map<String, Object> values
    ) {
        public LootContext {
            values = Map.copyOf(values == null ? Map.of() : values);
        }
    }

    record LootDrop(@NotNull String item, int amount) { }
}
