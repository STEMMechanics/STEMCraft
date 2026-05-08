/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.spellbook.SpellBook;
import dev.stemcraft.api.service.spellbook.SpellBookExtension;
import dev.stemcraft.api.service.spellbook.SpellBookMatch;
import dev.stemcraft.api.service.spellbook.SpellBookService;
import dev.stemcraft.api.service.spellbook.SpellBookSource;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.stemcraft.service.spellbook.DoublePigDropsSpellBookExtension;
import dev.stemcraft.service.spellbook.LockedChestSpellBookExtension;
import dev.stemcraft.service.spellbook.SpellBookExtensionContextImpl;
import dev.stemcraft.service.spellbook.HomeTeleportSpellBookExtension;
import dev.stemcraft.service.spellbook.NoBiteSpellBookExtension;
import dev.stemcraft.service.spellbook.RainGoWaySpellBookExtension;
import dev.stemcraft.service.spellbook.TreeFallSpellBookExtension;
import dev.stemcraft.service.spellbook.QuickFishingSpellBookExtension;
import dev.stemcraft.service.spellbook.WorldTeleportSpellBookExtension;

/**
 * Implementation of the spell-book service.
 */
public class SpellBookServiceImpl extends BaseService implements SpellBookService {
    private static final String OWNER_KEY = "spell-book-owner";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int OFF_HAND_SLOT = 40;

    private final Map<String, SpellBookExtension> extensions = new LinkedHashMap<>();
    private final Map<String, UUID> containerSpellOwners = new HashMap<>();
    private boolean enabled = true;

