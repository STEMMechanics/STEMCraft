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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
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
    private final Map<Player, List<String>> playerRegions = new HashMap<>();

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
        api.events().register(VehicleMoveEvent.class, event -> {
            Player rider = firstPassenger(event.getVehicle());
            if (rider == null) {
                return;
            }

            handleMovement(rider, event.getFrom(), event.getTo(), false);
        });
    }

    private void handleMovement(@NotNull Player player,
                                @Nullable Location from,
                                @Nullable Location requestedTo,
                                boolean preferActualPlayerLocation) {
        if (requestedTo == null || requestedTo.getWorld() == null) {
            return;
        }

        Location effectiveTo = preferActualPlayerLocation
            ? resolveEffectiveEnterLocation(player, from, requestedTo)
            : requestedTo;
        if (effectiveTo.getWorld() == null) {
            return;
        }

        World effectiveWorld = effectiveTo.getWorld();
        Set<String> previousIds = new HashSet<>(playerRegions.getOrDefault(player, List.of()));
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
                    } else {
                        listener.onExit(player, region, from, effectiveTo);
                    }
                    return;
                }

                if (!containsTo && !crossedRegion) {
                    return;
                }

                listener.onEnter(player, region, from, effectiveTo);
                if (containsTo) {
                    currentIds.add(id);
                } else {
                    listener.onExit(player, region, from, effectiveTo);
                }
                return;
            }

            if (world == null) {
                return;
            }

            if (wasInside) {
                if (world.equals(effectiveWorld)) {
                    currentIds.add(id);
                } else {
                    listener.onExitWorld(player, world, from, effectiveTo);
                }
                return;
            }

            if (world.equals(effectiveWorld)) {
                listener.onEnterWorld(player, world, from, effectiveTo);
                currentIds.add(id);
            }
        });

        playerRegions.put(player, currentIds);
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

    private Location resolveEffectiveEnterLocation(Player player, Location from, Location requestedTo) {
        Location actual = player.getLocation();
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
     * Checks if a player is currently within a region or world listener by its ID.
     *
     * @param player The player to check.
     * @param namespaceId The namespace ID of the region or world listener.
     * @return True if the player is within the region or world, false otherwise.
     */
    @Override
    public boolean contains(@NotNull Player player, @NotNull String namespaceId) {
        List<String> regions = playerRegions.get(player);
        return regions != null && regions.contains(namespaceId);
    }

    /**
     * Gets the set of region and world listener IDs that a player is currently within.
     *
     * @param player The player to check.
     * @return A set of region and world listener IDs.
     */
    @Override
    public @NotNull Set<String> getRegions(@NotNull Player player) {
        List<String> regions = playerRegions.get(player);
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
