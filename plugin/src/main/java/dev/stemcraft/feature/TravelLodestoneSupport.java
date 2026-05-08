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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Support methods for travel lodestone structure detection and teleport placement.
 */
final class TravelLodestoneSupport {
    static final Set<Material> VALID_SUPPORT_BLOCKS = EnumSet.of(
            Material.EMERALD_BLOCK,
            Material.GOLD_BLOCK,
            Material.DIAMOND_BLOCK
    );

    private TravelLodestoneSupport() { }

    static @Nullable Structure detectStructure(@NotNull Block changedBlock) {
        for (int lodestoneOffset = 0; lodestoneOffset <= 2; lodestoneOffset++) {
            Block lodestone = changedBlock.getRelative(BlockFace.UP, lodestoneOffset);
            if (lodestone.getType() != Material.LODESTONE) {
                continue;
            }

            Block upperSupport = lodestone.getRelative(BlockFace.DOWN);
            Block lowerSupport = lodestone.getRelative(BlockFace.DOWN, 2);
            Material supportType = upperSupport.getType();
            if (!VALID_SUPPORT_BLOCKS.contains(supportType)) {
                continue;
            }

            if (lowerSupport.getType() != supportType) {
                continue;
            }

            return new Structure(lodestone, upperSupport, lowerSupport, supportType);
        }

        return null;
    }

    static boolean isStructureActive(@NotNull World world, @NotNull TravelLodestoneRecord record) {
        Block lodestone = world.getBlockAt(record.x(), record.y(), record.z());
        if (lodestone.getType() != Material.LODESTONE) {
            return false;
        }

        Block upperSupport = lodestone.getRelative(BlockFace.DOWN);
        Block lowerSupport = lodestone.getRelative(BlockFace.DOWN, 2);
        return upperSupport.getType() == record.supportMaterial()
                && lowerSupport.getType() == record.supportMaterial();
    }

    static @Nullable TravelLodestoneRecord findClosestSameWorldDestination(
            @NotNull TravelLodestoneRecord source,
            @NotNull Collection<TravelLodestoneRecord> candidates
    ) {
        TravelLodestoneRecord closest = null;
        double closestDistanceSquared = Double.MAX_VALUE;

        for (TravelLodestoneRecord candidate : candidates) {
            if (!candidate.worldId().equals(source.worldId())) {
                continue;
            }
            if (candidate.supportMaterial() != source.supportMaterial()) {
                continue;
            }
            if (candidate.sameBlock(source)) {
                continue;
            }

            double distanceSquared = candidate.distanceSquared(source);
            if (distanceSquared < closestDistanceSquared) {
                closest = candidate;
                closestDistanceSquared = distanceSquared;
            }
        }

        return closest;
    }

    static @Nullable Location findSafeTeleportLocation(
            @NotNull World world,
            @NotNull TravelLodestoneRecord destination,
            int radius
    ) {
        Location best = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dy == 0) {
                    continue;
                }

                for (int dz = -radius; dz <= radius; dz++) {

                    int x = destination.x() + dx;
                    int y = destination.y() + dy;
                    int z = destination.z() + dz;

                    if (y < world.getMinHeight() || y + 1 > world.getMaxHeight()) {
                        continue;
                    }

                    Block feet = world.getBlockAt(x, y, z);
                    Block head = feet.getRelative(BlockFace.UP);
                    if (!canFit(feet) || !canFit(head)) {
                        continue;
                    }

                    double distanceSquared = squared(dx, dy, dz);
                    if (distanceSquared >= bestDistanceSquared) {
                        continue;
                    }

                    bestDistanceSquared = distanceSquared;
                    best = new Location(world, x + 0.5D, y, z + 0.5D);
                }
            }
        }

        return best;
    }

    static @Nullable TravelLodestoneRecord recordForStructureBlock(
            @NotNull Block block,
            @NotNull java.util.function.Function<Block, @Nullable TravelLodestoneRecord> lookup
    ) {
        for (int offset = 0; offset <= 2; offset++) {
            TravelLodestoneRecord record = lookup.apply(block.getRelative(BlockFace.UP, offset));
            if (record != null) {
                return record;
            }
        }
        return null;
    }

    private static boolean canFit(@NotNull Block block) {
        Material type = block.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    private static double squared(int dx, int dy, int dz) {
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    record Structure(
            @NotNull Block lodestoneBlock,
            @NotNull Block upperSupportBlock,
            @NotNull Block lowerSupportBlock,
            @NotNull Material supportMaterial
    ) {
        TravelLodestoneRecord toRecord() {
            World world = lodestoneBlock.getWorld();
            return new TravelLodestoneRecord(
                    world.getUID(),
                    world.getName(),
                    lodestoneBlock.getX(),
                    lodestoneBlock.getY(),
                    lodestoneBlock.getZ(),
                    supportMaterial
            );
        }

        Block[] blocks() {
            return new Block[] { lodestoneBlock, upperSupportBlock, lowerSupportBlock };
        }
    }

    record TravelLodestoneRecord(
            @NotNull UUID worldId,
            @NotNull String worldName,
            int x,
            int y,
            int z,
            @NotNull Material supportMaterial
    ) {
        double distanceSquared(@NotNull TravelLodestoneRecord other) {
            return squared(x - other.x, y - other.y, z - other.z);
        }

        boolean sameBlock(@NotNull TravelLodestoneRecord other) {
            return worldId.equals(other.worldId)
                    && x == other.x
                    && y == other.y
                    && z == other.z;
        }

        String key() {
            return TravelLodestoneSupport.key(worldId, x, y, z);
        }

        static @Nullable World world(@NotNull TravelLodestoneRecord record) {
            return Bukkit.getWorld(record.worldId);
        }
    }

    static String key(@NotNull UUID worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}
