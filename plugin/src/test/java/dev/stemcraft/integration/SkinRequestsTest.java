package dev.stemcraft.integration;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.config.ConfigFileImpl;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinRequestsTest {
    @TempDir Path folder;

    @Test void failureSurvivesNewCacheAndConfigReloadWithoutAnotherRequestOrWarning() throws Exception {
        var fixture = new Fixture();
        var count = new AtomicInteger();
        var cache = new SkinRequests<String>();
        cache.request(fixture.api, fixture.plugin, "stembot.yml", "url|false", () -> {
            count.incrementAndGet();
            throw new IllegalStateException(new java.net.SocketTimeoutException());
        }, value -> fail("Failure must not supply a skin"));
        fixture.complete();
        assertTrue(fixture.config.reload());
        new SkinRequests<String>().request(fixture.api, fixture.plugin, "stembot.yml", "url|false", () -> {
            count.incrementAndGet(); return "unexpected";
        }, value -> fail("Cooldown must suppress conversion"));
        assertEquals(1, count.get());
        verify(fixture.logger, times(1)).warning(anyString());
        String state = "skin-request-retries." + java.util.UUID.nameUUIDFromBytes("url|false".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fixture.config.set(state + ".retry-after", 0L);
        cache.request(fixture.api, fixture.plugin, "stembot.yml", "url|false", () -> "recovered", value -> assertEquals("recovered", value));
        fixture.complete();
        assertFalse(fixture.config.contains(state));
    }

    @Test void duplicateRequestsShareConversionAndSuccessfulResultsAreReused() throws Exception {
        var fixture = new Fixture();
        var cache = new SkinRequests<String>();
        var conversions = new AtomicInteger();
        var callbacks = new AtomicInteger();
        cache.request(fixture.api, fixture.plugin, "stembot.yml", "url", () -> {
            conversions.incrementAndGet(); return "skin";
        }, value -> callbacks.incrementAndGet());
        cache.request(fixture.api, fixture.plugin, "stembot.yml", "url", () -> {
            fail("Duplicate conversion"); return null;
        }, value -> callbacks.incrementAndGet());
        fixture.complete();
        cache.request(fixture.api, fixture.plugin, "stembot.yml", "url", () -> {
            fail("Cached conversion"); return null;
        }, value -> callbacks.incrementAndGet());
        assertEquals(1, conversions.get());
        assertEquals(3, callbacks.get());
        cache.request(fixture.api, fixture.plugin, "stembot.yml", "changed-url", () -> "new", value -> assertEquals("new", value));
        fixture.complete();
    }

    @Test void backoffIsExponentialAndCapped() {
        assertEquals(300_000, SkinRequests.retryDelayMillis(1));
        assertEquals(600_000, SkinRequests.retryDelayMillis(2));
        assertEquals(21_600_000, SkinRequests.retryDelayMillis(8));
        assertEquals(21_600_000, SkinRequests.retryDelayMillis(100));
    }

    private class Fixture {
        final STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        final Plugin plugin = mock(Plugin.class);
        final Logger logger = mock(Logger.class);
        final ConfigFileImpl config = new ConfigFileImpl();
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        Fixture() {
            assertTrue(config.load(folder.toFile(), "stembot.yml", true));
            when(api.config().load("stembot.yml", false)).thenReturn(config);
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(logger);
            var scheduler = api.tasks();
            doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(scheduler).nextTick(any(Runnable.class));
        }
        void complete() throws Exception {
            Runnable task = tasks.poll(5, TimeUnit.SECONDS);
            assertNotNull(task, "Conversion completion was not scheduled");
            task.run();
        }
    }
}
