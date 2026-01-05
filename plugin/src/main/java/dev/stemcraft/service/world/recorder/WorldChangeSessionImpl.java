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

import java.util.*;

public class WorldChangeSessionImpl implements WorldChangeSession {
    private final STEMCraftAPI api;
    private final World world;
    final Map<String, RecordedBlockState> blockStateMap = new HashMap<String, RecordedBlockState>();
    final Set<UUID> spawnedEntities = new HashSet<>();

    @Getter
    private boolean recording = false;

    /**
     * Constructs a WorldChangeSessionImpl for the given world.
     */
    public WorldChangeSessionImpl(STEMCraftAPI api, World world) {
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
            block.setType(rbs.getMaterial(), applyPhysics);
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
     */
    public void captureBlockState(BlockState state) {
        if(!recording) return;

        Material type = state.getType();
        Block block = state.getBlock();
        BlockData data = state.getBlockData();

        // Doors (two vertical blocks)
        if (isDoor(type) && data instanceof org.bukkit.block.data.type.Door door) {
            Block other = (door.getHalf() == org.bukkit.block.data.type.Door.Half.TOP)
                    ? block.getRelative(org.bukkit.block.BlockFace.DOWN)
                    : block.getRelative(org.bukkit.block.BlockFace.UP);
            addBlockState(other.getState()); // snapshot partner's *old* state
        }

        // Beds (two horizontal blocks)
        if (isBed(type) && data instanceof org.bukkit.block.data.type.Bed bed) {
            Block other = (bed.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD)
                    ? block.getRelative(bed.getFacing().getOppositeFace())
                    : block.getRelative(bed.getFacing());
            addBlockState(other.getState());
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

                addBlockState(other.getState());
            }
        }
    }

    /**
     * Captures the current state of an entity.
     */
    public void captureEntity(Entity entity) {
        if(!recording) return;

        spawnedEntities.add(entity.getUniqueId());
    }

    /**
     * Loads the session state from a configuration section.
     */
    public void load() {
        blockStateMap.clear();
        spawnedEntities.clear();

        api.database().query(
            "SELECT material, x, y, z, data, inventory FROM world_change_blocks WHERE world_name = ?",
            ps -> ps.setString(1, world.getName()),
            rs -> {
                String locString = rs.getInt("x") + "," + rs.getInt("y") + "," + rs.getInt("z");
                RecordedBlockState rbs = new RecordedBlockState(
                    rs.getString("material"),
                    rs.getString("data"),
                    rs.getBytes("inventory")
                );

                blockStateMap.put(locString, rbs);

                api.database().update("DELETE FROM world_change_blocks WHERE world = ?",
                    ps -> {
                        ps.setString(1, world.getName());
                    });
            });

        api.database().query(
                "SELECT uuid FROM world_change_entities WHERE world = ?",
                ps -> ps.setString(1, world.getName()),
                rs -> {
                    UUID entityId = UUID.fromString(rs.getString("uuid"));
                    spawnedEntities.add(entityId);

                    api.database().update("DELETE FROM world_change_entities WHERE world = ?",
                            ps -> {
                                ps.setString(1, world.getName());
                            });
                });
    }

    /**
     * Saves the session state to a configuration section.
     */
    public void save() {
        // Save block states
        api.database().update("DELETE FROM world_change_blocks WHERE world = ?",
            ps -> ps.setString(1, world.getName()));

        for (Map.Entry<String, RecordedBlockState> entry : blockStateMap.entrySet()) {
            String[] coords = entry.getKey().split(",");
            RecordedBlockState rbs = entry.getValue();

            api.database().update(
                "INSERT INTO world_change_blocks (world, material, x, y, z, data, inventory) VALUES (?, ?, ?, ?, ?, ?, ?)",
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
        api.database().update("DELETE FROM world_change_entities WHERE world = ?",
            ps -> ps.setString(1, world.getName()));
        for (UUID entityId : spawnedEntities) {
            api.database().update(
                "INSERT INTO world_change_entities (world, uuid) VALUES (?, ?)",
                ps -> {
                    ps.setString(1, world.getName());
                    ps.setString(2, entityId.toString());
                });
        }
    }

    /**
     * Adds the given block state to the recorded states.
     */
    private void addBlockState(BlockState state) {
        String locString = state.getX() + "," + state.getY() + "," + state.getZ();
        if (!blockStateMap.containsKey(locString)) {
            blockStateMap.put(locString, new RecordedBlockState(state));
        }
    }

    /**
     * Returns true if the given material is a door.
     */
    private boolean isDoor(Material type) {
        return type != null && type.name().endsWith("_DOOR");
    }

    /**
     * Returns true if the given material is a bed.
     */
    private boolean isBed(Material type) {
        return type != null && type.name().endsWith("_BED");
    }

    /**
     * Returns true if the given material is a chest.
     */
    private boolean isChest(Material type) {
        return type != null && type.name().endsWith("CHEST");
    }

    /**
     * Returns true if the given entity type is temporary and should be tracked.
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
