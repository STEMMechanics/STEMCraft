package dev.stemcraft.service.world.setting;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.config.ConfigFileImpl;
import org.bukkit.GameMode;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldGameModeSettingTest {
    @TempDir
    Path tempDir;

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private ConfigFileImpl config;
    private WorldGameModeSetting setting;
    private WorldService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("lobby");
        player = server.addPlayer("Alex");
        player.teleport(world.getSpawnLocation());
        player.setGameMode(GameMode.SURVIVAL);

        config = new ConfigFileImpl();
        config.load(tempDir.toFile(), "world-gamemode-test.yml", true);

        service = mock(WorldService.class);
        when(service.getConfigSection(world)).thenReturn(config);

        EventService eventService = mock(EventService.class);
        when(eventService.register(any(Class.class), any(), any(EventPriority.class), anyBoolean())).thenReturn(mock(Listener.class));

        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.events()).thenReturn(eventService);

        setting = new WorldGameModeSetting();
        setting.onEnable(api, service);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void applyConfiguredGameModeSetsPlayersToConfiguredWorldMode() {
        setting.set(world, config, "adventure");

        player.setGameMode(GameMode.SURVIVAL);
        setting.applyConfiguredGameMode(player);

        assertEquals(GameMode.ADVENTURE, player.getGameMode());
    }

    @Test
    void applyConfiguredGameModeLeavesModeUnchangedWhenUnset() {
        player.setGameMode(GameMode.SURVIVAL);
        setting.applyConfiguredGameMode(player);

        assertEquals(GameMode.SURVIVAL, player.getGameMode());
    }
}
