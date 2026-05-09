package dev.stemcraft.service.spellbook;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.spellbook.SpellBook;
import dev.stemcraft.api.service.spellbook.SpellBookMatch;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpellBookNegativeEffectTest {
    @Test
    void consumeClearsSingleBookFromStorageSlot() {
        ConfigSection config = config("consume");
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        ItemStack item = itemStack(1);
        SpellBookNegativeEffect.applyConfigured(player, match(item, 2), config);

        verify(inventory).setItem(2, null);
        verify(player, never()).getWorld();
    }

    @Test
    void dropRemovesOneBookAndDropsCloneFromOffHand() {
        ConfigSection config = config("drop");
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(world.dropItemNaturally(eq(location), any(ItemStack.class))).thenReturn(mock(Item.class));

        ItemStack item = itemStack(2);
        ItemStack clone = itemStack(1);
        when(item.clone()).thenReturn(clone);

        SpellBookNegativeEffect.applyConfigured(player, match(item, 40), config);

        verify(inventory).setItemInOffHand(item);
        verify(world).dropItemNaturally(location, clone);
    }

    @Test
    void noneDoesNothing() {
        ConfigSection config = config("none");
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        SpellBookNegativeEffect.applyConfigured(player, match(itemStack(1), 0), config);

        verify(inventory, never()).setItem(any(Integer.class), any());
        verify(inventory, never()).setItemInOffHand(any());
        verify(player, never()).getWorld();
    }

    private @NotNull ConfigSection config(@NotNull String negative) {
        ConfigSection config = mock(ConfigSection.class);
        when(config.getString("negative", "none")).thenReturn(negative);
        return config;
    }

    private @NotNull SpellBookMatch match(@NotNull ItemStack item, int slot) {
        return new SpellBookMatch(item, slot, new SpellBook("spell", "spell", UUID.randomUUID()));
    }

    private @NotNull ItemStack itemStack(int amount) {
        ItemStack item = mock(ItemStack.class);
        AtomicInteger currentAmount = new AtomicInteger(amount);
        when(item.getAmount()).thenAnswer(invocation -> currentAmount.get());
        when(item.clone()).thenReturn(item);
        org.mockito.Mockito.doAnswer(invocation -> {
            currentAmount.set(invocation.getArgument(0));
            return null;
        }).when(item).setAmount(any(Integer.class));
        return item;
    }
}
