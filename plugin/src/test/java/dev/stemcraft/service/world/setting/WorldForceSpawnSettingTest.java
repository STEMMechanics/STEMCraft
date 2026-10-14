package dev.stemcraft.service.world.setting;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.world.WorldService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldForceSpawnSettingTest {
    private ServerMock server;
    private WorldMock survival;
    private WorldMock cactus;
    private PlayerMock player;
    private WorldForceSpawnSetting setting;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        survival = server.addSimpleWorld("survival");
        cactus = server.addSimpleWorld("cactus");
        player = server.addPlayer();
        setting = new WorldForceSpawnSetting();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void activatesIndependentRespawnForEachWorld() {
        Location survivalSpawn = new Location(survival, 10.5d, 65.0d, 20.5d, 90.0f, 0.0f);
        Location cactusSpawn = new Location(cactus, -14.5d, 72.0d, 8.5d, 180.0f, 5.0f);
        setting.storeWorldRespawn(player, survival, survivalSpawn, true);
        setting.storeWorldRespawn(player, cactus, cactusSpawn, true);

        setting.activateWorldRespawn(player, survival);
        assertEquals(survivalSpawn, player.getRespawnLocation());

        setting.activateWorldRespawn(player, cactus);
        assertEquals(cactusSpawn, player.getRespawnLocation());

        setting.activateWorldRespawn(player, survival);
        assertEquals(survivalSpawn, player.getRespawnLocation());
    }

    @Test
    void worldWithoutSavedRespawnClearsOtherWorldPoint() {
        Location survivalSpawn = new Location(survival, 10.5d, 65.0d, 20.5d);
        setting.storeWorldRespawn(player, survival, survivalSpawn, true);
        setting.activateWorldRespawn(player, survival);

        setting.activateWorldRespawn(player, cactus);

        assertNull(player.getRespawnLocation());
    }

    @Test
    void importsExistingBedCoordinatesBeforeSwitchingWorlds() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        when(api.events()).thenReturn(events);
        setting.onEnable(api, mock(WorldService.class));
        ArgumentCaptor<EventHandler<PlayerChangedWorldEvent>> callback = ArgumentCaptor.captor();
        verify(events).register(eq(PlayerChangedWorldEvent.class), callback.capture());

        Player eventPlayer = mock(Player.class);
        Location bedBlock = new Location(survival, 10.0d, 65.0d, 20.0d);
        Location landing = new Location(survival, 11.5d, 65.1d, 20.5d);
        when(eventPlayer.getUniqueId()).thenReturn(player.getUniqueId());
        when(eventPlayer.getWorld()).thenReturn(cactus);
        when(eventPlayer.getPersistentDataContainer()).thenReturn(player.getPersistentDataContainer());
        when(eventPlayer.getRespawnLocation(false)).thenReturn(bedBlock);
        when(eventPlayer.getRespawnLocation()).thenReturn(landing);

        callback.getValue().handle(new PlayerChangedWorldEvent(eventPlayer, survival));
        verify(eventPlayer).setRespawnLocation(null, false);

        setting.activateWorldRespawn(eventPlayer, survival);
        verify(eventPlayer).setRespawnLocation(bedBlock, false);
    }

    @Test
    void crossWorldFallbackReturnsPlayerToDeathWorldSpawn() {
        WorldService worlds = mock(WorldService.class);
        ConfigSection config = mock(ConfigSection.class);
        Player eventPlayer = mock(Player.class);
        when(worlds.getConfigSection(survival)).thenReturn(config);
        when(config.getString("force-spawn-on-death")).thenReturn(null);
        when(eventPlayer.getWorld()).thenReturn(survival);
        setting.worldService = worlds;

        PlayerRespawnEvent event = new PlayerRespawnEvent(
            eventPlayer,
            new Location(cactus, 5.0d, 65.0d, 5.0d),
            true,
            false,
            false,
            PlayerRespawnEvent.RespawnReason.DEATH
        );
        setting.handleRespawn(event);

        assertEquals(survival.getSpawnLocation(), event.getRespawnLocation());
    }
}
