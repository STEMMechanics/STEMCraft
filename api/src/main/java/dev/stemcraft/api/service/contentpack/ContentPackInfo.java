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

/** A read-only summary of an installed STEMCraft content pack. */
public record ContentPackInfo(
        @NotNull String id,
        @NotNull String namespace,
        @NotNull String description,
        @NotNull String path,
        boolean enabled,
        boolean bundled,
        @NotNull List<String> dependencies,
        @NotNull List<String> validationErrors,
        boolean resourcePackRebuildRequired
) {
    public ContentPackInfo {
        id = requireText(id, "id");
        namespace = requireText(namespace, "namespace");
        description = description == null ? "" : description;
        path = requireText(path, "path");
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        validationErrors = List.copyOf(validationErrors == null ? List.of() : validationErrors);
    }

    public boolean valid() {
        return validationErrors.isEmpty();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
