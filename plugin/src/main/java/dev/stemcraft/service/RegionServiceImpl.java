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
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

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
     * @param api    The STEMCraft API instance.
     */
    public RegionServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the region service
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Location to = event.getTo();
            World toWorld = to.getWorld();

            // Previously active regions/worlds for this player
            List<String> previousIds = playerRegions.getOrDefault(player, List.of());
            List<String> currentIds = new ArrayList<>();

            // detect regions/worlds player left
            for (String id : previousIds) {
                RegionListenerEntry entry = listeners.get(id);
                if (entry == null) {
                    continue; // listener was removed
                }

                SCRegion region = entry.region();
                World world = entry.world();
                RegionListener listener = entry.listener();

                if (region != null) {
                    // Region-based listener: exit when player is no longer inside
                    if (!region.contains(to)) {
                        listener.onExit(player, region);
                    } else {
                        currentIds.add(id);
                    }
                } else if (world != null) {
                    // World-based listener: exit when player moved to a different world
                    if (!world.equals(toWorld)) {
                        listener.onExitWorld(player, world);
                    } else {
                        currentIds.add(id);
                    }
                }
            }

            // detect regions/worlds player entered
            listeners.forEach((id, entry) -> {
                if (currentIds.contains(id)) {
                    return; // already still inside this region/world
                }

                SCRegion region = entry.region();
                World world = entry.world();
                RegionListener listener = entry.listener();

                if (region != null) {
                    if (region.contains(to)) {
                        listener.onEnter(player, region);
                        currentIds.add(id);
                    }
                } else if (world != null) {
                    if (world.equals(toWorld)) {
                        listener.onEnterWorld(player, world);
                        currentIds.add(id);
                    }
                }
            });

            // update player's current active regions/worlds
            playerRegions.put(player, currentIds);
        });
    }

    /**
     * Adds a region listener for a specific region.
     *
     * @param namespaceId The namespace ID of the region listener.
     * @param region      The SCRegion to listen to.
     * @param listener    The RegionListener to notify on enter/exit events.
     */
    public void addListener(String namespaceId, SCRegion region, RegionListener listener) {
        NamespaceId.checkValid(namespaceId);

        listeners.put(namespaceId, new RegionListenerEntry(region, null, listener));
    }

    /**
     * Adds a region listener for a specific world.
     *
     * @param namespaceId The namespace ID of the world listener.
     * @param world       The World to listen to.
     * @param listener    The RegionListener to notify on enter/exit world events.
     */
    public void addListener(String namespaceId, World world, RegionListener listener) {
        listeners.put(namespaceId, new RegionListenerEntry(null, world, listener));
    }

    /**
     * Removes a region listener by its ID. Support asterisk wildcards at the end of the ID.
     *
     * @param namespaceId The namespace ID of the listener to remove.
     */
    @Override
    public void removeListener(String namespaceId) {
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
     * @param player      The player to check.
     * @param namespaceId The namespace ID of the region or world listener.
     * @return True if the player is within the region or world, false otherwise.
     */
    @Override
    public boolean contains(Player player, String namespaceId) {
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
    public Set<String> getRegions(Player player) {
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
    public SCRegion getRegion(String id) {
        RegionListenerEntry entry = listeners.get(id);
        if (entry != null) {
            return entry.region();
        }
        return null;
    }
}
