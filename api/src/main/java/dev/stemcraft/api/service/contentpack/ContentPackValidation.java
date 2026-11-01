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

import java.util.List;

/** Result of validating one content pack without changing active runtime state. */
public record ContentPackValidation(
        @NotNull String packId,
        boolean valid,
        @NotNull List<String> errors,
        @NotNull List<String> warnings
) {
    public ContentPackValidation {
        packId = packId == null ? "" : packId;
        errors = List.copyOf(errors == null ? List.of() : errors);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
