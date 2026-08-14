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
import dev.stemcraft.api.service.item.CustomItemDefinition;
import dev.stemcraft.api.service.item.CustomItemClientDefinition;
import dev.stemcraft.api.service.item.JavaItemVisualDefinition;
import dev.stemcraft.api.service.item.BedrockItemVisualDefinition;
import dev.stemcraft.api.service.item.CustomItemPlacementMode;
import dev.stemcraft.api.service.item.ItemService;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of the ItemService for managing item attributes and custom items.
 */
public class ItemServiceImpl extends BaseService implements ItemService {
    private static final String ATTR_ITEM_ID_KEY = "custom-item-id";
    private final Map<String, ItemStack> itemTemplates = new HashMap<>();
    private final Map<String, CustomItemDefinition> itemDefinitions = new LinkedHashMap<>();
    private final Set<Integer> configuredModelData = new HashSet<>();

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
        loadConfiguredItems();
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

        api.events().register(BlockPlaceEvent.class, event -> {
            CustomItemDefinition definition = customItemDefinition(getCustomItemId(event.getItemInHand()));
            if (definition == null) {
                return;
            }
            if (definition.placementMode() == CustomItemPlacementMode.DENY) {
                event.setCancelled(true);
            }
        });
    }

    private void loadConfiguredItems() {
        plugin.exportBundledDirectory("data-packs");
        var config = api.config().load("config.yml");
        ConfigSection items = config == null ? null : config.getSection("custom-items", false);
        loadConfiguredItems(items, "stemcraft");
        File dataPacks = new File(plugin.getDataFolder(), "data-packs");
        File[] packDirectories = dataPacks.listFiles(File::isDirectory);
        if (packDirectories == null) return;
        for (File packDirectory : packDirectories) {
            File packConfigFile = new File(packDirectory, "config.yml");
            if (!packConfigFile.isFile()) continue;
            ConfigSection packConfig = api.config().load(packConfigFile);
            if (packConfig == null || !packConfig.getBoolean("pack.enabled", true)) continue;
            String namespace = packConfig.getString("pack.namespace",
                packDirectory.getName().toLowerCase(java.util.Locale.ROOT).replace('-', '_'));
            loadConfiguredItems(packConfig.getSection("custom-items", false), namespace);
            File configsDirectory = new File(packDirectory, "configs");
            File[] configFiles = configsDirectory.listFiles((directory, name) ->
                name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
            if (configFiles == null) continue;
            for (File configFile : configFiles) {
                ConfigSection extraConfig = api.config().load(configFile);
                if (extraConfig != null) loadConfiguredItems(extraConfig.getSection("custom-items", false), namespace);
            }
        }
    }

    private void loadConfiguredItems(ConfigSection items, String namespace) {
        if (items == null) return;
        for (String id : items.getKeys(false)) {
            ConfigSection section = items.getSection(id, false);
            if (section == null) continue;
            Material material = Material.matchMaterial(section.getString("material", "").trim());
            if (material == null) {
                plugin.getLogger().warning("[items] Custom item '" + id + "' has an invalid material");
                continue;
            }
            try {
                ItemStack template = new ItemStack(material);
                ItemMeta meta = template.getItemMeta();
                String name = section.getString("name", "").trim();
                if (!name.isBlank()) meta.displayName(TextUtil.colourise(name));
                List<net.kyori.adventure.text.Component> lore = section.getStringList("lore").stream()
                    .map(TextUtil::colourise).toList();
                if (!lore.isEmpty()) meta.lore(lore);
                int maxStackSize = section.getInt("max-stack-size", 0);
                if (maxStackSize > 0) meta.setMaxStackSize(Math.min(99, maxStackSize));
                if (section.contains("glint")) meta.setEnchantmentGlintOverride(section.getBoolean("glint", false));
                ConfigSection food = section.getSection("food", false);
                if (food != null) {
                    var component = meta.getFood();
                    component.setNutrition(Math.max(0, food.getInt("nutrition", 0)));
                    component.setSaturation((float) Math.max(0D, food.getDouble("saturation", 0D)));
                    component.setCanAlwaysEat(food.getBoolean("always-edible", false));
                    meta.setFood(component);
                }
                template.setItemMeta(meta);
                CustomItemPlacementMode placement = CustomItemPlacementMode.valueOf(
                    section.getString("placement", "DENY").trim().toUpperCase(java.util.Locale.ROOT));
                registerCustomItem(new CustomItemDefinition(id, template, placement, null,
                    parseClients(section, namespace, id, name)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[items] Could not load custom item '" + id + "': " + exception.getMessage());
            }
        }
    }

    private CustomItemClientDefinition parseClients(ConfigSection item, String namespace, String id, String name) {
        String texture = item.getString("texture", "").trim();
        if (texture.isBlank()) return null;
        String safeId = id.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        String textureId = texture.contains(":") ? texture : namespace + ":" + texture;
        ConfigSection overrides = item.getSection("overrides", false);
        ConfigSection javaOverrides = overrides == null ? null : overrides.getSection("java", false);
        ConfigSection bedrockOverrides = overrides == null ? null : overrides.getSection("bedrock", false);
        int customModelData;
        if (javaOverrides != null && javaOverrides.contains("custom-model-data")) {
            customModelData = javaOverrides.getInt("custom-model-data", 0);
            if (!configuredModelData.add(customModelData)) {
                throw new IllegalArgumentException("duplicate custom-model-data " + customModelData);
            }
        } else {
            customModelData = allocateModelData(namespace + ":" + id);
        }
        String itemModel = javaOverrides == null ? namespace + ":" + safeId
            : javaOverrides.getString("item-model", namespace + ":" + safeId);
        String model = javaOverrides == null ? textureId
            : javaOverrides.getString("model", textureId);
        JavaItemVisualDefinition java = new JavaItemVisualDefinition(customModelData, itemModel, model, textureId);
        String defaultIcon = textureId.substring(textureId.indexOf(':') + 1).replace('/', '_');
        String identifier = bedrockOverrides == null ? namespace + ":" + safeId
            : bedrockOverrides.getString("identifier", namespace + ":" + safeId);
        String icon = bedrockOverrides == null ? defaultIcon : bedrockOverrides.getString("icon", defaultIcon);
        String displayName = bedrockOverrides == null ? TextUtil.stripColour(name)
            : bedrockOverrides.getString("display-name", TextUtil.stripColour(name));
        BedrockItemVisualDefinition bedrock = new BedrockItemVisualDefinition(identifier, icon, textureId,
            displayName.isBlank() ? id : displayName);
        return new CustomItemClientDefinition(java, bedrock);
    }

    private int allocateModelData(String key) {
        int value = 50_000 + Math.floorMod(key.hashCode(), 900_000);
        while (!configuredModelData.add(value)) value++;
        return value;
    }

    /**
     * Adds an attribute to the ItemStack with the given key and value.
     *
     * @param item The ItemStack to modify.
     * @param key The key for the attribute.
     * @param value The value for the attribute.
     */
    @Override
    public <T, Z> void addAttrib(@NotNull ItemStack item, @NotNull String key, @NotNull T value) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            PersistentDataType<Z, T> type = getPersistentDataType(value);
            if (type != null) {
                meta.getPersistentDataContainer().set(namespacedKey, type, value);
                if (!item.setItemMeta(meta)) {
                    throw new IllegalStateException("Failed to apply item metadata for key '" + key + "'");
                }
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
    public boolean hasAttrib(@NotNull ItemStack item, @NotNull String key) {
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
    public void removeAttrib(@NotNull ItemStack item, @NotNull String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            meta.getPersistentDataContainer().remove(namespacedKey);
            if (!item.setItemMeta(meta)) {
                throw new IllegalStateException("Failed to remove item metadata for key '" + key + "'");
            }
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
    public <T, Z> @NotNull T getAttrib(@NotNull ItemStack item, @NotNull String key, @NotNull Class<T> typeClass, @NotNull T defaultValue) {
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
    public void registerCustomItem(@NotNull String id, @NotNull ItemStack template) {
        if (id.isEmpty()) {
            return;
        }

        // Clone the template so callers can't mutate our stored instance
        ItemStack cloned = template.clone();
        // Tag with logical id so getItemId/isItemId work on created items
        addAttrib(cloned, ATTR_ITEM_ID_KEY, id);

        itemTemplates.put(id, cloned);
    }

    @Override
    public void registerCustomItem(@NotNull CustomItemDefinition definition) {
        ItemStack template = definition.template().clone();
        ItemMeta meta = template.getItemMeta();
        if (meta != null && definition.clients() != null && definition.clients().java() != null) {
            NamespacedKey itemModel = NamespacedKey.fromString(definition.clients().java().itemModelId());
            if (itemModel == null) {
                throw new IllegalArgumentException("Invalid item model id '" + definition.clients().java().itemModelId() + "'");
            }
            meta.setItemModel(itemModel);
            CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of((float) definition.clients().java().customModelData()));
            meta.setCustomModelDataComponent(customModelData);
            if (!template.setItemMeta(meta)) {
                throw new IllegalStateException("Failed to apply item metadata for custom item '" + definition.id() + "'");
            }
        }
        registerCustomItem(definition.id(), template);
        itemDefinitions.put(definition.id(), new CustomItemDefinition(
            definition.id(),
            template,
            definition.placementMode(),
            definition.managedObjectType(),
            definition.clients()
        ));
    }

    @Override
    public @Nullable CustomItemDefinition customItemDefinition(@NotNull String id) {
        return itemDefinitions.get(id);
    }

    @Override
    public @NotNull Collection<CustomItemDefinition> customItemDefinitions() {
        return Collections.unmodifiableCollection(itemDefinitions.values());
    }

    /**
     * Creates a new ItemStack instance of the custom item with the given id and quantity.
     *
     * @param id The unique identifier for the custom item.
     * @param quantity The quantity of the item stack.
     * @return A new ItemStack instance of the custom item, or null if not found or invalid parameters.
     */
    @Override
    public @Nullable ItemStack createCustomItem(@NotNull String id, int quantity) {
        if (quantity <= 0) {
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
    public boolean isCustomItemId(@NotNull String id, @NotNull ItemStack item) {
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
        String id = getAttrib(item, ATTR_ITEM_ID_KEY, String.class, "");
        return id.isBlank() ? null : id;
    }
}
