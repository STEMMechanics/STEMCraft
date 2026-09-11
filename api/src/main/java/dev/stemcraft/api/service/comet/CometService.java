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
    /**
     * Launches a comet at the supplied impact location from a random compass direction.
     *
     * @param impact terminal impact location
     * @example Random approach
     * {@code
     * api.comets().launch(impactLocation);
     * }
     */
    void launch(@NotNull Location impact);

    /**
     * Launches a comet from a random compass direction with loot around its terminal geode.
     *
     * @param impact terminal impact location
     * @param loot block ranges scattered around the terminal geode
     * @example Comet with loot
     * {@code
     * api.comets().launch(
     *     impactLocation,
     *     new CometLoot(Material.GOLD_BLOCK, 2, 15),
     *     new CometLoot(Material.EMERALD_BLOCK, 1, 4)
     * );
     * }
     */
    void launch(@NotNull Location impact, @NotNull CometLoot... loot);

    /**
     * Launches a comet at the supplied impact location.
     * The horizontal component of {@code direction} is the direction of travel into the impact
     * and along the crash scar; its Y component is ignored.
     *
     * @param impact terminal impact location
     * @param direction horizontal travel direction into the impact
     * @example Directed approach
     * {@code
     * api.comets().launch(impactLocation, new Vector(1, 0, -1));
     * }
     */
    void launch(@NotNull Location impact, @NotNull Vector direction);

    /**
     * Launches a directed comet with loot around its terminal geode.
     *
     * @param impact terminal impact location
     * @param direction horizontal travel direction into the impact
     * @param loot block ranges scattered around the terminal geode
     */
    void launch(@NotNull Location impact, @NotNull Vector direction, @NotNull CometLoot... loot);
}
