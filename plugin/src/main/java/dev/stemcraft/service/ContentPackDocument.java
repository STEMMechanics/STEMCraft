/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.service;

import dev.stemcraft.api.config.ConfigSection;

import java.io.File;

/** One YAML source document belonging to a discovered content pack. */
record ContentPackDocument(
        String packId,
        String namespace,
        File packDirectory,
        File file,
        ConfigSection config
) { }
