package dev.stemcraft.feature;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.GameMode;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SurvivalAdvancementsTest {
    @ParameterizedTest
    @EnumSource(GameMode.class)
    void onlySurvivalCanGainCriteria(GameMode mode) {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        when(api.events()).thenReturn(events);
        new SurvivalAdvancements(api).onEnable();

        ArgumentCaptor<EventHandler<PlayerAdvancementCriterionGrantEvent>> callback = ArgumentCaptor.captor();
        verify(events).register(eq(PlayerAdvancementCriterionGrantEvent.class), callback.capture(),
            eq(EventPriority.HIGHEST), eq(true));
        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(mode);
        PlayerAdvancementCriterionGrantEvent event = new PlayerAdvancementCriterionGrantEvent(
            player, mock(Advancement.class), "criterion");
        callback.getValue().handle(event);
        assertEquals(mode != GameMode.SURVIVAL, event.isCancelled());

        // Never undo a denial from another listener, including the minigame framework.
        event.setCancelled(true);
        callback.getValue().handle(event);
        assertEquals(true, event.isCancelled());
    }
}
