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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.service.region.RegionService;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.NamespaceId;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Implementation of the RegionService for managing region and world listeners.
 */
public class RegionServiceImpl extends BaseService implements RegionService {

    /**
     * Entry for a region or world listener.
     */
    record RegionListenerEntry(SCRegion region, World world, RegionListener listener) { }

    /**
     * Map of registered region/world listeners.
     */
    private final Map<String, RegionListenerEntry> listeners = new HashMap<>();

    /**
     * Map of players to their currently active regions/worlds.
     */
    private final Map<UUID, List<String>> entityRegions = new HashMap<>();

    /**
     * List of tracked entities.
     */
    private final List<UUID> trackedEntities = new ArrayList<>();

    /**
     * Constructor for RegionServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public RegionServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the region service.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerMoveEvent.class, event -> handleMovement(event.getPlayer(), event.getFrom(), event.getTo(), true));

        api.events().register(EntityMoveEvent.class, event -> {
            LivingEntity entity = event.getEntity();

            if (entity instanceof Player player) return;
            if (!trackedEntities.contains(entity.getUniqueId())) return;

            handleMovement(event.getEntity(), event.getFrom(), event.getTo(), false);
        });

        api.events().register(VehicleMoveEvent.class, event -> {
            Player rider = firstPassenger(event.getVehicle());
            if (rider == null) {
                return;
            }

            handleMovement(rider, event.getFrom(), event.getTo(), false);
        });

        api.events().register(EntityRemoveEvent.class, event -> {
            if (event.getCause() != EntityRemoveEvent.Cause.UNLOAD) {
                entityRegions.remove(event.getEntity().getUniqueId());
                trackedEntities.remove(event.getEntity().getUniqueId());
            }
        });

        api.events().register(PlayerQuitEvent.class, event -> entityRegions.remove(event.getPlayer().getUniqueId()));
        api.events().register(PlayerKickEvent.class, event -> entityRegions.remove(event.getPlayer().getUniqueId()));

        // 5 minutes in ticks
        long CLEANUP_ENTITY_INTERVAL = 6000;
        api.tasks().repeating(CLEANUP_ENTITY_INTERVAL, this::cleanupEntities);
    }

    private void cleanupEntities() {
        entityRegions.entrySet().removeIf(entry -> {
            Entity entity = Bukkit.getEntity(entry.getKey());
            return entity == null || !entity.isValid();
        });
    }

    private void handleMovement(@NotNull LivingEntity livingEntity,
                                @Nullable Location from,
                                @Nullable Location requestedTo,
                                boolean preferActualLocation) {
        if (requestedTo == null || requestedTo.getWorld() == null) {
            return;
        }

        Location effectiveTo = preferActualLocation
            ? resolveEffectiveEnterLocation(livingEntity, from, requestedTo)
            : requestedTo;
        if (effectiveTo.getWorld() == null) {
            return;
        }

        UUID entityId = livingEntity.getUniqueId();
        World effectiveWorld = effectiveTo.getWorld();
        Set<String> previousIds = new HashSet<>(entityRegions.getOrDefault(entityId, List.of()));
        List<String> currentIds = new ArrayList<>();

        listeners.forEach((id, entry) -> {
            SCRegion region = entry.region();
            World world = entry.world();
            RegionListener listener = entry.listener();
            boolean wasInside = previousIds.contains(id);

            if (region != null) {
                boolean containsTo = region.contains(effectiveTo);
                boolean crossedRegion = from != null && region.intersectsPath(from, effectiveTo);

                if (wasInside) {
                    if (containsTo) {
                        currentIds.add(id);
                    } else if (livingEntity instanceof Player player) {
                        listener.onExit(player, region, from, effectiveTo);
                    } else {
                        listener.onExit(livingEntity, region, from, effectiveTo);
                    }
                    return;
                }

                if (!containsTo && !crossedRegion) {
                    return;
                }

                if (livingEntity instanceof Player player) {
                    listener.onEnter(player, region, from, effectiveTo);
                } else {
                    listener.onEnter(livingEntity, region, from, effectiveTo);
                }
                if (containsTo) {
                    currentIds.add(id);
                } else if (livingEntity instanceof Player player) {
                    listener.onExit(player, region, from, effectiveTo);
                } else {
                    listener.onExit(livingEntity, region, from, effectiveTo);
                }
                return;
            }

            if (world == null) {
                return;
            }

            if (wasInside) {
                if (world.equals(effectiveWorld)) {
                    currentIds.add(id);
                } else if(livingEntity instanceof Player player){
                    listener.onExitWorld(player, world, from, effectiveTo);
                }
                return;
            }

            if (world.equals(effectiveWorld) && (livingEntity instanceof Player player)) {
                listener.onEnterWorld(player, world, from, effectiveTo);
                currentIds.add(id);
            }
        });

        entityRegions.put(entityId, currentIds);
    }

    private @Nullable Player firstPassenger(@Nullable Entity vehicle) {
        if (vehicle == null) {
            return null;
        }

        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }

        return null;
    }

    private Location resolveEffectiveEnterLocation(LivingEntity livingEntity, Location from, Location requestedTo) {
        Location actual = livingEntity.getLocation();
        if (differentBlockLocation(actual, from) && differentBlockLocation(actual, requestedTo)) {
            return actual;
        }

        return requestedTo;
    }

    private boolean differentBlockLocation(Location left, Location right) {
        if (left == null || right == null) {
            return true;
        }
        if (left.getWorld() == null || right.getWorld() == null) {
            return true;
        }

        return !left.getWorld().equals(right.getWorld())
            || left.getBlockX() != right.getBlockX()
            || left.getBlockY() != right.getBlockY()
            || left.getBlockZ() != right.getBlockZ();
    }

    /**
     * Adds a region listener for a specific region.
     *
     * @param namespaceId The namespace ID of the region listener.
     * @param region The SCRegion to listen to.
     * @param listener The RegionListener to notify on enter/exit events.
     */
    public void addListener(@NotNull String namespaceId, @NotNull SCRegion region, @NotNull RegionListener listener) {
        NamespaceId.checkValid(namespaceId);

        listeners.put(namespaceId, new RegionListenerEntry(region, null, listener));
    }

