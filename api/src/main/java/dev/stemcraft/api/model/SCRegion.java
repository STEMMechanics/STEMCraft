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

package dev.stemcraft.api.model;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import dev.stemcraft.api.serialize.RegionSerializer;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wrapper around a WorldEdit Region with additional utility methods.
 */
public class SCRegion implements ConfigurationSerializable {
    @Getter
    private final Region region;
    @Getter
    @Setter
    private World world;

    private static final int RANDOM_SAMPLES_COUNT = 32;

    /**
     * Constructs an SCRegion with the given WorldEdit region and Bukkit world.
     *
     * @param region The WorldEdit region.
     * @param world The Bukkit world.
     */
    public SCRegion(Region region, World world) {
        this.region = region;
        this.world = world;
    }

    /**
     * Returns true if the given location is contained within this region.
     * World must match.
     *
     * @param loc The location to check.
     * @return True if the location is inside the region, false otherwise.
     */
    public boolean contains(Location loc) {
        if (!loc.getWorld().equals(world)) return false;

        BlockVector3 pos = BlockVector3.at(
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
        return region.contains(pos);
    }

    /**
     * Returns true if the given region is fully contained within this region.
     * World must match.
     *
     * @param other The other region to check.
     * @return True if this region contains the other region, false otherwise.
     */
    public boolean contains(SCRegion other) {
        if (other == null) return false;
        if (this.world == null || other.world == null) return false;
        if (!this.world.equals(other.world)) return false;

        // Fast reject: if this AABB doesn't fully contain the other's AABB, it cannot contain it.
        BlockVector3 aMin = this.region.getMinimumPoint();
        BlockVector3 aMax = this.region.getMaximumPoint();
        BlockVector3 bMin = other.region.getMinimumPoint();
        BlockVector3 bMax = other.region.getMaximumPoint();

        if (!aabbContains(aMin, aMax, bMin, bMax)) return false;

        // Validate using representative points from the other region.
        for (BlockVector3 p : other.getRepresentativePoints()) {
            if (!this.region.contains(p)) return false;
        }

        // Extra safety: sample a few random points inside the other's bounding box and ensure they're inside this.
        // This helps with polygon edge cases.
        return containsRandomSamplesFrom(other);
    }

    /**
     * Returns true if this region intersects the given region.
     * World must match.
     *
     * @param other The other region to check.
     * @return True if the regions intersect, false otherwise.
     */
    public boolean intersects(SCRegion other) {
        if (other == null) return false;
        if (this.world == null || other.world == null) return false;
        if (!this.world.equals(other.world)) return false;

        BlockVector3 aMin = this.region.getMinimumPoint();
        BlockVector3 aMax = this.region.getMaximumPoint();
        BlockVector3 bMin = other.region.getMinimumPoint();
        BlockVector3 bMax = other.region.getMaximumPoint();

        // Fast reject: AABB doesn't overlap
        if (!aabbIntersects(aMin, aMax, bMin, bMax)) return false;

        // Cuboid + Cuboid: exact overlap check
        if (this.region instanceof CuboidRegion && other.region instanceof CuboidRegion) {
            return true; // AABB overlap is exact for cuboids
        }

        // If any representative point of either region is inside the other, they intersect.
        for (BlockVector3 p : other.getRepresentativePoints()) {
            if (this.region.contains(p)) return true;
        }
        for (BlockVector3 p : this.getRepresentativePoints()) {
            if (other.region.contains(p)) return true;
        }

        // Fallback: random sampling within the AABB overlap box to catch edge-only intersections.
        return intersectsByRandomSampling(other);
    }

    /**
     * AABB containment test.
     *
     * @param aMin the minimum corner of box A (lowest x, y, z).
     * @param aMax the maximum corner of box A (highest x, y, z).
     * @param bMin the minimum corner of box B (lowest x, y, z).
     * @param bMax the maximum corner of box B (highest x, y, z).
     * @return true if box A fully contains box B.
     */
    private static boolean aabbContains(BlockVector3 aMin, BlockVector3 aMax, BlockVector3 bMin, BlockVector3 bMax) {
        return aMin.x() <= bMin.x() && aMin.y() <= bMin.y() && aMin.z() <= bMin.z()
                && aMax.x() >= bMax.x() && aMax.y() >= bMax.y() && aMax.z() >= bMax.z();
    }

    /**
     * AABB intersection test.
     *
     * @param aMin the minimum corner of box A (lowest x, y, z).
     * @param aMax the maximum corner of box A (highest x, y, z).
     * @param bMin the minimum corner of box B (lowest x, y, z).
     * @param bMax the maximum corner of box B (highest x, y, z).
     * @return true if box A and box B overlap in any volume.
     */
    private static boolean aabbIntersects(BlockVector3 aMin, BlockVector3 aMax, BlockVector3 bMin, BlockVector3 bMax) {
        return aMin.x() <= bMax.x() && aMax.x() >= bMin.x()
                && aMin.y() <= bMax.y() && aMax.y() >= bMin.y()
                && aMin.z() <= bMax.z() && aMax.z() >= bMin.z();
    }

    /**
     * Returns a small set of representative points for containment/intersection checks.
     * Includes AABB corners, plus polygon vertices at minY/maxY.
     *
     * @return List of representative points.
     */
    private List<BlockVector3> getRepresentativePoints() {
        List<BlockVector3> pts = new ArrayList<>();

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // 8 corners of the bounding box
        pts.add(BlockVector3.at(min.x(), min.y(), min.z()));
        pts.add(BlockVector3.at(min.x(), min.y(), max.z()));
        pts.add(BlockVector3.at(min.x(), max.y(), min.z()));
        pts.add(BlockVector3.at(min.x(), max.y(), max.z()));
        pts.add(BlockVector3.at(max.x(), min.y(), min.z()));
        pts.add(BlockVector3.at(max.x(), min.y(), max.z()));
        pts.add(BlockVector3.at(max.x(), max.y(), min.z()));
        pts.add(BlockVector3.at(max.x(), max.y(), max.z()));

        // If polygon, include its 2D vertices at minY/maxY
        if (region instanceof Polygonal2DRegion poly) {
            int minY = poly.getMinimumY();
            int maxY = poly.getMaximumY();
            for (BlockVector2 p : poly.getPoints()) {
                pts.add(BlockVector3.at(p.x(), minY, p.z()));
                pts.add(BlockVector3.at(p.x(), maxY, p.z()));
            }
        }

        return pts;
    }

    /**
     * Checks if this region contains random samples from the other region.
     *
     * @param other the other region to sample from.
     * @param samples number of samples to test.
     * @return true if all sampled points from other are inside this region.
     */
    private boolean containsRandomSamplesFrom(SCRegion other, @SuppressWarnings("SameParameterValue") int samples) {
        if (samples <= 0) return true;

        Random r = ThreadLocalRandom.current();
        BlockVector3 min = other.region.getMinimumPoint();
        BlockVector3 max = other.region.getMaximumPoint();

        int tries = samples * 20;
        int found = 0;

        while (tries-- > 0 && found < samples) {
            int x = randomBetween(min.x(), max.x(), r);
            int y = randomBetween(min.y(), max.y(), r);
            int z = randomBetween(min.z(), max.z(), r);

            BlockVector3 p = BlockVector3.at(x, y, z);
            if (!other.region.contains(p)) continue;

            found++;
            if (!this.region.contains(p)) return false;
        }

        // If we couldn't find enough points inside other (very thin regions), fall back to representative points only.
        return true;
    }
    private boolean containsRandomSamplesFrom(SCRegion other) { return containsRandomSamplesFrom(other, RANDOM_SAMPLES_COUNT); }

    /**
     * Checks for intersection with another region using random sampling within the overlapping AABB.
     *
     * @param other the other region to check.
     * @param samples number of samples to test.
     * @return true if an intersection is found.
     */
    private boolean intersectsByRandomSampling(SCRegion other, @SuppressWarnings("SameParameterValue") int samples) {
        if (samples <= 0) return false;

        Random r = ThreadLocalRandom.current();

        // Sample points from the overlap AABB to increase hit-rate.
        BlockVector3 aMin = this.region.getMinimumPoint();
        BlockVector3 aMax = this.region.getMaximumPoint();
        BlockVector3 bMin = other.region.getMinimumPoint();
        BlockVector3 bMax = other.region.getMaximumPoint();

        int oMinX = Math.max(aMin.x(), bMin.x());
        int oMinY = Math.max(aMin.y(), bMin.y());
        int oMinZ = Math.max(aMin.z(), bMin.z());
        int oMaxX = Math.min(aMax.x(), bMax.x());
        int oMaxY = Math.min(aMax.y(), bMax.y());
        int oMaxZ = Math.min(aMax.z(), bMax.z());

        int tries = samples * 30;
        int checked = 0;

        while (tries-- > 0 && checked < samples) {
            int x = randomBetween(oMinX, oMaxX, r);
            int y = randomBetween(oMinY, oMaxY, r);
            int z = randomBetween(oMinZ, oMaxZ, r);

            BlockVector3 p = BlockVector3.at(x, y, z);
            checked++;

            if (this.region.contains(p) && other.region.contains(p)) return true;
        }

        return false;
    }

    private boolean intersectsByRandomSampling(SCRegion other) { return intersectsByRandomSampling(other, RANDOM_SAMPLES_COUNT); }

    /**
     * Returns a random integer between min and max, inclusive.
     *
     * @param min Minimum value.
     * @param max Maximum value.
     * @param random Random instance.
     * @return Random integer between min and max.
     */
    private int randomBetween(int min, int max, Random random) {
        return min + random.nextInt((max - min) + 1);
    }

    /**
     * Returns a random location within the region.
     * May return null if no valid location found after several attempts.
     *
     * @return Random location inside the region, or null if none found.
     */
    public Location getRandomLocation() {
        Random random = ThreadLocalRandom.current();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int attempts = 1000;

        while (attempts-- > 0) {
            int x = randomBetween(min.x(), max.x(), random);
            int y = randomBetween(min.y(), max.y(), random);
            int z = randomBetween(min.z(), max.z(), random);

            BlockVector3 pos = BlockVector3.at(x, y, z);
            if (!region.contains(pos)) continue; // works for cuboid and polygon

            return new Location(world, x + 0.5, y + 0.5, z + 0.5);
        }

        return null;
    }

    /**
     * Returns a random ground location within the region.
     * The location will be on solid ground with air above for player placement.
     * May return null if no valid ground location found after several attempts.
     *
     * @return Random ground location inside the region, or null if none found.
     */
    public Location getRandomGroundLocation() {
        for (int i = 0; i < 200; i++) {
            Location base = getRandomLocation();
            if (base == null) continue;

            World w = base.getWorld();
            int x = base.getBlockX();
            int z = base.getBlockZ();

            // start at the highest valid Y in the region
            int y = base.getBlockY();

            // walk downward until we hit something
            for (int dy = y; dy > w.getMinHeight(); dy--) {
                Block ground = w.getBlockAt(x, dy - 1, z);
                Block feet = w.getBlockAt(x, dy, z);
                Block head = w.getBlockAt(x, dy + 1, z);

                if (ground.getType().isSolid()
                        && feet.getType().isAir()
                        && head.getType().isAir()) {

                    return new Location(w, x + 0.5, dy, z + 0.5);
                }
            }
        }

        return null;
    }

    /**
     * Checks if the region is a cuboid.
     *
     * @return true if the region is a cuboid.
     */
    public boolean isCuboid() {
        return region instanceof CuboidRegion;
    }

    /**
     * Checks if the region is a polygon.
     *
     * @return true if the region is a polygon.
     */
    public boolean isPolygon() {
        return region instanceof Polygonal2DRegion;
    }

    /**
     * Checks if the given player is inside this region.
     *
     * @param player The player to check.
     * @return True if the player is inside the region, false otherwise.
     */
    public boolean containsPlayer(Player player) {
        if (player == null) return false;
        return contains(player.getLocation());
    }

    /**
     * Returns a list of all online players currently inside this region.
     *
     * @return List of players inside the region.
     */
    public List<Player> getPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(this::containsPlayer)
                .map(p -> (Player) p) // cast from ? extends Player to Player
                .toList();
    }

    /**
     * Serializes this SCRegion to a map for storage.
     *
     * @return A map representing the serialized region.
     */
    @Override
    public @NonNull Map<String, Object> serialize() {
        return RegionSerializer.serialize(this);
    }

    /**
     * Deserializes an SCRegion from a map.
     *
     * @param map The map containing serialized region data.
     * @return The deserialized SCRegion.
     */
    public static SCRegion deserialize(Map<String,Object> map) {
        return RegionSerializer.deserialize(map);
    }

    /**
     * Converts this SCRegion to a string representation.
     *
     * @return The string representation of the region.
     */
    public String toString() {
        return RegionSerializer.toString(this);
    }

    /**
     * Creates an SCRegion from its string representation.
     *
     * @param s The string representation of the region.
     * @param world The Bukkit world for the region.
     * @return The SCRegion instance.
     */
    public static SCRegion fromString(String s, World world) {
        return RegionSerializer.fromString(s, world);
    }
}