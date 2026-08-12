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

package dev.stemcraft.api.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Utility class for inventory-related operations.
 */
public final class InventoryUtil {
    private InventoryUtil() {
    }

    /**
     * Converts the contents of an inventory to a string representation.
     *
     * @param inv The inventory to convert.
     * @return A string representation of the inventory contents.
     */
    public static String toString(Inventory inv) {
        ItemStack[] contents = Objects.requireNonNull(inv.getContents());
        if (contents.length == 0) return "(empty)";

        StringBuilder out = new StringBuilder();

        for (ItemStack item : contents) {
            if (item == null) continue;
            out.append(item.getType().name())
                    .append(" x")
                    .append(item.getAmount())
                    .append(", ");
        }

        if (out.isEmpty()) return "(empty)";
        return out.substring(0, out.length() - 2);
    }

    /**
     * Clears any block-backed inventory/cooking contents for the given block state.
     *
     * @param block The block whose contents should be cleared.
     */
    public static void clearBlockContents(Block block) {
        if (block.getState() instanceof Chest chest) {
            chest.getInventory().clear();
            return;
        }
        if (block.getState() instanceof Container container) {
            container.getInventory().clear();
            return;
        }
        if (block.getState() instanceof Campfire campfire) {
            for (int slot = 0; slot < campfire.getSize(); slot++) {
                campfire.setItem(slot, null);
            }
        }
    }

    /**
     * Removes a block without leaving inventory contents behind.
     *
     * @param block The block to clear.
     * @param applyPhysics Whether block removal should apply physics.
     */
    public static void clearBlock(Block block, boolean applyPhysics) {
        clearBlockContents(block);
        block.setType(Material.AIR, applyPhysics);
    }
}
