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
import dev.stemcraft.api.service.item.CustomItemPropertyHandler;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.block.Campfire;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of the ItemService for managing item attributes and custom items.
 */
public class ItemServiceImpl extends BaseService implements ItemService {
    private static final String ATTR_ITEM_ID_KEY = "custom-item-id";
    private final Map<String, ItemStack> itemTemplates = new HashMap<>();
    private final Map<String, CustomItemDefinition> itemDefinitions = new LinkedHashMap<>();
    private final Map<String, CustomItemPropertyHandler> propertyHandlers = new HashMap<>();
    private final Set<Integer> configuredModelData = new HashSet<>();
    private final Map<String, FoodBehavior> foodBehaviors = new HashMap<>();
    private final Map<String, PotionDefinition> potionDefinitions = new HashMap<>();
    private final Map<String, Set<String>> ownedItemIds = new HashMap<>();
    private final Map<String, Set<String>> ownedPotionDefinitions = new HashMap<>();
    private final Map<String, Set<Integer>> ownedModelData = new HashMap<>();
    private NamespacedKey definitionVersionKey;
    private dev.stemcraft.api.command.Command giveCommand;
    private org.bukkit.command.Command originalGiveCommand;

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
        definitionVersionKey = new NamespacedKey(plugin, "workshop_definition_version");
        if (plugin.contentPacks() != null) {
            plugin.contentPacks().registerHandler("items", this::reloadContentPack, this::unloadContentPack);
        }
        loadConfiguredItems();
        registerGiveCommand();
        api.events().register(PlayerInteractEvent.class, this::handleCampfireInput, EventPriority.HIGHEST, false);
        api.events().register(PlayerItemConsumeEvent.class, this::handleConfiguredFood, EventPriority.MONITOR, true);
        api.events().register(PlayerJoinEvent.class, event -> reconcileInventory(event.getPlayer()));
        api.events().register(PlayerItemHeldEvent.class, event -> reconcileItem(
            event.getPlayer().getInventory().getItem(event.getNewSlot())));
        api.events().register(InventoryCloseEvent.class, this::handleInventoryClose, EventPriority.HIGHEST, false);
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
            String id = getCustomItemId(event.getItemInHand());
            CustomItemDefinition definition = id == null ? null : customItemDefinition(id);
            if (definition == null) {
                return;
            }
            if (definition.placementMode() == CustomItemPlacementMode.DENY) {
                event.setCancelled(true);
            }
        });
    }

    /** Remove disposable items left in storage, including Bedrock transfers without click events. */
    void handleInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory(); // Only the upper/container inventory.
        InventoryHolder holder = inventory.getHolder(false);
        // Avoid player crafting inventories and virtual plugin menus, which may contain display copies.
        if (inventory.getType() != InventoryType.ENDER_CHEST && !(holder instanceof Container)
            && !(holder instanceof DoubleChest) && !(holder instanceof Vehicle)) return;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir() && getAttrib(item, "destroy-on-drop", Integer.class, 0) == 1) {
                inventory.setItem(slot, null);
            }
        }
    }

    @Override
    public void onDisable() {
        if (giveCommand != null) giveCommand.unregister();
        if (originalGiveCommand != null) Bukkit.getCommandMap().getKnownCommands().put("give", originalGiveCommand);
        giveCommand = null;
        originalGiveCommand = null;
    }

    private void loadConfiguredItems() {
        var config = api.config().load("config.yml");
        if (config != null) loadPotionDefinitions(config.getSection("potions", false), "__root__");
        ConfigSection items = config == null ? null : config.getSection("custom-items", false);
        loadConfiguredItems(items, "stemcraft", "__root__");
    }

    private void loadPotionDefinitions(@Nullable ConfigSection definitions, String owner) {
        if (definitions == null) return;
        for (String id : definitions.getKeys(false)) {
            ConfigSection section = definitions.getSection(id, false);
            if (section == null) throw new IllegalArgumentException("potion definition '" + id + "' must be a mapping");
            String normalized = normaliseCustomItemId(id);
            if (potionDefinitions.containsKey(normalized)) {
                throw new IllegalArgumentException("duplicate potion definition '" + id + "'");
            }
            potionDefinitions.put(normalized, parsePotionDefinition(section, id));
            ownedPotionDefinitions.computeIfAbsent(owner, ignored -> new HashSet<>()).add(normalized);
        }
    }

    private static PotionDefinition parsePotionDefinition(ConfigSection section, String id) {
        String color = section.getString("color", section.getString("colour", "")).trim().replace("#", "");
        if (!color.isBlank() && !color.matches("[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("potion '" + id + "' has an invalid colour");
        List<PotionDefinitionEffect> effects = new ArrayList<>();
        for (Object raw : section.getList("effects")) {
            if (!(raw instanceof Map<?, ?> values))
                throw new IllegalArgumentException("potion '" + id + "' effects must be mappings");
            String typeName = values.get("type") == null ? "" : values.get("type").toString().trim();
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(typeName.toLowerCase(Locale.ROOT)));
            if (type == null) throw new IllegalArgumentException("unknown potion effect '" + typeName + "'");
            int seconds = Math.max(1, (int) Math.round(number(values.get("duration-seconds"), 1D)));
            int level = Math.max(1, (int) Math.round(number(values.get("level"), 1D)));
            effects.add(new PotionDefinitionEffect(type, seconds, level));
        }
        if (effects.isEmpty()) throw new IllegalArgumentException("potion '" + id + "' must define at least one effect");
        return new PotionDefinition(color, List.copyOf(effects));
    }

    private void loadConfiguredItems(ConfigSection items, String namespace, String owner) {
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
                if (!name.isBlank()) meta.displayName(TextUtil.colourise(name)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                List<net.kyori.adventure.text.Component> lore = section.getStringList("lore").stream()
                    .map(TextUtil::colourise).toList();
                if (!lore.isEmpty()) meta.lore(lore);
                int maxStackSize = section.getInt("max-stack-size", 0);
                if (maxStackSize > 0) meta.setMaxStackSize(Math.min(99, maxStackSize));
                int maxDamage = section.getInt("max-damage", 0);
                if (maxDamage > 0) {
                    if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) {
                        throw new IllegalArgumentException("material does not support durability");
                    }
                    damageable.setMaxDamage(maxDamage);
                }
                if (section.contains("glint")) meta.setEnchantmentGlintOverride(section.getBoolean("glint", false));
                applyCombatStats(meta, section, id, material);
                applyEnchantments(meta, section.getSection("enchantments", false), id);
                applyColourAndPotion(meta, section);
                meta.getPersistentDataContainer().set(definitionVersionKey, PersistentDataType.STRING,
                    definitionVersion(section));
                ConfigSection food = section.getSection("food", false);
                if (food != null) {
                    applyFoodComponent(meta, food);
                    foodBehaviors.put(normaliseCustomItemId(id), parseFoodBehavior(food));
                }
                template.setItemMeta(meta);
                CustomItemPlacementMode placement = CustomItemPlacementMode.valueOf(
                    section.getString("placement", "DENY").trim().toUpperCase(java.util.Locale.ROOT));
                CustomItemClientDefinition clients = parseClients(section, namespace, id, name);
                Map<String, CustomItemClientDefinition> visualStates = new LinkedHashMap<>();
                ConfigSection states = section.getSection("visual-states", false);
                if (states != null) {
                    for (String state : states.getKeys(false)) {
                        ConfigSection stateSection = states.getSection(state, false);
                        if (stateSection != null) visualStates.put(state, parseClients(
                            stateSection, namespace, id + "_" + state, name));
                    }
                }
                registerCustomItem(new CustomItemDefinition(id, template, placement, null, clients, visualStates));
                ownedItemIds.computeIfAbsent(owner, ignored -> new HashSet<>()).add(id);
                Set<Integer> modelData = ownedModelData.computeIfAbsent(normaliseCustomItemId(id), ignored -> new HashSet<>());
                collectModelData(modelData, clients);
                visualStates.values().forEach(value -> collectModelData(modelData, value));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[items] Could not load custom item '" + id + "': " + exception.getMessage());
            }
        }
    }

    private void applyCombatStats(ItemMeta meta, ConfigSection section, String id, Material material) {
        ConfigSection stats = section.getSection("stats", false);
        if (stats == null) return;
        applyAttribute(meta, Attribute.ATTACK_DAMAGE, stats, "attack-damage", id, material, EquipmentSlotGroup.MAINHAND);
        applyAttribute(meta, Attribute.ATTACK_SPEED, stats, "attack-speed", id, material, EquipmentSlotGroup.MAINHAND);
        applyAttribute(meta, Attribute.ARMOR, stats, "armor", id, material, equipmentGroup(material));
        applyAttribute(meta, Attribute.ARMOR_TOUGHNESS, stats, "toughness", id, material, equipmentGroup(material));
    }

    private void applyAttribute(ItemMeta meta, Attribute attribute, ConfigSection stats, String field,
                                String id, Material material, EquipmentSlotGroup slot) {
        if (!stats.contains(field)) return;
        double value = configuredModifierValue(material, attribute, stats.getDouble(field, 0D));
        NamespacedKey key = new NamespacedKey(plugin, "workshop_" + normaliseCustomItemId(id) + "_" + field);
        var existing = meta.getAttributeModifiers(attribute);
        if (existing != null) {
            existing.stream().filter(modifier -> key.equals(modifier.getKey()))
                .forEach(modifier -> meta.removeAttributeModifier(attribute, modifier));
        }
        meta.addAttributeModifier(attribute, new AttributeModifier(key, value,
            AttributeModifier.Operation.ADD_NUMBER, slot));
    }

    /** Convert a child-facing total into the additive modifier Bukkit stores. */
    static double configuredModifierValue(Material material, Attribute attribute, double total) {
        return total - vanillaTotal(material, attribute);
    }

    static EquipmentSlotGroup equipmentGroup(Material material) {
        String name = material == null ? "" : material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return EquipmentSlotGroup.HEAD;
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return EquipmentSlotGroup.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlotGroup.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlotGroup.FEET;
        return EquipmentSlotGroup.ARMOR;
    }

    private static double vanillaTotal(Material material, Attribute attribute) {
        if (material == null) return genericBase(attribute);
        String name = material.name();
        if (attribute == Attribute.ATTACK_DAMAGE) {
            if (name.endsWith("_SWORD")) return tier(name, 4D, 5D, 6D, 7D, 8D);
            if (name.endsWith("_AXE")) return tier(name, 7D, 9D, 9D, 9D, 10D);
            if (name.endsWith("_PICKAXE")) return tier(name, 2D, 3D, 4D, 5D, 6D);
            if (name.endsWith("_SHOVEL")) return tier(name, 2.5D, 3.5D, 4.5D, 5.5D, 5.5D);
        }
        if (attribute == Attribute.ATTACK_SPEED) {
            if (name.endsWith("_SWORD")) return 1.6D;
            if (name.endsWith("_AXE")) return 0.8D;
            if (name.endsWith("_PICKAXE")) return 1.2D;
            if (name.endsWith("_SHOVEL")) return 1.0D;
        }
        if (attribute == Attribute.ARMOR || attribute == Attribute.ARMOR_TOUGHNESS) {
            boolean armor = name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")
                || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
            if (armor) {
                String piece = name.endsWith("_HELMET") || name.equals("TURTLE_HELMET") ? "head"
                    : name.endsWith("_CHESTPLATE") ? "chest"
                    : name.endsWith("_LEGGINGS") ? "legs" : "feet";
                if (attribute == Attribute.ARMOR) {
                    if (name.equals("TURTLE_HELMET")) return 2D;
                    return switch (piece) {
                        case "head" -> tier(name, 1D, 2D, 2D, 3D, 3D);
                        case "chest" -> tier(name, 3D, 5D, 6D, 8D, 8D);
                        case "legs" -> tier(name, 2D, 4D, 5D, 6D, 6D);
                        default -> tier(name, 1D, 1D, 2D, 3D, 3D);
                    };
                }
                if (name.contains("NETHERITE")) return 3D;
                if (name.contains("DIAMOND")) return 2D;
            }
        }
        return genericBase(attribute);
    }

    private static double genericBase(Attribute attribute) {
        if (attribute == Attribute.ATTACK_DAMAGE) return 1D;
        if (attribute == Attribute.ATTACK_SPEED) return 4D;
        return 0D;
    }

    private static double tier(String name, double wood, double stone, double iron, double diamond, double netherite) {
        if (name.contains("NETHERITE")) return netherite;
        if (name.contains("DIAMOND")) return diamond;
        if (name.contains("IRON")) return iron;
        if (name.contains("STONE")) return stone;
        return wood;
    }

    private void applyEnchantments(ItemMeta meta, @Nullable ConfigSection enchantments, String id) {
        if (enchantments == null) return;
        for (String rawId : enchantments.getKeys(false)) {
            NamespacedKey key = NamespacedKey.fromString(rawId.contains(":") ? rawId
                : "minecraft:" + rawId.toLowerCase(Locale.ROOT));
            org.bukkit.enchantments.Enchantment enchantment = key == null ? null
                : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
            int level = Math.max(1, enchantments.getInt(rawId, 1));
            if (enchantment == null) throw new IllegalArgumentException("unknown enchantment '" + rawId + "'");
            meta.addEnchant(enchantment, level, true);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void applyColourAndPotion(ItemMeta meta, ConfigSection item) {
        String colour = item.getString("color", item.getString("colour", "")).trim().replace("#", "");
        Object rawPotion = item.get("potion");
        PotionDefinition namedPotion = null;
        ConfigSection inlinePotion = item.getSection("potion", false);
        if (rawPotion instanceof String reference && !reference.isBlank()) {
            namedPotion = potionDefinitions.get(normaliseCustomItemId(reference));
            if (namedPotion == null) throw new IllegalArgumentException("unknown potion definition '" + reference + "'");
            if (colour.isBlank()) colour = namedPotion.color();
        }
        Color parsedColour = null;
        if (!colour.isBlank()) {
            try { parsedColour = Color.fromRGB(Integer.parseInt(colour, 16)); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid colour '" + colour + "'"); }
        }
        if (parsedColour != null && meta instanceof LeatherArmorMeta leather) leather.setColor(parsedColour);
        if (!(meta instanceof PotionMeta potionMeta) || (namedPotion == null && inlinePotion == null)) return;
        if (parsedColour != null) potionMeta.setColor(parsedColour);
        if (namedPotion != null) {
            applyPotionDefinition(potionMeta, namedPotion);
        } else {
            for (Object raw : inlinePotion.getList("effects")) {
                if (!(raw instanceof Map<?, ?> values)) continue;
                String typeName = values.get("type") == null ? "" : values.get("type").toString().trim();
                PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(typeName.toLowerCase(Locale.ROOT)));
                if (type == null) throw new IllegalArgumentException("unknown potion effect '" + typeName + "'");
                int seconds = Math.max(1, (int) Math.round(number(values.get("duration-seconds"), 1D)));
                int level = Math.max(1, (int) Math.round(number(values.get("level"), 1D)));
                potionMeta.addCustomEffect(new PotionEffect(type, seconds * 20, level - 1), true);
            }
        }
    }

    void applyPotionDefinition(ItemMeta meta, String reference) {
        if (!(meta instanceof PotionMeta potionMeta)) return;
        PotionDefinition definition = potionDefinitions.get(normaliseCustomItemId(reference));
        if (definition == null) return;
        if (!definition.color().isBlank()) potionMeta.setColor(Color.fromRGB(Integer.parseInt(definition.color(), 16)));
        applyPotionDefinition(potionMeta, definition);
    }

    private static void applyPotionDefinition(PotionMeta meta, PotionDefinition definition) {
        for (PotionDefinitionEffect effect : definition.effects()) {
            meta.addCustomEffect(new PotionEffect(effect.type(), effect.seconds() * 20, effect.level() - 1), true);
        }
    }

    private static void collectModelData(Set<Integer> target, @Nullable CustomItemClientDefinition clients) {
        if (clients != null && clients.java() != null) target.add(clients.java().customModelData());
    }

    private static String definitionVersion(ConfigSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) values.put(key, section.get(key));
        return Integer.toHexString(values.toString().hashCode());
    }

    private void reconcileInventory(org.bukkit.entity.Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) contents[index] = reconcileItem(contents[index]);
        player.getInventory().setContents(contents);
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int index = 0; index < armor.length; index++) armor[index] = reconcileItem(armor[index]);
        player.getInventory().setArmorContents(armor);
        player.getInventory().setItemInOffHand(reconcileItem(player.getInventory().getItemInOffHand()));
    }

    private ItemStack reconcileItem(ItemStack item) {
        if (item == null || item.getType().isAir() || api.items().hasAttrib(item, "workshop-preview-token")) return item;
        String id = getCustomItemId(item);
        if (id == null) return item;
        ItemStack template = itemTemplates.get(id);
        if (template == null) template = itemTemplates.entrySet().stream()
            .filter(entry -> normaliseCustomItemId(entry.getKey()).equals(normaliseCustomItemId(id)))
            .map(Map.Entry::getValue).findFirst().orElse(null);
        if (template == null || template.getItemMeta() == null) return item;
        String current = getAttrib(item, "workshop-definition-version", String.class, "");
        String latest = getAttrib(template, "workshop-definition-version", String.class, "");
        if (latest.isBlank() || latest.equals(current)) return item;
        ItemStack replacement = template.clone();
        replacement.setAmount(Math.min(item.getAmount(), replacement.getMaxStackSize()));
        ItemMeta oldMeta = item.getItemMeta();
        ItemMeta newMeta = replacement.getItemMeta();
        if (oldMeta != null && newMeta != null) {
            oldMeta.getPersistentDataContainer().copyTo(newMeta.getPersistentDataContainer(), false);
            if (oldMeta instanceof org.bukkit.inventory.meta.Damageable oldDamage
                && newMeta instanceof org.bukkit.inventory.meta.Damageable newDamage) {
                newDamage.setDamage(Math.min(oldDamage.getDamage(), newDamage.getMaxDamage()));
            }
            replacement.setItemMeta(newMeta);
        }
        return replacement;
    }

    /** Reload the item definitions owned by one content pack. */
    public boolean reloadContentPack(@NotNull String packId) {
        unloadContentPack(packId);
        if (plugin.contentPacks() == null) return true;
        var info = plugin.contentPacks().find(packId);
        if (info == null || !info.enabled()) return true;
        try {
            for (ContentPackDocument document : plugin.contentPacks().documents(packId)) {
                loadPotionDefinitions(document.config().getSection("potions", false), document.packId());
            }
            for (ContentPackDocument document : plugin.contentPacks().documents(packId)) {
                loadConfiguredItems(document.config().getSection("custom-items", false), document.namespace(), document.packId());
            }
        } catch (RuntimeException exception) {
            unloadContentPack(packId);
            plugin.getLogger().warning("[items] Refusing to activate '" + packId + "': "
                + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            return false;
        }
        Bukkit.getOnlinePlayers().forEach(this::reconcileInventory);
        return true;
    }

    /** Reload the legacy/root custom-item section without disturbing pack-owned items. */
    public boolean reloadRootItems() {
        unloadContentPack("__root__");
        var config = api.config().load("config.yml");
        if (config == null || !config.reload()) return false;
        try {
            loadPotionDefinitions(config.getSection("potions", false), "__root__");
            loadConfiguredItems(config.getSection("custom-items", false), "stemcraft", "__root__");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[items] Could not reload root custom items: " + exception.getMessage());
            return false;
        }
        Bukkit.getOnlinePlayers().forEach(this::reconcileInventory);
        return true;
    }

    /** Remove the item registrations owned by one content pack. */
    public void unloadContentPack(@NotNull String packId) {
        Set<String> ids = ownedItemIds.remove(packId);
        if (ids != null) {
            for (String id : ids) {
                itemTemplates.remove(id);
                itemDefinitions.remove(id);
                foodBehaviors.remove(normaliseCustomItemId(id));
                Set<Integer> modelData = ownedModelData.remove(normaliseCustomItemId(id));
                if (modelData != null) configuredModelData.removeAll(modelData);
            }
        }
        Set<String> potions = ownedPotionDefinitions.remove(packId);
        if (potions != null) potions.forEach(potionDefinitions::remove);
    }

    // Bukkit marks its food component API experimental; keep its use isolated here.
    @SuppressWarnings("UnstableApiUsage")
    private static void applyFoodComponent(ItemMeta meta, ConfigSection food) {
        var component = meta.getFood();
        component.setNutrition(Math.max(0, food.getInt("nutrition", 0)));
        component.setSaturation((float) Math.max(0D, food.getDouble("saturation", 0D)));
        component.setCanAlwaysEat(food.getBoolean("always-edible", false));
        meta.setFood(component);
    }

    private FoodBehavior parseFoodBehavior(ConfigSection food) {
        List<ConfiguredEffect> effects = new java.util.ArrayList<>();
        for (Object raw : food.getList("effects")) {
            if (!(raw instanceof Map<?, ?> values)) continue;
            Object configuredType = values.get("type");
            String type = configuredType == null ? "" : configuredType.toString().trim();
            if (type.isEmpty()) continue;
            effects.add(new ConfiguredEffect(type,
                number(values.get("duration-seconds"), 0D),
                (int) number(values.get("amplifier"), 0D),
                Math.clamp(number(values.get("probability"), 1D), 0D, 1D)));
        }
        return new FoodBehavior(food.getDouble("heal", 0D), food.getDouble("damage", 0D),
            food.getString("returns", ""), List.copyOf(effects));
    }

    private void handleConfiguredFood(PlayerItemConsumeEvent event) {
        String id = getCustomItemId(event.getItem());
        if (id == null) return;
        FoodBehavior behavior = foodBehaviors.get(normaliseCustomItemId(id));
        if (behavior == null) return;
        var player = event.getPlayer();
        if (behavior.heal() > 0D) player.heal(behavior.heal());
        if (behavior.damage() > 0D) player.damage(behavior.damage());
        for (ConfiguredEffect effect : behavior.effects()) {
            if (ThreadLocalRandom.current().nextDouble() > effect.probability()) continue;
            NamespacedKey effectKey = NamespacedKey.minecraft(effect.type().toLowerCase(Locale.ROOT));
            PotionEffectType type = org.bukkit.Registry.MOB_EFFECT.get(effectKey);
            if (type != null) player.addPotionEffect(new PotionEffect(type,
                Math.max(1, (int) Math.round(effect.durationSeconds() * 20D)), Math.max(0, effect.amplifier())));
        }
        if (!behavior.returns().isBlank() && !hasVanillaContainerReturn(event.getItem().getType())) api.tasks().runLater(1L, () -> {
            ItemStack returned = createRecipeResult(behavior.returns());
            if (returned == null) return;
            Map<Integer, ItemStack> excess = player.getInventory().addItem(returned);
            excess.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        });
    }

    private static boolean hasVanillaContainerReturn(Material material) {
        return material == Material.RABBIT_STEW || material == Material.MUSHROOM_STEW
            || material == Material.BEETROOT_SOUP || material == Material.SUSPICIOUS_STEW
            || material == Material.HONEY_BOTTLE || material == Material.MILK_BUCKET;
    }

    private ItemStack createRecipeResult(String configured) {
        ItemStack custom = createCustomItem(configured);
        if (custom != null) return custom;
        Material material = Material.matchMaterial(configured.toUpperCase(Locale.ROOT));
        return material == null ? null : new ItemStack(material);
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private record ConfiguredEffect(String type, double durationSeconds, int amplifier, double probability) { }
    private record FoodBehavior(double heal, double damage, String returns, List<ConfiguredEffect> effects) { }
    private record PotionDefinition(String color, List<PotionDefinitionEffect> effects) { }
    private record PotionDefinitionEffect(PotionEffectType type, int seconds, int level) { }

    private void registerGiveCommand() {
        api.tabComplete().register("give-target", (player, args, currentArgument) -> {
            java.util.List<String> targets = new java.util.ArrayList<>(
                Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList());
            targets.addAll(java.util.List.of("@s", "@p", "@a", "@r", "@e"));
            return targets;
        });
        api.tabComplete().register("item", (player, args, currentArgument) -> {
            java.util.List<String> items = new java.util.ArrayList<>();
            for (Material material : Material.values()) {
                if (material.isItem()) items.add("minecraft:" + material.getKey().getKey());
            }
            itemTemplates.keySet().stream().map(id -> "stemcraft:" + id.replace('-', '_')).forEach(items::add);
            return items;
        });
        var builder = api.commands().create("give")
            .description("Give an item to one or more players")
            .usage("/give <targets> <item> [count]")
            .permission("minecraft.command.give")
            .ignoreArg(1)
            .executor((unusedApi, unusedCommand, context) -> executeGive(context));
        builder.tabCompletion("{give-target}", "{item}");
        builder.tabCompletion("{give-target}", "{item}", "{number}");
        originalGiveCommand = Bukkit.getCommandMap().getCommand("give");
        giveCommand = builder.register(STEMCraft.getPlugin());
        org.bukkit.command.Command registered = Bukkit.getCommandMap().getCommand("stemcraft:give");
        if (registered != null) Bukkit.getCommandMap().getKnownCommands().put("give", registered);
    }

    private void executeGive(dev.stemcraft.api.command.CommandContext context) {
        List<String> arguments = context.args();
        if (arguments.size() < 2) {
            context.error("Usage: /give <targets> <item> [count]");
            return;
        }
        org.bukkit.command.CommandSender sender = context.getSender();
        String targetArgument = arguments.get(0);
        String exactTargetName = unquoteTarget(targetArgument);
        String itemArgument = arguments.get(1);
        ItemStack probe;
        try {
            probe = createCustomItem(itemArgument);
        } catch (IllegalArgumentException exception) {
            context.error(exception.getMessage());
            return;
        }
        if (probe == null) {
            List<String> vanillaArguments = new java.util.ArrayList<>(arguments);
            org.bukkit.entity.Player exactPlayer = Bukkit.getPlayerExact(exactTargetName);
            if (exactPlayer != null) vanillaArguments.set(0, vanillaPlayerTarget(exactPlayer.getName()));
            try {
                Bukkit.dispatchCommand(sender, "minecraft:give " + String.join(" ", vanillaArguments));
            } catch (org.bukkit.command.CommandException exception) {
                Throwable cause = exception.getCause();
                context.error(cause == null || cause.getMessage() == null ? exception.getMessage() : cause.getMessage());
            }
            return;
        }
        int amount = 1;
        if (arguments.size() > 2) {
            try { amount = Math.clamp(Integer.parseInt(arguments.get(2)), 1, 99 * 36); }
            catch (NumberFormatException exception) {
                context.error("Invalid item count: {count}", "count", arguments.get(2));
                return;
            }
        }
        java.util.List<org.bukkit.entity.Entity> selected;
        org.bukkit.entity.Player exactPlayer = Bukkit.getPlayerExact(exactTargetName);
        if (exactPlayer != null) {
            selected = java.util.List.of(exactPlayer);
        } else {
            try {
                selected = Bukkit.selectEntities(sender, targetArgument);
            } catch (IllegalArgumentException exception) {
                context.error(exception.getMessage() == null ? "Invalid target selector." : exception.getMessage());
                return;
            }
        }
        int recipients = 0;
        for (org.bukkit.entity.Entity entity : selected) {
            if (!(entity instanceof org.bukkit.entity.Player player)) continue;
            int remaining = amount;
            int maximum = probe.getMaxStackSize();
            while (remaining > 0) {
                ItemStack stack = createCustomItem(itemArgument, Math.min(maximum, remaining));
                if (stack == null) break;
                player.getInventory().addItem(stack).values()
                    .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                remaining -= stack.getAmount();
            }
            recipients++;
        }
        context.success("Gave {amount} [{item}] to {recipients} player(s)",
            "amount", amount, "item", itemArgument, "recipients", recipients);
    }

    private static String unquoteTarget(String target) {
        if (target.length() >= 2 && target.startsWith("\"") && target.endsWith("\"")) {
            return target.substring(1, target.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return target;
    }

    private static String vanillaPlayerTarget(String name) {
        if (name.matches("[A-Za-z0-9_]{1,16}")) return name;
        return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void handleCampfireInput(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
            || !(event.getClickedBlock().getState() instanceof Campfire campfire) || event.getItem() == null) return;
        CampfireRecipe recipe = null;
        java.util.Iterator<org.bukkit.inventory.Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            org.bukkit.inventory.Recipe candidate = recipes.next();
            if (candidate instanceof CampfireRecipe campfireRecipe
                && campfireRecipe.getInputChoice().test(event.getItem())
                && event.getItem().getType() == Material.EGG) {
                recipe = campfireRecipe;
                break;
            }
        }
        if (recipe == null) return;
        int slot = -1;
        for (int index = 0; index < campfire.getSize(); index++) {
            ItemStack existing = campfire.getItem(index);
            if (existing == null || existing.getType().isAir()) { slot = index; break; }
        }
        if (slot < 0) return;
        event.setCancelled(true);
        ItemStack input = event.getItem().clone();
        input.setAmount(1);
        campfire.setItem(slot, input);
        campfire.setCookTime(slot, 0);
        campfire.setCookTimeTotal(slot, recipe.getCookingTime());
        campfire.startCooking(slot);
        campfire.update(true);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) event.getItem().subtract(1);
    }

    private CustomItemClientDefinition parseClients(ConfigSection item, String namespace, String id, String name) {
        String texture = item.getString("texture", "").trim();
        if (texture.isBlank()) return null;
        String safeId = id.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        String textureId = normalizeTextureId(texture, namespace);
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
     * Accept the editor's displayed PNG path as well as the resource-pack
     * form used by existing definitions. Minecraft texture references do not
     * include the textures directory or the .png suffix.
     */
    private static String normalizeTextureId(String value, String namespace) {
        String texture = value.trim().replace('\\', '/');
        String textureNamespace = namespace;
        int separator = texture.indexOf(':');
        if (separator >= 0) {
            textureNamespace = texture.substring(0, separator).trim();
            texture = texture.substring(separator + 1);
        }
        if (texture.startsWith("textures/")) texture = texture.substring("textures/".length());
        if (texture.toLowerCase(Locale.ROOT).endsWith(".png")) {
            texture = texture.substring(0, texture.length() - 4);
        }
        if (texture.isBlank()) throw new IllegalArgumentException("texture path must not be blank");
        return textureNamespace + ":" + texture;
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
            } else if (typeClass == byte[].class) {
                return (PersistentDataType<Z, T>) PersistentDataType.BYTE_ARRAY;
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
            } else if (object instanceof byte[]) {
                return (PersistentDataType<Z, T>) PersistentDataType.BYTE_ARRAY;
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
            applyCustomModelData(meta, definition.clients().java().customModelData());
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
            definition.clients(),
            definition.visualStates()
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

    @Override
    public boolean applyCustomItemVisualState(@NotNull ItemStack item, @Nullable String state) {
        String id = getCustomItemId(item);
        CustomItemDefinition definition = id == null ? null : customItemDefinition(id);
        if (definition == null) return false;
        CustomItemClientDefinition clients = state == null || state.isBlank()
            ? definition.clients() : definition.visualStates().get(state);
        if (clients == null || clients.java() == null) return false;
        return applyClientVisual(item, clients);
    }

    // Bukkit marks its custom model data component API experimental.
    @SuppressWarnings("UnstableApiUsage")
    private static void applyCustomModelData(ItemMeta meta, int modelData) {
        CustomModelDataComponent data = meta.getCustomModelDataComponent();
        data.setFloats(List.of((float) modelData));
        meta.setCustomModelDataComponent(data);
    }

    static boolean applyClientVisual(@NotNull ItemStack item, @NotNull CustomItemClientDefinition clients) {
        if (clients.java() == null) return false;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey model = NamespacedKey.fromString(clients.java().itemModelId());
        if (model == null) return false;
        meta.setItemModel(model);
        applyCustomModelData(meta, clients.java().customModelData());
        return item.setItemMeta(meta);
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

        CustomItemSpecification specification = parseCustomItemSpecification(id);
        ItemStack template = itemTemplates.get(specification.id());
        if (template == null) {
            String path = specification.id().contains(":")
                ? specification.id().substring(specification.id().indexOf(':') + 1) : specification.id();
            template = itemTemplates.get(path);
            if (template == null) template = itemTemplates.get(path.replace('_', '-'));
        }
        if (template == null) {
            return null;
        }

        ItemStack stack = template.clone();
        stack.setAmount(quantity);
        if (!specification.properties().isEmpty()) {
            String path = normaliseCustomItemId(specification.id());
            CustomItemPropertyHandler handler = propertyHandlers.get(path);
            if (handler == null) throw new IllegalArgumentException("Custom item '" + specification.id()
                + "' does not support properties.");
            handler.apply(stack, specification.properties());
        }
        return stack;
    }

    @Override
    public void registerCustomItemPropertyHandler(@NotNull String id, @NotNull CustomItemPropertyHandler handler) {
        propertyHandlers.put(normaliseCustomItemId(id), handler);
    }

    @Override
    public void unregisterCustomItemPropertyHandler(@NotNull String id) {
        propertyHandlers.remove(normaliseCustomItemId(id));
    }

    private static String normaliseCustomItemId(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return path.replace('_', '-').toLowerCase(java.util.Locale.ROOT);
    }

    private static CustomItemSpecification parseCustomItemSpecification(String value) {
        int open = value.indexOf('[');
        if (open < 0) return new CustomItemSpecification(value, Map.of());
        if (!value.endsWith("]") || open == 0) throw new IllegalArgumentException("Invalid custom item properties: " + value);
        String id = value.substring(0, open);
        String body = value.substring(open + 1, value.length() - 1).trim();
        if (body.isEmpty()) return new CustomItemSpecification(id, Map.of());
        Map<String, String> properties = new LinkedHashMap<>();
        for (String entry : body.split(",")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank())
                throw new IllegalArgumentException("Invalid custom item property: " + entry.trim());
            properties.put(pair[0].trim().toLowerCase(java.util.Locale.ROOT), pair[1].trim());
        }
        return new CustomItemSpecification(id, Collections.unmodifiableMap(properties));
    }

    private record CustomItemSpecification(String id, Map<String, String> properties) { }

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

    @Override
    public @NotNull String getItemName(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        var displayNameComponent = meta == null ? null : meta.displayName();
        if (displayNameComponent != null) {
            String displayName = PlainTextComponentSerializer.plainText().serialize(displayNameComponent).trim();
            if (!displayName.isBlank()) return displayName;
        }
        String materialName = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(materialName.length());
        boolean capitalize = true;
        for (int index = 0; index < materialName.length(); index++) {
            char character = materialName.charAt(index);
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }
}
