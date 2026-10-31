package dev.stemcraft;

import dev.stemcraft.api.service.web.WebService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class STEMCraftShutdownTest {

    @Test
    void publishesRemainingPlayerQuitEventsBeforeServerStop() {
        WebService webService = mock(WebService.class);
        Player alice = mock(Player.class);
        Player bob = mock(Player.class);
        UUID aliceUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bobUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(alice.getUniqueId()).thenReturn(aliceUuid);
        when(alice.getName()).thenReturn("Alice");
        when(bob.getUniqueId()).thenReturn(bobUuid);
        when(bob.getName()).thenReturn("Bob");

        STEMCraft.publishServerShutdownEvents(webService, List.of(alice, bob));

        var order = inOrder(webService);
        order.verify(webService).publishEvent(
            "minecraft.player_quit", aliceUuid, "Alice", Map.of("online_players", 1));
        order.verify(webService).publishEvent(
            "minecraft.player_quit", bobUuid, "Bob", Map.of("online_players", 0));
        order.verify(webService).publishEvent("minecraft.server_stop", null, null, Map.of());
        verifyNoMoreInteractions(webService);
    }
}
