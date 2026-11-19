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

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** Shared condition registry used by loot and item abilities. */
public interface ConditionService {
    ConditionService UNAVAILABLE = new ConditionService() {
        @Override public void register(@NotNull ConditionProvider provider) { }
        @Override public void unregister(@NotNull String id) { }
        @Override public @NotNull List<String> validate(@NotNull Map<String, ?> conditions, @NotNull String path) {
            return List.of("Condition service is unavailable.");
        }
        @Override public boolean matches(@NotNull Map<String, ?> conditions, @NotNull ConditionContext context) {
            return false;
        }
    };

    void register(@NotNull ConditionProvider provider);

    void unregister(@NotNull String id);

    @NotNull List<String> validate(@NotNull Map<String, ?> conditions, @NotNull String path);

    boolean matches(@NotNull Map<String, ?> conditions, @NotNull ConditionContext context);
}
