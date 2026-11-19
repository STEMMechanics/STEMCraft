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

/** A reusable condition provider registered by a gameplay service. */
public interface ConditionProvider {
    @NotNull String id();

    /** Validate the provider-specific YAML value. */
    @NotNull List<String> validate(@NotNull Object value, @NotNull String path);

    /** Evaluate the provider-specific YAML value in a runtime context. */
    boolean matches(@NotNull Object value, @NotNull ConditionContext context);
}
