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

package dev.stemcraft.api.service.item;

import org.bukkit.inventory.ItemStack;

public interface ItemService {
    /**
     * Adds an attribute to the ItemStack with the given key and value.
     */
    <T, Z> void addAttrib(ItemStack item, String key, T value);

    /**
     * Checks if the ItemStack has an attribute with the given key.
     */
    boolean hasAttrib(ItemStack item, String key);

    /**
     * Removes an attribute from the ItemStack with the given key.
     */
    void removeAttrib(ItemStack item, String key);

    /**
     * Retrieves an attribute from the ItemStack with the given key or returns a default value if not found.
     */
    <T, Z> T getAttrib(ItemStack item, String key, Class<T> typeClass, T defaultValue);

    default <T, Z> T getAttrib(ItemStack item, String key, Class<T> typeClass) {
        return getAttrib(item, key, typeClass, null);
    }

    /**
     * Registers a custom item.
     */
    void registerCustomItem(String id, ItemStack template);

    /**
     * Creates a new ItemStack for a custom item.
     */
    ItemStack createCustomItem(String id, int quantity);
    default ItemStack createCustomItem(String id) { return createCustomItem(id, 1); }

    /**
     * Checks if the given ItemStack is the specified custom item.
     */
    boolean isCustomItemId(String id, ItemStack item);

    /**
     * Returns the ld of the custom item on this stack, if any.
     */
    String getCustomItemId(ItemStack item);
}
