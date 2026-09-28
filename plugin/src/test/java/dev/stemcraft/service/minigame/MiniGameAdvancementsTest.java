package dev.stemcraft.service.minigame;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.GameMode;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MiniGameAdvancementsTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deniesSurvivalOccupantsUntilTheyLeave(boolean spectator) {
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        EventService events = mock(EventService.class);
        when(api.events()).thenReturn(events);
        when(api.config().load("config.yml")).thenReturn(null);
        MiniGameServiceImpl service = new MiniGameServiceImpl(null, api);
        service.onEnable();

        ArgumentCaptor<EventHandler<PlayerAdvancementCriterionGrantEvent>> callback = ArgumentCaptor.captor();
        verify(events).register(eq(PlayerAdvancementCriterionGrantEvent.class), callback.capture(),
            eq(EventPriority.HIGHEST), eq(true));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        MiniGameArenaImpl arena = mock(MiniGameArenaImpl.class);

        PlayerAdvancementCriterionGrantEvent before = criterion(player);
        callback.getValue().handle(before);
        assertFalse(before.isCancelled());

        service.registerPlayerArena(player, arena, spectator);
        PlayerAdvancementCriterionGrantEvent during = criterion(player);
        callback.getValue().handle(during);
        assertTrue(during.isCancelled());

        service.unregisterPlayerArena(player, arena);
        PlayerAdvancementCriterionGrantEvent after = criterion(player);
        callback.getValue().handle(after);
        assertFalse(after.isCancelled());

        after.setCancelled(true);
        callback.getValue().handle(after);
        assertTrue(after.isCancelled());
    }

    private PlayerAdvancementCriterionGrantEvent criterion(Player player) {
        return new PlayerAdvancementCriterionGrantEvent(player, mock(Advancement.class), "criterion");
    }
}
