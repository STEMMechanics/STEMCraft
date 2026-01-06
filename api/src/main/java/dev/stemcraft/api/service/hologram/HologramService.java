/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
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

/**
 * Service for managing holograms in the Minecraft world.
 */
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
     *
     * @param type type/category (e.g. "UNIQUE", "LEADERBOARD", "NPC_NAME")
     * @param context context/reference (e.g. unique ID, leaderboard name, NPC id)
     * @param location world location to spawn at
     * @param data additional data for the hologram
     * @return unique hologram id
     */
    int create(String type, String context, Location location, List<String> data);

    /**
     * Update all lines for a hologram and refresh it in-world.
     *
     * @param id hologram id
     * @param lines new lines to set
     */
    void update(int id, List<String> lines);

    /**
     * Update lines of all holograms of this type.
     *
     * @param type type/category (e.g. "UNIQUE", "LEADERBOARD", "NPC_NAME")
     * @param ref context/reference (e.g. unique ID, leaderboard name, NPC id
     */
    void update(String type, String ref);

    /**
     * Move a hologram to a new location.
     *
     * @param id hologram id
     * @param newLocation new world location
     */
    void move(int id, Location newLocation);

    /**
     * Delete a hologram by id and remove its entities.
     *
     * @param id hologram id
     */
    void delete(int id);

    /**
     * Delete all holograms of a given type.
     *
     * @param type type/category (e.g. "UNIQUE", "LEADERBOARD", "NPC_NAME")
     * @param context context/reference (e.g. unique ID, leaderboard name, NPC id
     */
    void delete(String type, String context);

    /**
     * Find the closest hologram from a location.
     *
     * @param loc location to search from
     * @param range maximum range to search within
     * @return hologram id or -1 if none found within range
     */
    int closest(Location loc, int range);
    default int closest(Location loc) { return closest(loc, DEFAULT_RANGE); }

    /**
     * Despawn all entities, typically on plugin disable.
     */
    void despawnAll();

    /**
     * Save a hologram to disk (or null for all).
     *
     * @param id hologram id or null for all
     */
    void save(Integer id);
    default void saveAll() { save(null); }
}
