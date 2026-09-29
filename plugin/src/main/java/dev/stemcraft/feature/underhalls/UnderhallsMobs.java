package dev.stemcraft.feature.underhalls;

import dev.stemcraft.chunkgen.UnderhallsGenerator;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Sparse, transient melee encounters. Never loads chunks to find a spawn site. */
public final class UnderhallsMobs {
    private static final NamespacedKey TAG = new NamespacedKey("stemcraft", "underhalls-stalker");
    private final Random random = new Random();
    private long nextAttempt;
    public static boolean isStalker(Entity entity) {
        return entity.getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
    }
    public static void configure(Skeleton skeleton) {
        skeleton.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1);
        skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        skeleton.getEquipment().setItemInMainHandDropChance(0);
        skeleton.setCanPickupItems(false);
        skeleton.setPersistent(false);
        skeleton.setRemoveWhenFarAway(true);
        skeleton.setSilent(false);
        attribute(skeleton, Attribute.MAX_HEALTH, 60);
        skeleton.setHealth(60);
        attribute(skeleton, Attribute.ATTACK_DAMAGE, 5);
        attribute(skeleton, Attribute.ARMOR, 12);
        attribute(skeleton, Attribute.KNOCKBACK_RESISTANCE, .5);
        attribute(skeleton, Attribute.MOVEMENT_SPEED, .24);
        attribute(skeleton, Attribute.FOLLOW_RANGE, 28);
    }
    private static void attribute(Skeleton skeleton, Attribute attribute, double value) {
        var instance = skeleton.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }
    public void tick(boolean enabled, int intervalSeconds, int cap) {
        long now = System.currentTimeMillis();
        if (!enabled || now < nextAttempt) return;
        nextAttempt = now + Math.max(10, intervalSeconds) * 1000L;
        for (World world : Bukkit.getWorlds()) {
            if (!(world.getGenerator() instanceof UnderhallsGenerator) || world.getDifficulty() == Difficulty.PEACEFUL) continue;
            List<Player> players = world.getPlayers().stream().filter(p -> !p.isDead() &&
                (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)).toList();
            List<LivingEntity> mobs = world.getLivingEntities().stream().filter(UnderhallsMobs::isStalker).toList();
            if (players.isEmpty() || mobs.size() >= cap) continue;
            Player player = players.get(random.nextInt(players.size()));
            if (mobs.stream().anyMatch(m -> m.getLocation().distanceSquared(player.getLocation()) < 64 * 64)) continue;
            for (int attempt = 0; attempt < 24; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2, radius = 18 + random.nextInt(15);
                Location site = player.getLocation().clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                site.setY(UnderhallsGenerator.floor(world) + 1);
                site = site.toBlockLocation().add(.5, 0, .5);
                if (!world.isChunkLoaded(site.getBlockX() >> 4, site.getBlockZ() >> 4) || !world.getWorldBorder().isInside(site) ||
                    UnderhallsGenerator.reserved(world, site.getBlockX(), site.getBlockZ()) ||
                    !site.getBlock().isPassable() || !site.clone().add(0, 1, 0).getBlock().isPassable() ||
                    !site.clone().add(0, -1, 0).getBlock().getType().isSolid()) continue;
                Location candidate = site;
                if (world.getPlayers().stream().anyMatch(p -> p.getLocation().distanceSquared(candidate) < 16 * 16 ||
                    p.getLocation().distanceSquared(candidate) < 64 * 64 && visible(p, candidate))) continue;
                world.spawn(site, Skeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, UnderhallsMobs::configure);
                break;
            }
        }
    }
    private static boolean visible(Player player, Location site) {
        var direction = site.clone().add(0, 1.5, 0).toVector().subtract(player.getEyeLocation().toVector());
        double length = direction.length();
        return length < .01 || player.getWorld().rayTraceBlocks(player.getEyeLocation(), direction.normalize(), length,
            FluidCollisionMode.NEVER, true) == null;
    }
    public static void clear(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) if (isStalker(entity)) entity.remove();
    }
    public static void clearAll() {
        for (World world : Bukkit.getWorlds()) if (world.getGenerator() instanceof UnderhallsGenerator)
            for (Chunk chunk : world.getLoadedChunks()) clear(chunk);
    }
}
