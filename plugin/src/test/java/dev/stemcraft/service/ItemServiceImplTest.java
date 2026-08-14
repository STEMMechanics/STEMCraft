package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ItemServiceImplTest {
    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) MockBukkit.unmock();
    }

    @Test
    void itemNamePrefersCustomDisplayNameOverBackingMaterial() {
        MockBukkit.mock();
        ItemServiceImpl service = new ItemServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));
        ItemStack gift = new ItemStack(Material.STICK);
        ItemMeta meta = gift.getItemMeta();
        meta.displayName(Component.text("Gift"));
        gift.setItemMeta(meta);

        assertEquals("Gift", service.getItemName(gift));
        assertEquals("Cobblestone Wall", service.getItemName(new ItemStack(Material.COBBLESTONE_WALL)));
    }
}
