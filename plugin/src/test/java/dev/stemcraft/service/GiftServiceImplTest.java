package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.item.ItemService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GiftServiceImplTest {
    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) MockBukkit.unmock();
    }

    @Test
    void serializedGiftPreservesMultipleStacks() throws Exception {
        MockBukkit.mock();
        Method serialize = GiftServiceImpl.class.getDeclaredMethod("serialize", List.class);
        Method deserialize = GiftServiceImpl.class.getDeclaredMethod("deserialize", byte[].class);
        serialize.setAccessible(true);
        deserialize.setAccessible(true);

        byte[] bytes = (byte[]) serialize.invoke(null,
            List.of(new ItemStack(Material.EMERALD, 3), new ItemStack(Material.EGG, 2)));
        @SuppressWarnings("unchecked")
        List<ItemStack> result = (List<ItemStack>) deserialize.invoke(null, bytes);

        assertEquals(2, result.size());
        assertEquals(new ItemStack(Material.EMERALD, 3), result.get(0));
        assertEquals(new ItemStack(Material.EGG, 2), result.get(1));
    }

    @Test
    void parsesVanillaQuantityRangeAndDelegatesCustomProperties() {
        MockBukkit.mock();
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ItemService items = mock(ItemService.class);
        when(api.items()).thenReturn(items);
        when(items.createCustomItem(anyString(), anyInt())).thenReturn(null);
        when(items.createCustomItem(eq("stemcraft:animal_crate[animal=chicken]"), eq(1)))
            .thenReturn(new ItemStack(Material.BARREL));
        GiftServiceImpl service = new GiftServiceImpl(mock(STEMCraft.class), api);

        ItemStack emeralds = service.createItem("emerald,2-4");
        assertNotNull(emeralds);
        assertEquals(Material.EMERALD, emeralds.getType());
        assertTrue(emeralds.getAmount() >= 2 && emeralds.getAmount() <= 4);
        assertEquals(Material.BARREL,
            service.createItem("stemcraft:animal_crate[animal=chicken]").getType());
    }
}
