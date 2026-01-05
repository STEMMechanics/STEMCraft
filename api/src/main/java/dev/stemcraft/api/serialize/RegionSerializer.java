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

package dev.stemcraft.api.serialize;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RegionSerializer {

    public static java.util.@NonNull Map<String, Object> serialize(SCRegion region) {
        World world = region.getWorld();
        Region worldEditRegion = region.getRegion();

        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("world", world != null ? world.getName() : null);

        if (worldEditRegion instanceof CuboidRegion cuboid) {
            map.put("type", "CUBOID");
            map.put("min", vectorToMap(cuboid.getMinimumPoint()));
            map.put("max", vectorToMap(cuboid.getMaximumPoint()));
        } else if (worldEditRegion instanceof Polygonal2DRegion poly) {
            map.put("type", "POLYGON");
            map.put("minY", poly.getMinimumY());
            map.put("maxY", poly.getMaximumY());

            java.util.List<java.util.Map<String, Object>> pts = new java.util.ArrayList<>();
            for (BlockVector2 p : poly.getPoints()) {
                java.util.Map<String, Object> pm = new java.util.LinkedHashMap<>();
                pm.put("x", p.x());
                pm.put("z", p.z());
                pts.add(pm);
            }
            map.put("points", pts);
        } else {
            throw new IllegalStateException("Unsupported region type " + worldEditRegion.getClass().getSimpleName());
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    public static SCRegion deserialize(java.util.Map<String, Object> map) {
        if (map == null) return null;

        String worldName = (String) map.get("world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) return null;

        String type = (String) map.get("type");
        if ("CUBOID".equalsIgnoreCase(type)) {
            java.util.Map<String, Object> minMap = (java.util.Map<String, Object>) map.get("min");
            java.util.Map<String, Object> maxMap = (java.util.Map<String, Object>) map.get("max");
            if (minMap == null || maxMap == null) {
                throw new IllegalArgumentException("Missing min/max for cuboid region");
            }

            int minX = ((Number) minMap.get("x")).intValue();
            int minY = ((Number) minMap.get("y")).intValue();
            int minZ = ((Number) minMap.get("z")).intValue();
            int maxX = ((Number) maxMap.get("x")).intValue();
            int maxY = ((Number) maxMap.get("y")).intValue();
            int maxZ = ((Number) maxMap.get("z")).intValue();

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
            BlockVector3 min = BlockVector3.at(minX, minY, minZ);
            BlockVector3 max = BlockVector3.at(maxX, maxY, maxZ);
            Region region = new CuboidRegion(weWorld, min, max);
            return new SCRegion(region, world);
        }

        if ("POLYGON".equalsIgnoreCase(type)) {
            int minY = ((Number) map.get("minY")).intValue();
            int maxY = ((Number) map.get("maxY")).intValue();

            java.util.List<java.util.Map<String, Object>> ptsMap =
                    (java.util.List<java.util.Map<String, Object>>) map.get("points");
            if (ptsMap == null || ptsMap.isEmpty()) {
                throw new IllegalArgumentException("Missing points for polygon region");
            }

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
            java.util.List<BlockVector2> pts = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> pm : ptsMap) {
                int x = ((Number) pm.get("x")).intValue();
                int z = ((Number) pm.get("z")).intValue();
                pts.add(BlockVector2.at(x, z));
            }

            Polygonal2DRegion poly = new Polygonal2DRegion(weWorld, pts, minY, maxY);
            return new SCRegion(poly, world);
        }

        throw new IllegalArgumentException("Unknown region type: " + type);
    }

    private static java.util.Map<String, Object> vectorToMap(BlockVector3 vec) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("x", vec.x());
        map.put("y", vec.y());
        map.put("z", vec.z());
        return map;
    }

    /**
     * Compact string representation of this region, used for manual or legacy storage.
     * This is not the Bukkit ConfigurationSerializable API.
     */
    public static String toString(SCRegion region) {
        Region weRegion = region.getRegion();

        if (weRegion instanceof CuboidRegion cuboid) {
            return serializeCuboid(cuboid);
        } else if (weRegion instanceof Polygonal2DRegion poly) {
            return serializePolygon(poly);
        }
        throw new IllegalStateException("Unsupported region type " + region.getClass().getSimpleName());
    }

    public static SCRegion fromString(String s, World world) {
        String[] typeSplit = s.split(":", 2);
        if (typeSplit.length != 2) {
            throw new IllegalArgumentException("Invalid region string: " + s);
        }

        String type = typeSplit[0];
        String[] parts = typeSplit[1].split(",");

        if ("CUBOID".equalsIgnoreCase(type)) {
            return deserializeCuboid(parts, world);
        }
        if ("POLYGON".equalsIgnoreCase(type)) {
            return deserializePolygon(parts, world);
        }

        throw new IllegalArgumentException("Unknown region type: " + type);
    }

    private static String serializeCuboid(CuboidRegion cuboid) {
        BlockVector3 min = cuboid.getMinimumPoint();
        BlockVector3 max = cuboid.getMaximumPoint();
        return String.format(
                "CUBOID:%d,%d,%d,%d,%d,%d",
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z()
        );
    }

    private static String serializePolygon(Polygonal2DRegion poly) {
        int minY = poly.getMinimumY();
        int maxY = poly.getMaximumY();

        String points = poly.getPoints().stream()
                .map(p -> p.x() + "," + p.z())
                .collect(Collectors.joining(","));

        return String.format(
                "POLYGON:%d,%d,%s",
                minY,
                maxY,
                points
        );
    }

    private static SCRegion deserializeCuboid(String[] parts, World world) {
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid cuboid data");
        }
        if (world == null) return null;

        int minX = Integer.parseInt(parts[0]);
        int minY = Integer.parseInt(parts[1]);
        int minZ = Integer.parseInt(parts[2]);
        int maxX = Integer.parseInt(parts[3]);
        int maxY = Integer.parseInt(parts[4]);
        int maxZ = Integer.parseInt(parts[5]);

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        BlockVector3 min = BlockVector3.at(minX, minY, minZ);
        BlockVector3 max = BlockVector3.at(maxX, maxY, maxZ);

        Region region = new CuboidRegion(weWorld, min, max);

        return new SCRegion(region, world);
    }

    private static SCRegion deserializePolygon(String[] parts, World world) {
        int minY = Integer.parseInt(parts[0]);
        int maxY = Integer.parseInt(parts[1]);

        if (world == null) return null;

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        List<BlockVector2> points = new ArrayList<>();
        // remaining parts are x,z pairs
        for (int i = 2; i + 1 < parts.length; i += 2) {
            int x = Integer.parseInt(parts[i]);
            int z = Integer.parseInt(parts[i + 1]);
            points.add(BlockVector2.at(x, z));
        }

        Polygonal2DRegion poly = new Polygonal2DRegion(weWorld, points, minY, maxY);
        return new SCRegion(poly, world);
    }
}
