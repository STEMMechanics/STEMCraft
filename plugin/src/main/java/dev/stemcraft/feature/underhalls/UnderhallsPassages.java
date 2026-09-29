package dev.stemcraft.feature.underhalls;

import dev.stemcraft.chunkgen.UnderhallsGenerator;
import dev.stemcraft.feature.HeldLightFeature;
import org.bukkit.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Seeded details confined to one chunk, leaving door cells and player builds alone. */
public final class UnderhallsPassages {
    private static final NamespacedKey VERSION = new NamespacedKey("stemcraft", "underhalls-passages-v1");
    public record Change(int x, int y, int z, Material material) { }
    private UnderhallsPassages() { }

    public static List<List<Change>> plan(World world, int cx, int cz, double chance) {
        if (!(world.getGenerator() instanceof UnderhallsGenerator generator)) return List.of();
        Random random = new Random(world.getSeed() ^ cx * 341873128712L ^ cz * 132897987541L ^ 0x76656e74L);
        List<List<Change>> result = new ArrayList<>();
        for (boolean rotate : new boolean[]{false, true}) for (int offset : new int[]{0, 8}) {
            int x = rotate ? offset + 4 : 8, z = rotate ? 8 : offset + 4;
            int wx = cx * 16 + x, wz = cz * 16 + z;
            if (random.nextDouble() >= chance || generator.roomAt(world, wx, wz) ||
                generator.roomAt(world, wx - (rotate ? 0 : 1), wz - (rotate ? 1 : 0)) ||
                UnderhallsGenerator.reserved(wx - 3, wz - 3)) continue;
            var maze = new UnderhallsGenerator.Maze(world.getSeed(), Math.floorDiv(wx, 128), Math.floorDiv(wz, 128));
            boolean open = maze.passage(Math.floorMod(wx, 128), Math.floorMod(wz, 128));
            boolean raised = random.nextBoolean();
            List<Change> changes = new ArrayList<>();
            if (open) {
                for (int a = -2; a <= 2; a++) for (int b : new int[]{-1, 1}) for (int y = 1; y <= 5; y++)
                    add(changes, x, z, rotate, a, b, y, Material.END_STONE);
            } else {
                for (int y = raised ? 4 : 1; y <= (raised ? 5 : 2); y++)
                    add(changes, x, z, rotate, 0, 0, y, Material.AIR);
                if (raised) for (int side : new int[]{-1, 1}) for (int distance = 1; distance <= 3; distance++)
                    for (int y = 1; y <= 4 - distance; y++)
                        add(changes, x, z, rotate, side * distance, 0, y, Material.SMOOTH_SANDSTONE);
            }
            result.add(List.copyOf(changes));
        }
        return List.copyOf(result);
    }
    private static void add(List<Change> changes, int x, int z, boolean rotate, int a, int b, int y, Material material) {
        changes.add(new Change(x + (rotate ? b : a), y, z + (rotate ? a : b), material));
    }
    public static void upgrade(Chunk chunk, UnderhallsStore store, double chance) {
        if (chance <= 0 || chunk.getPersistentDataContainer().has(VERSION, PersistentDataType.BYTE)) return;
        World world = chunk.getWorld();
        int floor = UnderhallsGenerator.floor(world);
        for (List<Change> changes : plan(world, chunk.getX(), chunk.getZ(), chance)) {
            boolean clear = changes.stream().allMatch(change -> {
                var block = chunk.getBlock(change.x(), floor + change.y(), change.z());
                Material type = block.getType();
                return !store.isPlaced(UnderhallsStore.Pos.of(block)) &&
                    (type.isAir() || type == Material.END_STONE || type == Material.END_STONE_BRICKS ||
                        type == Material.SMOOTH_SANDSTONE || HeldLightFeature.isTemporaryLight(block));
            });
            // An upgrade must not build steps or walls into someone exploring an old chunk.
            if (!clear || Arrays.stream(chunk.getEntities()).anyMatch(entity -> changes.stream().anyMatch(change ->
                entity.getBoundingBox().overlaps(new org.bukkit.util.BoundingBox(chunk.getX() * 16 + change.x(),
                    floor + change.y(), chunk.getZ() * 16 + change.z(), chunk.getX() * 16 + change.x() + 1,
                    floor + change.y() + 1, chunk.getZ() * 16 + change.z() + 1))))) continue;
            for (Change change : changes) {
                var block = chunk.getBlock(change.x(), floor + change.y(), change.z());
                HeldLightFeature.releaseTemporaryLight(block);
                block.setType(change.material(), false);
            }
        }
        chunk.getPersistentDataContainer().set(VERSION, PersistentDataType.BYTE, (byte) 1);
    }
}
