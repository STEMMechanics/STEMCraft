package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RestrictCreativeBlockDropsTest {
    private EventHandler<BlockDropItemEvent> callback;
    private Player player;

    @BeforeEach
    void setUp() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        when(api.events()).thenReturn(events);
        new RestrictCreative(api).onEnable();
        ArgumentCaptor<EventHandler<BlockDropItemEvent>> captured = ArgumentCaptor.captor();
        verify(events).register(eq(BlockDropItemEvent.class), captured.capture(),
            eq(EventPriority.HIGHEST), eq(true));
        callback = captured.getValue();
        player = mock(Player.class);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"JUKEBOX", "CHEST", "BARREL", "FURNACE", "SHULKER_BOX", "DECORATED_POT"})
    void suppressesAllCapturedContainerDrops(Material type) {
        BlockDropItemEvent event = drops(type);
        callback.handle(event);
        assertTrue(event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "ADVENTURE", "SPECTATOR"})
    void leavesOtherGameModesUnchanged(GameMode mode) {
        when(player.getGameMode()).thenReturn(mode);
        BlockDropItemEvent event = drops(Material.CHEST);
        callback.handle(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void existingDropOverrideAllowsCreativeContainerDrops() {
        when(player.hasPermission("stemcraft.creative.override.drop_items")).thenReturn(true);
        BlockDropItemEvent event = drops(Material.JUKEBOX);
        callback.handle(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void inventoryOverrideDoesNotGrantDropOverride() {
        when(player.hasPermission("stemcraft.creative.override.inventory")).thenReturn(true);
        BlockDropItemEvent event = drops(Material.CHEST);
        callback.handle(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void neverUncancelsAnotherPluginsDropRestriction() {
        when(player.hasPermission("stemcraft.creative.override.drop_items")).thenReturn(true);
        BlockDropItemEvent event = drops(Material.CHEST);
        event.setCancelled(true);
        callback.handle(event);
        assertTrue(event.isCancelled());
    }

    private BlockDropItemEvent drops(Material brokenType) {
        Block block = mock(Block.class);
        // Paper supplies the old state; the live block is already air by this event.
        when(block.getType()).thenReturn(Material.AIR);
        BlockState state = mock(BlockState.class);
        when(state.getType()).thenReturn(brokenType);
        return new BlockDropItemEvent(block, state, player,
            new ArrayList<>(List.of(mock(Item.class), mock(Item.class))));
    }
}
