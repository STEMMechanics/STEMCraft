package dev.stemcraft.service.minigame;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniGameRespawnProtectionTest {
    @Test
    void blocksPlayerRespawnPointChangesInsideMinigames() {
        MiniGameServiceImpl service = new MiniGameServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));
        MiniGameArenaImpl arena = mock(MiniGameArenaImpl.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        service.registerPlayerArena(player, arena, false);

        PlayerSetSpawnEvent event = new PlayerSetSpawnEvent(
            player, PlayerSetSpawnEvent.Cause.BED, mock(Location.class), false, true, Component.empty()
        );
        service.handleSpawnChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void allowsInternalRespawnSwapInsideMinigames() {
        MiniGameServiceImpl service = new MiniGameServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));
        MiniGameArenaImpl arena = mock(MiniGameArenaImpl.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        service.registerPlayerArena(player, arena, false);

        PlayerSetSpawnEvent event = new PlayerSetSpawnEvent(
            player, PlayerSetSpawnEvent.Cause.PLUGIN, null, false, false, Component.empty()
        );
        service.handleSpawnChange(event);

        assertFalse(event.isCancelled());
    }
}
