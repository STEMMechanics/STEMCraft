package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class EventServiceImpl extends BaseService implements EventService {

    public EventServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Register an event listener with callback handler.
     */
    public <T extends Event> Listener register(Class<T> event, EventHandler<T> callback, EventPriority priority, boolean ignoreCancelled) {
        Listener listener = new Listener() {};

        plugin.getServer().getPluginManager().registerEvent(event, listener, priority, (ignored, rawEvent) -> {
            if(event.isInstance(rawEvent)) {
                T castedEvent = event.cast(rawEvent);
                if (ignoreCancelled && rawEvent instanceof Cancellable c && c.isCancelled()) {
                    return;
                }

                callback.handle(castedEvent);
            }
        }, plugin, ignoreCancelled);

        return listener;
    }


}
