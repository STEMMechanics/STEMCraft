package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.item.BedrockItemVisualDefinition;
import dev.stemcraft.api.service.item.CustomItemClientDefinition;
import dev.stemcraft.api.service.item.JavaItemVisualDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

class ItemServiceImplTest {
    static java.util.stream.Stream<Class<? extends InventoryHolder>> storageHolders() {
        return java.util.stream.Stream.of(org.bukkit.block.Chest.class, org.bukkit.block.Barrel.class,
            org.bukkit.block.ShulkerBox.class, org.bukkit.block.DoubleChest.class,
            org.bukkit.entity.minecart.StorageMinecart.class, org.bukkit.entity.ChestedHorse.class);
    }

    @ParameterizedTest
    @MethodSource("storageHolders")
    void closingStorageRemovesOnlyFlaggedStacks(Class<? extends InventoryHolder> holderType) {
        MockBukkit.mock();
        ItemServiceImpl service = itemService();
        Inventory inventory = storageInventory(mock(holderType));
        ItemStack disposable = new ItemStack(Material.WRITTEN_BOOK, 2);
        service.addAttrib(disposable, "destroy-on-drop", 1);
        ItemStack retained = disposable.clone();
        service.addAttrib(retained, "destroy-on-drop", 0);
        ItemStack noDrop = new ItemStack(Material.BOOK);
        service.addAttrib(noDrop, "no-drop", 1);
        inventory.setItem(0, disposable);
        inventory.setItem(inventory.getSize() - 1, disposable.clone());
        inventory.setItem(13, retained);
        inventory.setItem(2, noDrop);
        close(service, inventory);
        assertNull(inventory.getItem(0));
        assertNull(inventory.getItem(inventory.getSize() - 1));
        assertEquals(retained, inventory.getItem(13));
        assertEquals(noDrop, inventory.getItem(2));
        close(service, inventory); // Closing a second time leaves ordinary items intact.
        assertEquals(retained, inventory.getItem(13));
    }

    @Test
    void enderChestCleanupLeavesThePlayersOwnCopyAlone() {
        var server = MockBukkit.mock();
        ItemServiceImpl service = itemService();
        var player = server.addPlayer();
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        service.addAttrib(book, "destroy-on-drop", 1);
        player.getInventory().setItem(0, book.clone());
        player.getEnderChest().setItem(0, book.clone());
        assertEquals(InventoryType.ENDER_CHEST, player.getEnderChest().getType());
        close(service, player.getEnderChest());
        assertNull(player.getEnderChest().getItem(0));
        assertNotNull(player.getInventory().getItem(0));
        close(service, player.getInventory());
        assertNotNull(player.getInventory().getItem(0));
    }

    @Test
    void closingVirtualMenuDoesNotDeleteDisplayItems() {
        MockBukkit.mock();
        ItemServiceImpl service = itemService();
        Inventory menu = storageInventory(null);
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        service.addAttrib(book, "destroy-on-drop", 1);
        menu.setItem(0, book);
        close(service, menu);
        assertEquals(book, menu.getItem(0));
    }

    private ItemServiceImpl itemService() {
        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getName()).thenReturn("STEMCraft");
        when(plugin.namespace()).thenReturn("stemcraft");
        return new ItemServiceImpl(plugin, mock(STEMCraftAPI.class));
    }

    private Inventory storageInventory(@org.jetbrains.annotations.Nullable InventoryHolder holder) {
        return org.bukkit.Bukkit.createInventory(holder, holder instanceof org.bukkit.block.DoubleChest ? 54 : 27);
    }

    private void close(ItemServiceImpl service, Inventory inventory) {
        // MockBukkit lacks getHolder(false); delegate contents to the real test inventory.
        Inventory supported = mock(Inventory.class, org.mockito.AdditionalAnswers.delegatesTo(inventory));
        org.mockito.Mockito.doReturn(inventory.getHolder()).when(supported).getHolder(false);
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(supported);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.handleInventoryClose(event));
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) MockBukkit.unmock();
    }

    @Test
    void namespacedPortalItemsPreserveUnderscoresAndLegacyHyphenAliasesStillWork() {
        MockBukkit.mock();
        STEMCraft plugin = mock(STEMCraft.class);
        org.mockito.Mockito.when(plugin.getName()).thenReturn("STEMCraft");
        org.mockito.Mockito.when(plugin.namespace()).thenReturn("stemcraft");
        var service = new ItemServiceImpl(plugin, mock(STEMCraftAPI.class));
        for (String id : List.of("echo_core", "sky_eye", "legacy-item")) {
            service.registerCustomItem(id, new ItemStack(Material.ECHO_SHARD));
            ItemStack created = service.createCustomItem("stemcraft:" + id.replace('-', '_'), 2);
            org.junit.jupiter.api.Assertions.assertNotNull(created);
            assertEquals(2, created.getAmount()); assertTrue(service.isCustomItemId(id, created));
        }
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
        var itemModel = item.getItemMeta().getItemModel();
        assertNotNull(itemModel);
        assertEquals("stemcraft_slime_bucket:slime_bucket_excited", itemModel.asString());

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
