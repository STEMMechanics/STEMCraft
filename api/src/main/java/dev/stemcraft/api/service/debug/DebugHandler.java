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

/** Handles one registered server-side debug test. */
@FunctionalInterface
public interface DebugHandler {
    /** Run the test for the selected player. */
    void run(DebugContext context);
}
