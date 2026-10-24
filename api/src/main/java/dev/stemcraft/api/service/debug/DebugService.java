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

import java.util.List;
import java.util.Optional;

/** Registry for feature and service diagnostics exposed through {@code /debug}. */
public interface DebugService {
    DebugService UNAVAILABLE = new DebugService() {
        @Override
        public @NotNull DebugRegistration register(@NotNull String id, @NotNull String description,
                                                   @NotNull DebugHandler handler) {
            return DebugRegistration.UNAVAILABLE;
        }

        @Override
        public @NotNull List<DebugTest> tests() { return List.of(); }

        @Override
        public @NotNull Optional<DebugTest> find(@NotNull String id) { return Optional.empty(); }
    };

    /**
     * Register or replace a test. IDs are case-insensitive and use lowercase
     * letters, numbers, dots, underscores, and hyphens.
     */
    @NotNull DebugRegistration register(@NotNull String id, @NotNull String description,
                                        @NotNull DebugHandler handler);

    /** Return all registered tests in display order. */
    @NotNull List<DebugTest> tests();

    /** Find a test by its case-insensitive ID. */
    @NotNull Optional<DebugTest> find(@NotNull String id);
}
