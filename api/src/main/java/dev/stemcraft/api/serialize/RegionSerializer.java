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
import dev.stemcraft.api.util.MapParse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serializer and deserializer for SCRegion objects.
 */
public class RegionSerializer {

    /**
     * Serializes an SCRegion to a map representation.
     *
     * @param region The SCRegion to serialize.
     * @return A map representing the serialized region.
     */
    public static java.util.@NonNull Map<String, Object> serialize(SCRegion region) {
        World world = region.getWorld();
        Region worldEditRegion = region.getRegion();

        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("world", world != null ? world.getName() : null);

        if (worldEditRegion instanceof CuboidRegion cuboid) {
            map.put("type", "CUBOID");
            map.put("min", vectorToMap(cuboid.getMinimumPoint()));
            map.put("max", vectorToMap(cuboid.getMaximumPoint()));
        } else if (worldEditRegion instanceof Polygonal2DRegion poly) {
            map.put("type", "POLYGON");
            map.put("minY", poly.getMinimumY());
            map.put("maxY", poly.getMaximumY());

            java.util.List<Map<String, Object>> pts = new java.util.ArrayList<>();
            for (BlockVector2 p : poly.getPoints()) {
                Map<String, Object> pm = new java.util.LinkedHashMap<>();
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

    /**
     * Deserializes an SCRegion from a map representation.
     *
     * Map Format -
     * region.world: String (world name)
     * region.type: String ("CUBOID" or "POLYGON")
     * * For CUBOID:
     *  region.min: Map { x: int, y: int, z: int }
     *  region.max: Map { x: int, y: int, z: int }
     *  * For POLYGON:
     *  region.minY: int
     *  region.maxY: int
     *  region.points: List of Maps { x: int, z: int }.
     *
     * @param map The map representing the serialized region.
     * @return The deserialized SCRegion.
     * @throws IllegalArgumentException If the map is invalid or missing required fields.
     */
    public static SCRegion deserialize(Map<String, Object> map) throws IllegalArgumentException {
        if (map == null) return null;

        String worldName = MapParse.string(map, "world", "region");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) return null;

        String type = MapParse.requireString(map, "type", "region");

        if ("CUBOID".equalsIgnoreCase(type)) {
            Map<String, Object> minMap = MapParse.requireMap(map, "min", "region");
            Map<String, Object> maxMap = MapParse.requireMap(map, "max", "region");

            int minX = MapParse.requireInt(minMap, "x", "region.min");
            int minY = MapParse.requireInt(minMap, "y", "region.min");
            int minZ = MapParse.requireInt(minMap, "z", "region.min");
            int maxX = MapParse.requireInt(maxMap, "x", "region.max");
            int maxY = MapParse.requireInt(maxMap, "y", "region.max");
            int maxZ = MapParse.requireInt(maxMap, "z", "region.max");

            BlockVector3 min = BlockVector3.at(minX, minY, minZ);
            BlockVector3 max = BlockVector3.at(maxX, maxY, maxZ);
            com.sk89q.worldedit.world.World weWorld = adaptWorld(world);
            Region region = weWorld != null
                ? new CuboidRegion(weWorld, min, max)
                : new CuboidRegion(min, max);
            return new SCRegion(region, world);
        }

        if ("POLYGON".equalsIgnoreCase(type)) {
            int minY = MapParse.requireInt(map, "minY", "region");
            int maxY = MapParse.requireInt(map, "maxY", "region");

            List<Map<String, Object>> ptsMap = MapParse.listOfMaps(map.get("points"), "region.points");
            if (ptsMap.isEmpty()) {
                throw new IllegalArgumentException("Missing points for polygon region");
            }

            List<BlockVector2> pts = new ArrayList<>(ptsMap.size());

            for (int i = 0; i < ptsMap.size(); i++) {
                Map<String, Object> pm = ptsMap.get(i);
                int x = MapParse.requireInt(pm, "x", "region.points[" + i + "]");
                int z = MapParse.requireInt(pm, "z", "region.points[" + i + "]");
                pts.add(BlockVector2.at(x, z));
            }
            com.sk89q.worldedit.world.World weWorld = adaptWorld(world);
            Polygonal2DRegion poly = weWorld != null
                ? new Polygonal2DRegion(weWorld, pts, minY, maxY)
                : new Polygonal2DRegion(null, pts, minY, maxY);
            return new SCRegion(poly, world);
        }

        throw new IllegalArgumentException("Unknown region type: " + type);
    }

    /**
     * Converts a BlockVector3 to a map representation.
     *
     * @param vec The BlockVector3 to convert.
     * @return A map representing the vector.
     */
    private static Map<String, Object> vectorToMap(BlockVector3 vec) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("x", vec.x());
        map.put("y", vec.y());
        map.put("z", vec.z());
        return map;
    }

    /**
     * Compact string representation of this region, used for manual or legacy storage.
     * This is not the Bukkit ConfigurationSerializable API.
     *
     * @param region The SCRegion to serialize.
     * @return The string representation of the region.
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

    /**
     * Deserializes an SCRegion from its compact string representation.
     *
     * @param s The string representation of the region.
     * @param world The Bukkit world the region belongs to.
     * @return The deserialized SCRegion.
     */
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

    /**
     * Serializes a CuboidRegion to a compact string representation.
     *
     * @param cuboid The CuboidRegion to serialize.
     * @return The string representation of the cuboid region.
     */
    private static String serializeCuboid(CuboidRegion cuboid) {
        BlockVector3 min = cuboid.getMinimumPoint();
        BlockVector3 max = cuboid.getMaximumPoint();
        return String.format(
                "CUBOID:%d,%d,%d,%d,%d,%d",
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z()
        );
    }

    /**
     * Serializes a Polygonal2DRegion to a compact string representation.
     *
     * @param poly The Polygonal2DRegion to serialize.
     * @return The string representation of the polygonal region.
     */
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

    /**
     * Deserializes a CuboidRegion from its compact string representation.
     *
     * @param parts The parts of the cuboid data.
     * @param world The Bukkit world the region belongs to.
     * @return The deserialized SCRegion.
     */
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

        BlockVector3 min = BlockVector3.at(minX, minY, minZ);
        BlockVector3 max = BlockVector3.at(maxX, maxY, maxZ);
        com.sk89q.worldedit.world.World weWorld = adaptWorld(world);
        Region region = weWorld != null
            ? new CuboidRegion(weWorld, min, max)
            : new CuboidRegion(min, max);

        return new SCRegion(region, world);
    }

    /**
     * Deserializes a Polygonal2DRegion from its compact string representation.
     *
     * @param parts The parts of the polygon data.
     * @param world The Bukkit world the region belongs to.
     * @return The deserialized SCRegion.
     */
    private static SCRegion deserializePolygon(String[] parts, World world) {
        int minY = Integer.parseInt(parts[0]);
        int maxY = Integer.parseInt(parts[1]);

        if (world == null) return null;

        List<BlockVector2> points = new ArrayList<>();
        // remaining parts are x,z pairs
        for (int i = 2; i + 1 < parts.length; i += 2) {
            int x = Integer.parseInt(parts[i]);
            int z = Integer.parseInt(parts[i + 1]);
            points.add(BlockVector2.at(x, z));
        }
        com.sk89q.worldedit.world.World weWorld = adaptWorld(world);
        Polygonal2DRegion poly = weWorld != null
            ? new Polygonal2DRegion(weWorld, points, minY, maxY)
            : new Polygonal2DRegion(null, points, minY, maxY);
        return new SCRegion(poly, world);
    }

    private static com.sk89q.worldedit.world.World adaptWorld(World world) {
        try {
            return BukkitAdapter.adapt(world);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
