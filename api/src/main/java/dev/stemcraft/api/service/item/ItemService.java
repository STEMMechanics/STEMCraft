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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Service for managing item attributes and custom items.
 */
public interface ItemService {
    /**
     * Adds an attribute to the ItemStack with the given key and value.
     *
     * @param <T> The type of the attribute value.
     * @param <Z> A placeholder type parameter (not used).
     * @param item The ItemStack to which the attribute will be added.
     * @param key The key of the attribute.
     * @param value The value of the attribute.
     */
    <T, Z> void addAttrib(@NotNull ItemStack item, @NotNull String key, @NotNull T value);

    /**
     * Checks if the ItemStack has an attribute with the given key.
     *
     * @param item The ItemStack to check.
     * @param key The key of the attribute.
     * @return True if the attribute exists, false otherwise.
     */
    boolean hasAttrib(@NotNull ItemStack item, @NotNull String key);

    /**
     * Removes an attribute from the ItemStack with the given key.
     *
     * @param item The ItemStack from which the attribute will be removed.
     * @param key The key of the attribute to remove.
     */
    void removeAttrib(@NotNull ItemStack item, @NotNull String key);

    /**
     * Retrieves an attribute from the ItemStack with the given key or returns a default value if not found.
     *
     * @param <T> The type of the attribute value.
     * @param <Z> A placeholder type parameter (not used).
     * @param item The ItemStack from which the attribute will be retrieved.
     * @param key The key of the attribute.
     * @param typeClass The class of the attribute type.
     * @param defaultValue The default value to return if the attribute is not found.
     * @return The attribute value or the default value if not found.
     */
    <T, Z> @NotNull T getAttrib(@NotNull ItemStack item, @NotNull String key, @NotNull Class<T> typeClass, @NotNull T defaultValue);

    /**
     * Registers a custom item.
     *
     * @param id The unique identifier for the custom item.
     * @param template The ItemStack template for the custom item.
     */
    void registerCustomItem(@NotNull String id, @NotNull ItemStack template);

    /**
     * Creates a new ItemStack for a custom item.
     *
     * @param id The unique identifier for the custom item.
     * @param quantity The quantity of the item stack.
     * @return The created ItemStack.
     */
    @Nullable ItemStack createCustomItem(@NotNull String id, int quantity);
    default @Nullable ItemStack createCustomItem(@NotNull String id) { return createCustomItem(id, 1); }

    /**
     * Checks if the given ItemStack is the specified custom item.
     *
     * @param id The unique identifier for the custom item.
     * @param item The ItemStack to check.
     * @return True if the ItemStack is the specified custom item, false otherwise.
     */
    boolean isCustomItemId(@NotNull String id, @NotNull ItemStack item);

    /**
     * Returns the ld of the custom item on this stack, if any.
     *
     * @param item The ItemStack to check.
     * @return The custom item id, or null if not a custom item.
     */
    @Nullable String getCustomItemId(@Nullable ItemStack item);
}
