package dev.stemcraft.service.spellbook;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.spellbook.SpellBookExtensionContext;
import dev.stemcraft.api.service.spellbook.SpellBookMatch;
import dev.stemcraft.api.service.spellbook.SpellBookService;
import dev.stemcraft.api.service.spellbook.SpellBookSource;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpellBookProtectionExtensionsTest {
    @Test
    void noFallDeathReducesLethalFallDamageWhenSpellMatches() {
        EventService events = mock(EventService.class);
        AtomicReference<EventHandler<EntityDamageEvent>> handlerRef = new AtomicReference<>();
        when(events.register(eq(EntityDamageEvent.class), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            EventHandler<EntityDamageEvent> handler = invocation.getArgument(1);
            handlerRef.set(handler);
            return mock(Listener.class);
        });

        SpellBookService spellBooks = mock(SpellBookService.class);
        when(spellBooks.isEnabled()).thenReturn(true);

        ConfigSection config = config(true, "fall no die", "inventory");
        new NoFallDeathSpellBookExtension().register(context(events, spellBooks, config));

        Player player = mock(Player.class);
        when(player.getHealth()).thenReturn(10.0d);
        when(spellBooks.findSpell(player, SpellBookSource.INVENTORY, "fall no die")).thenReturn(mock(SpellBookMatch.class));

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(event.getEntity()).thenReturn(player);
        when(event.getFinalDamage()).thenReturn(20.0d);

        handlerRef.get().handle(event);

        verify(event).setDamage(9.0d);
    }

    @Test
    void noFallDeathLeavesNonMatchingPlayersAlone() {
        EventService events = mock(EventService.class);
        AtomicReference<EventHandler<EntityDamageEvent>> handlerRef = new AtomicReference<>();
        when(events.register(eq(EntityDamageEvent.class), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            EventHandler<EntityDamageEvent> handler = invocation.getArgument(1);
            handlerRef.set(handler);
            return mock(Listener.class);
        });

        SpellBookService spellBooks = mock(SpellBookService.class);
        when(spellBooks.isEnabled()).thenReturn(true);

        ConfigSection config = config(true, "fall no die", "inventory");
        new NoFallDeathSpellBookExtension().register(context(events, spellBooks, config));

        Player player = mock(Player.class);
        when(player.getHealth()).thenReturn(10.0d);

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(event.getEntity()).thenReturn(player);
        when(event.getFinalDamage()).thenReturn(20.0d);

        handlerRef.get().handle(event);

        verify(event, never()).setDamage(anyDouble());
    }

    @Test
    void keepInventoryRetainsItemsAndClearsDropsWhenSpellMatches() {
        EventService events = mock(EventService.class);
        AtomicReference<EventHandler<PlayerDeathEvent>> handlerRef = new AtomicReference<>();
        when(events.register(eq(PlayerDeathEvent.class), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            EventHandler<PlayerDeathEvent> handler = invocation.getArgument(1);
            handlerRef.set(handler);
            return mock(Listener.class);
        });

        SpellBookService spellBooks = mock(SpellBookService.class);
        when(spellBooks.isEnabled()).thenReturn(true);

        ConfigSection config = config(true, "die keep inventory", "inventory");
        new KeepInventorySpellBookExtension().register(context(events, spellBooks, config));

        Player player = mock(Player.class);
        when(spellBooks.findSpell(player, SpellBookSource.INVENTORY, "die keep inventory")).thenReturn(mock(SpellBookMatch.class));

        List<ItemStack> drops = new ArrayList<>();
        drops.add(mock(ItemStack.class));

        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(drops);

        handlerRef.get().handle(event);

        verify(event).setKeepInventory(true);
        assertTrue(drops.isEmpty());
    }

    @Test
    void keepInventoryDoesNothingWithoutSpellMatch() {
        EventService events = mock(EventService.class);
        AtomicReference<EventHandler<PlayerDeathEvent>> handlerRef = new AtomicReference<>();
        when(events.register(eq(PlayerDeathEvent.class), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            EventHandler<PlayerDeathEvent> handler = invocation.getArgument(1);
            handlerRef.set(handler);
            return mock(Listener.class);
        });

        SpellBookService spellBooks = mock(SpellBookService.class);
        when(spellBooks.isEnabled()).thenReturn(true);

        ConfigSection config = config(true, "die keep inventory", "inventory");
        new KeepInventorySpellBookExtension().register(context(events, spellBooks, config));

        Player player = mock(Player.class);
        List<ItemStack> drops = new ArrayList<>();
        drops.add(mock(ItemStack.class));

        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(drops);

        handlerRef.get().handle(event);

        verify(event, never()).setKeepInventory(true);
        assertEquals(1, drops.size());
    }

    private SpellBookExtensionContext context(@NotNull EventService events,
                                              @NotNull SpellBookService spellBooks,
                                              @NotNull ConfigSection config) {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.events()).thenReturn(events);
        return new SpellBookExtensionContextImpl(api, spellBooks, () -> config);
    }

    @SuppressWarnings("SameParameterValue")
    private ConfigSection config(boolean enabled, @NotNull String spell, @NotNull String source) {
        ConfigSection config = mock(ConfigSection.class);
        when(config.getBoolean("enabled", true)).thenReturn(enabled);
        when(config.getString("spell", spell)).thenReturn(spell);
        when(config.getString("source", "inventory")).thenReturn(source);
        when(config.getString("negative", "none")).thenReturn("none");
        return config;
    }
}
