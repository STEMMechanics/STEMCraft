package dev.stemcraft.service.resourcepack;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;

/**
 * Class to handle resource pack related events.
 */
public class ResourcePackEvents {
    private final STEMCraftAPI api;
    private final ResourcePackServiceImpl service;

    /**
     * Constructor for ResourcePackEvents.
     *
     * @param api     The STEMCraftAPI instance.
     * @param service The ResourcePackServiceImpl instance.
     */
    public ResourcePackEvents(STEMCraftAPI api, ResourcePackServiceImpl service) {
        this.api = api;
        this.service = service;
    }

    /**
     * Register event listeners for resource pack handling.
     */
    public void onEnable() {
        api.events().register(PlayerJoinEvent.class, event -> {
            File resourcePack = service.getResourcePack();

            if (resourcePack != null && resourcePack.exists()) {
                service.sendPack(event.getPlayer());
            }
        }, EventPriority.MONITOR, false);
    }
}
