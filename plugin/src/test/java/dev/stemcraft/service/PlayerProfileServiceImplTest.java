package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.player.PlayerService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerProfileServiceImplTest {
    private ServerMock server;
    private STEMCraftAPI api;
    private PlayerProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        api = mock(STEMCraftAPI.class);
        service = new PlayerProfileServiceImpl(mock(STEMCraft.class), api);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registeredSectionsAreAggregatedAndCanBeSelected() {
        Player player = server.addPlayer("Alex");
        PlayerService players = mock(PlayerService.class);
        when(api.players()).thenReturn(players);
        when(players.resolveIdentityByUuid(player.getUniqueId()))
            .thenReturn(new PlayerService.ResolvedPlayer(player.getUniqueId(), "Alex", "java"));

        service.register(new TestProvider("stats", "Statistics", Map.of("count", 3)));
        service.register(new TestProvider("skills", "Skills", Map.of("mining", 12)));

        assertEquals(2, service.definitions().size());
        assertTrue(service.definitions().stream().anyMatch(definition -> "skills".equals(definition.id())));

        var profile = service.snapshot(player.getUniqueId(), Set.of("skills"), "all");
        assertNotNull(profile);
        assertEquals("Alex", profile.username());
        assertTrue(profile.online());
        assertEquals(1, profile.sections().size());
        assertEquals("skills", profile.sections().getFirst().id());
        assertEquals(12, profile.sections().getFirst().data().get("mining"));
    }

    @Test
    void unknownPlayerDoesNotProduceAProfile() {
        PlayerService players = mock(PlayerService.class);
        when(api.players()).thenReturn(players);
        var uuid = java.util.UUID.randomUUID();
        when(players.resolveIdentityByUuid(uuid)).thenReturn(null);

        assertNull(service.snapshot(uuid, Set.of(), "all"));
    }

    private record TestProvider(String id, String title, Map<String, Object> values)
        implements dev.stemcraft.api.service.playerprofile.PlayerProfileSectionProvider {

        @Override
        public Map<String, Object> snapshot(java.util.UUID playerUuid, String period) {
            return new LinkedHashMap<>(values);
        }
    }
}
