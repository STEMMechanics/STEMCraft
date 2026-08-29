package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Small, independently configurable survival quality-of-life mechanics. */
public final class SurvivalQolFeature extends BaseFeature {
    private static final String REFILL_TASK_PREFIX = "feature:survival-qol-refill:";
    private final NamespacedKey mobOwnerKey = new NamespacedKey("stemcraft", "named-mob-owner");
    private final NamespacedKey questNpcProfileKey = new NamespacedKey("stemcraft", "quest-npc-profile");
    private final Map<UUID, Long> anvilWarnings = new HashMap<>();

    public SurvivalQolFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        api.events().register(PlayerInteractEvent.class, this::onAutoSelectTool, EventPriority.NORMAL, true);
        api.events().register(PlayerInteractEvent.class, this::onCropHarvest, EventPriority.HIGHEST, true);
        api.events().register(EntityChangeBlockEvent.class, this::onCropTrample, EventPriority.HIGHEST, true);
        api.events().register(EntityUnleashEvent.class, this::onEntityUnleash, EventPriority.HIGHEST, true);
        api.events().register(PlayerInteractEntityEvent.class, this::onEntityInteract, EventPriority.MONITOR, false);
        api.events().register(PrepareAnvilEvent.class, this::onPrepareAnvil, EventPriority.MONITOR, false);
        api.events().register(PlayerItemDamageEvent.class, this::onItemDamage, EventPriority.MONITOR, true);
        api.events().register(PlayerItemConsumeEvent.class, event -> scheduleRefill(event.getPlayer(), event.getHand(), event.getItem()));
        api.events().register(BlockPlaceEvent.class, event -> scheduleRefill(event.getPlayer(), event.getHand(), event.getItemInHand()));
        api.events().register(PlayerInteractEvent.class, event -> {
            EquipmentSlot hand = event.getHand();
            if (hand != null) scheduleRefill(event.getPlayer(), hand, event.getItem());
        }, EventPriority.MONITOR, false);
        api.tasks().repeating("feature:survival-qol-minecarts", 1L, this::accelerateMinecarts);
    }

    private void onAutoSelectTool(PlayerInteractEvent event) {
        if (!enabled("auto-select-tool", true) || !allowed("auto-select-tool", event.getPlayer())
            || !triggersAutoSelect(event.getAction())
            || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        PlayerInventory inventory = event.getPlayer().getInventory();
        int heldSlot = inventory.getHeldItemSlot();
        int bestSlot = preferredToolSlot(event.getClickedBlock(), inventory, heldSlot);
        if (bestSlot < 0 || bestSlot == heldSlot) return;
        ItemStack held = inventory.getItem(heldSlot);
        inventory.setItem(heldSlot, inventory.getItem(bestSlot));
        inventory.setItem(bestSlot, held);
    }

    static boolean triggersAutoSelect(Action action) {
        return action == Action.LEFT_CLICK_BLOCK;
    }

    static int preferredToolSlot(Block block, PlayerInventory inventory, int heldSlot) {
        int bestSlot = -1;
        float bestSpeed = -1.0f;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack candidate = contents[slot];
            if (candidate == null || candidate.getType().isAir() || !block.isPreferredTool(candidate)) continue;
            float speed = block.getDestroySpeed(candidate);
            if (speed > bestSpeed || speed == bestSpeed && slot == heldSlot) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    @Override
    public void onDisable() {
        api.tasks().cancel("feature:survival-qol-minecarts");
        anvilWarnings.clear();
    }

    private void onCropHarvest(PlayerInteractEvent event) {
        if (!enabled("hoe-harvest", true) || !allowed("hoe-harvest", event.getPlayer())
            || !event.getPlayer().isSneaking() || event.getClickedBlock() == null
            || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
            || event.getHand() != EquipmentSlot.HAND || !isHoe(event.getItem())) return;
        Block centre = event.getClickedBlock();
        if (!(centre.getBlockData() instanceof Ageable centreCrop) || centreCrop.getAge() < centreCrop.getMaximumAge()) return;
        event.setCancelled(true);
        ItemStack tool = event.getItem();
        int harvested = 0;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            Block crop = centre.getRelative(x, 0, z);
            if (crop.getType() != centre.getType() || !(crop.getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) continue;
            boolean replanted = false;
            for (ItemStack drop : crop.getDrops(tool, event.getPlayer())) {
                if (!replanted && isPlantingItem(crop.getType(), drop.getType())) {
                    drop.subtract(1);
                    replanted = true;
                }
                if (!drop.isEmpty()) crop.getWorld().dropItemNaturally(crop.getLocation(), drop);
            }
            ageable.setAge(0);
            crop.setBlockData(ageable, true);
            harvested++;
        }
        if (harvested > 0 && tool != null) event.getPlayer().getInventory().setItemInMainHand(tool.damage(1, event.getPlayer()));
    }

    private void onCropTrample(EntityChangeBlockEvent event) {
        if (enabled("disable-crop-trampling", true)
            && (!(event.getEntity() instanceof Player player) || allowed("disable-crop-trampling", player))
            && event.getBlock().getType() == Material.FARMLAND && event.getTo() == Material.DIRT) event.setCancelled(true);
    }

    private void onEntityUnleash(EntityUnleashEvent event) {
        Entity leashHolder = event.getEntity() instanceof LivingEntity living && living.isLeashed()
            ? living.getLeashHolder() : null;
        if (enabled("stronger-leads", true)
            && (!(leashHolder instanceof Player player) || allowed("stronger-leads", player))
            && event.getReason() == EntityUnleashEvent.UnleashReason.DISTANCE) event.setCancelled(true);
    }

    private void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (isManagedNpc(entity)) return;
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (held.getType() == Material.NAME_TAG) {
            api.tasks().runLater(1L, () -> {
                if (entity.isValid() && entity.customName() != null) entity.getPersistentDataContainer()
                    .set(mobOwnerKey, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
            });
            return;
        }
        if (!enabled("named-mob-info", true) || !allowed("named-mob-info", event.getPlayer())
            || entity.customName() == null || !(entity instanceof LivingEntity living)) return;
        String owner = "Unknown";
        if (entity instanceof Tameable tameable && tameable.getOwner() != null) owner = tameable.getOwner().getName();
        else {
            String uuid = entity.getPersistentDataContainer().get(mobOwnerKey, PersistentDataType.STRING);
            if (uuid != null) {
                try {
                    String known = org.bukkit.Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                    if (known != null) owner = known;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        var maxHealth = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        api.messages().send(event.getPlayer(), getConfigSection().getString("named-mob-info.message",
            "/info/{name} • {type} • {health}/{max_health} health • Owner: {owner}"),
            "name", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(entity.customName()),
            "type", friendly(entity.getType().name()), "health", Math.ceil(living.getHealth()),
            "max_health", Math.ceil(maxHealth == null ? living.getHealth() : maxHealth.getValue()), "owner", owner);
    }

    private boolean isManagedNpc(Entity entity) {
        return entity.hasMetadata("NPC")
            || entity.getPersistentDataContainer().has(questNpcProfileKey, PersistentDataType.STRING);
    }

    private void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!enabled("anvil-warning", true)) return;
        int threshold = getConfigSection().getInt("anvil-warning.level", 35);
        if (event.getView().getRepairCost() < threshold || !(event.getView().getPlayer() instanceof Player player)
            || !allowed("anvil-warning", player)) return;
        long now = System.currentTimeMillis();
        if (now - anvilWarnings.getOrDefault(player.getUniqueId(), 0L) < 2000L) return;
        anvilWarnings.put(player.getUniqueId(), now);
        api.messages().send(player, getConfigSection().getString("anvil-warning.message",
            "/warn/This anvil operation costs {levels} levels and is close to becoming Too Expensive!"),
            "levels", event.getView().getRepairCost());
    }

    private void onItemDamage(PlayerItemDamageEvent event) {
        scheduleToolRefillIfBreaking(event);
        if (!enabled("durability-warning", true)
            || !allowed("durability-warning", event.getPlayer())
            || !(event.getItem().getItemMeta() instanceof Damageable damageable)) return;
        int maximum = event.getItem().getType().getMaxDurability();
        if (maximum <= 0) return;
        int before = maximum - damageable.getDamage();
        int after = maximum - damageable.getDamage() - event.getDamage();
        int warning = getConfigSection().getInt("durability-warning.warning-percent", 10);
        int critical = getConfigSection().getInt("durability-warning.critical-percent", 2);
        int beforePercent = before * 100 / maximum;
        int afterPercent = Math.max(0, after * 100 / maximum);
        String path = beforePercent > critical && afterPercent <= critical ? "durability-warning.critical-message"
            : beforePercent > warning && afterPercent <= warning ? "durability-warning.warning-message" : null;
        if (path != null) api.messages().send(event.getPlayer(), getConfigSection().getString(path,
            path.endsWith("critical-message") ? "/warn/⚠ Your {item} is about to break!"
                : "/warn/⚠ Your {item} is getting badly damaged!"), "item", friendly(event.getItem().getType().name()));
    }

    private void scheduleToolRefillIfBreaking(PlayerItemDamageEvent event) {
        if (!enabled("auto-refill-tools", true)
            || !allowed("auto-refill-tools", event.getPlayer())
            || !(event.getItem().getItemMeta() instanceof Damageable damageable)) return;
        int maximum = event.getItem().getType().getMaxDurability();
        if (maximum <= 0 || damageable.getDamage() + event.getDamage() < maximum) return;
        PlayerInventory inventory = event.getPlayer().getInventory();
        EquipmentSlot hand;
        if (event.getItem().equals(inventory.getItemInMainHand())) hand = EquipmentSlot.HAND;
        else if (event.getItem().equals(inventory.getItemInOffHand())) hand = EquipmentSlot.OFF_HAND;
        else return;
        scheduleToolRefill(event.getPlayer(), hand, event.getItem().getType());
    }

    private void scheduleToolRefill(Player player, EquipmentSlot hand, Material material) {
        UUID uuid = player.getUniqueId();
        api.tasks().runLater(1L, () -> {
            Player online = org.bukkit.Bukkit.getPlayer(uuid);
            if (online == null) return;
            PlayerInventory inventory = online.getInventory();
            ItemStack current = inventory.getItem(hand);
            if (current != null && !current.getType().isAir()) return;
            for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (candidate != null && sameToolMaterial(material, candidate.getType())) {
                    inventory.setItem(hand, candidate);
                    inventory.setItem(slot, null);
                    break;
                }
            }
        });
    }

    static boolean sameToolMaterial(Material broken, Material candidate) {
        return broken == candidate;
    }

    private boolean allowed(String feature, Player player) {
        return permissionAllows(getConfigSection().getString(feature + ".permission", ""), player);
    }

    private boolean enabled(String feature, boolean defaultValue) {
        Object legacyValue = getConfigSection().get(feature);
        return legacyValue instanceof Boolean legacy ? legacy
            : getConfigSection().getBoolean(feature + ".enabled", defaultValue);
    }

    static boolean permissionAllows(String configuredPermission, Player player) {
        String permission = configuredPermission == null ? "" : configuredPermission.trim();
        return permission.isEmpty() || player.hasPermission(permission);
    }

    private void scheduleRefill(Player player, EquipmentSlot hand, ItemStack used) {
        if (!enabled("auto-refill", true) || !allowed("auto-refill", player)
            || used == null || used.getType().isAir()) return;
        ItemStack match = used.clone();
        UUID uuid = player.getUniqueId();
        api.tasks().runLater(1L, () -> {
            Player online = org.bukkit.Bukkit.getPlayer(uuid);
            if (online == null) return;
            PlayerInventory inventory = online.getInventory();
            ItemStack current = inventory.getItem(hand);
            if (current != null && !current.getType().isAir()) return;
            for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (candidate != null && candidate.isSimilar(match)) {
                    inventory.setItem(hand, candidate);
                    inventory.setItem(slot, null);
                    break;
                }
            }
        });
    }

    private void accelerateMinecarts() {
        if (!enabled("powered-minecarts", true)) return;
        double maximum = getConfigSection().getDouble("powered-minecarts.max-speed", 0.8D);
        double multiplier = getConfigSection().getDouble("powered-minecarts.multiplier", 1.08D);
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
            String permission = getConfigSection().getString("powered-minecarts.permission", "").trim();
            if (!permission.isEmpty() && cart.getPassengers().stream().filter(Player.class::isInstance)
                .map(Player.class::cast).noneMatch(player -> permissionAllows(permission, player))) continue;
            Block rail = cart.getLocation().getBlock();
            if (!Tag.RAILS.isTagged(rail.getType())) rail = rail.getRelative(0, -1, 0);
            if (!Tag.RAILS.isTagged(rail.getType()) || !(rail.getBlockData() instanceof Powerable powerable) || !powerable.isPowered()) continue;
            cart.setMaxSpeed(maximum);
            Vector velocity = cart.getVelocity();
            double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
            if (horizontal > 0.01D && horizontal < maximum) cart.setVelocity(velocity.multiply(Math.min(multiplier, maximum / horizontal)));
        }
    }

    private static boolean isHoe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_HOE");
    }

    private static boolean isPlantingItem(Material crop, Material item) {
        return switch (crop) {
            case WHEAT -> item == Material.WHEAT_SEEDS;
            case BEETROOTS -> item == Material.BEETROOT_SEEDS;
            case CARROTS -> item == Material.CARROT;
            case POTATOES -> item == Material.POTATO;
            case NETHER_WART -> item == Material.NETHER_WART;
            default -> false;
        };
    }

    private static String friendly(String value) {
        String[] words = value.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) result.append(result.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return result.toString();
    }
}
