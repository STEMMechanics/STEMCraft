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
import dev.stemcraft.api.model.SCManagedRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

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
    void addListener(@NotNull String namespaceId, @NotNull SCRegion region, @NotNull RegionListener listener);

    /**
     * Adds a region listener for a managed region by its identifier.
     *
     * @param namespaceId The unique ID for the region listener.
     * @param managedRegionId The managed region identifier to resolve.
     * @param listener The listener to handle region events.
     */
    void addListener(@NotNull String namespaceId, @NotNull String managedRegionId, @NotNull RegionListener listener);

    /**
     * Adds a region listener for an entire world.
     *
     * @param namespaceId The unique ID for the world listener.
     * @param world The world to listen to.
     * @param listener The listener to handle world events.
     */
    void addListener(@NotNull String namespaceId, @NotNull World world, @NotNull RegionListener listener);

    /**
     * Removes a region listener by its ID. Support asterisk wildcards at the end of the ID.
     *
     * @param namespaceId The unique ID of the listener to remove.
     */
    void removeListener(@NotNull String namespaceId);

    /**
     * Tracks a living entity for region and world listeners.
     *
     * @param livingEntity The entity to track.
     */
    void trackLivingEntity(@NotNull LivingEntity livingEntity);

    /**
     * Untracks a living entity from region and world listeners.
     *
     * @param livingEntity The entity to untrack.
     */
    void untrackLivingEntity(@NotNull LivingEntity livingEntity);

    /**
     * Checks if a player is currently within a region or world listener.
     *
     * @param livingEntity The entity to check.
     * @return True if the entity is within a region or world listener, false otherwise.
     */
    boolean isTracked(@NotNull LivingEntity livingEntity);

    /**
     * Checks if a player is currently within a region or world listener by its ID.
     *
     * @param uuid The entity UUID to check.
     * @param namespaceId The unique ID of the region or world listener.
     * @return True if the player is within the specified region or world listener, false otherwise.
     */
    boolean contains(@NotNull UUID uuid, @NotNull String namespaceId);
    default boolean contains(@NotNull Player player, @NotNull String namespaceId) { return contains(player.getUniqueId(), namespaceId); }
    default boolean contains(@NotNull LivingEntity livingEntity, @NotNull String namespaceId) { return contains(livingEntity.getUniqueId(), namespaceId); }

    /**
     * Gets the set of region and world listener IDs that a player is currently within.
     *
     * @param uuid The entity UUID to check.
     * @return A set of region and world listener IDs the player is within.
     */
    @NotNull Set<String> getRegions(@NotNull UUID uuid);
    default @NotNull Set<String> getRegions(@NotNull Player player) { return getRegions(player.getUniqueId()); }
    default @NotNull Set<String> getRegions(@NotNull LivingEntity livingEntity) { return getRegions(livingEntity.getUniqueId()); }

    /**
     * Gets a managed region shape by world name and local region ID.
     *
     * @param worldName The world name that owns the region.
     * @param id The local managed region ID.
     * @return The SCRegion instance, or null if not found.
     */
    @Nullable
    SCRegion getRegion(@NotNull String worldName, @NotNull String id);
    default @Nullable SCRegion getRegion(@NotNull World world, @NotNull String id) { return getRegion(world.getName(), id); }

    /**
     * Stores or updates a managed region definition.
     *
     * @param region The managed region definition to store.
     */
    void saveManagedRegion(@NotNull SCManagedRegion region);

    /**
     * Retrieves a managed region definition by its world name and local region ID.
     *
     * @param worldName The world name that owns the region.
     * @param id The local managed region ID.
     * @return The managed region definition, or null if not found.
     */
    @Nullable
    SCManagedRegion getManagedRegion(@NotNull String worldName, @NotNull String id);
    default @Nullable SCManagedRegion getManagedRegion(@NotNull World world, @NotNull String id) { return getManagedRegion(world.getName(), id); }

    /**
     * Checks whether a managed region exists in the given world.
     *
     * @param worldName The world name that owns the region.
     * @param id The local managed region ID.
     * @return True if the managed region exists, false otherwise.
     */
    boolean hasManagedRegion(@NotNull String worldName, @NotNull String id);
    default boolean hasManagedRegion(@NotNull World world, @NotNull String id) { return hasManagedRegion(world.getName(), id); }

    /**
     * Removes a managed region definition from the given world.
     *
     * @param worldName The world name that owns the region.
     * @param id The local managed region ID.
     * @return True if a region was removed, false otherwise.
     */
    boolean removeManagedRegion(@NotNull String worldName, @NotNull String id);
    default boolean removeManagedRegion(@NotNull World world, @NotNull String id) { return removeManagedRegion(world.getName(), id); }

    /**
     * Returns all managed region definitions known to the region service for one world.
     *
     * @param worldName The world name that owns the regions.
     * @return The managed region definitions for the world.
     */
    @NotNull Collection<SCManagedRegion> getManagedRegions(@NotNull String worldName);
    default @NotNull Collection<SCManagedRegion> getManagedRegions(@NotNull World world) { return getManagedRegions(world.getName()); }

    /**
     * Returns all managed regions that match the given location.
     * <p>
     * The returned collection is ordered from highest priority to lowest priority.
     *
     * @param location The location to resolve.
     * @return The matching managed regions ordered by priority.
     */
    @NotNull Collection<SCManagedRegion> getManagedRegionsAt(@NotNull Location location);

    /**
     * Returns the highest-priority managed region that matches the given location.
     *
     * @param location The location to resolve.
     * @return The highest-priority matching managed region, or null if none match.
     */
    @Nullable
    SCManagedRegion getManagedRegionAt(@NotNull Location location);

    /**
     * Registers a pluggable extension for managed region data.
     *
     * @param extension The extension to register.
     */
    void registerExtension(@NotNull RegionExtension<?> extension);

    /**
     * Retrieves a registered managed-region extension by its key.
     *
     * @param key The extension key.
     * @return The registered extension, or null if not found.
     */
    @Nullable
    RegionExtension<?> getExtension(@NotNull String key);

    /**
     * Returns all registered managed-region extensions.
     *
     * @return The registered managed-region extensions.
     */
    @NotNull Collection<RegionExtension<?>> getExtensions();
}
