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

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.world.WorldChangeSession;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Implementation of the WorldChangeSession interface.
 */
public class WorldChangeSessionImpl implements WorldChangeSession {
    private final STEMCraftAPI api;
    private final World world;
    final Map<String, RecordedBlockState> blockStateMap = new HashMap<>();
    final Set<UUID> spawnedEntities = new HashSet<>();

    @Getter
    private boolean recording = false;

    /**
     * Constructs a WorldChangeSessionImpl for the given world.
     *
     * @param api The STEMCraft API instance.
     * @param world The world to track changes in.
     */
    public WorldChangeSessionImpl(@NotNull STEMCraftAPI api, @NotNull World world) {
        this.api = api;
        this.world = world;
    }

    /**
     * Starts recording changes in the world.
     */
    public void start() {
        recording = true;
    }

    /**
     * Stops recording changes in the world.
     */
    public void stop() {
        recording = false;
    }

    /**
     * Clears all recorded changes.
     */
    public void clear() {
        blockStateMap.clear();
        spawnedEntities.clear();
    }

    /**
     * Rolls back all recorded changes.
     *
     * @param applyPhysics Whether to apply physics when restoring blocks.
     */
    public void rollback(boolean applyPhysics) {
        // Restore block states
        for (Map.Entry<String, RecordedBlockState> entry : blockStateMap.entrySet()) {
            String[] coords = entry.getKey().split(",");
            RecordedBlockState rbs = entry.getValue();

            Block block = world.getBlockAt(
                Integer.parseInt(coords[0]),
                Integer.parseInt(coords[1]),
                Integer.parseInt(coords[2])
            );

            // Just restore what we recorded
            Material material = rbs.getMaterial();
            if(material == null) continue;
            block.setType(material, applyPhysics);
            BlockData data = Bukkit.createBlockData(rbs.getData());
            block.setBlockData(data, applyPhysics);

            if (rbs.getInventory() != null) {
                BlockState state = block.getState();
                if (state instanceof Container container) {
                    int invSize = container.getInventory().getSize();
                    ItemStack[] toApply = new ItemStack[invSize];
                    int copyLen = Math.min(invSize, rbs.getInventory().length);
                    System.arraycopy(rbs.getInventory(), 0, toApply, 0, copyLen);

                    container.getInventory().clear();
                    container.getInventory().setContents(toApply);
                } else if (state instanceof Campfire campfire) {
                    int size = campfire.getSize();
                    for (int i = 0; i < size; i++) {
                        ItemStack item = (i < rbs.getInventory().length ? rbs.getInventory()[i] : null);
                        campfire.setItem(i, item == null ? null : item.clone());
                    }
                    campfire.update(true, applyPhysics);
                }
            }
        }

        // Remove spawned entities
        for (UUID entityId : spawnedEntities) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }

