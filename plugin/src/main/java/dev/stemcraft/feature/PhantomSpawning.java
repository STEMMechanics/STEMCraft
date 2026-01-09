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

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Feature that modifies phantom and enderman spawning behavior.
 */
public class PhantomSpawning extends BaseFeature {

    /**
     * Constructor.
     *
     * @param api The STEMCraft API instance.
     */
    public PhantomSpawning(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is being enabled.
     */
    @Override
    public void onEnable() {
        api.events().register(CreatureSpawnEvent.class, this::onCreatureSpawn, EventPriority.HIGH, false);
    }

   /**
    * Handle creature spawn events to modify phantom and enderman spawning.
    *
    * @param event The creature spawn event.
    */
    private void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType();
        if (type != EntityType.PHANTOM && type != EntityType.ENDERMAN) {
            return;
        }

        World world = event.getLocation().getWorld();
        if (world == null) {
            return;
        }

        World.Environment env = world.getEnvironment();

        // 1) Overworld: block insomnia phantoms
        if (env == World.Environment.NORMAL) {
            if (type == EntityType.PHANTOM && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                event.setCancelled(true);
            }
            return;
        }

        // 2) End: 50/50 mix between phantoms and endermen
        if (env == World.Environment.THE_END) {
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();

            // only touch natural / phantom spawns, leave others (e.g. plugins) alone
            if (reason != CreatureSpawnEvent.SpawnReason.NATURAL) {
                return;
            }

            boolean wantPhantom = ThreadLocalRandom.current().nextBoolean();
            EntityType desiredType = wantPhantom ? EntityType.PHANTOM : EntityType.ENDERMAN;

            if (type == desiredType) {
                // already the desired type, let it spawn
                return;
            }

            // replace with the desired type
            event.setCancelled(true);
            world.spawnEntity(
                    event.getLocation(),
                    desiredType
            );
        }
    }
}