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

package dev.stemcraft.service.spellbook;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.spellbook.SpellBookExtension;
import dev.stemcraft.api.service.spellbook.SpellBookExtensionContext;
import dev.stemcraft.api.service.spellbook.SpellBookMatch;
import dev.stemcraft.api.service.spellbook.SpellBookService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Built-in spell that locks container access to the player that placed the book.
 */
public final class LockedChestSpellBookExtension implements SpellBookExtension {
    private static final String DEFAULT_SPELL = "me chest be locked";

    @Override
    public @NotNull String id() {
        return "locked-chest";
    }

    @Override
    public void register(@NotNull SpellBookExtensionContext context) {
        STEMCraftAPI api = context.api();
        SpellBookService spellBooks = context.spellBooks();

        api.events().register(InventoryOpenEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true) || !(event.getPlayer() instanceof Player player)) {
                return;
            }

            Inventory top = event.getView().getTopInventory();
            UUID ownerId = spellBooks.getContainerSpellOwner(top, spell);
            if (ownerId == null || ownerId.equals(player.getUniqueId())) {
                return;
            }

            SpellBookMatch match = spellBooks.findSpell(top, spell);
            if (match != null) {
                SpellBookNegativeEffect.applyConfigured(top, match, config, inventoryLocation(top));
            }
            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        api.events().register(InventoryClickEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true) || !(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            Inventory top = event.getView().getTopInventory();
            UUID ownerId = spellBooks.getContainerSpellOwner(top, spell);
            if (ownerId != null && !ownerId.equals(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            if (isChestInventory(top)) {
                api.tasks().nextTick(() -> spellBooks.updateContainerSpellOwner(top, spell, player.getUniqueId()));
            }
        }, EventPriority.HIGHEST, false);

        api.events().register(InventoryDragEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true) || !(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            Inventory top = event.getView().getTopInventory();
            UUID ownerId = spellBooks.getContainerSpellOwner(top, spell);
            if (ownerId != null && !ownerId.equals(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            if (isChestInventory(top)) {
                api.tasks().nextTick(() -> spellBooks.updateContainerSpellOwner(top, spell, player.getUniqueId()));
            }
        }, EventPriority.HIGHEST, false);

        api.events().register(InventoryCloseEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true) || !(event.getPlayer() instanceof Player player)) {
                return;
            }

            Inventory top = event.getView().getTopInventory();
            if (!isChestInventory(top)) {
                return;
            }

            spellBooks.updateContainerSpellOwner(top, spell, player.getUniqueId());
        }, EventPriority.MONITOR, false);

        api.events().register(InventoryMoveItemEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }

            if (spellBooks.getContainerSpellOwner(event.getSource(), spell) != null
                || spellBooks.getContainerSpellOwner(event.getDestination(), spell) != null) {
                event.setCancelled(true);
            }
        }, EventPriority.HIGHEST, true);

        api.events().register(BlockBreakEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }

            Inventory inventory = inventoryFor(event.getBlock());
            UUID ownerId = inventory == null ? null : spellBooks.getContainerSpellOwner(inventory, spell);
            if (ownerId == null) {
                return;
            }

            if (!ownerId.equals(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            spellBooks.updateContainerSpellOwner(inventory, spell, null);
        }, EventPriority.HIGHEST, true);

        api.events().register(BlockBurnEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }

            if (getOwner(spellBooks, event.getBlock(), spell) != null) {
                event.setCancelled(true);
            }
        }, EventPriority.HIGHEST, true);

        api.events().register(EntityExplodeEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }
            event.blockList().removeIf(block -> getOwner(spellBooks, block, spell) != null);
        }, EventPriority.HIGHEST, true);

        api.events().register(BlockExplodeEvent.class, event -> {
            ConfigSection config = context.config();
            String spell = config.getString("spell", DEFAULT_SPELL);
            if (!spellBooks.isEnabled() || !config.getBoolean("enabled", true)) {
                return;
            }
            event.blockList().removeIf(block -> getOwner(spellBooks, block, spell) != null);
        }, EventPriority.HIGHEST, true);
    }

    private @Nullable UUID getOwner(@NotNull SpellBookService spellBooks, @Nullable Block block, @NotNull String spell) {
        Inventory inventory = inventoryFor(block);
        if (inventory == null) {
            return null;
        }
        return spellBooks.getContainerSpellOwner(inventory, spell);
    }

    private @Nullable Inventory inventoryFor(@Nullable Block block) {
        if (block == null || !(block.getState() instanceof Chest chest)) {
            return null;
        }
        return chest.getInventory();
    }

    private boolean isChestInventory(@Nullable Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Chest || holder instanceof org.bukkit.block.DoubleChest;
    }

    private @Nullable Location inventoryLocation(@Nullable Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Chest chest) {
            return chest.getLocation();
        }
        if (holder instanceof org.bukkit.block.DoubleChest doubleChest) {
            return doubleChest.getLocation();
        }
        return null;
    }
}
