package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.concurrent.ThreadLocalRandom;

public class PhantomSpawning implements STEMCraftFeature {
    @Override
    public void onEnable(STEMCraftAPI api) {
        api.registerEvent(CreatureSpawnEvent.class, this::onCreatureSpawn, EventPriority.HIGH, false);
    }

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

            // only touch natural / phantom spawns, leave others (eg. plugins) alone
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
