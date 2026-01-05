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

/**
 * Feature that adjusts mob difficulty based on distance from world spawn.
 */
public class DistanceDifficulty extends BaseFeature {
    private static final int DEFAULT_RANGE = 500;
    private static final double DEFAULT_MULTIPLIER = 0.25;

    private NamespacedKey key;
    private int range;
    private double multiplier;

    /**
     * Constructor for DistanceDifficulty feature.
     */
    public DistanceDifficulty(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Registers event listeners to adjust mob difficulty based on distance from world spawn.
     */
    @Override
    public void onEnable() {
        key = new NamespacedKey(STEMCraft.getPlugin(), "distance_multiplier");

        range = getConfigSection().getInt("range", DEFAULT_RANGE);
        multiplier = getConfigSection().getDouble("multiplier", DEFAULT_MULTIPLIER);

        api.events().register(CreatureSpawnEvent.class, event -> {
            LivingEntity entity = event.getEntity();
            if (!(entity instanceof Monster)) return;

            World world = entity.getWorld();

            int dx = Math.abs(entity.getLocation().getBlockX() - world.getSpawnLocation().getBlockX());
            int dz = Math.abs(entity.getLocation().getBlockZ() - world.getSpawnLocation().getBlockZ());
            int dist = Math.max(dx, dz);

            double multiplier = resolveMultiplier(dist);
            if (multiplier == 1.0) return;

            entity.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, multiplier);
        });

        api.events().register(EntityDamageByEntityEvent.class, event -> {
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

    /**
     * Calculates the difficulty multiplier based on distance.
     *
     * @param dist Distance from world spawn.
     * @return Calculated multiplier.
     */
    private double resolveMultiplier(int dist) {
        int bands = dist / range;
        return 1.0 + (bands * multiplier);
    }

    /**
     * Unwraps the true damager entity from projectiles.
     *
     * @param damager The raw damager entity.
     * @return The true damager entity.
     */
    private Entity unwrapDamager(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) {
                return shooterEntity;
            }
        }
        return damager;
    }

    /**
     * Checks if a mob is damaging a player.
     *
     * @param damager The entity causing damage.
     * @param victim  The entity receiving damage.
     * @return True if a mob is damaging a player, false otherwise.
     */
    private boolean isMobDamagingPlayer(Entity damager, LivingEntity victim) {
        if (!(victim instanceof Player)) return false;
        if (damager instanceof Player) return false;
        return damager instanceof Monster;
    }

    /**
     * Checks if a player is damaging a mob.
     *
     * @param damager The entity causing damage.
     * @param victim  The entity receiving damage.
     * @return True if a player is damaging a mob, false otherwise.
     */
    private boolean isPlayerDamagingMob(Entity damager, LivingEntity victim) {
        if (!(damager instanceof Player)) return false;
        if (victim instanceof Player) return false;
        return victim instanceof Monster;
    }

    /**
     * Retrieves the difficulty multiplier from an entity's persistent data.
     *
     * @param entity The entity to check.
     * @return The difficulty multiplier, or 1.0 if not set.
     */
    private double getMultiplierFromEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return 1.0;
        PersistentDataContainer pdc = living.getPersistentDataContainer();
        Double val = pdc.get(key, PersistentDataType.DOUBLE);
        return val != null ? val : 1.0;
    }
}