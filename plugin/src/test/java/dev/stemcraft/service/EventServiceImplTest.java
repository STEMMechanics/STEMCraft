package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.event.EventHandler;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceImplTest {
    private ServerMock server;
    private EventServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        service = new EventServiceImpl(plugin, mock(STEMCraftAPI.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registerInvokesCallbackForMatchingEvents() {
        AtomicInteger calls = new AtomicInteger();
        EventHandler<TestEvent> handler = event -> calls.incrementAndGet();

        Listener listener = service.register(TestEvent.class, handler, EventPriority.NORMAL, false);
        assertNotNull(listener);

        TestEvent event = new TestEvent();
        server.getPluginManager().callEvent(event);

        assertEquals(1, calls.get());
        assertSame(TestEvent.getHandlerList(), event.getHandlers());
    }

    @Test
    void registerHonorsIgnoreCancelledForCancellableEvents() {
        AtomicInteger calls = new AtomicInteger();

        service.register(TestCancellableEvent.class, event -> calls.incrementAndGet(), EventPriority.NORMAL, true);

        TestCancellableEvent cancelled = new TestCancellableEvent();
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertEquals(0, calls.get());

        TestCancellableEvent allowed = new TestCancellableEvent();
        server.getPluginManager().callEvent(allowed);
        assertEquals(1, calls.get());
    }

    private static final class TestEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        public static @NotNull HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }
    }

    private static final class TestCancellableEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;

        public static @NotNull HandlerList getHandlerList() {
            return HANDLERS;
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            this.cancelled = cancel;
        }
    }
}
