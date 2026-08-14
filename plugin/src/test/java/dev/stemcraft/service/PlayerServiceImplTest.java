package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.player.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void resolveIdentityReturnsExactOnlineJavaPlayer() {
        PlayerService.ResolvedPlayer resolved = service.resolveIdentity("Hidden");

        assertNotNull(resolved);
        assertEquals(hidden.getUniqueId(), resolved.uuid());
        assertEquals("Hidden", resolved.name());
        assertEquals("java", resolved.platform());
    }

    @Test
    void resolveIdentityByUuidReturnsOnlinePlayerName() {
        PlayerService.ResolvedPlayer resolved = service.resolveIdentityByUuid(hidden.getUniqueId());

        assertNotNull(resolved);
        assertEquals("Hidden", resolved.name());
    }

    @Test
    void resolveIdentityFallsBackFromPlainToBedrockStyleName() {
        PlayerMock bedrock = server.addPlayer("*nomadjimbob");

        PlayerService.ResolvedPlayer resolved = service.resolveIdentity("nomadjimbob");

        assertNotNull(resolved);
        assertEquals(bedrock.getUniqueId(), resolved.uuid());
        assertEquals("*nomadjimbob", resolved.name());
    }

    @Test
    void resolveIdentityReturnsExactBedrockStyleNameWhenProvided() {
        PlayerMock bedrock = server.addPlayer("*nomadjimbob");

        PlayerService.ResolvedPlayer resolved = service.resolveIdentity("*nomadjimbob");

        assertNotNull(resolved);
        assertEquals(bedrock.getUniqueId(), resolved.uuid());
        assertEquals("*nomadjimbob", resolved.name());
    }

    @Test
    void resolveIdentityReturnsNullForBlankInput() {
        assertNull(service.resolveIdentity("   "));
        assertNull(service.resolveIdentity(null));
    }
}
