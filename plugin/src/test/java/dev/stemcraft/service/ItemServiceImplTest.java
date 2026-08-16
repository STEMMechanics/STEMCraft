package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.item.BedrockItemVisualDefinition;
import dev.stemcraft.api.service.item.CustomItemClientDefinition;
import dev.stemcraft.api.service.item.JavaItemVisualDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

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

    @Test
    void visualStateChangesModelsWithoutChangingLogicalItemData() {
        MockBukkit.mock();
        ItemStack template = new ItemStack(Material.BUCKET);
        ItemMeta templateMeta = template.getItemMeta();
        templateMeta.displayName(Component.text("Slime in a Bucket"));
        templateMeta.lore(List.of(Component.text("Still the same slime")));
        template.setItemMeta(templateMeta);
        CustomItemClientDefinition normal = visuals(51001, "stemcraft_slime_bucket:slime_bucket");
        CustomItemClientDefinition excited = visuals(51002, "stemcraft_slime_bucket:slime_bucket_excited");
        ItemStack item = template.clone();
        item.setAmount(1);
        assertTrue(ItemServiceImpl.applyClientVisual(item, excited));
        assertEquals(1, item.getAmount());
        assertEquals(Component.text("Slime in a Bucket"), item.getItemMeta().displayName());
        assertEquals(List.of(Component.text("Still the same slime")), item.getItemMeta().lore());
        assertEquals("stemcraft_slime_bucket:slime_bucket_excited", item.getItemMeta().getItemModel().asString());

        assertTrue(ItemServiceImpl.applyClientVisual(item, normal));
        assertEquals("stemcraft_slime_bucket:slime_bucket", item.getItemMeta().getItemModel().asString());
    }

    private CustomItemClientDefinition visuals(int modelData, String model) {
        return new CustomItemClientDefinition(
            new JavaItemVisualDefinition(modelData, model, model, "stemcraft_slime_buckets:item/slime_bucket"),
            new BedrockItemVisualDefinition(model, model.substring(model.indexOf(':') + 1),
                "stemcraft_slime_buckets:item/slime_bucket", "Slime in a Bucket"));
    }
}
