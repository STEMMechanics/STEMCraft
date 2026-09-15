package dev.stemcraft.feature.underhalls;

import dev.stemcraft.chunkgen.UnderhallsGenerator;
import dev.stemcraft.feature.HeldLightFeature;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.persistence.PersistentDataType;

/** Add dead-end doorways to existing chunks without overwriting player construction. */
public final class UnderhallsRooms {
    private static final NamespacedKey VERSION = new NamespacedKey("stemcraft", "underhalls-doorways-v2");
    private UnderhallsRooms() { }

    public static void upgrade(Chunk chunk, UnderhallsStore store) {
        World world = chunk.getWorld();
        if (!(world.getGenerator() instanceof UnderhallsGenerator generator) ||
            chunk.getPersistentDataContainer().has(VERSION, PersistentDataType.BYTE)) return;
        int floor = UnderhallsGenerator.floor(world);
        for (int dx = 0; dx < 16; dx += 8) for (int dz = 0; dz < 16; dz += 8) {
            int x = chunk.getX() * 16 + dx, z = chunk.getZ() * 16 + dz;
            if (!generator.roomAt(world, x, z) || !untouched(world, store, x, floor, z)) continue;
            for (int rx = 2; rx <= 6; rx++) for (int rz = 2; rz <= 6; rz++) {
                boolean wall = rx == 2 || rx == 6 || rz == 2 || rz == 6;
                if (!wall) continue;
                for (int y = 1; y <= 5; y++) {
                    Block block = world.getBlockAt(x + rx, floor + y, z + rz);
                    if (rx == 4 && rz == 2 && y <= 2) {
                        if (block.getType() == Material.DARK_OAK_DOOR) continue;
                        HeldLightFeature.releaseTemporaryLight(block);
                        Door door = (Door) Material.DARK_OAK_DOOR.createBlockData();
                        door.setFacing(BlockFace.NORTH);
                        door.setHalf(y == 1 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
                        block.setBlockData(door, false);
                    } else if (block.getType() != Material.END_STONE && block.getType() != Material.END_STONE_BRICKS) {
                        HeldLightFeature.releaseTemporaryLight(block);
                        block.setType(Material.END_STONE, false);
                    }
                }
            }
            world.getBlockAt(x + 4, floor + 6, z + 4).setType(Material.OCHRE_FROGLIGHT, false);
        }
        chunk.getPersistentDataContainer().set(VERSION, PersistentDataType.BYTE, (byte) 1);
    }

    private static boolean untouched(World world, UnderhallsStore store, int x, int floor, int z) {
        for (int rx = 2; rx <= 6; rx++) for (int rz = 2; rz <= 6; rz++) for (int y = 1; y <= 6; y++) {
            Block block = world.getBlockAt(x + rx, floor + y, z + rz);
            if (store.isPlaced(UnderhallsStore.Pos.of(block))) return false;
            Material type = block.getType();
            if (!(type.isAir() || type == Material.END_STONE || type == Material.END_STONE_BRICKS ||
                type == Material.SMOOTH_SANDSTONE || type == Material.OCHRE_FROGLIGHT || type == Material.DARK_OAK_DOOR ||
                type == Material.LIGHT && HeldLightFeature.isTemporaryLight(block))) return false;
        }
        return true;
    }
}
