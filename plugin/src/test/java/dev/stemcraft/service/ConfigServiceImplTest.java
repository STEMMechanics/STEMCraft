package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.config.ConfigChangeOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceImplTest {
    @TempDir
    Path tempDir;

    @Test
    void sharedWatcherDetectsExternalEditsAndCleansUp() throws Exception {
        STEMCraft plugin = mock(STEMCraft.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.getDataFolder()).thenReturn(tempDir.toFile());
        ConfigServiceImpl service = new ConfigServiceImpl(plugin, api);

        Path file = tempDir.resolve("watched.yml");
        Files.writeString(file, "value: before\n");
        service.onEnable();
        var config = service.load(file.toFile(), false);
        assertTrue(config != null);

        CountDownLatch changed = new CountDownLatch(1);
        var subscription = service.onChange(file.toFile(), event -> {
            if (event.origin() == ConfigChangeOrigin.EXTERNAL) changed.countDown();
        });
        Files.writeString(file, "value: after\n");

        assertTrue(changed.await(3, TimeUnit.SECONDS));
        assertEquals("after", config.getString("value", ""));
        subscription.close();
        service.onDisable();
    }

    @Test
    void savesMadeThroughConfigServiceDoNotEchoAsExternalChanges() throws IOException, InterruptedException {
        STEMCraft plugin = mock(STEMCraft.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.getDataFolder()).thenReturn(tempDir.toFile());
        ConfigServiceImpl service = new ConfigServiceImpl(plugin, api);
        Path file = tempDir.resolve("internal.yml");
        Files.writeString(file, "value: before\n");

        service.onEnable();
        var config = service.load(file.toFile(), false);
        AtomicInteger internal = new AtomicInteger();
        AtomicInteger external = new AtomicInteger();
        var subscription = service.onChange(file.toFile(), event -> {
            if (event.origin() == ConfigChangeOrigin.INTERNAL) internal.incrementAndGet();
            if (event.origin() == ConfigChangeOrigin.EXTERNAL) external.incrementAndGet();
        });

        config.set("value", "after");
        assertTrue(config.saveChecked().success());
        Thread.sleep(300L);

        assertEquals(1, internal.get());
        assertEquals(0, external.get());
        subscription.close();
        service.onDisable();
    }
}
