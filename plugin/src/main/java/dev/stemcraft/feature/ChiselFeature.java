/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Rotates safe, single-block building states with the custom chisel item. */
public final class ChiselFeature extends BaseFeature {
    static final String ITEM_ID = "chisel";
    private static final List<BlockFace> CARDINAL_FACES = List.of(
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
    private static final List<BlockFace> ROTATIONS = List.of(
        BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST,
        BlockFace.WEST_SOUTH_WEST, BlockFace.WEST, BlockFace.WEST_NORTH_WEST,
        BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST, BlockFace.NORTH,
        BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
        BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST,
        BlockFace.SOUTH_SOUTH_EAST);
    private static final List<Axis> AXES = List.of(Axis.X, Axis.Y, Axis.Z);

    public ChiselFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        api.events().register(PlayerInteractEvent.class, this::onUse, EventPriority.HIGHEST, true);
    }

    private void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null || !api.items().isCustomItemId(ITEM_ID, event.getItem())) return;

        Block block = event.getClickedBlock();
        BlockData data = block.getBlockData();
        if (!rotate(block.getType(), data)) return;

        event.setCancelled(true);
        block.setBlockData(data, true);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.45F, 1.35F);
        block.getWorld().spawnParticle(Particle.CRIT, block.getLocation().add(0.5, 0.5, 0.5),
            3, 0.18, 0.18, 0.18, 0.01);

        ItemStack chisel = event.getItem();
        if (chisel != null && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getPlayer().getInventory().setItemInMainHand(chisel.damage(1, event.getPlayer()));
        }
    }

    /**
     * Rotates one supported state in place. Multi-block and redstone-sensitive
     * blocks are deliberately not included here.
     */
    static boolean rotate(Material material, BlockData data) {
        if (data instanceof Orientable orientable) return rotateAxis(orientable);
        if (data instanceof Rotatable rotatable) return rotateRotation(rotatable);
        if (data instanceof Directional directional && isSafeDirectional(material)) {
            return rotateDirection(directional);
        }
        return false;
    }

    static boolean isSafeDirectional(Material material) {
        String name = material.name();
        return name.endsWith("_STAIRS") || name.endsWith("_GLAZED_TERRACOTTA")
            || material == Material.CARVED_PUMPKIN || material == Material.JACK_O_LANTERN;
    }

    private static boolean rotateDirection(Directional data) {
        int current = CARDINAL_FACES.indexOf(data.getFacing());
        for (int step = 1; step <= CARDINAL_FACES.size(); step++) {
            BlockFace next = CARDINAL_FACES.get(Math.floorMod(current + step, CARDINAL_FACES.size()));
            if (data.getFaces().contains(next)) {
                data.setFacing(next);
                return true;
            }
        }
        return false;
    }

    private static boolean rotateAxis(Orientable data) {
        int current = AXES.indexOf(data.getAxis());
        for (int step = 1; step <= AXES.size(); step++) {
            Axis next = AXES.get(Math.floorMod(current + step, AXES.size()));
            if (data.getAxes().contains(next)) {
                data.setAxis(next);
                return true;
            }
        }
        return false;
    }

    private static boolean rotateRotation(Rotatable data) {
        int current = ROTATIONS.indexOf(data.getRotation());
        data.setRotation(ROTATIONS.get(Math.floorMod(current + 1, ROTATIONS.size())));
        return true;
    }
}