    /**
     * Adds a region listener for a specific world.
     *
     * @param namespaceId The namespace ID of the world listener.
     * @param world The World to listen to.
     * @param listener The RegionListener to notify on enter/exit world events.
     */
    public void addListener(@NotNull String namespaceId, @NotNull World world, @NotNull RegionListener listener) {
        listeners.put(namespaceId, new RegionListenerEntry(null, world, listener));
    }

    /**
     * Removes a region listener by its ID. Support asterisk wildcards at the end of the ID.
     *
     * @param namespaceId The namespace ID of the listener to remove.
     */
    @Override
    public void removeListener(@NotNull String namespaceId) {
        Set<String> idList = new HashSet<>();

        if(namespaceId.indexOf('*') != -1) {
            String prefix = namespaceId.substring(0, namespaceId.indexOf('*'));
            String suffix = namespaceId.substring(namespaceId.indexOf('*') + 1);

            for (String item : listeners.keySet()) {
                if (item.startsWith(prefix) && item.endsWith(suffix)) {
                    idList.add(item);
                }
            }
        } else {
            idList.add(namespaceId);
        }

        for(String item : idList) {
            listeners.remove(item);
        }
    }

    /**
     * Tracks a living entity for region and world listeners.
     *
     * @param livingEntity The entity to track.
     */
    public void trackLivingEntity(@NotNull LivingEntity livingEntity) {
        trackedEntities.add(livingEntity.getUniqueId());
    }

    /**
     * Untracks a living entity from region and world listeners.
     *
     * @param livingEntity The entity to untrack.
     */
    public void untrackLivingEntity(@NotNull LivingEntity livingEntity) {
        trackedEntities.remove(livingEntity.getUniqueId());
    }

    /**
     * Checks if a player is currently within a region or world listener.
     *
     * @param livingEntity The entity to check.
     * @return True if the entity is within a region or world listener, false otherwise.
     */
    public boolean isTracked(@NotNull LivingEntity livingEntity) {
        return trackedEntities.contains(livingEntity.getUniqueId());
    }

    /**
     * Checks if a player is currently within a region or world listener by its ID.
     *
     * @param uuid The entity UUID to check.
     * @param namespaceId The namespace ID of the region or world listener.
     * @return True if the player is within the region or world, false otherwise.
     */
    @Override
    public boolean contains(@NotNull UUID uuid, @NotNull String namespaceId) {
        List<String> regions = entityRegions.get(uuid);
        return regions != null && regions.contains(namespaceId);
    }

    /**
     * Gets the set of region and world listener IDs that a player is currently within.
     *
     * @param uuid The entity UUID to check.
     * @return A set of region and world listener IDs.
     */
    @Override
    public @NotNull Set<String> getRegions(@NotNull UUID uuid) {
        List<String> regions = entityRegions.get(uuid);
        if (regions == null) {
            return Set.of();
        }
        return new HashSet<>(regions);
    }

    /**
     * Gets a region by its ID.
     *
     * @param id The ID of the region.
     * @return The SCRegion associated with the ID, or null if not found.
     */
    @Override
    @Nullable
    public SCRegion getRegion(@NonNull String id) {
        RegionListenerEntry entry = listeners.get(id);
        if (entry != null) {
            return entry.region();
        }
        return null;
    }
}
