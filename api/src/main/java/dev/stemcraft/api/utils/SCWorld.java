package dev.stemcraft.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class SCWorld {
    World world;

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

//    public World getBukkitWorld() {
//        return this.world;
//    }
//
//    public void recordChanges() {
//        STEMCraftApi.getInstance().getWorldService().recordWorldChanges(this.world);
//    }
//
//    public void discardChanges() {
//        STEMCraftApi.getInstance().getWorldService().discardWorldChanges(this.world);
//    }
//
//    public boolean hasRecordedChanges() {
//        return STEMCraftApi.getInstance().getWorldService().hasRecordedChanges(this.world);
//    }
//
//    public void rollbackChanges() {
//        STEMCraftApi.getInstance().getWorldService().rollbackWorldChanges(this.world);
//    }
//
//    public void commitChanges() {
//        STEMCraftApi.getInstance().getWorldService().commitWorldChanges(this.world);
//    }
//
//    public void recordBlockChange(int x, int y, int z) {
//        STEMCraftApi.getInstance().getWorldService().recordBlockChange(this.world, x, y, z);
//    }
//
//    public void recordBlockChange(Location loc) {
//        STEMCraftApi.getInstance().getWorldService().recordBlockChange(this.world, loc);
//    }
//
//    public void recordBlockChange(Block block) {
//        STEMCraftApi.getInstance().getWorldService().recordBlockChange(this.world, block);
//    }
}
