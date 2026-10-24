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

/** Handle owned by the feature or service that registered a debug test. */
public interface DebugRegistration extends AutoCloseable {
    DebugRegistration UNAVAILABLE = new DebugRegistration() {
        @Override public @NotNull String id() { return ""; }
        @Override public void close() { }
    };

    /** The normalized debug test ID. */
    @NotNull String id();

    /** Remove this registration. */
    @Override
    default void close() { }
}
