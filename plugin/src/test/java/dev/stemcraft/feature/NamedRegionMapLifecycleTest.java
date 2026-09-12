package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.integration.pl3xmap.Pl3xMapNamedRegions;
import org.bukkit.Bukkit;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamedRegionMapLifecycleTest {
    @SuppressWarnings("unchecked")
    @Test void waitsForPl3xMapEnableAndReattachesAfterPluginRestart() {
        var api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        var feature = spy(new NamedRegions(api));
        var config = mock(ConfigSection.class);
        when(config.getBoolean("map.enabled", true)).thenReturn(true);
        doReturn(config).when(feature).getConfigSection();
        var callbacks = new HashMap<Class<?>, EventHandler<?>>();
        var events = api.events();
        doAnswer(invocation -> {
            callbacks.put(invocation.getArgument(0), invocation.getArgument(1));
            return mock(org.bukkit.event.Listener.class);
        }).when(events).register(any(), any());
        var manager = mock(PluginManager.class);
        Plugin pl3x = mock(Plugin.class);
        when(pl3x.getName()).thenReturn("Pl3xMap");
        when(manager.getPlugin("Pl3xMap")).thenReturn(pl3x);
        try (var bukkit = mockStatic(Bukkit.class);
             var stemcraft = mockStatic(STEMCraft.class);
             var layers = mockConstruction(Pl3xMapNamedRegions.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            stemcraft.when(STEMCraft::getPlugin).thenReturn(mock(STEMCraft.class));
            feature.watchMapPlugin();
            assertTrue(layers.constructed().isEmpty());
            when(pl3x.isEnabled()).thenReturn(true);
            var enable = (EventHandler<PluginEnableEvent>) callbacks.get(PluginEnableEvent.class);
            enable.handle(new PluginEnableEvent(pl3x));
            assertEquals(1, layers.constructed().size());
            verify(layers.constructed().getFirst()).enable();
            enable.handle(new PluginEnableEvent(pl3x));
            assertEquals(1, layers.constructed().size());
            var disable = (EventHandler<PluginDisableEvent>) callbacks.get(PluginDisableEvent.class);
            disable.handle(new PluginDisableEvent(pl3x));
            verify(layers.constructed().getFirst()).disable();
            enable.handle(new PluginEnableEvent(pl3x));
            assertEquals(2, layers.constructed().size());
        }
    }
}
