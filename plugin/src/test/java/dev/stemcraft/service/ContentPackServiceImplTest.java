package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentPackServiceImplTest {
    @TempDir
    Path tempDir;

    private STEMCraft plugin;
    private STEMCraftAPI api;
    private ConfigServiceImpl config;
    private Path packs;

    @BeforeEach
    void setUp() throws IOException {
        plugin = mock(STEMCraft.class);
        api = mock(STEMCraftAPI.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ContentPackServiceImplTest"));
        when(api.getDataFolder()).thenReturn(tempDir.toFile());
        config = new ConfigServiceImpl(plugin, api);
        when(api.config()).thenReturn(config);
        packs = Files.createDirectories(tempDir.resolve("data-packs"));
        InstanceHolder.set(api, plugin);
    }

    @AfterEach
    void tearDown() {
        InstanceHolder.set(null, null);
    }

    @Test
    void reportsDependenciesAndDuplicateIdsBeforeActivation() throws IOException {
        writePack("dependent", "pack:\n  enabled: true\n  dependencies: [missing]\n");
        writePack("one", itemPack("shared"));
        writePack("two", itemPack("shared"));

        ContentPackServiceImpl service = new ContentPackServiceImpl(plugin, api);
        service.onEnable();

        assertTrue(service.find("dependent").validationErrors().stream()
            .anyMatch(error -> error.contains("missing dependency 'missing'")));
        assertTrue(service.find("one").validationErrors().stream()
            .anyMatch(error -> error.contains("duplicate custom item ID 'shared'")));
        assertTrue(service.find("two").validationErrors().stream()
            .anyMatch(error -> error.contains("duplicate custom item ID 'shared'")));
    }

    @Test
    void invalidReloadKeepsThePreviousPackAndHandlerState() throws IOException {
        writePack("reloadable", "pack:\n  enabled: true\n");
        ContentPackServiceImpl service = new ContentPackServiceImpl(plugin, api);
        service.onEnable();
        List<String> calls = new ArrayList<>();
        service.registerHandler("test", id -> {
            calls.add("reload:" + id);
            return true;
        }, id -> calls.add("unload:" + id));
        service.activateEnabledPacks();
        assertTrue(calls.contains("reload:reloadable"));
        calls.clear();

        Files.writeString(packs.resolve("reloadable/config.yml"), "pack: [invalid\n");
        assertFalse(service.reload("reloadable"));
        assertTrue(service.find("reloadable").validationErrors().isEmpty());
        assertTrue(calls.isEmpty());
    }

    @Test
    void createsPersistsEnablementAndDeletesToRecoverableTrash() {
        ContentPackServiceImpl service = new ContentPackServiceImpl(plugin, api);
        service.onEnable();

        assertTrue(service.create("child-workshop", null));
        assertFalse(service.find("child-workshop").enabled());
        assertTrue(service.enable("child-workshop"));
        assertTrue(service.find("child-workshop").enabled());
        assertTrue(service.disable("child-workshop"));
        assertFalse(service.find("child-workshop").enabled());
        assertTrue(service.delete("child-workshop"));
        assertTrue(service.find("child-workshop") == null);
        try (var children = Files.list(tempDir.resolve(".content-pack-trash"))) {
            assertTrue(children.findAny().isPresent());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private void writePack(String id, String content) throws IOException {
        Path directory = Files.createDirectories(packs.resolve(id));
        Files.writeString(directory.resolve("config.yml"), content);
    }

    private String itemPack(String id) {
        return "pack:\n  enabled: true\n  namespace: " + id + "_pack\n"
            + "custom-items:\n  " + id + ":\n    material: STICK\n";
    }
}
