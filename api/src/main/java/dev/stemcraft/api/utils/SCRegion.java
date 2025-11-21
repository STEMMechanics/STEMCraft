package dev.stemcraft.api.utils;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SCRegion {
    @Getter
    private final Region region;
    @Getter
    private final World world;

    public SCRegion(Region region, World world) {
        this.region = region;
        this.world = world;
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().equals(world)) return false;

        BlockVector3 pos = BlockVector3.at(
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
        return region.contains(pos);
    }

    public String serialize() {
        if (region instanceof CuboidRegion cuboid) {
            return serializeCuboid(cuboid);
        } else if (region instanceof Polygonal2DRegion poly) {
            return serializePolygon(poly);
        }
        throw new IllegalStateException("Unsupported region type " + region.getClass().getSimpleName());
    }

    private String serializeCuboid(CuboidRegion cuboid) {
        BlockVector3 min = cuboid.getMinimumPoint();
        BlockVector3 max = cuboid.getMaximumPoint();
        return String.format(
                "CUBOID:%d,%d,%d,%d,%d,%d",
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z()
        );
    }

    private String serializePolygon(Polygonal2DRegion poly) {
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

    public static SCRegion deserialize(String s, World world) {
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
        int minY = Integer.parseInt(parts[1]);
        int maxY = Integer.parseInt(parts[2]);
        String pointsStr = parts[3];

        if (world == null) return null;

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        List<BlockVector2> points = new ArrayList<>();
        for (String token : pointsStr.split(",")) {
            String[] xy = token.split(",");
            int x = Integer.parseInt(xy[0]);
            int z = Integer.parseInt(xy[1]);
            points.add(BlockVector2.at(x, z));
        }

        Polygonal2DRegion poly = new Polygonal2DRegion(weWorld, points, minY, maxY);
        return new SCRegion(poly, world);
    }

    public static SCRegion getWESelection(Player player) {
        // assume you already checked that WorldEdit is installed/enabled
        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);

        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(wePlayer);

        com.sk89q.worldedit.world.World weWorld = wePlayer.getWorld(); // or BukkitAdapter.adapt(player.getWorld());

        Region region;
        try {
            region = session.getSelection(weWorld);
        } catch (IncompleteRegionException e) {
            // no full selection set
            return null;
        }

        // optionally restrict to types you support for serialize()
        if (!(region instanceof CuboidRegion) && !(region instanceof Polygonal2DRegion)) {
            // unsupported region type for now
            return null;
        }

        return new SCRegion(region, player.getWorld());
    }

    public void setWESelection(Player player) {
        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);

        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(wePlayer);

        com.sk89q.worldedit.world.World weWorld = wePlayer.getWorld();

        RegionSelector selector;

        if (region instanceof CuboidRegion cuboid) {
            selector = new CuboidRegionSelector(
                    weWorld,
                    cuboid.getMinimumPoint(),
                    cuboid.getMaximumPoint()
            );
        } else if (region instanceof Polygonal2DRegion poly) {
            selector = new Polygonal2DRegionSelector(
                    weWorld,
                    poly.getPoints(),
                    poly.getMinimumY(),
                    poly.getMaximumY()
            );
        } else {
            return; // unsupported type for now
        }

        session.setRegionSelector(weWorld, selector);
        session.dispatchCUISelection(wePlayer);
    }

    private int randomBetween(int min, int max, Random random) {
        return min + random.nextInt((max - min) + 1);
    }

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

    public boolean isCuboid() {
        return region instanceof CuboidRegion;
    }

    public boolean isPolygon() {
        return region instanceof Polygonal2DRegion;
    }

    public boolean containsPlayer(Player player) {
        if (player == null) return false;
        return contains(player.getLocation());
    }

    public List<Player> getPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(this::containsPlayer)
                .map(p -> (Player) p) // cast from ? extends Player to Player
                .toList();
    }
}
