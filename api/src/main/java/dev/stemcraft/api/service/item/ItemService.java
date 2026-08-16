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
import java.util.Collection;

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
     * Registers a handler for bracket properties on a custom item identifier.
     * For example, {@code stemcraft:animal_barrel[animal=chicken]}.
     *
     * @param id custom item identifier
     * @param handler property handler
     */
    void registerCustomItemPropertyHandler(@NotNull String id, @NotNull CustomItemPropertyHandler handler);

    /** Removes a previously registered bracket-property handler. */
    void unregisterCustomItemPropertyHandler(@NotNull String id);

    /**
     * Registers a richer custom item definition, including placement and
     * client presentation metadata.
     *
     * @param definition The custom item definition.
     */
    void registerCustomItem(@NotNull CustomItemDefinition definition);

    /**
     * Gets the registered definition for the given custom item id.
     *
     * @param id The custom item id.
     * @return The definition, or null if none is registered.
     */
    @Nullable CustomItemDefinition customItemDefinition(@NotNull String id);

    /**
     * Returns all registered custom item definitions.
     *
     * @return Registered definitions.
     */
    @NotNull Collection<CustomItemDefinition> customItemDefinitions();

    /**
     * Changes only the client visual used by a custom item. Passing {@code null}
     * restores its normal visual; all other item data is preserved.
     *
     * @return true when the item and requested state are valid
     */
    default boolean applyCustomItemVisualState(@NotNull ItemStack item, @Nullable String state) {
        return false;
    }

    /**
     * Checks if the given ItemStack is the specified custom item.
     *
     * @param id The unique identifier for the custom item.
     * @param item The ItemStack to check.
     * @return True if the ItemStack is the specified custom item, false otherwise.
     */
    boolean isCustomItemId(@NotNull String id, @NotNull ItemStack item);

    /**
     * Returns the id of the custom item on this stack, if any.
     *
     * @param item The ItemStack to check.
     * @return The custom item id, or null if not a custom item.
     */
    @Nullable String getCustomItemId(@Nullable ItemStack item);

    /**
     * Returns the player-facing plain-text name of an item stack.
     * Custom or renamed item display names take precedence over the backing
     * Minecraft material name.
     *
     * @param item The item stack to name.
     * @return The display name, or a readable Minecraft material name.
     */
    @NotNull String getItemName(@NotNull ItemStack item);
}
