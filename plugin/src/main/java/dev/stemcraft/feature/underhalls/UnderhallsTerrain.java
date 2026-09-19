package dev.stemcraft.feature.underhalls;

import dev.stemcraft.chunkgen.UnderhallsGenerator;
import org.bukkit.*;
import org.bukkit.persistence.PersistentDataType;

/** Incrementally replace the old unbreakable shell without touching player construction. */
public final class UnderhallsTerrain {
    private static final NamespacedKey PROGRESS = new NamespacedKey("stemcraft", "underhalls-void-y");
    private UnderhallsTerrain() { }
    public static boolean upgrade(Chunk chunk, UnderhallsStore store) {
        World world = chunk.getWorld();
        int floor = UnderhallsGenerator.floor(world);
        int start = chunk.getPersistentDataContainer().getOrDefault(PROGRESS, PersistentDataType.INTEGER, world.getMinHeight());
        if (start >= floor) return true;
        int end = Math.min(floor, start + 8);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            for (int y = start; y < end; y++) replace(chunk, store, x, y, z, Material.AIR);
            if (start == world.getMinHeight()) replace(chunk, store, x, floor + 7, z, Material.SMOOTH_SANDSTONE);
        }
        chunk.getPersistentDataContainer().set(PROGRESS, PersistentDataType.INTEGER, end);
        return end >= floor;
    }
    private static void replace(Chunk chunk, UnderhallsStore store, int x, int y, int z, Material material) {
        var block = chunk.getBlock(x, y, z);
        if (block.getType() == Material.BEDROCK && !store.isPlaced(UnderhallsStore.Pos.of(block))) block.setType(material, false);
    }
}
