package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.region.RegionListener;
import dev.stemcraft.api.services.region.RegionService;
import dev.stemcraft.api.utils.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class RegionManager implements RegionService {
    private final STEMCraft plugin;
    private final Map<String, RegionListenerEntry> listeners = new HashMap<>();
    private final Map<Player, List<String>> playerRegions = new HashMap<>();

    public RegionManager(STEMCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.registerEvent(PlayerMoveEvent.class, event -> {
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

    public void addRegionListener(String id, SCRegion region, RegionListener listener) {
        listeners.put(id, new RegionListenerEntry(region, null, listener));
    }

    public void addRegionListener(String id, World world, RegionListener listener) {
        listeners.put(id, new RegionListenerEntry(null, world, listener));
    }

    @Override
    public void removeRegionListener(String id) {
        Set<String> idList = new HashSet<>();

        if(id.indexOf('*') != -1) {
            String prefix = id.substring(0, id.indexOf('*'));
            String suffix = id.substring(id.indexOf('*') + 1);

            for (String item : listeners.keySet()) {
                if (item.startsWith(prefix) && item.endsWith(suffix)) {
                    idList.add(item);
                }
            }
        } else {
            idList.add(id);
        }

        for(String item : idList) {
            listeners.remove(item);
        }
    }

    record RegionListenerEntry(SCRegion region, World world, RegionListener listener) { }
}
