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

package dev.stemcraft.api.service.region;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Service for managing region listeners and querying player locations within regions.
 */
public interface RegionService {

    /**
     * Adds a region listener for a specific region.
     *
     * @param namespaceId The unique ID for the region listener.
     * @param region The region to listen to.
     * @param listener The listener to handle region events.
     */
    void addListener(String namespaceId, SCRegion region, RegionListener listener);

    /**
     * Adds a region listener for an entire world.
     *
     * @param namespaceId The unique ID for the world listener.
     * @param world The world to listen to.
     * @param listener The listener to handle world events.
     */
    void addListener(String namespaceId, World world, RegionListener listener);

    /**
     * Removes a region listener by its ID. Support asterisk wildcards at the end of the ID.
     *
     * @param namespaceId The unique ID of the listener to remove.
     */
    void removeListener(String namespaceId);

    /**
     * Checks if a player is currently within a region or world listener by its ID.
     *
     * @param player The player to check.
     * @param namespaceId The unique ID of the region or world listener.
     * @return True if the player is within the specified region or world listener, false otherwise.
     */
    boolean contains(Player player, String namespaceId);

    /**
     * Gets the set of region and world listener IDs that a player is currently within.
     *
     * @param player The player to check.
     * @return A set of region and world listener IDs the player is within.
     */
    Set<String> getRegions(Player player);

    /**
     * Gets a region by its ID.
     *
     * @param id The unique ID of the region.
     * @return The SCRegion instance, or null if not found.
     */
    @Nullable
    SCRegion getRegion(String id);
}