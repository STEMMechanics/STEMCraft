package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded, loaded-chunk-only waypoint search; Citizens still verifies and walks each route. */
final class BotNavigationRecovery {
    private BotNavigationRecovery() { }

    static List<Location> candidates(Location from, Location target) {
        World world = from.getWorld();
        if (world == null || !world.equals(target.getWorld())) return List.of();
        List<Location> candidates = new ArrayList<>();
        // Sample rings so an escape route can initially lead away from the destination.
        for (int radius : new int[]{2, 4, 6, 8, 12, 16}) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = from.getBlockX() + dx;
                    int z = from.getBlockZ() + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    for (int y = Math.max(world.getMinHeight() + 1, from.getBlockY() - 3);
                         y <= Math.min(world.getMaxHeight() - 2, from.getBlockY() + 6); y++) {
                        var floor = world.getBlockAt(x, y - 1, z);
                        if (!floor.getType().isSolid() || hazardous(floor.getType())) continue;
                        var feet = world.getBlockAt(x, y, z);
                        var head = world.getBlockAt(x, y + 1, z);
                        if (!feet.isPassable() || !head.isPassable() || hazardous(feet.getType())
                            || hazardous(head.getType())) continue;
                        candidates.add(new Location(world, x + .5, y, z + .5));
                    }
                }
            }
        }
        // Prefer elevation matching the destination, then short detours. This favours stair landings.
        candidates.sort(Comparator.comparingDouble(point -> score(from, target, point)));
        return candidates.stream().limit(64).toList();
    }

    private static double score(Location from, Location target, Location point) {
        return point.distance(target) + .35 * point.distance(from) + 2 * Math.abs(point.getY() - target.getY());
    }

    private static boolean hazardous(Material material) {
        return switch (material) {
            case WATER, LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK,
                 CACTUS, SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE -> true;
            default -> false;
        };
    }
}
