package dev.stemcraft.service.world;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.service.ConfigServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldServiceImplTransitionCommandsTest {
    @TempDir
    Path tempDir;

    private STEMCraft plugin;
    private STEMCraftAPI api;
    private ConfigServiceImpl configService;

    @BeforeEach
    void setUp() throws IOException {
        plugin = mock(STEMCraft.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("WorldServiceImplTransitionCommandsTest"));

        api = mock(STEMCraftAPI.class);
        when(api.getDataFolder()).thenReturn(tempDir.toFile());

        configService = new ConfigServiceImpl(plugin, api);
        when(api.config()).thenReturn(configService);

        InstanceHolder.set(api, plugin);

        Files.writeString(tempDir.resolve("config.yml"), "worlds:\n  challenge_treehouse: {}\n");
    }

    @AfterEach
    void tearDown() {
        InstanceHolder.set(null, null);
    }

    @Test
    void transitionCommandsRoundTripWithStructuredConfig() {
        WorldServiceImpl service = new WorldServiceImpl(plugin, api);

        service.setWorldTransitionCommands(
            "challenge_treehouse",
            WorldService.TransitionCommandPhase.JOIN,
            List.of("book show challenge-treehouse", "server:title @a actionbar Welcome")
        );

        assertEquals(
            List.of("book show challenge-treehouse", "server:title @a actionbar Welcome"),
            service.getWorldTransitionCommands("challenge_treehouse", WorldService.TransitionCommandPhase.JOIN)
        );

        ConfigFile config = configService.load("config.yml", false);
        assertNotNull(config);
        assertTrue(config.reload());

        WorldServiceImpl reloaded = new WorldServiceImpl(plugin, api);
        assertEquals(
            List.of("book show challenge-treehouse", "server:title @a actionbar Welcome"),
            reloaded.getWorldTransitionCommands("challenge_treehouse", WorldService.TransitionCommandPhase.JOIN)
        );
    }

    @Test
    void transitionCommandsReadLegacyShorthandWithPlayerDefault() throws IOException {
        Files.writeString(
            tempDir.resolve("config.yml"),
            """
            worlds:
              challenge_treehouse:
                join-commands: book show challenge-treehouse
                leave-commands:
                  - say goodbye
            """
        );

        WorldServiceImpl service = new WorldServiceImpl(plugin, api);

        assertEquals(
            List.of("book show challenge-treehouse"),
            service.getWorldTransitionCommands("challenge_treehouse", WorldService.TransitionCommandPhase.JOIN)
        );
        assertEquals(
            List.of("say goodbye"),
            service.getWorldTransitionCommands("challenge_treehouse", WorldService.TransitionCommandPhase.LEAVE)
        );
    }

    @Test
    void transitionCommandsMapLegacyConsoleModeToServerPrefix() throws IOException {
        Files.writeString(
            tempDir.resolve("config.yml"),
            """
            worlds:
              challenge_treehouse:
                join-commands:
                  as-player: false
                  commands:
                    - title @a actionbar Legacy
                    - server:say already-prefixed
            """
        );

        WorldServiceImpl service = new WorldServiceImpl(plugin, api);

        assertEquals(
            List.of("server:title @a actionbar Legacy", "server:say already-prefixed"),
            service.getWorldTransitionCommands("challenge_treehouse", WorldService.TransitionCommandPhase.JOIN)
        );
    }
}
