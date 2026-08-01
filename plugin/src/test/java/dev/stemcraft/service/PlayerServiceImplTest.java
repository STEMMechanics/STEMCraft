package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerServiceImplTest {
    private ServerMock server;
    private STEMCraft plugin;
    private PlayerServiceImpl service;
    private PlayerMock hidden;
    private PlayerMock observer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(STEMCraft.class);
        when(plugin.getServer()).thenReturn(server);
        service = new PlayerServiceImpl(plugin, mock(STEMCraftAPI.class));
        hidden = server.addPlayer("Hidden");
        observer = server.addPlayer("Observer");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void hideAndShowUpdateVisibilityAndIgnoreDuplicateCalls() {
        assertTrue(observer.canSee(hidden));

        service.hide(hidden);
        assertFalse(observer.canSee(hidden));

        service.hide(hidden);
        assertFalse(observer.canSee(hidden));

        service.show(hidden);
        assertTrue(observer.canSee(hidden));

        service.show(hidden);
        assertTrue(observer.canSee(hidden));
    }
}
