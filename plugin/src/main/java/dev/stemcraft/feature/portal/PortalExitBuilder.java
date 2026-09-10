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

package dev.stemcraft.feature.portal;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import java.util.*;
import java.util.function.Predicate;
import static dev.stemcraft.feature.portal.PortalPattern.Offset;

/** Bounded gameplay-side placement. Only preloaded chunks are inspected; terrain generation is independent. */
final class PortalExitBuilder {
    record Placement(Offset at, BlockData data) {}
    record Plan(Offset origin, List<Placement> blocks) {
        Plan { blocks = List.copyOf(blocks); }
        void build(World world) {
            List<BlockState> before = new ArrayList<>(blocks.size());
            try {
                for (Placement placement : blocks) {
                    Offset p = placement.at();
                    var block = world.getBlockAt(p.x(), p.y(), p.z());
                    before.add(block.getState());
                    block.setBlockData(placement.data(), false);
                }
            } catch (RuntimeException failure) {
                for (int i = before.size() - 1; i >= 0; i--) before.get(i).update(true, false);
                throw failure;
            }
        }
    }
    record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, int floorY) {}
    static int scaled(int coordinate, double scale) { return (int) Math.floor(coordinate / scale); }
    static Bounds bounds(PortalPattern pattern, int rotation) {
        int minX = 16, maxX = -16, minY = 16, maxY = -16, minZ = 16, maxZ = -16, floor = 16;
        List<Offset> points = new ArrayList<>(pattern.interior());
        for (var cell : pattern.cells()) points.add(cell.offset());
        for (Offset offset : points) {
            Offset p = offset.rotate(rotation);
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
        }
        for (Offset p : pattern.interior()) floor = Math.min(floor, p.y() - 1);
        return new Bounds(minX - 2, maxX + 2, Math.min(minY, floor), maxY + 2, minZ - 2, maxZ + 2, floor);
    }
    static Plan find(World world, SurvivalPortalType type, int x, int z, int rotation, int preferredY,
                     Predicate<Location> blocked) {
        if (type.exitPlacement() == SurvivalPortalType.ExitPlacement.AIR_DROP)
            return AirDropExit.find(world,type,x,z,rotation,preferredY,blocked);
        Bounds b = bounds(type.pattern(), rotation);
        int low = world.getMinHeight() + 1 - b.minY(), high = world.getMaxHeight() - 1 - b.maxY();
        if (low > high) return null;
        int preferred = Math.clamp(preferredY, low, high);
        // Prefer a dry floor at any height, including cavern floors below a sealed roof.
        for (int radius = 0; radius <= type.searchRadius(); radius += 8)
            for (int dx = -radius; dx <= radius; dx += 8) for (int dz = -radius; dz <= radius; dz += 8) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                for (int step = 0; step <= 2 * (high - low); step++) {
                    int y = preferred + (step == 0 ? 0 : (step + 1) / 2 * (step % 2 == 1 ? 1 : -1));
                    if (y < low || y > high) continue;
                    int gx = x + dx, gz = z + dz, gy = y + b.minY() - 1;
                    if (!world.isChunkLoaded(Math.floorDiv(gx,16), Math.floorDiv(gz,16))) continue;
                    if (!ExplorationFloor.safe(world.getBlockAt(gx, gy, gz).getType())) continue;
                    if (!replaceable(world.getBlockAt(gx,y+b.minY(),gz).getType())) continue;
                    // Embed the configured base into the surface, or stand it directly on the ground.
                    for (int base = Math.max(low, y - 1); base <= Math.min(y, high); base++) {
                        Plan plan = at(world, type, new Offset(gx,base,gz), rotation, b, blocked);
                        if (plan != null) return plan;
                    }
                }
            }
        return null;
    }
    static Plan at(World world, SurvivalPortalType type, Offset origin, int rotation, Bounds b, Predicate<Location> blocked) {
        if (type.exitPlacement() == SurvivalPortalType.ExitPlacement.AIR_DROP)
            return AirDropExit.at(world,type,origin,rotation,blocked);
        Map<Offset, BlockData> changes = new LinkedHashMap<>();
        boolean anchored = false;
        for (var cell : type.pattern().cells()) {
            Offset point = origin.add(cell.offset().rotate(rotation));
            if (!accessible(world,point,blocked)) return null;
            Material existing = world.getBlockAt(point.x(),point.y(),point.z()).getType();
            boolean base = cell.offset().y() == b.minY();
            if (!replaceable(existing) && !(base && naturalGround(existing))) return null;
            if (base && point.y() > world.getMinHeight()
                    && ExplorationFloor.safe(world.getBlockAt(point.x(),point.y()-1,point.z()).getType())) anchored = true;
            BlockData data = cell.material().createBlockData();
            if (cell.material() == Material.LIGHTNING_ROD && data instanceof Directional rod) rod.setFacing(BlockFace.UP);
            changes.put(point, data);
        }
        if (!anchored) return null;
        for (Offset cell : type.pattern().interior()) {
            Offset point = origin.add(cell.rotate(rotation));
            if (!accessible(world,point,blocked) || !replaceable(world.getBlockAt(point.x(),point.y(),point.z()).getType())) return null;
            BlockData data = type.pattern().interiorMaterial().createBlockData();
            if (data instanceof Orientable portal) portal.setAxis(rotation % 2 == 0 ? Axis.X : Axis.Z);
            changes.put(point, data);
        }
        // Horizontal openings need their existing terrain floor; vertical ones need only their lowest row supported.
        for (Offset cell : type.pattern().interior()) {
            if (cell.y() != b.floorY() + 1) continue;
            Offset below = origin.add(cell.rotate(rotation)).add(new Offset(0,-1,0));
            if (!ExplorationFloor.safe(plannedType(world,changes,below))) return null;
        }
        // Prove a natural landing exists before writing any blocks. No surrounding slab or support pillars.
        boolean landing = false;
        Offset entry = type.pattern().interior().getFirst().rotate(rotation);
        for (int x = entry.x()-4; x <= entry.x()+4 && !landing; x++) for (int z = entry.z()-4; z <= entry.z()+4 && !landing; z++) {
            for (int y = entry.y()-2; y <= entry.y()+2; y++) {
                Offset feet = origin.add(new Offset(x,y,z)), head = feet.add(new Offset(0,1,0));
                if (!accessible(world,feet,blocked) || !accessible(world,head,blocked)) continue;
                if (openingContains(type.pattern(),origin,rotation,feet) || openingContains(type.pattern(),origin,rotation,head)) continue;
                if (plannedType(world,changes,feet).isAir() && plannedType(world,changes,head).isAir()
                        && ExplorationFloor.safe(plannedType(world,changes,feet.add(new Offset(0,-1,0))))) {
                    landing = true; break;
                }
            }
        }
        if (!landing) return null;
        List<Placement> blocks = new ArrayList<>();
        changes.forEach((p,d) -> blocks.add(new Placement(p,d)));
        return new Plan(origin, blocks);
    }
    static boolean accessible(World world, Offset point, Predicate<Location> blocked) {
        if (point.y() <= world.getMinHeight() || point.y() >= world.getMaxHeight()) return false;
        if (!world.isChunkLoaded(Math.floorDiv(point.x(),16),Math.floorDiv(point.z(),16))) return false;
        Location at = new Location(world,point.x()+.5,point.y(),point.z()+.5);
        return world.getWorldBorder().isInside(at) && !blocked.test(at);
    }
    private static Material plannedType(World world, Map<Offset,BlockData> changes, Offset point) {
        BlockData planned = changes.get(point);
        return planned == null ? world.getBlockAt(point.x(),point.y(),point.z()).getType() : planned.getMaterial();
    }
    private static boolean openingContains(PortalPattern pattern, Offset origin, int rotation, Offset point) {
        for (Offset cell : pattern.interior()) if (origin.add(cell.rotate(rotation)).equals(point)) return true;
        return false;
    }
    private static boolean naturalGround(Material material) {
        return switch (material) {
            case STONE, DEEPSLATE, TUFF, CALCITE, DRIPSTONE_BLOCK, BASALT, BLACKSTONE,
                    DIRT, COARSE_DIRT, ROOTED_DIRT, GRASS_BLOCK, PODZOL, MYCELIUM, MOSS_BLOCK,
                    SAND, RED_SAND, SANDSTONE, RED_SANDSTONE, GRAVEL, CLAY, TERRACOTTA,
                    END_STONE, SNOW_BLOCK -> true;
            default -> false;
        };
    }
    static void faceAway(Location landing, PortalPattern pattern, Offset origin, int rotation) {
        Bounds bounds = bounds(pattern,rotation);
        double dx = landing.getX() - (origin.x() + (bounds.minX()+bounds.maxX()) / 2.0 + .5);
        double dz = landing.getZ() - (origin.z() + (bounds.minZ()+bounds.maxZ()) / 2.0 + .5);
        // Upright portals face straight out of their plane. Pads face outward from their centre.
        boolean upright = pattern.interior().stream().mapToInt(Offset::y).distinct().count() > 1;
        if (upright) {
            if (rotation % 2 == 0 && Math.abs(dz) > .01) dx = 0;
            else if (rotation % 2 != 0 && Math.abs(dx) > .01) dz = 0;
        }
        if (Math.abs(dx) + Math.abs(dz) < .01) dz = 1;
        landing.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));
        landing.setPitch(0);
    }
    private static boolean replaceable(Material material) {
        return material.isAir() || material == Material.GLOW_LICHEN || material == Material.CAVE_VINES
                || material == Material.CAVE_VINES_PLANT || material == Material.SHORT_GRASS || material == Material.TALL_GRASS
                || material == Material.DEAD_BUSH || material == Material.SNOW || material == Material.FERN
                || material == Material.BROWN_MUSHROOM || material == Material.RED_MUSHROOM;
    }
    private static final class ExplorationFloor {
        private static boolean safe(Material material) {
            return material.isSolid() && material.isOccluding() && material != Material.MAGMA_BLOCK
                    && material != Material.CACTUS && material != Material.CAMPFIRE && material != Material.SOUL_CAMPFIRE;
        }
    }
}
