package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoordinateBarServiceImplTest {
    @Test
    void rendersRegisteredProvidersInPriorityOrderAndSkipsEmptyContent() {
        STEMCraft stemCraft = mock(STEMCraft.class);
        CoordinateBarServiceImpl service = new CoordinateBarServiceImpl(stemCraft, mock(STEMCraftAPI.class));
        Plugin first = enabledPlugin("First");
        Plugin second = enabledPlugin("Second");
        Player player = mock(Player.class);

        service.register(first, "later", 20, ignored -> Component.text("later"));
        service.register(second, "earlier", 10, ignored -> Component.text("earlier"));
        service.register(first, "hidden", 30, ignored -> null);

        assertEquals(List.of(Component.text("earlier"), Component.text("later")), service.render(player));
    }

    @Test
    void replacesAndUnregistersProviderByOwnerAndId() {
        CoordinateBarServiceImpl service = new CoordinateBarServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));
        Plugin owner = enabledPlugin("Owner");
        Player player = mock(Player.class);

        service.register(owner, "waypoint", 10, ignored -> Component.text("old"));
        service.register(owner, "waypoint", 10, ignored -> Component.text("new"));
        assertEquals(List.of(Component.text("new")), service.render(player));

        service.unregister(owner, "waypoint");
        assertEquals(List.of(), service.render(player));
    }

    private Plugin enabledPlugin(String name) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }
}
