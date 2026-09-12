package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.event.guide.GuideActionRequestEvent;
import dev.stemcraft.api.service.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoordinatesGuideTest {
    @Test void onlyAnEnabledBarCompletesTheStepAndCancellationUnregistersIt() throws Exception {
        var server = MockBukkit.mock();
        Coordinates feature = null;
        try (var staticPlugin = mockStatic(STEMCraft.class)) {
            var plugin = mock(STEMCraft.class);
            when(plugin.getName()).thenReturn("STEMCraft");
            when(plugin.namespace()).thenReturn("stemcraft");
            staticPlugin.when(STEMCraft::getPlugin).thenReturn(plugin);
            var api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
            var config = api.config().load("config.yml");
            when(config.isSection(anyString())).thenReturn(true);
            var events = api.events();
            var callback = new AtomicReference<EventHandler<GuideActionRequestEvent>>();
            doAnswer(call -> {
                if (call.getArgument(0) == GuideActionRequestEvent.class) callback.set(call.getArgument(1));
                return new Listener() {};
            }).when(events).register(any(), any());
            var player = server.addPlayer();
            player.addAttachment(MockBukkit.createMockPlugin(), "stemcraft.command.coordbar", true);
            feature = new Coordinates(api);
            feature.onEnable();
            var results = new ArrayList<Boolean>();
            var request = new GuideActionRequestEvent(player, "stemcraft:coordbar", results::add);
            callback.get().handle(request);
            assertTrue(request.claimed());
            assertFalse(request.finished());
            var toggle = Coordinates.class.getDeclaredMethod("toggleBossBar", org.bukkit.entity.Player.class);
            toggle.setAccessible(true);
            toggle.invoke(feature, player);
            assertEquals(List.of(true), results);
            var alreadyEnabled = new GuideActionRequestEvent(player, "stemcraft:coordbar", results::add);
            callback.get().handle(alreadyEnabled);
            assertTrue(alreadyEnabled.finished());
            toggle.invoke(feature, player);
            var cancelled = new GuideActionRequestEvent(player, "stemcraft:coordbar", results::add);
            callback.get().handle(cancelled);
            cancelled.cancel();
            toggle.invoke(feature, player);
            assertEquals(List.of(true, true), results);
        } finally {
            if (feature != null) feature.onDisable();
            MockBukkit.unmock();
        }
    }
}
