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

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared grave storage rules used by land and liquid grave placement.
 */
final class GraveStorageSupport {
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };
    private static final int SINGLE_CHEST_SIZE = 27;

    private GraveStorageSupport() {
    }

    static boolean requiresDoubleChest(List<ItemStack> drops) {
        Inventory probe = Bukkit.createInventory(null, SINGLE_CHEST_SIZE);
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            Map<Integer, ItemStack> overflow = probe.addItem(stack.clone());
            if (!overflow.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    static Block findDoubleChestPartner(Block primaryChestBlock, boolean allowLiquidPartner) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Block candidate = primaryChestBlock.getRelative(face);
            Material candidateType = candidate.getType();
            if (!canReplaceForChest(candidateType)) {
                continue;
            }
            if (!allowLiquidPartner && isLiquid(candidateType)) {
                continue;
            }
            if (isHazard(candidateType)) {
                continue;
            }
            if (!allowLiquidPartner && !isStableLandSupport(candidate.getRelative(BlockFace.DOWN).getType())) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    static Chest placeStorageChest(Block primaryChestBlock, Block secondChestBlock) {
        primaryChestBlock.setType(Material.CHEST, false);
        if (secondChestBlock != null) {
            secondChestBlock.setType(Material.CHEST, false);
        }

        if (!(primaryChestBlock.getState() instanceof Chest chest)) {
            return null;
        }

        return chest;
    }

    static void ensureSolidAroundChest(Block chestBlock, Block partnerChestBlock) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Block side = chestBlock.getRelative(face);
            if (side.equals(partnerChestBlock)) {
                continue;
            }
            if (!side.getType().isSolid() && canReplaceWithDirt(side.getType())) {
                side.setType(Material.DIRT, false);
            }
        }

        Block below = chestBlock.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid() && canReplaceWithDirt(below.getType())) {
            below.setType(Material.DIRT, false);
        }
    }

    static List<ItemStack> fillStorage(Chest chest, Block secondChestBlock, List<ItemStack> drops) {
        List<Inventory> inventories = new ArrayList<>();
        inventories.add(chest.getBlockInventory());
        if (secondChestBlock != null && secondChestBlock.getState() instanceof Chest secondChest) {
            inventories.add(secondChest.getBlockInventory());
        }

        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            List<ItemStack> remaining = new ArrayList<>();
            remaining.add(stack);
            for (Inventory inventory : inventories) {
                if (remaining.isEmpty()) {
                    break;
                }

                List<ItemStack> nextRemaining = new ArrayList<>();
                for (ItemStack item : remaining) {
                    Map<Integer, ItemStack> notFit = inventory.addItem(item);
                    if (!notFit.isEmpty()) {
                        nextRemaining.addAll(notFit.values());
                    }
                }
                remaining = nextRemaining;
            }

            if (!remaining.isEmpty()) {
                overflow.addAll(remaining);
            }
        }
        return overflow;
    }

    static boolean canReplaceForChest(Material mat) {
        if (mat == Material.AIR) return true;
        if (isLiquid(mat)) return true;
        return isReplaceableForGrave(mat);
    }

    static boolean canReplaceWithDirt(Material mat) {
        if (mat == Material.AIR) return true;
        if (isLiquid(mat)) return true;
        return isReplaceableForGrave(mat);
    }

    static boolean isReplaceableForGrave(Material mat) {
        if (mat == Material.AIR) return true;

        if (mat.isSolid()) return false;

        return mat != Material.CHEST
                && mat != Material.BARREL
                && mat != Material.HOPPER
                && mat != Material.FURNACE
                && mat != Material.BLAST_FURNACE
                && mat != Material.SMOKER
                && mat != Material.SPAWNER;
    }

    static boolean isLiquid(Material mat) {
        return mat == Material.WATER || mat == Material.LAVA;
    }

    static boolean isStableLandSupport(Material mat) {
        return mat.isSolid() && !Tag.LEAVES.isTagged(mat) && !Tag.LOGS.isTagged(mat)
            && !isLiquid(mat) && !isHazard(mat);
    }

    static boolean isHazard(Material mat) {
        return mat == Material.FIRE
                || mat == Material.SOUL_FIRE
                || mat == Material.CAMPFIRE
                || mat == Material.SOUL_CAMPFIRE
                || mat == Material.MAGMA_BLOCK
                || mat == Material.CACTUS
                || mat == Material.SWEET_BERRY_BUSH
                || mat == Material.POWDER_SNOW;
    }
}
