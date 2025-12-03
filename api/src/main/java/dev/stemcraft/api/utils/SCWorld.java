package dev.stemcraft.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class SCWorld {
    final World world;

    public SCWorld(World world) {
        this.world = world;
    }

    public SCWorld evictAllPlayers() {
        World firstWorld = Bukkit.getWorlds().getFirst();

        if(this.world.equals(firstWorld)) {
            throw new IllegalStateException("Cannot evict players from the main world");
        }

        world.getPlayers().forEach(player -> {
            SCPlayer.teleport(player, Bukkit.getWorlds().getFirst().getSpawnLocation());
        });

        return this;
    }
}
