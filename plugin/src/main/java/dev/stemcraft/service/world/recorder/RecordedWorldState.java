package dev.stemcraft.service.world.recorder;

import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.*;

public class RecordedWorldState {
    final Map<String, RecordedBlockState> blockStateMap = new HashMap<String, RecordedBlockState>();
    final Set<UUID> spawnedEntities = new HashSet<>();
    final World world;

    /**
     * Construct a new WorldState for the given world.
     */
    public RecordedWorldState(World world) {
        this.world = world;
    }

    /**
     * Record that an entity has been spawned in this world.
     */
    void recordEntity(Entity e) {
        spawnedEntities.add(e.getUniqueId());
    }

    /**
     * Record the given block state, including any related blocks (e.g. door halves).
     */
    public void recordBlockState(BlockState state) {
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
     * Load a RecordedWorldState from the given configuration section.
     */
    static RecordedWorldState load(World world, ConfigSection section) {
        RecordedWorldState worldState = new RecordedWorldState(world);

        ConfigSection blockListSection = section.getSection("blocks");
        if (blockListSection != null) {
            for (String key : blockListSection.getKeys(false)) {
                ConfigSection blockItemSection = blockListSection.getSection(key);
                if (blockItemSection == null) continue;
                RecordedBlockState rbs = RecordedBlockState.load(blockItemSection);
                worldState.blockStateMap.put(key, rbs);
            }
        }

        List<String> entities = section.getStringList("entities");
        if (entities != null && !entities.isEmpty()) {
            for (String idStr : entities) {
                try {
                    UUID id = UUID.fromString(idStr);
                    worldState.spawnedEntities.add(id);
                } catch (IllegalArgumentException ex) {
                    // skip invalid UUID
                }
            }
        }

        return worldState;
    }

    /**
     * Save this RecordedWorldState to the given configuration section.
     */
    void save(ConfigSection section) {
        ConfigSection blockListSection = section.createSection("blocks");
        for (Map.Entry<String, RecordedBlockState> entry : blockStateMap.entrySet()) {
            ConfigSection blockItemSection = blockListSection.createSection(entry.getKey());
            entry.getValue().save(blockItemSection);
        }

        if(!spawnedEntities.isEmpty()) {
            List<String> entities = new ArrayList<>();
            for (UUID id : spawnedEntities) {
                entities.add(id.toString());
            }

            section.set("entities", entities);
        }
    }


    public void rollback(World world) {
        // remove tracked entities
        for (UUID id : spawnedEntities) {
            Entity e = world.getEntity(id);
            if (e != null && !e.isDead()) {
                e.remove();
            }
        }
        spawnedEntities.clear();

        // rollback blocks
        Iterator<Map.Entry<String, RecordedBlockState>> it = blockStateMap.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, RecordedBlockState> entry = it.next();

            String[] locParts = entry.getKey().split(",");
            if (locParts.length == 3) {
                int x = Integer.parseInt(locParts[0]);
                int y = Integer.parseInt(locParts[1]);
                int z = Integer.parseInt(locParts[2]);

                int cx = x >> 4;
                int cz = z >> 4;

                // Load chunk if needed
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunkAt(cx, cz); // loads the chunk
                }

                Location loc = new Location(world, x, y, z);
                entry.getValue().restore(loc, false);
            }

            it.remove(); // clears as we go
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