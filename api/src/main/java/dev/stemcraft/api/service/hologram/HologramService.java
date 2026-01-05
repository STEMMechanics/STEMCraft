/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */
package dev.stemcraft.api.service.hologram;

import org.bukkit.Location;

import java.util.List;

public interface HologramService {
    int DEFAULT_RANGE = 20;

    /**
     * Register a hologram type handler.
     *
     * @param type    type/category (e.g. "UNIQUE", "LEADERBOARD", "NPC_NAME")
     * @param handler handler for this type
     */
    void registerType(String type, HologramTypeHandler handler);

    /**
     * Create and spawn a hologram.
     */
    int create(String type, String context, Location location, List<String> data);

    /**
     * Update all lines for a hologram and refresh it in-world.
     */
    void update(int id, List<String> lines);

    /**
     * Update lines of all holograms of this type.
     */
    void update(String type, String ref);

    /**
     * Move a hologram to a new location.
     */
    void move(int id, Location newLocation);

    /**
     * Delete a hologram by id and remove its entities.
     */
    void delete(int id);

    /**
     * Delete all holograms of a given type.
     */
    void delete(String type, String context);

    /**
     * Find the closest hologram from a location.
     */
    int closest(Location loc, int range);
    default int closest(Location loc) { return closest(loc, DEFAULT_RANGE); }

    /**
     * Despawn all entities, typically on plugin disable.
     */
    void despawnAll();

    /**
     * Save a hologram to disk (or null for all).
     */
    void save(Integer id);
    default void saveAll() { save(null); }
}
