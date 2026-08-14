package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.database.DatabaseService;
import dev.stemcraft.api.service.hologram.HologramService;
import dev.stemcraft.api.service.item.ItemService;
import dev.stemcraft.api.service.mailbox.MailSendRequest;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.placedobject.PlacedBlockRef;
import dev.stemcraft.api.service.placedobject.PlacedObject;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailboxesTest {

    @Test
    void explicitMailDelayOverridesConfiguredDeliveryDelay() throws Exception {
        Mailboxes mailboxes = new Mailboxes(mock(STEMCraftAPI.class));
        Field delay = Mailboxes.class.getDeclaredField("deliveryBaseDelayTicks");
        delay.setAccessible(true);
        delay.setLong(mailboxes, 72_000L);
        long queuedAt = 1_000_000L;

        assertEquals(queuedAt, invokePrivate(mailboxes, "resolveDeliverAfter",
            new Class<?>[] { long.class, long.class }, queuedAt, 0L));
        assertEquals(queuedAt + 15_000L, invokePrivate(mailboxes, "resolveDeliverAfter",
            new Class<?>[] { long.class, long.class }, queuedAt, 300L));
        assertEquals(queuedAt + 3_600_000L, invokePrivate(mailboxes, "resolveDeliverAfter",
            new Class<?>[] { long.class, long.class }, queuedAt, -1L));

        UUID recipient = UUID.randomUUID();
        assertEquals(0L, new MailSendRequest("Rewards", recipient, "Winner", java.util.List.of(), 0L).deliveryDelayTicks());
        assertEquals(-1L, new MailSendRequest("Rewards", recipient, "Normal", java.util.List.of()).deliveryDelayTicks());
    }

    @Test
    void mailLetterIncludesSenderMessageAndItems() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ItemService items = mock(ItemService.class);
        when(api.items()).thenReturn(items);
        when(items.getItemName(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack item = invocation.getArgument(0);
            return item.getType() == Material.DIAMOND ? "Diamond" : "Bread";
        });
        Mailboxes mailboxes = new Mailboxes(api);

        @SuppressWarnings("unchecked")
        java.util.List<net.kyori.adventure.text.Component> pages =
            (java.util.List<net.kyori.adventure.text.Component>) invokePrivate(
            mailboxes,
            "buildMailLetterPages",
            new Class<?>[] { String.class, String.class, java.util.List.class },
            "Alex",
            "Enjoy these supplies!",
            java.util.List.of(new ItemStack(Material.DIAMOND, 3), new ItemStack(Material.BREAD, 5))
        );

        String contents = pages.stream()
            .map(PlainTextComponentSerializer.plainText()::serialize)
            .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(contents.contains("From: Alex"));
        assertTrue(contents.contains("Enjoy these supplies!"));
        assertTrue(contents.contains("3x Diamond"));
        assertTrue(contents.contains("5x Bread"));
    }

    @Test
    void mailLetterUsesItemServiceNameForCustomItems() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ItemService items = mock(ItemService.class);
        when(api.items()).thenReturn(items);
        ItemStack gift = new ItemStack(Material.STICK);
        when(items.getItemName(gift)).thenReturn("Gift");
        Mailboxes mailboxes = new Mailboxes(api);

        @SuppressWarnings("unchecked")
        java.util.List<net.kyori.adventure.text.Component> pages =
            (java.util.List<net.kyori.adventure.text.Component>) invokePrivate(
                mailboxes, "buildMailLetterPages",
                new Class<?>[] { String.class, String.class, java.util.List.class },
                "Minigame Rewards", "Congratulations!", java.util.List.of(gift));

        String contents = pages.stream()
            .map(PlainTextComponentSerializer.plainText()::serialize)
            .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(contents.contains("1x Gift"));
        assertFalse(contents.contains("1x Stick"));
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void normalizeRecipientNamePreservesBedrockPrefix() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        Mailboxes mailboxes = new Mailboxes(api);

        Object recipient = invokePrivate(
            mailboxes,
            "normalizeRecipientName",
            new Class<?>[] { String.class },
            "  *nomadjimbob  "
        );

        assertEquals("*nomadjimbob", recipient);
    }

    @Test
    void closeEventTreatsClosingPlayerAsLastViewer() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("mailbox-tests");
        PlayerMock player = server.addPlayer("owner");
        player.teleport(world.getSpawnLocation());

        STEMCraftAPI api = mock(STEMCraftAPI.class);
        Mailboxes mailboxes = new Mailboxes(api);

        InventoryHolder holder = (InventoryHolder) invokePrivate(
            mailboxes,
            "newMailboxInventoryHolder",
            new Class<?>[] { UUID.class, UUID.class, UUID.class, org.bukkit.Location.class },
            UUID.randomUUID(),
            UUID.randomUUID(),
            player.getUniqueId(),
            world.getSpawnLocation()
        );
        Inventory inventory = server.createInventory(holder, org.bukkit.event.inventory.InventoryType.BARREL, "Mailbox");
        player.openInventory(inventory);
        InventoryCloseEvent event = new InventoryCloseEvent(player.getOpenInventory());

        Object result = invokePrivate(
            mailboxes,
            "hasOtherViewers",
            new Class<?>[] { Inventory.class, InventoryCloseEvent.class },
            inventory,
            event
        );

        assertTrue(Boolean.FALSE.equals(result));
    }

    @Test
    void onDisablePersistsOpenMailboxInventoriesToPlayerInbox() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("mailbox-tests");
        PlayerMock player = server.addPlayer("owner");
        player.teleport(world.getSpawnLocation());

        DatabaseService database = mock(DatabaseService.class);
        TaskService tasks = mock(TaskService.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.database()).thenReturn(database);
        when(api.tasks()).thenReturn(tasks);

        Mailboxes mailboxes = new Mailboxes(api);
        InventoryHolder holder = (InventoryHolder) invokePrivate(
            mailboxes,
            "newMailboxInventoryHolder",
            new Class<?>[] { UUID.class, UUID.class, UUID.class, org.bukkit.Location.class },
            UUID.randomUUID(),
            UUID.randomUUID(),
            player.getUniqueId(),
            world.getSpawnLocation()
        );
        Inventory inventory = server.createInventory(holder, org.bukkit.event.inventory.InventoryType.BARREL, "Mailbox");
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 3));

        Field field = Mailboxes.class.getDeclaredField("openMailboxInventories");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Inventory> openInventories = (Map<UUID, Inventory>) field.get(mailboxes);
        openInventories.put(UUID.randomUUID(), inventory);

        mailboxes.onDisable();

        verify(database).update(any(), any());
        verify(tasks).cancel("mailbox:delivery-queue");
    }

    @Test
    void resolveRecipientPreservesResolvedIdentity() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("mailbox-tests");
        PlayerMock player = server.addPlayer("owner");
        player.teleport(world.getSpawnLocation());

        PlayerService players = mock(PlayerService.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.players()).thenReturn(players);
        when(players.resolveIdentity("*owner")).thenReturn(new PlayerService.ResolvedPlayer(player.getUniqueId(), player.getName(), "java"));

        Mailboxes mailboxes = new Mailboxes(api);
        Object recipient = invokePrivate(
            mailboxes,
            "resolveRecipient",
            new Class<?>[] { String.class },
            "*owner"
        );

        Method uuid = recipient.getClass().getDeclaredMethod("uuid");
        uuid.setAccessible(true);
        assertEquals(player.getUniqueId(), uuid.invoke(recipient));
    }

    @Test
    void closeOpenMailboxSessionClosesAllSessionsForMailbox() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("mailbox-tests");
        PlayerMock owner = server.addPlayer("owner");
        PlayerMock other = server.addPlayer("other");

        DatabaseService database = mock(DatabaseService.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.database()).thenReturn(database);
        Mailboxes mailboxes = new Mailboxes(api);

        UUID mailboxId = UUID.randomUUID();
        UUID ownerSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        InventoryHolder ownerHolder = (InventoryHolder) invokePrivate(
            mailboxes,
            "newMailboxInventoryHolder",
            new Class<?>[] { UUID.class, UUID.class, UUID.class, org.bukkit.Location.class },
            ownerSessionId,
            mailboxId,
            owner.getUniqueId(),
            world.getSpawnLocation()
        );
        InventoryHolder otherHolder = (InventoryHolder) invokePrivate(
            mailboxes,
            "newMailboxInventoryHolder",
            new Class<?>[] { UUID.class, UUID.class, UUID.class, org.bukkit.Location.class },
            otherSessionId,
            mailboxId,
            other.getUniqueId(),
            world.getSpawnLocation()
        );
        Inventory ownerInventory = server.createInventory(ownerHolder, org.bukkit.event.inventory.InventoryType.BARREL, "Mailbox");
        Inventory otherInventory = server.createInventory(otherHolder, org.bukkit.event.inventory.InventoryType.BARREL, "Mailbox");
        ownerInventory.setItem(0, new ItemStack(Material.DIAMOND, 1));
        otherInventory.setItem(0, new ItemStack(Material.EMERALD, 1));

        Field field = Mailboxes.class.getDeclaredField("openMailboxInventories");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Inventory> openInventories = (Map<UUID, Inventory>) field.get(mailboxes);
        openInventories.put(ownerSessionId, ownerInventory);
        openInventories.put(otherSessionId, otherInventory);

        invokePrivate(
            mailboxes,
            "closeOpenMailboxSession",
            new Class<?>[] { UUID.class },
            mailboxId
        );

        assertTrue(openInventories.isEmpty());
        verify(database, atLeast(2)).update(any(), any());
    }

    @Test
    void registersMailboxHologramByPlacedObjectId() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("mailbox-tests");
        UUID mailboxId = UUID.randomUUID();
        PlacedObject mailbox = new PlacedObject(
            mailboxId,
            Mailboxes.MAILBOX_OBJECT_TYPE,
            null,
            PlacedBlockRef.of(world.getSpawnLocation()),
            System.currentTimeMillis(),
            System.currentTimeMillis()
        );
        HologramService holograms = mock(HologramService.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        when(api.holograms()).thenReturn(holograms);
        Mailboxes mailboxes = new Mailboxes(api);

        invokePrivate(mailboxes, "registerMailboxHologram", new Class<?>[] { PlacedObject.class }, mailbox);

        verify(holograms).createDynamic(
            eq("mailbox"),
            eq(mailboxId.toString()),
            any(org.bukkit.Location.class),
            org.mockito.ArgumentMatchers.<java.util.function.Predicate<org.bukkit.entity.Player>>any(),
            org.mockito.ArgumentMatchers.<java.util.function.Function<org.bukkit.entity.Player,
                net.kyori.adventure.text.Component>>any()
        );
    }

    private static Object invokePrivate(@NotNull Object target,
                                        @NotNull String methodName,
                                        @NotNull Class<?>[] parameterTypes,
                                        Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
