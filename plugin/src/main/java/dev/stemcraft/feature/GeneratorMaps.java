package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.integration.pl3xmap.Pl3xMapGeneratorRenders;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.ArrayList;
import java.util.List;

/** Optional Pl3xMap bridge for custom renderer policies owned by world generators. */
public final class GeneratorMaps extends BaseFeature {
    private final List<Listener> listeners = new ArrayList<>();
    private Pl3xMapGeneratorRenders integration;

    /** @param api owning plugin API */
    public GeneratorMaps(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        listeners.add(api.events().register(PluginEnableEvent.class, event -> {
            if (event.getPlugin().getName().equals("Pl3xMap")) connect();
        }));
        listeners.add(api.events().register(PluginDisableEvent.class, event -> {
            if (event.getPlugin().getName().equals("Pl3xMap")) disconnect();
        }));
        connect();
    }

    private void connect() {
        if (integration != null || !Bukkit.getPluginManager().isPluginEnabled("Pl3xMap")) return;
        integration = new Pl3xMapGeneratorRenders(api);
        integration.enable();
    }

    private void disconnect() {
        if (integration != null) { integration.disable(); integration = null; }
    }

    @Override public void onDisable() {
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        disconnect();
    }
}
