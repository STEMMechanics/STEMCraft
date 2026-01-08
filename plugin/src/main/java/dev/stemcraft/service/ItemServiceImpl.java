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
import dev.stemcraft.api.service.item.ItemService;
import org.bukkit.NamespacedKey;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the ItemService for managing item attributes and custom items.
 */
public class ItemServiceImpl extends BaseService implements ItemService {
    private static final String ATTR_ITEM_ID_KEY = "custom-item-id";
    private final Map<String, ItemStack> itemTemplates = new HashMap<>();

    /**
     * Constructor for ItemServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public ItemServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the item service and registers event handlers.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerDropItemEvent.class, (event) -> {
            ItemStack item = event.getItemDrop().getItemStack();
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                if (getAttrib(item, "destroy-on-drop", Integer.class, 0) == 1) {
                    event.getItemDrop().remove();
                }

                if (getAttrib(item, "no-drop", Integer.class, 0) == 1) {
                    event.setCancelled(true);
                }
            }
        });
    }

    /**
     * Adds an attribute to the ItemStack with the given key and value.
     *
     * @param item The ItemStack to modify.
     * @param key The key for the attribute.
     * @param value The value for the attribute.
     */
    @Override
    public <T, Z> void addAttrib(ItemStack item, String key, T value) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            PersistentDataType<Z, T> type = getPersistentDataType(value);
            if (type != null) {
                meta.getPersistentDataContainer().set(namespacedKey, type, value);
                item.setItemMeta(meta);
            }
        }
    }

    /**
     * Checks if the ItemStack has an attribute with the given key.
     *
     * @param item The ItemStack to check.
     * @param key The key for the attribute.
     * @return true if the attribute exists, false otherwise.
     */
    @Override
    public boolean hasAttrib(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            return container.getKeys().contains(namespacedKey);
        }
        return false;
    }

    /**
     * Removes an attribute from the ItemStack with the given key.
     *
     * @param item The ItemStack to modify.
     * @param key The key for the attribute to remove.
     */
    @Override
    public void removeAttrib(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            meta.getPersistentDataContainer().remove(namespacedKey);
            item.setItemMeta(meta);
        }
    }

    /**
     * Retrieves an attribute from the ItemStack with the given key or returns a default value if not found.
     *
     * @param item The ItemStack to check.
     * @param key The key for the attribute.
     * @param typeClass The class of the type you're expecting (String.class, Byte.class, etc.).
     * @param defaultValue The default value to return if the attribute is not found or there's an issue.
     * @return The value of the attribute or the default value.
     */
    @Override
    public <T, Z> T getAttrib(ItemStack item, String key, Class<T> typeClass, T defaultValue) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            PersistentDataType<Z, T> type = getPersistentDataType(typeClass);
            if (type != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(namespacedKey, type)) {
                    T value = container.get(namespacedKey, type);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }

        return defaultValue;
    }

    /**
     * Determines the PersistentDataType based on the object provided (class or value).
     *
     * @param object The object for which to determine the PersistentDataType (Class<?> or instance of a type).
     * @return The corresponding PersistentDataType, or null if the type is unsupported.
     */
    @SuppressWarnings("unchecked")
    private static <T, Z> PersistentDataType<Z, T> getPersistentDataType(Object object) {
        if (object instanceof Class<?> typeClass) {
            if (typeClass == String.class) {
                return (PersistentDataType<Z, T>) PersistentDataType.STRING;
            } else if (typeClass == Byte.class) {
                return (PersistentDataType<Z, T>) PersistentDataType.BYTE;
            } else if (typeClass == Integer.class) {
                return (PersistentDataType<Z, T>) PersistentDataType.INTEGER;
            } else if (typeClass == Double.class) {
                return (PersistentDataType<Z, T>) PersistentDataType.DOUBLE;
            } else if (typeClass == Float.class) {
                return (PersistentDataType<Z, T>) PersistentDataType.FLOAT;
            }
        } else {
            if (object instanceof String) {
                return (PersistentDataType<Z, T>) PersistentDataType.STRING;
            } else if (object instanceof Byte) {
                return (PersistentDataType<Z, T>) PersistentDataType.BYTE;
            } else if (object instanceof Integer) {
                return (PersistentDataType<Z, T>) PersistentDataType.INTEGER;
            } else if (object instanceof Double) {
                return (PersistentDataType<Z, T>) PersistentDataType.DOUBLE;
            } else if (object instanceof Float) {
                return (PersistentDataType<Z, T>) PersistentDataType.FLOAT;
            }
        }
        // Add more types if needed
        return null;
    }

    /**
     * Registers a custom item template with the given id.
     *
     * @param id The unique identifier for the custom item.
     * @param template The ItemStack template for the custom item.
     */
    @Override
    public void registerCustomItem(String id, ItemStack template) {
        if (id == null || id.isEmpty() || template == null) {
            return;
        }

        // Clone the template so callers can't mutate our stored instance
        ItemStack cloned = template.clone();
        // Tag with logical id so getItemId/isItemId work on created items
        addAttrib(cloned, ATTR_ITEM_ID_KEY, id);

        itemTemplates.put(id, cloned);
    }

    /**
     * Creates a new ItemStack instance of the custom item with the given id and quantity.
     *
     * @param id The unique identifier for the custom item.
     * @param quantity The quantity of the item stack.
     * @return A new ItemStack instance of the custom item, or null if not found or invalid parameters.
     */
    @Override
    public ItemStack createCustomItem(String id, int quantity) {
        if (id == null || quantity <= 0) {
            return null;
        }

        ItemStack template = itemTemplates.get(id);
        if (template == null) {
            return null;
        }

        ItemStack stack = template.clone();
        stack.setAmount(quantity);
        return stack;
    }

    /**
     * Checks if the given ItemStack matches the custom item with the specified id.
     *
     * @param id The unique identifier for the custom item.
     * @param item The ItemStack to check.
     * @return true if the ItemStack matches the custom item id, false otherwise.
     */
    @Override
    public boolean isCustomItemId(String id, ItemStack item) {
        if (id == null || item == null) {
            return false;
        }
        String itemId = getCustomItemId(item);
        return id.equalsIgnoreCase(itemId);
    }

    /**
     * Retrieves the custom item id from the given ItemStack.
     *
     * @param item The ItemStack to check.
     * @return The custom item id, or null if not found.
     */
    @Override
    public @Nullable String getCustomItemId(ItemStack item) {
        if (item == null) {
            return null;
        }
        return getAttrib(item, ATTR_ITEM_ID_KEY, String.class, null);
    }
}