    /**
     * Constructor for SpellBookServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public SpellBookServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "spell-books");
    }

    @Override
    public void onEnable() {
        refreshConfig();
        loadBuiltInExtensions();
    }

    @Override
    public void onReload() {
        super.onReload();
        refreshConfig();
    }

    @Override
    public void onDisable() {
        containerSpellOwners.clear();
        extensions.clear();
    }

    private void refreshConfig() {
        enabled = getConfigSection().getBoolean("enabled", true);
        if (!enabled) {
            containerSpellOwners.clear();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public @NotNull String normalizeSpell(@NotNull String spell) {
        String normalized = TextUtil.plain(Component.text(spell));
        normalized = normalized.replace('\n', ' ').replace('\r', ' ');
        normalized = WHITESPACE.matcher(normalized.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
        return normalized;
    }

    @Override
    public @Nullable SpellBook read(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            return null;
        }
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return null;
        }

        List<String> nonEmptyPages = new ArrayList<>();
        for (Component page : meta.pages()) {
            String plain = TextUtil.plain(page);
            String trimmed = plain.trim();
            if (!trimmed.isEmpty()) {
                nonEmptyPages.add(trimmed);
            }
        }

        if (nonEmptyPages.size() != 1) {
            return null;
        }

        String spell = nonEmptyPages.getFirst();
        String normalizedSpell = normalizeSpell(spell);
        if (normalizedSpell.isEmpty()) {
            return null;
        }

        return new SpellBook(spell, normalizedSpell, getOwner(item));
    }

    @Override
    public boolean isSpellBook(@Nullable ItemStack item) {
        return read(item) != null;
    }

    @Override
    public boolean hasSpell(@Nullable ItemStack item, @NotNull String spell) {
        SpellBook spellBook = read(item);
        return spellBook != null && spellBook.normalizedSpell().equals(normalizeSpell(spell));
    }

    @Override
    public @NotNull List<SpellBookMatch> getSpellBooks(@NotNull Inventory inventory) {
        List<SpellBookMatch> matches = new ArrayList<>();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            SpellBook spellBook = read(item);
            if (spellBook != null && item != null) {
                matches.add(new SpellBookMatch(item, slot, spellBook));
            }
        }
        return matches;
    }

    @Override
    public @NotNull List<SpellBookMatch> getSpellBooks(@NotNull Player player, @NotNull SpellBookSource source) {
        PlayerInventory inventory = player.getInventory();
        List<SpellBookMatch> matches = new ArrayList<>();

        switch (source) {
            case INVENTORY -> {
                ItemStack[] storage = inventory.getStorageContents();
                for (int slot = 0; slot < storage.length; slot++) {
                    ItemStack item = storage[slot];
                    SpellBook spellBook = read(item);
                    if (spellBook != null && item != null) {
                        matches.add(new SpellBookMatch(item, slot, spellBook));
                    }
                }

                ItemStack offHand = inventory.getItemInOffHand();
                SpellBook offHandSpell = read(offHand);
                if (offHandSpell != null) {
                    matches.add(new SpellBookMatch(offHand, OFF_HAND_SLOT, offHandSpell));
                }
            }
            case MAIN_HAND -> addIfSpellBook(matches, inventory.getItemInMainHand(), inventory.getHeldItemSlot());
            case OFF_HAND -> addIfSpellBook(matches, inventory.getItemInOffHand(), OFF_HAND_SLOT);
            case HANDS -> {
                addIfSpellBook(matches, inventory.getItemInMainHand(), inventory.getHeldItemSlot());
                addIfSpellBook(matches, inventory.getItemInOffHand(), OFF_HAND_SLOT);
            }
        }

        return matches;
    }

    @Override
    public @Nullable SpellBookMatch findSpell(@NotNull Inventory inventory, @NotNull String spell) {
        String normalized = normalizeSpell(spell);
        for (SpellBookMatch match : getSpellBooks(inventory)) {
            if (match.spellBook().normalizedSpell().equals(normalized)) {
                return match;
            }
        }
        return null;
    }

    @Override
    public @Nullable SpellBookMatch findSpell(@NotNull Player player, @NotNull SpellBookSource source, @NotNull String spell) {
        String normalized = normalizeSpell(spell);
        for (SpellBookMatch match : getSpellBooks(player, source)) {
            if (match.spellBook().normalizedSpell().equals(normalized)) {
                return match;
            }
        }
        return null;
    }

    @Override
    public void registerExtension(@NotNull SpellBookExtension extension) {
        String id = extension.id().trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Spell-book extension id cannot be blank.");
        }
        if (extensions.containsKey(id)) {
            throw new IllegalArgumentException("Spell-book extension already registered: " + id);
        }

        extensions.put(id, extension);
        extension.register(new SpellBookExtensionContextImpl(api, this, () -> extensionConfig(id)));
    }

    @Override
    public void setOwner(@NotNull ItemStack item, @Nullable UUID ownerId) {
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return;
        }

        NamespacedKey key = ownerKey();
        if (ownerId == null) {
            meta.getPersistentDataContainer().remove(key);
        } else {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, ownerId.toString());
        }

        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Failed to update spell-book owner metadata.");
        }
    }

    @Override
    public @Nullable UUID getOwner(@Nullable ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BookMeta meta)) {
            return null;
        }

        NamespacedKey key = ownerKey();
        String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public @Nullable UUID getContainerSpellOwner(@NotNull Inventory inventory, @NotNull String spell) {
        String key = containerSpellKey(inventory, spell);
        if (key == null) {
            return null;
        }

        SpellBookMatch match = findSpell(inventory, spell);
        if (match == null) {
            containerSpellOwners.remove(key);
            return null;
        }

        UUID ownerId = containerSpellOwners.get(key);
        if (ownerId == null) {
            ownerId = match.spellBook().ownerId();
            if (ownerId != null) {
                containerSpellOwners.put(key, ownerId);
            }
        }

        return ownerId;
    }

    @Override
    public void updateContainerSpellOwner(@NotNull Inventory inventory, @NotNull String spell, @Nullable UUID ownerId) {
        String key = containerSpellKey(inventory, spell);
        if (key == null) {
            return;
        }

        SpellBookMatch match = findSpell(inventory, spell);
        if (match == null) {
            containerSpellOwners.remove(key);
            return;
        }

        if (ownerId == null) {
            setOwner(match.item(), null);
            containerSpellOwners.remove(key);
            return;
        }

        setOwner(match.item(), ownerId);
        containerSpellOwners.put(key, ownerId);
    }

    private void addIfSpellBook(List<SpellBookMatch> matches, @Nullable ItemStack item, int slot) {
        SpellBook spellBook = read(item);
        if (spellBook != null && item != null) {
            matches.add(new SpellBookMatch(item, slot, spellBook));
        }
    }

    private @NotNull NamespacedKey ownerKey() {
        return new NamespacedKey(plugin.getName().toLowerCase(Locale.ROOT), OWNER_KEY);
    }

    private void loadBuiltInExtensions() {
        registerExtension(new LockedChestSpellBookExtension());
        registerExtension(new WorldTeleportSpellBookExtension());
        registerExtension(new HomeTeleportSpellBookExtension());
        registerExtension(new DoublePigDropsSpellBookExtension());
        registerExtension(new TreeFallSpellBookExtension());
        registerExtension(new QuickFishingSpellBookExtension());
        registerExtension(new NoBiteSpellBookExtension());
        registerExtension(new RainGoWaySpellBookExtension());
    }

    private @NotNull ConfigSection extensionConfig(@NotNull String id) {
        return getConfigSection().getSection("extensions." + id);
    }

    private @Nullable String containerSpellKey(@Nullable Inventory inventory, @NotNull String spell) {
        String containerKey = containerKey(inventory);
        if (containerKey == null) {
            return null;
        }
        return containerKey + "#" + normalizeSpell(spell);
    }

    private @Nullable String containerKey(@Nullable Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            List<Location> locations = new ArrayList<>(2);
            addHolderLocation(locations, doubleChest.getLeftSide());
            addHolderLocation(locations, doubleChest.getRightSide());
            if (locations.isEmpty()) {
                return null;
            }

            locations.sort(Comparator
                .comparing((Location loc) -> loc.getWorld() == null ? "" : loc.getWorld().getName())
                .thenComparingInt(Location::getBlockX)
                .thenComparingInt(Location::getBlockY)
                .thenComparingInt(Location::getBlockZ));

            StringBuilder key = new StringBuilder("double:");
            boolean first = true;
            for (Location location : locations) {
                if (!first) {
                    key.append('|');
                }
                first = false;
                key.append(blockKey(location));
            }
            return key.toString();
        }

        if (holder instanceof Chest chest) {
            return blockKey(chest.getLocation());
        }

        return null;
    }

    private void addHolderLocation(List<Location> locations, @Nullable InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            locations.add(chest.getLocation());
        }
    }

    private @NotNull String blockKey(@NotNull Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "" : world.getName();
        return worldName + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
