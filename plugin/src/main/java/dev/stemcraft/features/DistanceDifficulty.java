package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public class DistanceDifficulty implements STEMCraftFeature {
    private static final int DEFAULT_RANGE = 500;
    private static final double DEFAULT_MULTIPLIER = 0.25;

    private NamespacedKey key;
    private int range;
    private double multiplier;

    @Override
    public void onEnable(STEMCraftAPI api) {
        String base = getConfigBase();

        key = new NamespacedKey(STEMCraft.getInstance(), "distance_multiplier");
        range = api.config().getInt(base + ".range", DEFAULT_RANGE);
        multiplier = api.config().getDouble(base + ".multiplier", DEFAULT_MULTIPLIER);

        api.registerEvent(CreatureSpawnEvent.class, event -> {
            LivingEntity entity = event.getEntity();
            if (!(entity instanceof Monster)) return;

            World world = entity.getWorld();

            int dx = Math.abs(entity.getLocation().getBlockX() - world.getSpawnLocation().getBlockX());
            int dz = Math.abs(entity.getLocation().getBlockZ() - world.getSpawnLocation().getBlockZ());
            int dist = Math.max(dx, dz);

            double mult = resolveMultiplier(dist);
            if (mult == 1.0) return;

            entity.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, mult);
        });

        api.registerEvent(EntityDamageByEntityEvent.class, event -> {
            Entity rawDamager = event.getDamager();
            Entity victim = event.getEntity();
            if (!(victim instanceof LivingEntity livingVictim)) return;

            Entity trueDamager = unwrapDamager(rawDamager);

            boolean mobHitsPlayer = isMobDamagingPlayer(trueDamager, livingVictim);
            boolean playerHitsMob = isPlayerDamagingMob(trueDamager, livingVictim);

            if (!mobHitsPlayer && !playerHitsMob) return;

            if (mobHitsPlayer) {
                Player player = (Player) livingVictim;
                if (player.getGameMode() != GameMode.SURVIVAL) return;

                double entityMultiplier = getMultiplierFromEntity(trueDamager);
                if (entityMultiplier != 1.0) {
                    event.setDamage(event.getDamage() * entityMultiplier);
                }
            } else {
                Player player = (Player) trueDamager;
                if (player.getGameMode() != GameMode.SURVIVAL) return;

                double entityMultiplier = getMultiplierFromEntity(livingVictim);
                if (entityMultiplier != 1.0) {
                    event.setDamage(event.getDamage() / entityMultiplier);
                }
            }
        });
    }

    private double resolveMultiplier(int dist) {
        int bands = dist / range;
        return 1.0 + (bands * multiplier);
    }

    private Entity unwrapDamager(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) {
                return shooterEntity;
            }
        }
        return damager;
    }

    private boolean isMobDamagingPlayer(Entity damager, LivingEntity victim) {
        if (!(victim instanceof Player)) return false;
        if (damager instanceof Player) return false;
        return damager instanceof Monster;
    }

    private boolean isPlayerDamagingMob(Entity damager, LivingEntity victim) {
        if (!(damager instanceof Player)) return false;
        if (victim instanceof Player) return false;
        return victim instanceof Monster;
    }

    private double getMultiplierFromEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return 1.0;
        PersistentDataContainer pdc = living.getPersistentDataContainer();
        Double val = pdc.get(key, PersistentDataType.DOUBLE);
        return val != null ? val : 1.0;
    }
}