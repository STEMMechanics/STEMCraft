/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 3.
 */

package dev.stemcraft.api.service.comet;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** Public API for launching destructive comet events. */
public interface CometService {
    /** Launches a comet at the supplied impact location from a random compass direction. */
    void launch(@NotNull Location impact);

    /**
     * Launches a comet at the supplied impact location.
     * The horizontal component of {@code direction} is the direction of travel into the impact
     * and along the crash scar; its Y component is ignored.
     */
    void launch(@NotNull Location impact, @NotNull Vector direction);
}
