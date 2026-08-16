package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Rotten-flesh recipes and efficient, pathfinder-backed mob luring mechanics. */
public final class RottenFleshUsesFeature extends BaseFeature {
    static final String DOG_TREAT_ID = "dog-treat";
    static final String ZOMBIE_BAIT_ID = "zombie-bait";
    static final String STEW_ID = "rotten-flesh-stew";
    private static final String TASK_ID = "feature:rotten-flesh-uses";

    private final Set<UUID> droppedBaits = new HashSet<>();
    private final Map<UUID, UUID> zombieBaitTargets = new HashMap<>();
    private final Map<UUID, UUID> zombiePlayerTargets = new HashMap<>();

    private double dogTreatHeal;
    private int dogTreatLoveTicks;
    private double carriedMultiplier;
    private double heldMultiplier;
    private double droppedRange;
    private double consumeDistance;
    private double zombiePathSpeed;
    private int nightVisionTicks;
    private int hungerTicks;
    private long updateTicks;

    public RottenFleshUsesFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        loadSettings();
        api.events().register(PlayerInteractEvent.class, this::onComposterInteract, EventPriority.HIGHEST, true);
        api.events().register(PlayerInteractEntityEvent.class, this::onEntityInteract, EventPriority.HIGHEST, true);
        api.events().register(PlayerItemConsumeEvent.class, event -> {
            if (isZombieBait(event.getItem())) event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
        api.events().register(PlayerItemConsumeEvent.class, this::onStewConsume, EventPriority.MONITOR, true);
        api.events().register(ItemSpawnEvent.class, event -> trackBait(event.getEntity()));
        api.events().register(ItemDespawnEvent.class, event -> releaseBait(event.getEntity().getUniqueId()));
        api.events().register(EntityPickupItemEvent.class, event -> {
            if (isZombieBait(event.getItem().getItemStack())) releaseBait(event.getItem().getUniqueId());
        }, EventPriority.MONITOR, true);
        api.events().register(ChunkLoadEvent.class, event -> {
            for (Entity entity : event.getChunk().getEntities()) if (entity instanceof Item item) trackBait(item);
        });
        api.events().register(ChunkUnloadEvent.class, this::onChunkUnload);
        api.events().register(EntityDeathEvent.class, this::onEntityDeath);
        api.events().register(PlayerQuitEvent.class, event -> zombiePlayerTargets.entrySet().removeIf(
            entry -> entry.getValue().equals(event.getPlayer().getUniqueId())));
        for (var world : Bukkit.getWorlds()) for (Item item : world.getEntitiesByClass(Item.class)) trackBait(item);
        api.tasks().repeating(TASK_ID, updateTicks, this::updateMobLures);
    }

    @Override
    public void onReload() {
        super.onReload();
        loadSettings();
        api.tasks().cancel(TASK_ID);
        api.tasks().repeating(TASK_ID, updateTicks, this::updateMobLures);
    }

    @Override
    public void onDisable() {
        api.tasks().cancel(TASK_ID);
        zombieBaitTargets.keySet().forEach(this::stopZombiePath);
        droppedBaits.clear();
        zombieBaitTargets.clear();
        zombiePlayerTargets.clear();
    }

    private void loadSettings() {
        var config = getConfigSection();
        dogTreatHeal = Math.max(0.0, config.getDouble("dog-treat.heal", 12.0));
        dogTreatLoveTicks = Math.max(1, config.getInt("dog-treat.love-mode-ticks", 600));
        carriedMultiplier = Math.max(1.0, config.getDouble("zombie-bait.carried-range-multiplier", 1.5));
        heldMultiplier = Math.max(carriedMultiplier, config.getDouble("zombie-bait.held-range-multiplier", 2.0));
        droppedRange = Math.max(1.0, config.getDouble("zombie-bait.dropped-range", 20.0));
        consumeDistance = Math.max(0.5, config.getDouble("zombie-bait.consume-distance", 1.5));
        zombiePathSpeed = Math.max(0.1, config.getDouble("zombie-bait.path-speed", 1.1));
        updateTicks = Math.max(5L, config.getLong("zombie-bait.update-ticks", 10L));
        nightVisionTicks = Math.max(1, config.getInt("stew.night-vision-ticks", 400));
        hungerTicks = Math.max(1, config.getInt("stew.hunger-ticks", 200));
    }

    private void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Wolf wolf)) return;
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (isZombieBait(held)) {
            event.setCancelled(true);
            return;
        }
        if (!api.items().isCustomItemId(DOG_TREAT_ID, held)) return;
        event.setCancelled(true);
        if (!wolf.isTamed()) return;
        double maximum = wolf.getAttribute(Attribute.MAX_HEALTH) == null
            ? wolf.getHealth() : wolf.getAttribute(Attribute.MAX_HEALTH).getValue();
        boolean wasFull = wolf.getHealth() >= maximum;
        if (!wasFull) wolf.setHealth(Math.min(maximum, wolf.getHealth() + dogTreatHeal));
        else if (wolf.isAdult() && wolf.canBreed() && !wolf.isLoveMode()) {
            wolf.setLoveModeTicks(dogTreatLoveTicks);
            wolf.setBreedCause(event.getPlayer().getUniqueId());
            wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 1, 0), 7, 0.35, 0.35, 0.35, 0);
        }
        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.2f);
        consumeHeldItem(event.getPlayer(), event.getHand(), held);
    }

    private void onComposterInteract(PlayerInteractEvent event) {
        if (!getConfigSection().getBoolean("composting.enabled", true)
            || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.COMPOSTER
            || event.getItem() == null || event.getItem().getType() != Material.ROTTEN_FLESH
            || api.items().getCustomItemId(event.getItem()) != null) return;
        Block composter = event.getClickedBlock();
        if (!(composter.getBlockData() instanceof Levelled levelled)
            || levelled.getLevel() >= levelled.getMaximumLevel()) return;
        event.setCancelled(true);
        levelled.setLevel(levelled.getLevel() + 1);
        composter.setBlockData(levelled, true);
        Sound sound = levelled.getLevel() == levelled.getMaximumLevel()
            ? Sound.BLOCK_COMPOSTER_READY : Sound.BLOCK_COMPOSTER_FILL_SUCCESS;
        composter.getWorld().playSound(composter.getLocation(), sound, 1.0f, 1.0f);
        consumeHeldItem(event.getPlayer(), event.getHand(), event.getItem());
    }

    private void onStewConsume(PlayerItemConsumeEvent event) {
        if (!api.items().isCustomItemId(STEW_ID, event.getItem())) return;
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, nightVisionTicks, 0));
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, hungerTicks, 0));
    }

    private void updateMobLures() {
        updateDroppedBaits();
        updateCarriedBaits();
    }

    private void updateDroppedBaits() {
        Map<UUID, BaitCandidate> candidates = new HashMap<>();
        Iterator<UUID> iterator = droppedBaits.iterator();
        while (iterator.hasNext()) {
            UUID baitId = iterator.next();
            Entity entity = Bukkit.getEntity(baitId);
            if (!(entity instanceof Item bait) || !bait.isValid() || !isZombieBait(bait.getItemStack())) {
                iterator.remove();
                releaseBaitAssignments(baitId);
                continue;
            }
            for (Entity nearby : bait.getNearbyEntities(droppedRange, droppedRange, droppedRange)) {
                if (!(nearby instanceof Zombie zombie) || !zombie.isValid() || zombie.isDead()) continue;
                double distance = zombie.getLocation().distanceSquared(bait.getLocation());
                BaitCandidate existing = candidates.get(zombie.getUniqueId());
                if (existing == null || distance < existing.distanceSquared()) {
                    candidates.put(zombie.getUniqueId(), new BaitCandidate(bait, distance));
                }
            }
        }

        for (Map.Entry<UUID, UUID> entry : new HashMap<>(zombieBaitTargets).entrySet()) {
            if (!candidates.containsKey(entry.getKey())) releaseZombie(entry.getKey());
        }
        Set<UUID> consumed = new HashSet<>();
        for (Map.Entry<UUID, BaitCandidate> entry : candidates.entrySet()) {
            Zombie zombie = entity(entry.getKey(), Zombie.class);
            Item bait = entry.getValue().bait();
            if (zombie == null || consumed.contains(bait.getUniqueId()) || !bait.isValid()) continue;
            if (entry.getValue().distanceSquared() <= consumeDistance * consumeDistance) {
                consumeBait(bait);
                consumed.add(bait.getUniqueId());
                releaseZombie(zombie.getUniqueId());
                continue;
            }
            zombieBaitTargets.put(zombie.getUniqueId(), bait.getUniqueId());
            zombiePlayerTargets.remove(zombie.getUniqueId());
            if (zombie.getTarget() != null) zombie.setTarget(null);
            zombie.getPathfinder().moveTo(bait, zombiePathSpeed);
        }
    }

    private void updateCarriedBaits() {
        Set<UUID> activeAssignments = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) continue;
            double multiplier = baitRangeMultiplier(player);
            if (multiplier <= 1.0) continue;
            double baseRange = 35.0;
            for (Entity nearby : player.getNearbyEntities(baseRange * multiplier, baseRange * multiplier, baseRange * multiplier)) {
                if (!(nearby instanceof Zombie zombie) || zombieBaitTargets.containsKey(zombie.getUniqueId())) continue;
                AttributeInstance followRange = zombie.getAttribute(Attribute.FOLLOW_RANGE);
                if (followRange != null) baseRange = Math.max(1.0, followRange.getBaseValue());
                double maximum = baseRange * multiplier;
                if (zombie.getLocation().distanceSquared(player.getLocation()) > maximum * maximum) continue;
                LivingEntity current = zombie.getTarget();
                if (current != null && current != player && current.getLocation().distanceSquared(zombie.getLocation())
                    <= player.getLocation().distanceSquared(zombie.getLocation())) continue;
                zombie.setTarget(player);
                zombiePlayerTargets.put(zombie.getUniqueId(), player.getUniqueId());
                activeAssignments.add(zombie.getUniqueId());
            }
        }
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(zombiePlayerTargets).entrySet()) {
            if (activeAssignments.contains(entry.getKey())) continue;
            Zombie zombie = entity(entry.getKey(), Zombie.class);
            Player player = Bukkit.getPlayer(entry.getValue());
            if (zombie != null && zombie.getTarget() == player && player != null) {
                AttributeInstance range = zombie.getAttribute(Attribute.FOLLOW_RANGE);
                double normal = range == null ? 35.0 : range.getBaseValue();
                if (!sameWorld(zombie, player) || zombie.getLocation().distanceSquared(player.getLocation()) > normal * normal) {
                    zombie.setTarget(null);
                }
            }
            zombiePlayerTargets.remove(entry.getKey());
        }
    }

    private double baitRangeMultiplier(Player player) {
        if (isZombieBait(player.getInventory().getItemInMainHand())
            || isZombieBait(player.getInventory().getItemInOffHand())) return heldMultiplier;
        for (ItemStack item : player.getInventory().getStorageContents()) if (isZombieBait(item)) return carriedMultiplier;
        return 1.0;
    }

    private void trackBait(Item item) {
        if (isZombieBait(item.getItemStack())) droppedBaits.add(item.getUniqueId());
    }

    private void consumeBait(Item bait) {
        ItemStack stack = bait.getItemStack();
        if (stack.getAmount() <= 1) {
            UUID id = bait.getUniqueId();
            droppedBaits.remove(id);
            releaseBaitAssignments(id);
            bait.remove();
        } else {
            stack.setAmount(stack.getAmount() - 1);
            bait.setItemStack(stack);
        }
    }

    private void releaseBait(UUID baitId) {
        droppedBaits.remove(baitId);
        releaseBaitAssignments(baitId);
    }

    private void releaseBaitAssignments(UUID baitId) {
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(zombieBaitTargets).entrySet()) {
            if (entry.getValue().equals(baitId)) releaseZombie(entry.getKey());
        }
    }

    private void releaseZombie(UUID zombieId) {
        zombieBaitTargets.remove(zombieId);
        stopZombiePath(zombieId);
    }

    private void stopZombiePath(UUID id) {
        Zombie zombie = entity(id, Zombie.class);
        if (zombie != null) zombie.getPathfinder().stopPathfinding();
    }

    private void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            UUID id = entity.getUniqueId();
            if (entity instanceof Item) releaseBait(id);
            if (entity instanceof Zombie) {
                zombieBaitTargets.remove(id);
                zombiePlayerTargets.remove(id);
            }
        }
    }

    private void onEntityDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        zombieBaitTargets.remove(id);
        zombiePlayerTargets.remove(id);
    }

    private void consumeHeldItem(Player player, EquipmentSlot hand, ItemStack stack) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (stack.getAmount() <= 1) player.getInventory().setItem(hand, null);
        else {
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItem(hand, stack);
        }
    }

    private boolean isZombieBait(ItemStack item) {
        return item != null && api.items().isCustomItemId(ZOMBIE_BAIT_ID, item);
    }

    private static boolean sameWorld(Entity first, Entity second) {
        return first.getWorld().equals(second.getWorld());
    }

    private static <T extends Entity> T entity(UUID id, Class<T> type) {
        Entity entity = Bukkit.getEntity(id);
        return type.isInstance(entity) && entity.isValid() ? type.cast(entity) : null;
    }

    private record BaitCandidate(Item bait, double distanceSquared) { }
}
