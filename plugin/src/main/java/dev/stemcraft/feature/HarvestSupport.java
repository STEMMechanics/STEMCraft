package dev.stemcraft.feature;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded traversal of loaded blocks; harvesting itself uses Player.breakBlock. */
final class HarvestSupport {
    private HarvestSupport() { }

    static boolean isLog(Material material) {
        return material.name().endsWith("_LOG") && !material.name().startsWith("STRIPPED_");
    }

    static boolean isOre(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    static boolean isCorrectTool(Material material, boolean tree) {
        return material.name().endsWith(tree ? "_AXE" : "_PICKAXE");
    }

    static List<Block> connected(Block origin, boolean tree, int limit) {
        List<Block> found = new ArrayList<>();
        ArrayDeque<Block> pending = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        pending.add(origin);
        visited.add(origin);
        while (!pending.isEmpty() && found.size() < limit) {
            Block block = pending.removeFirst();
            found.add(block);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                int targetY = block.getY() + y;
                if (targetY < origin.getWorld().getMinHeight() || targetY >= origin.getWorld().getMaxHeight()
                    || (tree && targetY < origin.getY())) continue;
                int targetX = block.getX() + x, targetZ = block.getZ() + z;
                if (!origin.getWorld().isChunkLoaded(targetX >> 4, targetZ >> 4)) continue;
                Block next = origin.getWorld().getBlockAt(targetX, targetY, targetZ);
                if (visited.add(next) && next.getType() == origin.getType()) pending.addLast(next);
            }
        }
        return found;
    }

    static boolean hasNaturalCanopy(List<Block> logs) {
        for (Block log : logs) {
            for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                int targetX = log.getX() + x, targetY = log.getY() + y, targetZ = log.getZ() + z;
                if (targetY >= log.getWorld().getMaxHeight()
                    || !log.getWorld().isChunkLoaded(targetX >> 4, targetZ >> 4)) continue;
                if (log.getWorld().getBlockAt(targetX, targetY, targetZ).getBlockData() instanceof Leaves leaves
                    && !leaves.isPersistent()) return true;
            }
        }
        return false;
    }
}
