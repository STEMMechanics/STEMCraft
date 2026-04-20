package dev.stemcraft.service.resourcepack;

import dev.stemcraft.api.STEMCraftAPI;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
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
     * @param api The STEMCraftAPI instance.
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
        //noinspection UnstableApiUsage
        api.events().register(AsyncPlayerConnectionConfigureEvent.class, event -> {
            File resourcePack = service.getResourcePack();

            if (resourcePack != null && resourcePack.exists()) {
                service.sendPack(event.getConnection().getAudience());
            }
        }, EventPriority.MONITOR, false);
    }
}