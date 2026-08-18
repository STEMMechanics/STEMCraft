package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaveServiceImplTest {
    @Test
    void savesEnabledExtensionsAndIsolatesFailures() {
        STEMCraft stemCraft = mock(STEMCraft.class);
        when(stemCraft.getLogger()).thenReturn(Logger.getLogger("SaveServiceImplTest"));
        Plugin owner = mock(Plugin.class);
        when(owner.isEnabled()).thenReturn(true);
        when(owner.getName()).thenReturn("ExampleExtension");
        SaveServiceImpl service = new SaveServiceImpl(stemCraft, mock(STEMCraftAPI.class));
        AtomicInteger calls = new AtomicInteger();
        service.register(owner, "good", calls::incrementAndGet);
        service.register(owner, "bad", () -> { throw new IllegalStateException("disk full"); });

        var report = service.saveExtensions();

        assertEquals(2, report.attempted());
        assertEquals(1, report.succeeded());
        assertEquals(1, calls.get());
        assertEquals("IllegalStateException: disk full", report.failures().get("extension:ExampleExtension:bad"));
    }

    @Test
    void replacementAndUnregisterUseOwnerAndNormalizedId() {
        STEMCraft stemCraft = mock(STEMCraft.class);
        Plugin owner = mock(Plugin.class);
        when(owner.isEnabled()).thenReturn(true);
        when(owner.getName()).thenReturn("ExampleExtension");
        SaveServiceImpl service = new SaveServiceImpl(stemCraft, mock(STEMCraftAPI.class));
        AtomicInteger calls = new AtomicInteger();
        service.register(owner, "Data", () -> calls.addAndGet(1));
        service.register(owner, "data", () -> calls.addAndGet(10));

        assertEquals(1, service.saveExtensions().attempted());
        assertEquals(10, calls.get());
        service.unregister(owner, "DATA");
        assertEquals(0, service.saveExtensions().attempted());
    }

    @Test
    void disabledOwnersAreNotInvoked() {
        STEMCraft stemCraft = mock(STEMCraft.class);
        Plugin owner = mock(Plugin.class);
        when(owner.isEnabled()).thenReturn(false);
        SaveServiceImpl service = new SaveServiceImpl(stemCraft, mock(STEMCraftAPI.class));
        service.register(owner, "data", () -> fail("disabled extension was invoked"));

        assertEquals(0, service.saveExtensions().attempted());
    }
}
