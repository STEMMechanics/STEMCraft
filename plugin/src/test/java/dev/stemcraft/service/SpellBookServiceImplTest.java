package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.spellbook.SpellBookExtension;
import dev.stemcraft.api.service.spellbook.SpellBookExtensionContext;
import dev.stemcraft.api.service.spellbook.SpellBookMatch;
import dev.stemcraft.api.service.spellbook.SpellBookSource;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpellBookServiceImplTest {
    private SpellBookServiceImpl service;

    @BeforeEach
    void setUp() {
        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getName()).thenReturn("STEMCraft");

        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ConfigService configService = mock(ConfigService.class);
        ConfigFile rootConfig = mock(ConfigFile.class);
        ConfigSection spellBooksConfig = mock(ConfigSection.class);
        ConfigSection extensionConfig = mock(ConfigSection.class);
        when(api.config()).thenReturn(configService);
        when(configService.load("config.yml")).thenReturn(rootConfig);
        when(rootConfig.isSection("spell-books")).thenReturn(true);
        when(rootConfig.getSection("spell-books", false)).thenReturn(spellBooksConfig);
        when(spellBooksConfig.getSection("extensions.test-extension")).thenReturn(extensionConfig);

        service = new SpellBookServiceImpl(plugin, api);
    }

    @Test
    void readParsesSinglePageWrittenBook() {
        ItemStack book = spellBook("  Yo   go\nto   bo zo du  ");

        var spell = service.read(book);

        assertNotNull(spell);
        assertEquals("Yo   go\nto   bo zo du", spell.spell());
        assertEquals("yo go to bo zo du", spell.normalizedSpell());
    }

    @Test
    void readRejectsMultiPageBooks() {
        ItemStack book = spellBook(List.of("me chest be locked", "extra page"));

        assertNull(service.read(book));
    }

    @Test
    void inventoryLookupFindsMatchingSpellBook() {
        Inventory inventory = mock(Inventory.class);
        ItemStack[] contents = new ItemStack[9];
        contents[1] = spellBook("me chest be locked");
        contents[4] = spellBook("do zo ham");
        when(inventory.getContents()).thenReturn(contents);

        SpellBookMatch match = service.findSpell(inventory, "do zo ham");

        assertNotNull(match);
        assertEquals(4, match.slot());
        assertEquals("do zo ham", match.spellBook().normalizedSpell());
    }

    @Test
    void playerSourceLookupReadsHandsAndInventory() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        ItemStack mainHand = spellBook("yo go to bo zo du");
        ItemStack offHand = spellBook("me chest be locked");
        ItemStack[] storage = new ItemStack[9];
        storage[2] = mainHand;
        storage[5] = spellBook("do zo ham");

        when(inventory.getStorageContents()).thenReturn(storage);
        when(inventory.getItemInMainHand()).thenReturn(mainHand);
        when(inventory.getItemInOffHand()).thenReturn(offHand);
        when(inventory.getHeldItemSlot()).thenReturn(2);

        List<SpellBookMatch> hands = service.getSpellBooks(player, SpellBookSource.HANDS);
        List<SpellBookMatch> inventoryMatches = service.getSpellBooks(player, SpellBookSource.INVENTORY);

        assertEquals(2, hands.size());
        assertEquals(3, inventoryMatches.size());
        assertNotNull(service.findSpell(player, SpellBookSource.MAIN_HAND, "yo go to bo zo du"));
        assertNotNull(service.findSpell(player, SpellBookSource.OFF_HAND, "me chest be locked"));
        assertNotNull(service.findSpell(player, SpellBookSource.INVENTORY, "do zo ham"));
    }

    @Test
    void ownerMetadataRoundTrips() {
        ItemStack book = spellBook("me chest be locked");
        UUID ownerId = UUID.randomUUID();

        service.setOwner(book, ownerId);

        assertEquals(ownerId, service.getOwner(book));
        assertTrue(service.hasSpell(book, "me chest be locked"));

        service.setOwner(book, null);

        assertNull(service.getOwner(book));
    }

    @Test
    void registerExtensionInvokesSamePathBuiltInsUse() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicReference<SpellBookExtensionContext> seenContext = new AtomicReference<>();

        SpellBookExtension extension = new SpellBookExtension() {
            @Override
            public @NotNull String id() {
                return "test-extension";
            }

            @Override
            public void register(@NotNull SpellBookExtensionContext context) {
                registrations.incrementAndGet();
                seenContext.set(context);
            }
        };

        service.registerExtension(extension);

        assertEquals(1, registrations.get());
        assertNotNull(seenContext.get());
        assertEquals(service, seenContext.get().spellBooks());
        assertNotNull(seenContext.get().config());
        assertThrows(IllegalArgumentException.class, () -> service.registerExtension(extension));
    }

    @Test
    void nonWrittenBooksAreNotSpellBooks() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.BOOK);

        assertFalse(service.isSpellBook(item));
    }

    private ItemStack spellBook(String spell) {
        return spellBook(List.of(spell));
    }

    private ItemStack spellBook(List<String> pages) {
        ItemStack item = mock(ItemStack.class);
        BookMeta meta = mock(BookMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        AtomicReference<String> owner = new AtomicReference<>();

        when(item.getType()).thenReturn(Material.WRITTEN_BOOK);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.setItemMeta(meta)).thenReturn(true);
        when(meta.pages()).thenReturn(pages.stream().<Component>map(Component::text).toList());
        when(meta.getPersistentDataContainer()).thenReturn(container);

        doAnswer(invocation -> {
            PersistentDataType<?, ?> type = invocation.getArgument(1);
            Object value = invocation.getArgument(2);
            if (type == PersistentDataType.STRING) {
                owner.set((String) value);
            }
            return null;
        }).when(container).set(any(), eq(PersistentDataType.STRING), any(String.class));

        doAnswer(invocation -> {
            owner.set(null);
            return null;
        }).when(container).remove(any());

        when(container.get(any(), eq(PersistentDataType.STRING))).thenAnswer(invocation -> owner.get());

        return item;
    }
}
