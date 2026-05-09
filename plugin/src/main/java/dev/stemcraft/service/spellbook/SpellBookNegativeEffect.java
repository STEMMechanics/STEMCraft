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

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.spellbook.SpellBookMatch;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Applies configured downsides when a spell book effect is triggered.
 */
final class SpellBookNegativeEffect {
    private static final int OFF_HAND_SLOT = 40;

    private SpellBookNegativeEffect() { }

    static void applyConfigured(@NotNull Player player,
                                @NotNull SpellBookMatch match,
                                @NotNull ConfigSection config) {
        switch (parseAction(config.getString("negative", "none"))) {
            case CONSUME -> removeOne(player.getInventory(), match);
            case DROP -> {
                ItemStack dropped = match.item().clone();
                dropped.setAmount(1);
                removeOne(player.getInventory(), match);
                player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            }
            case NONE -> {
                // no configured downside
            }
        }
    }

    static void applyConfigured(@NotNull Inventory inventory,
                                @NotNull SpellBookMatch match,
                                @NotNull ConfigSection config,
                                Location dropLocation) {
        switch (parseAction(config.getString("negative", "none"))) {
            case CONSUME -> removeOne(inventory, match);
            case DROP -> {
                ItemStack dropped = match.item().clone();
                dropped.setAmount(1);
                removeOne(inventory, match);
                if (dropLocation != null && dropLocation.getWorld() != null) {
                    dropLocation.getWorld().dropItemNaturally(dropLocation, dropped);
                }
            }
            case NONE -> {
                // no configured downside
            }
        }
    }

    private static void removeOne(@NotNull PlayerInventory inventory, @NotNull SpellBookMatch match) {
        ItemStack item = match.item();
        int amount = item.getAmount();
        if (amount <= 1) {
            if (match.slot() == OFF_HAND_SLOT) {
                inventory.setItemInOffHand(null);
            } else {
                inventory.setItem(match.slot(), null);
            }
            return;
        }

        item.setAmount(amount - 1);
        if (match.slot() == OFF_HAND_SLOT) {
            inventory.setItemInOffHand(item);
        } else {
            inventory.setItem(match.slot(), item);
        }
    }

    private static void removeOne(@NotNull Inventory inventory, @NotNull SpellBookMatch match) {
        ItemStack item = match.item();
        int amount = item.getAmount();
        if (amount <= 1) {
            inventory.setItem(match.slot(), null);
            return;
        }

        item.setAmount(amount - 1);
        inventory.setItem(match.slot(), item);
    }

    private static @NotNull NegativeAction parseAction(@NotNull String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "consume", "disappear", "remove" -> NegativeAction.CONSUME;
            case "drop" -> NegativeAction.DROP;
            default -> NegativeAction.NONE;
        };
    }

    private enum NegativeAction {
        NONE,
        CONSUME,
        DROP
    }
}
