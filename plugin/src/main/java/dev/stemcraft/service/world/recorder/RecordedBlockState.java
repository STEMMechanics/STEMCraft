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

package dev.stemcraft.service.world.recorder;

import dev.stemcraft.api.config.ConfigSection;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.util.Arrays;
import java.util.List;

/**
 * Class representing the recorded state of a block, including its type, data, and inventory contents.
 */
public class RecordedBlockState {
    @Getter
    final Material material;
    @Getter
    final String data;
    @Getter
    ItemStack[] inventory;

    /**
     * Constructs a RecordedBlockState from the given parameters.
     *
     * @param type The material type of the block.
     * @param data The block data as a string.
     * @param inventoryContents The inventory contents of the block, if applicable.
     */
    RecordedBlockState(Material type, String data, ItemStack[] inventoryContents) {
        this.material = type;
        this.data = data;
        this.inventory = inventoryContents;
    }

    /**
     * Constructs a RecordedBlockState from the given parameters.
     *
     * @param materialName The material name of the block.
     * @param data The block data as a string.
     * @param inventoryBytes The serialized inventory contents of the block, if applicable.
     */
    RecordedBlockState(String materialName, String data, byte[] inventoryBytes) {
        this.material = Material.matchMaterial(materialName);
        this.data = data;

        if (inventoryBytes != null) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(inventoryBytes);
                 DataInputStream in = new DataInputStream(bais)) {

                int size = in.readInt();
                this.inventory = new ItemStack[size];

                for (int i = 0; i < size; i++) {
                    int len = in.readInt();
                    if (len > 0) {
                        byte[] itemData = in.readNBytes(len);
                        this.inventory[i] = ItemStack.deserializeBytes(itemData);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to deserialize ItemStack[]", e);
            }
        }
    }

    /**
     * Constructs a RecordedBlockState from the given BlockState.
     *
     * @param state The BlockState to record.
     */
    RecordedBlockState(BlockState state) {
        material = state.getType();
        data = state.getBlockData().getAsString();

        if (state instanceof Container container) {

            Inventory inv = container.getInventory();

            // If it's a chest, handle double chest deterministically (record once)
            if (state instanceof Chest chest) {
                inv = chest.getInventory(); // may be DoubleChestInventory
                InventoryHolder holder = inv.getHolder();

                if (holder instanceof DoubleChest dc) {
                    // Only record when this block is the LEFT side
                    if (dc.getLeftSide() instanceof Chest left) {
                        if (!left.getBlock().getLocation().equals(state.getBlock().getLocation())) {
                            return; // we are not the left chest => skip
                        }
                    }
                }
            }

            ItemStack[] contents = inv.getContents();
            inventory = Arrays.stream(contents)
                    .map(item -> item == null ? null : item.clone())
                    .toArray(ItemStack[]::new);

            return;
        }

        if (state instanceof Campfire campfire) {
            int size = campfire.getSize();
            inventory = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                ItemStack item = campfire.getItem(i);
                inventory[i] = (item == null ? null : item.clone());
            }
        }
    }

    /**
     * Returns the material type of this block state.
     *
     * @return The material type.
     */
    public String getMaterialName() {
        return material.name();
    }

    /**
     * Returns the inventory contents of this block state, or null if none.
     *
     * @return The inventory contents as a byte array, or null.
     */
    public byte[] getInventoryAsBytes() {
        if (inventory == null) return null;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {

            out.writeInt(inventory.length);
            for (ItemStack item : inventory) {
                if (item == null) {
                    out.writeInt(0);
                } else {
                    byte[] data = item.serializeAsBytes();
                    out.writeInt(data.length);
                    out.write(data);
                }
            }

            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize ItemStack[]", e);
        }
    }

    /**
     * Loads a RecordedBlockState from the given configuration section.
     *
     * @param section The configuration section to load from.
     * @return The loaded RecordedBlockState.
     */
    public static RecordedBlockState load(ConfigSection section) {
        String typeName = section.getString("type");
        Material type = Material.matchMaterial(typeName);
        String data = section.getString("data", "minecraft:air");

        List<?> list = section.getList("inventory");
        ItemStack[] inventory = null;
        if (list != null) {
            inventory = list.stream()
                    .map(o -> (ItemStack) o)
                    .toArray(ItemStack[]::new);
        }
        return new RecordedBlockState(type, data, inventory);
    }

    /**
     * Restores the recorded block state at the given location.
     *
     * @param location The location to restore the block state to.
     * @param applyPhysics Whether to apply physics when setting the block.
     */
    public void restore(Location location, boolean applyPhysics) {
        Block block = location.getBlock();

        // Just restore what we recorded
        block.setType(this.material, applyPhysics);
        BlockData data = Bukkit.createBlockData(this.data);
        block.setBlockData(data, applyPhysics);

        if (inventory != null) {
            org.bukkit.block.BlockState state = block.getState();
            if (state instanceof org.bukkit.block.Container container) {
                int invSize = container.getInventory().getSize();
                ItemStack[] toApply = new ItemStack[invSize];
                int copyLen = Math.min(invSize, inventory.length);
                System.arraycopy(inventory, 0, toApply, 0, copyLen);

                container.getInventory().clear();
                container.getInventory().setContents(toApply);
            } else if (state instanceof Campfire campfire) {
                int size = campfire.getSize();
                for (int i = 0; i < size; i++) {
                    ItemStack item = (i < inventory.length ? inventory[i] : null);
                    campfire.setItem(i, item == null ? null : item.clone());
                }
                campfire.update(true, applyPhysics);
            }
        }
    }
}