        clear();
    }

    /**
     * Captures the current state of a block.
     *
     * @param state The block state to capture.
     * @param overwriteExisting Whether to overwrite existing recorded state for this block.
     */
    public void captureBlockState(@NotNull BlockState state, boolean overwriteExisting) {
        if(!recording) return;

        Material type = state.getType();
        Block block = state.getBlock();
        BlockData data = state.getBlockData();

        // Always snapshot the requested block itself before any special multi-block handling.
        addBlockState(state, overwriteExisting);

        // Doors (two vertical blocks)
        if (isDoor(type) && data instanceof org.bukkit.block.data.type.Door door) {
            Block other = (door.getHalf() == org.bukkit.block.data.type.Door.Half.TOP)
                    ? block.getRelative(org.bukkit.block.BlockFace.DOWN)
                    : block.getRelative(org.bukkit.block.BlockFace.UP);
            addBlockState(other.getState(), overwriteExisting); // snapshot partner's *old* state
        }

        // Beds (two horizontal blocks)
        if (isBed(type) && data instanceof org.bukkit.block.data.type.Bed bed) {
            Block other = (bed.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD)
                    ? block.getRelative(bed.getFacing().getOppositeFace())
                    : block.getRelative(bed.getFacing());
            addBlockState(other.getState(), overwriteExisting);
        }

        // Chests (double chest – record any neighbouring chest halves too)
        if (isChest(type)) {
            for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                    org.bukkit.block.BlockFace.NORTH,
                    org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST,
                    org.bukkit.block.BlockFace.WEST
            }) {
                Block other = block.getRelative(face);
                if (!isChest(other.getType())) continue;

                addBlockState(other.getState(), overwriteExisting);
            }
        }
    }

    /**
     * Captures the current state of an entity.
     *
     * @param entity The entity to capture.
     */
    public void captureEntity(@NotNull Entity entity) {
        if(!recording) return;

        spawnedEntities.add(entity.getUniqueId());
    }

    /**
     * Loads the session state from a configuration section.
     */
    public void load() {
        blockStateMap.clear();
        spawnedEntities.clear();

        api.database().queryEach(
            "SELECT material, x, y, z, data, inventory FROM world_changes_blocks WHERE world = ?",
            ps -> ps.setString(1, world.getName()),
            rs -> {
                String locString = rs.getInt("x") + "," + rs.getInt("y") + "," + rs.getInt("z");
                RecordedBlockState rbs = new RecordedBlockState(
                    rs.getString("material"),
                    rs.getString("data"),
                    rs.getBytes("inventory")
                );

                blockStateMap.put(locString, rbs);
            });

        api.database().update("DELETE FROM world_changes_blocks WHERE world = ?",
            ps -> ps.setString(1, world.getName()));

        api.database().queryEach(
            "SELECT uuid FROM world_changes_entities WHERE world = ?",
            ps -> ps.setString(1, world.getName()),
            rs -> {
                UUID entityId = UUID.fromString(rs.getString("uuid"));
                spawnedEntities.add(entityId);
            });

        api.database().update("DELETE FROM world_changes_entities WHERE world = ?",
            ps -> ps.setString(1, world.getName()));
    }

    /**
     * Saves the session state to a configuration section.
     */
    public void save() {
        // Save block states
        api.database().update("DELETE FROM world_changes_blocks WHERE world = ?",
            ps -> ps.setString(1, world.getName()));

        for (Map.Entry<String, RecordedBlockState> entry : blockStateMap.entrySet()) {
            String[] coords = entry.getKey().split(",");
            RecordedBlockState rbs = entry.getValue();

            api.database().update(
                "INSERT INTO world_changes_blocks (world, material, x, y, z, data, inventory) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, world.getName());
                    ps.setString(2, rbs.getMaterialName());
                    ps.setInt(3, Integer.parseInt(coords[0]));
                    ps.setInt(4, Integer.parseInt(coords[1]));
                    ps.setInt(5, Integer.parseInt(coords[2]));
                    ps.setString(6, rbs.getData());
                    ps.setBytes(7, rbs.getInventoryAsBytes());
                }
            );
        }

        // Save spawned entities
        api.database().update("DELETE FROM world_changes_entities WHERE world = ?",
            ps -> ps.setString(1, world.getName()));
        for (UUID entityId : spawnedEntities) {
            api.database().update(
                "INSERT INTO world_changes_entities (world, uuid) VALUES (?, ?)",
                ps -> {
                    ps.setString(1, world.getName());
                    ps.setString(2, entityId.toString());
                });
        }
    }

    /**
     * Adds the given block state to the recorded states.
     *
     * @param state The block state to add.
     * @param overwriteExisting Whether to overwrite existing recorded state for this block.
     */
    private void addBlockState(BlockState state, boolean overwriteExisting) {
        String locString = state.getX() + "," + state.getY() + "," + state.getZ();
        if (overwriteExisting || !blockStateMap.containsKey(locString)) {
            blockStateMap.put(locString, new RecordedBlockState(state));
        }
    }

    /**
     * Returns true if the given material is a door.
     *
     * @param type The material to check.
     * @return True if the material is a door.
     */
    private boolean isDoor(Material type) {
        return type != null && type.name().endsWith("_DOOR");
    }

    /**
     * Returns true if the given material is a bed.
     *
     * @param type The material to check.
     * @return True if the material is a bed.
     */
    private boolean isBed(Material type) {
        return type != null && type.name().endsWith("_BED");
    }

    /**
     * Returns true if the given material is a chest.
     *
     * @param type The material to check.
     * @return True if the material is a chest.
     */
    private boolean isChest(Material type) {
        return type != null && type.name().endsWith("CHEST");
    }

    /**
     * Returns true if the given entity type is temporary and should be tracked.
     *
     * @param type The entity type to check.
     * @return True if the entity type is temporary.
     */
    private boolean isTemporaryEntity(EntityType type) {
        String name = type.name();

        // Minecarts
        if (name.contains("MINECART")) return true;

        // Boats and rafts
        if (name.contains("BOAT") || name.contains("RAFT")) return true;

        // Projectiles, drops, misc
        return switch (type) {
            case ARROW, SPECTRAL_ARROW, TRIDENT,
                 FALLING_BLOCK, TNT,
                 EXPERIENCE_ORB -> true;
            default -> false;
        };
    }
}
