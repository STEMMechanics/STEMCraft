/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.ability.ItemAbilityService;
import dev.stemcraft.api.service.condition.ConditionContext;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Runtime trigger/action engine for configuration-backed custom item abilities. */
public final class ItemAbilityServiceImpl extends BaseService implements ItemAbilityService {
    private static final int MAX_PARTICLES = 64;
    private static final int MAX_FLOWERS_PER_TRIGGER = 1;
    private final Map<String, List<Ability>> abilities = new LinkedHashMap<>();
    private final Map<String, List<Ability>> previewAbilities = new HashMap<>();
    private final Map<String, Set<String>> ownedItems = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<PotionEffectType, AppliedEffect>> appliedEffects = new HashMap<>();
    private final Map<UUID, Map<String, Location>> walkAnchors = new HashMap<>();
    private final Map<UUID, List<Ability>> projectileSnapshots = new HashMap<>();
    private NamespacedKey projectileKey;

    public ItemAbilityServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "item-abilities");
    }

    @Override
    public void onEnable() {
        projectileKey = new NamespacedKey(plugin, "workshop_projectile_ability");
        if (plugin.contentPacks() != null) {
            plugin.contentPacks().registerHandler("item-abilities", this::reloadContentPack, this::unloadContentPack,
                this::validateContentPack);
        }
        api.events().register(PlayerJoinEvent.class, event -> refresh(event.getPlayer()));
        api.events().register(PlayerQuitEvent.class, event -> {
            removeOwnedEffects(event.getPlayer());
            cooldowns.remove(event.getPlayer().getUniqueId());
            appliedEffects.remove(event.getPlayer().getUniqueId());
            walkAnchors.remove(event.getPlayer().getUniqueId());
        });
        api.events().register(PlayerItemHeldEvent.class, event -> scheduleRefresh(event.getPlayer()));
        api.events().register(PlayerSwapHandItemsEvent.class, event -> scheduleRefresh(event.getPlayer()));
        api.events().register(PlayerItemBreakEvent.class, event -> scheduleRefresh(event.getPlayer()));
        api.events().register(PlayerDropItemEvent.class, event -> scheduleRefresh(event.getPlayer()));
        api.events().register(InventoryClickEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) scheduleRefresh(player);
        });
        api.events().register(InventoryDragEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) scheduleRefresh(player);
        });
        api.events().register(EntityDamageByEntityEvent.class, this::handleDamage, EventPriority.MONITOR, true);
        api.events().register(ProjectileLaunchEvent.class, this::handleProjectileLaunch, EventPriority.MONITOR, true);
        api.events().register(ProjectileHitEvent.class, this::handleProjectileHit, EventPriority.MONITOR, true);
        api.events().register(EntityRemoveFromWorldEvent.class,
            event -> projectileSnapshots.remove(event.getEntity().getUniqueId()));
        api.events().register(PlayerItemConsumeEvent.class, this::handleConsume, EventPriority.MONITOR, true);
        api.events().register(PlayerMoveEvent.class, this::handleWalk, EventPriority.MONITOR, true);
        api.tasks().repeating("workshop:item-abilities:refresh", 40L, 40L,
            () -> Bukkit.getOnlinePlayers().forEach(this::refresh));
        reloadAll();
    }

    @Override
    public void onDisable() {
        api.tasks().cancel("workshop:item-abilities:refresh");
        for (Player player : Bukkit.getOnlinePlayers()) removeOwnedEffects(player);
        abilities.clear();
        ownedItems.clear();
        cooldowns.clear();
        appliedEffects.clear();
        walkAnchors.clear();
        projectileSnapshots.clear();
        previewAbilities.clear();
    }

    @Override
    public boolean reloadContentPack(@NotNull String packId) {
        unloadContentPack(packId);
        if (plugin.contentPacks() == null) return true;
        var info = plugin.contentPacks().find(packId);
        if (info == null || !info.enabled()) return true;
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ContentPackDocument document : plugin.contentPacks().documents(packId)) {
            parseDocument(document, ids, errors, false);
        }
        if (!errors.isEmpty()) {
            plugin.getLogger().warning("[item-abilities] Refusing to activate '" + packId + "': "
                + String.join(" | ", errors));
            return false;
        }
        ownedItems.put(packId, ids);
        walkAnchors.clear();
        refreshOnlinePlayers();
        return true;
    }

    @Override
    public void unloadContentPack(@NotNull String packId) {
        Set<String> ids = ownedItems.remove(packId);
        if (ids != null) ids.forEach(abilities::remove);
        walkAnchors.values().forEach(anchors -> anchors.keySet().removeIf(id -> ids != null && ids.contains(id)));
        refreshOnlinePlayers();
    }

    @Override
    public void registerPreview(@NotNull String token, @NotNull String itemId,
                                @NotNull Map<String, Object> definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> abilitiesMap = stringMap(definition.get("abilities"));
        List<Ability> parsed = parseAbilities(abilitiesMap, normalize(itemId),
            "live-draft." + itemId + ".abilities", errors);
        if (errors.isEmpty()) previewAbilities.put(token, parsed);
        else previewAbilities.remove(token);
    }

    @Override
    public void unregisterPreview(@NotNull String token) {
        previewAbilities.remove(token);
    }

    /** Validate configured ability syntax without changing the active registry. */
    public List<String> validateContentPack(@NotNull String packId) {
        if (plugin.contentPacks() == null) return List.of();
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ContentPackDocument document : plugin.contentPacks().documents(packId)) {
            parseDocument(document, ids, errors);
        }
        return List.copyOf(errors);
    }

    @Override
    public void refresh(@NotNull Player player) {
        removeOwnedEffects(player);
        Map<PotionEffectType, AppliedEffect> owned = new HashMap<>();
        for (Equipped equipped : equipped(player)) {
            String itemId = api.items().getCustomItemId(equipped.item());
            if (itemId == null) continue;
            for (Ability ability : abilitiesFor(equipped.item())) {
                if (!"while-equipped".equals(ability.trigger()) || !slotMatches(ability.slot(), equipped.slot())) continue;
                executeAbility(ability, player, null, null, equipped.item(), owned);
            }
        }
        if (!owned.isEmpty()) appliedEffects.put(player.getUniqueId(), owned);
    }

    private void reloadAll() {
        abilities.clear();
        ownedItems.clear();
        walkAnchors.clear();
    }

    private void parseDocument(ContentPackDocument document, Set<String> itemIds, List<String> errors) {
        parseDocument(document, itemIds, errors, true);
    }

    private void parseDocument(ContentPackDocument document, Set<String> itemIds, List<String> errors,
                               boolean register) {
        ConfigSection items = document.config().getSection("custom-items", false);
        if (items == null) return;
        for (String itemId : items.getKeys(false)) {
            ConfigSection item = items.getSection(itemId, false);
            if (item == null || !item.isSection("abilities")) continue;
            String normalizedId = normalize(itemId);
            List<Ability> parsed = parseAbilities(item.getSection("abilities", false), normalizedId,
                document.packId() + "/" + document.file().getName() + ":custom-items." + itemId + ".abilities", errors);
            if (!parsed.isEmpty()) {
                if (register) abilities.put(normalizedId, parsed);
                itemIds.add(normalizedId);
            }
        }
    }

    private List<Ability> parseAbilities(dev.stemcraft.api.config.ConfigSectionView section, String itemId,
                                         String path, List<String> errors) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) values.put(key, section.get(key));
        }
        return parseAbilities(values, itemId, path, errors);
    }

    private List<Ability> parseAbilities(Map<String, Object> section, String itemId,
                                         String path, List<String> errors) {
        List<Ability> result = new ArrayList<>();
        if (section == null) return result;
        for (Map.Entry<String, Object> sectionEntry : section.entrySet()) {
            String trigger = sectionEntry.getKey();
            if (!Set.of("while-equipped", "on-hit", "on-hurt", "on-projectile-hit", "on-consume", "on-walk")
                .contains(normalize(trigger))) {
                errors.add(path + "." + trigger + " is not a supported trigger.");
                continue;
            }
            Object raw = sectionEntry.getValue();
            Collection<?> entries = raw == null ? List.of()
                : raw instanceof Collection<?> collection ? collection : List.of(raw);
            int index = 0;
            for (Object value : entries) {
                if (!(value instanceof Map<?, ?> map)) {
                    errors.add(path + "." + trigger + "[" + index + "] must be a mapping.");
                    index++;
                    continue;
                }
                String abilityId = itemId + ":" + normalize(trigger) + ":" + index;
                List<ActionDefinition> actions = parseActions(map.get("actions"), map, path + "." + trigger + "[" + index + "]", errors);
                double chance = number(map.get("chance"), 1D);
                long cooldown = Math.max(0L, Math.round(number(map.get("cooldown-seconds"),
                    number(map.get("cooldown"), 0D)) * 20D));
                double spacing = Math.max(0D, number(map.get("spacing"), 0D));
                String slot = normalize(text(map.get("slot"), "any"));
                String defaultTarget = normalize(text(map.get("target"), defaultTarget(trigger)));
                Map<String, Object> conditions = stringMap(map.get("conditions"));
                errors.addAll(api.conditions().validate(conditions, path + "." + trigger + "[" + index + "].conditions"));
                if (chance < 0D || chance > 1D) errors.add(path + "." + trigger + " chance must be between 0 and 1.");
                result.add(new Ability(abilityId, normalize(trigger), Math.clamp(chance, 0D, 1D), cooldown,
                    spacing, slot, defaultTarget, conditions, stringMap(map.get("target-filter")), actions));
                index++;
            }
        }
        return List.copyOf(result);
    }

    private List<ActionDefinition> parseActions(Object rawActions, Map<?, ?> ability, String path, List<String> errors) {
        Collection<?> values = rawActions instanceof Collection<?> collection ? collection
            : List.of(ability);
        List<ActionDefinition> result = new ArrayList<>();
        for (Object raw : values) {
            if (!(raw instanceof Map<?, ?> map)) {
                errors.add(path + ".actions must contain mappings.");
                continue;
            }
            String type = normalize(text(map.get("type"), ""));
            if (!Set.of("effect", "particles", "title", "sound", "plant-flower").contains(type)) {
                errors.add(path + ".actions.type '" + type + "' is unsupported.");
                continue;
            }
            double chance = number(map.get("chance"), 1D);
            if (chance < 0D || chance > 1D) errors.add(path + ".actions.chance must be between 0 and 1.");
            result.add(new ActionDefinition(type, stringMap(map)));
        }
        return List.copyOf(result);
    }

    private void handleDamage(EntityDamageByEntityEvent event) {
        Entity rawDamager = event.getDamager();
        Entity trueDamager = rawDamager;
        if (rawDamager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            trueDamager = shooter;
        }
        Player attacker = trueDamager instanceof Player player ? player : null;
        if (attacker != null) {
            executeForItem(attacker, attacker.getInventory().getItemInMainHand(), "on-hit", event.getEntity(), null);
        }
        if (event.getEntity() instanceof Player victim) {
            executeForEquipped(victim, "on-hurt", trueDamager, null);
        }
    }

    private void handleProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack projectileItem = event.getEntity() instanceof AbstractArrow arrow ? arrow.getItemStack() : null;
        String itemId = api.items().getCustomItemId(projectileItem);
        if (itemId == null) itemId = api.items().getCustomItemId(held);
        List<Ability> snapshot = new ArrayList<>();
        if (projectileItem != null) snapshot.addAll(abilitiesFor(projectileItem).stream()
            .filter(ability -> "on-projectile-hit".equals(ability.trigger()))
            .toList());
        if (snapshot.isEmpty()) snapshot.addAll(abilitiesFor(held).stream()
            .filter(ability -> "on-projectile-hit".equals(ability.trigger()))
            .toList());
        if (!snapshot.isEmpty()) {
            projectileSnapshots.put(event.getEntity().getUniqueId(), snapshot);
            if (itemId != null) event.getEntity().getPersistentDataContainer()
                .set(projectileKey, PersistentDataType.STRING, itemId);
        }
    }

    private void handleProjectileHit(ProjectileHitEvent event) {
        List<Ability> snapshot = projectileSnapshots.remove(event.getEntity().getUniqueId());
        if (snapshot == null || !(event.getEntity().getShooter() instanceof Player player)) return;
        for (Ability ability : snapshot) executeAbility(ability, player, event.getHitEntity(), null,
            player.getInventory().getItemInMainHand(), null);
    }

    private void handleConsume(PlayerItemConsumeEvent event) {
        executeForItem(event.getPlayer(), event.getItem(), "on-consume", event.getPlayer(), null);
        scheduleRefresh(event.getPlayer());
    }

    private void handleWalk(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || event.getTo() == null) return;
        Player player = event.getPlayer();
        Location previous = event.getFrom().clone();
        for (Equipped equipped : equipped(player)) {
            String id = api.items().getCustomItemId(equipped.item());
            if (id == null) continue;
            for (Ability ability : abilitiesFor(equipped.item())) {
                if (!"on-walk".equals(ability.trigger()) || !slotMatches(ability.slot(), equipped.slot())) continue;
                Location anchor = walkAnchors.getOrDefault(player.getUniqueId(), Map.of()).get(ability.id());
                if (ability.spacing() > 0D && anchor != null
                    && anchor.getWorld() == previous.getWorld()
                    && anchor.distanceSquared(previous) < ability.spacing() * ability.spacing()) continue;
                if (executeAbility(ability, player, player, previous, equipped.item(), null)) {
                    walkAnchors.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                        .put(ability.id(), previous.clone());
                }
            }
        }
    }

    private void executeForItem(Player player, ItemStack item, String trigger, Entity struck, Location previous) {
        String id = api.items().getCustomItemId(item);
        if (id == null) return;
        for (Ability ability : abilitiesFor(item)) {
            if (trigger.equals(ability.trigger())) executeAbility(ability, player, struck, previous, item, null);
        }
    }

    private void executeForEquipped(Player player, String trigger, Entity struck, Location previous) {
        for (Equipped equipped : equipped(player)) {
            String id = api.items().getCustomItemId(equipped.item());
            if (id == null) continue;
            for (Ability ability : abilitiesFor(equipped.item())) {
                if (trigger.equals(ability.trigger()) && slotMatches(ability.slot(), equipped.slot()))
                    executeAbility(ability, player, struck, previous, equipped.item(), null);
            }
        }
    }

    private boolean executeAbility(Ability ability, Player player, Entity struck, Location previous,
                                   ItemStack item, Map<PotionEffectType, AppliedEffect> effectTarget) {
        if (!ready(player, ability)) return false;
        if (ThreadLocalRandom.current().nextDouble() > ability.chance()) return false;
        if (!api.conditions().matches(ability.conditions(), conditionContext(player, struck))) return false;
        if (!targetAllowed(ability.targetFilter(), struck)) return false;
        markCooldown(player, ability);
        int[] flowerCount = {0};
        for (ActionDefinition action : ability.actions()) {
            if (!roll(action.values().get("chance"), 1D)) continue;
            String target = normalize(text(action.values().get("target"), ability.defaultTarget()));
            Object resolved = resolveTarget(target, player, struck, previous);
            executeAction(action, resolved, player, previous, effectTarget, flowerCount);
        }
        return true;
    }

    private ConditionContext conditionContext(Player player, Entity target) {
        Location location = player.getLocation();
        String biome = "";
        if (location.getWorld() != null && location.getBlock().getBiome() != null) {
            biome = location.getBlock().getBiome().getKey().asString();
        }
        String entityType = target == null ? player.getType().name() : target.getType().name();
        return new ConditionContext(player, target, player, location, biome, entityType, "",
            api.distanceDifficulty().tier(location), Map.of("player-kill", target != null && !(target instanceof Player)));
    }

    private void executeAction(ActionDefinition action, Object target, Player owner, Location previous,
                               Map<PotionEffectType, AppliedEffect> effectTarget, int[] flowerCount) {
        switch (action.type()) {
            case "effect" -> {
                if (!(target instanceof LivingEntity living)) return;
                String effectName = text(action.values().get("effect"), "");
                PotionEffectType effect = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(effectName.toLowerCase(Locale.ROOT)));
                if (effect == null) return;
                int duration = Math.max(1, (int) Math.round(number(action.values().get("duration-seconds"), 3D) * 20D));
                int level = Math.max(1, integer(action.values().get("level"), 1));
                PotionEffect current = living.getPotionEffect(effect);
                if (current != null && current.getAmplifier() >= level - 1) return;
                living.addPotionEffect(new PotionEffect(effect, duration, level - 1));
                if (effectTarget != null && living == owner)
                    effectTarget.put(effect, new AppliedEffect(level - 1, duration));
            }
            case "particles" -> {
                Location location = locationOf(target);
                if (location == null || location.getWorld() == null) return;
                Particle particle;
                try { particle = Particle.valueOf(text(action.values().get("particle"), "POOF").toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ignored) { return; }
                int count = Math.clamp(integer(action.values().get("count"), 8), 1, MAX_PARTICLES);
                location.getWorld().spawnParticle(particle, location, count, 0.25, 0.35, 0.25, 0.01);
            }
            case "title" -> {
                if (!(target instanceof Player targetPlayer)) return;
                targetPlayer.showTitle(Title.title(
                    Component.text(text(action.values().get("title"), "")),
                    Component.text(text(action.values().get("subtitle"), "")),
                    integer(action.values().get("fade-in"), 5), integer(action.values().get("stay"), 30),
                    integer(action.values().get("fade-out"), 5)));
            }
            case "sound" -> {
                Location location = locationOf(target);
                if (location == null || location.getWorld() == null) return;
                String soundName = text(action.values().get("sound"), "").toLowerCase(Locale.ROOT);
                Sound sound = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT)
                    .get(NamespacedKey.minecraft(soundName));
                if (sound != null) location.getWorld().playSound(location, sound,
                    (float) number(action.values().get("volume"), 1D), (float) number(action.values().get("pitch"), 1D));
            }
            case "plant-flower" -> {
                if (flowerCount[0] >= MAX_FLOWERS_PER_TRIGGER) return;
                if (plantFlower(owner, action, previous)) flowerCount[0]++;
            }
            default -> { }
        }
    }

    private boolean plantFlower(Player player, ActionDefinition action, Location previous) {
        if (previous == null || player.getWorld() != previous.getWorld()) return false;
        Block place = previous.getBlock();
        Block ground = place.getRelative(BlockFace.DOWN);
        if (!place.getChunk().isLoaded() || !ground.isSolid() || !place.isReplaceable()) return false;
        Material flower = Material.matchMaterial(text(action.values().get("flower"), "POPPY").toUpperCase(Locale.ROOT));
        if (flower == null || !flower.isBlock()) return false;
        BlockPlaceEvent protectionCheck = new BlockPlaceEvent(place, place.getState(), ground,
            new ItemStack(flower), player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(protectionCheck);
        if (protectionCheck.isCancelled() || !protectionCheck.canBuild()) return false;
        place.setType(flower, false);
        return true;
    }

    private Object resolveTarget(String target, Player player, Entity struck, Location previous) {
        return switch (target) {
            case "struck", "target" -> struck;
            case "attacker", "self", "player" -> player;
            case "previous", "previous-walking-position" -> previous;
            default -> player;
        };
    }

    private static Location locationOf(Object target) {
        if (target instanceof Entity entity) return entity.getLocation();
        return target instanceof Location location ? location : null;
    }

    private boolean targetAllowed(Map<String, Object> filters, Entity target) {
        if (filters.isEmpty()) return true;
        String type = text(filters.get("entity-type"), null);
        if (type != null && (target == null || !target.getType().name().equalsIgnoreCase(type))) return false;
        Object playerOnly = filters.get("player");
        return !(playerOnly instanceof Boolean required) || target instanceof Player == required;
    }

    private boolean ready(Player player, Ability ability) {
        long now = System.currentTimeMillis();
        return cooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(ability.id(), 0L) <= now;
    }

    private void markCooldown(Player player, Ability ability) {
        if (ability.cooldownTicks() <= 0) return;
        cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
            .put(ability.id(), System.currentTimeMillis() + ability.cooldownTicks() * 50L);
    }

    private void removeOwnedEffects(Player player) {
        Map<PotionEffectType, AppliedEffect> owned = appliedEffects.remove(player.getUniqueId());
        if (owned == null) return;
        for (Map.Entry<PotionEffectType, AppliedEffect> entry : owned.entrySet()) {
            PotionEffect current = player.getPotionEffect(entry.getKey());
            AppliedEffect expected = entry.getValue();
            if (current != null && current.getAmplifier() == expected.amplifier()
                && current.getDuration() <= expected.durationTicks() + 2) {
                player.removePotionEffect(entry.getKey());
            }
        }
    }

    private void scheduleRefresh(Player player) {
        api.tasks().runOnceDelay("workshop:item-abilities:player:" + player.getUniqueId(), 1L,
            () -> refresh(player));
    }

    private List<Ability> abilitiesFor(ItemStack item) {
        if (item == null) return List.of();
        String token = api.items().getAttrib(item, "workshop-preview-token", String.class, "");
        if (!token.isBlank() && previewAbilities.containsKey(token)) return previewAbilities.get(token);
        String id = api.items().getCustomItemId(item);
        return id == null ? List.of() : abilities.getOrDefault(normalize(id), List.of());
    }

    private void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    private static List<Equipped> equipped(Player player) {
        List<Equipped> result = new ArrayList<>();
        result.add(new Equipped(EquipmentSlot.HAND, player.getInventory().getItemInMainHand()));
        result.add(new Equipped(EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand()));
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length > 0) result.add(new Equipped(EquipmentSlot.FEET, armor[0]));
        if (armor.length > 1) result.add(new Equipped(EquipmentSlot.LEGS, armor[1]));
        if (armor.length > 2) result.add(new Equipped(EquipmentSlot.CHEST, armor[2]));
        if (armor.length > 3) result.add(new Equipped(EquipmentSlot.HEAD, armor[3]));
        return result;
    }

    private static boolean slotMatches(String configured, EquipmentSlot actual) {
        return configured.equals("any") || configured.equals("armor") && actual.isArmor()
            || configured.equals("hand") && (actual == EquipmentSlot.HAND || actual == EquipmentSlot.OFF_HAND)
            || configured.replace('-', '_').equalsIgnoreCase(actual.name());
    }

    private static boolean roll(Object configured, double fallback) {
        double chance = configured instanceof Number number ? number.doubleValue() : fallback;
        return chance >= 1D || chance > 0D && ThreadLocalRandom.current().nextDouble() < chance;
    }

    private static String defaultTarget(String trigger) {
        return switch (normalize(trigger)) {
            case "on-hit", "on-projectile-hit" -> "struck";
            case "on-hurt" -> "attacker";
            default -> "self";
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Map<String, Object> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private record Ability(String id, String trigger, double chance, long cooldownTicks, double spacing, String slot,
                           String defaultTarget, Map<String, Object> conditions, Map<String, Object> targetFilter,
                           List<ActionDefinition> actions) { }

    private record ActionDefinition(String type, Map<String, Object> values) { }

    private record Equipped(EquipmentSlot slot, ItemStack item) { }

    private record AppliedEffect(int amplifier, int durationTicks) { }
}
