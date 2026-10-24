/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.debug;

import org.jetbrains.annotations.NotNull;

/** Public description of one registered debug test. */
public record DebugTest(@NotNull String id, @NotNull String description) { }
