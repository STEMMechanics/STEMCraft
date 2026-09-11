/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 3.
 */

package dev.stemcraft.api.service.comet;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/** A block material and inclusive quantity range scattered around a comet's terminal geode. */
public record CometLoot(@NotNull Material material, int minimum, int maximum) {
    public CometLoot {
        if (!material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException("Comet loot material must be a placeable block");
        }
        if (minimum < 0 || maximum < minimum || maximum > 4096) {
            throw new IllegalArgumentException("Comet loot range must satisfy 0 <= minimum <= maximum <= 4096");
        }
    }
}
