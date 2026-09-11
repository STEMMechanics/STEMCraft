package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AfkTest {
    private org.mockito.MockedStatic<dev.stemcraft.STEMCraft> pluginAccess;
    private ServerMock server;
    private STEMCraftAPI api;
    private Afk afk;
    private ConfigSection config;
    private AtomicLong now;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        pluginAccess = mockStatic(dev.stemcraft.STEMCraft.class);
        now = new AtomicLong();
        config = mock(ConfigSection.class);
        when(config.getBoolean("enabled", true)).thenReturn(true);
        when(config.getInt("idle-minutes", 5)).thenReturn(5);
        when(config.getInt("kick-after-afk-minutes", 30)).thenReturn(30);
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        afk = spy(new Afk(api, now::get));
        doReturn(config).when(afk).getConfigSection();
        afk.onReload();
    }

    @AfterEach void cleanup() { pluginAccess.close(); MockBukkit.unmock(); }

    @SuppressWarnings("unchecked")
    @Test void eventsClearStatusAndCleanupRemovesTheCommandAndPermissions() {
        var callbacks = new java.util.HashMap<Class<?>, dev.stemcraft.api.service.event.EventHandler<?>>();
        var events = api.events();
        doAnswer(invocation -> {
            callbacks.put(invocation.getArgument(0), invocation.getArgument(1));
            return mock(org.bukkit.event.Listener.class);
        }).when(events).register(any(), any(), any(), anyBoolean());
        afk.onEnable();
        var player = server.addPlayer("James");
        afk.toggle(player);
        var commands = (dev.stemcraft.api.service.event.EventHandler<org.bukkit.event.player.PlayerCommandPreprocessEvent>)
            callbacks.get(org.bukkit.event.player.PlayerCommandPreprocessEvent.class);
        commands.handle(new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, "/afk"));
        assertTrue(afk.isAfk(player));
        commands.handle(new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, "/help"));
        assertFalse(afk.isAfk(player));
        afk.toggle(player);
        var chat = (dev.stemcraft.api.service.event.EventHandler<io.papermc.paper.event.player.AsyncChatEvent>)
            callbacks.get(io.papermc.paper.event.player.AsyncChatEvent.class);
        var event = mock(io.papermc.paper.event.player.AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        chat.handle(event);
        assertTrue(afk.isAfk(player));
        var scheduled = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(api.tasks()).nextTick(scheduled.capture());
        scheduled.getValue().run();
        assertFalse(afk.isAfk(player));
        assertEquals(org.bukkit.permissions.PermissionDefault.FALSE,
            server.getPluginManager().getPermission("stemcraft.afk.kick-exempt").getDefault());
        afk.onDisable();
        verify(api.tasks()).cancel("afk-check");
        assertNull(server.getPluginManager().getPermission("stemcraft.command.afk"));
    }

    @Test void announcesEachTransitionOnceInYellowToEveryone() {
        var player = server.addPlayer("James");
        var other = server.addPlayer("Alex");
        afk.activity(player);
        now.set(5 * 60_000);
        afk.tick();
        assertTrue(afk.isAfk(player));
        Component expected = Component.text("James is now AFK", NamedTextColor.YELLOW);
        assertEquals(expected, player.nextComponentMessage());
        assertEquals(expected, other.nextComponentMessage());
        afk.tick();
        assertNull(player.nextComponentMessage());
        afk.activity(player);
        assertFalse(afk.isAfk(player));
        expected = Component.text("James is no longer AFK", NamedTextColor.YELLOW);
        assertEquals(expected, player.nextComponentMessage());
        assertEquals(expected, other.nextComponentMessage());
        afk.activity(player);
        assertNull(player.nextComponentMessage());
    }

    @Test void kicksOnlyAfterThirtyMinutesAfk() {
        var player = server.addPlayer("James");
        player.setOp(false);
        afk.activity(player);
        now.set(5 * 60_000);
        afk.tick();
        now.set(35 * 60_000 - 1);
        afk.tick();
        assertTrue(player.isOnline());
        now.incrementAndGet();
        afk.tick();
        assertFalse(player.isOnline());
    }

    @Test void returningAndManualToggleResetTheTimers() {
        var player = server.addPlayer("James");
        afk.toggle(player);
        assertTrue(afk.isAfk(player));
        now.set(29 * 60_000);
        afk.toggle(player);
        assertFalse(afk.isAfk(player));
        now.set(33 * 60_000);
        afk.tick();
        assertFalse(afk.isAfk(player));
        assertTrue(player.isOnline());
    }

    @Test void zeroDisablesKickingAndReloadDisableClearsAfk() {
        var player = server.addPlayer("James");
        when(config.getInt("kick-after-afk-minutes", 30)).thenReturn(0);
        afk.onReload();
        afk.toggle(player);
        now.set(100 * 60_000);
        afk.tick();
        assertTrue(player.isOnline());
        when(config.getBoolean("enabled", true)).thenReturn(false);
        afk.onReload();
        assertFalse(afk.isAfk(player));
        afk.activity(player);
        afk.tick();
        assertFalse(afk.isAfk(player));
    }

    @Test void explicitKickExemptionStillAllowsAfkStatus() {
        var player = server.addPlayer("James");
        player.addAttachment(MockBukkit.createMockPlugin(), "stemcraft.afk.kick-exempt", true);
        afk.toggle(player);
        now.set(31 * 60_000);
        afk.tick();
        assertTrue(afk.isAfk(player));
        assertTrue(player.isOnline());
    }
}
