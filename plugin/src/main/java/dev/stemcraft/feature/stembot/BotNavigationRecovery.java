package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.*;

/** Connected ground routes, searched incrementally on the server thread. Citizens walks each segment. */
final class BotNavigationRecovery {
    private BotNavigationRecovery() { }

    record Point(int x, double y, int z) { }
    record Entry(Point point, double cost, double estimate) { }

    /** Separate terrain access makes route topology testable without a running Minecraft server. */
    interface Terrain {
        List<Double> surfaces(int x, int z, double low, double high);
        boolean clear(int x, int z, double feet, double top);
    }

    static final class Search implements BotActor.RouteSearch {
        private static final int MAX_NODES = 65536;
        private final World world;
        private final Terrain terrain;
        private final Point start;
        private final Point goal;
        private final PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble(Entry::estimate)
            .thenComparing(Comparator.comparingDouble(Entry::cost).reversed()));
        private final Map<Point, Double> costs = new HashMap<>();
        private final Map<Point, Point> parents = new HashMap<>();
        private List<Location> result;
        private int visited;

        Search(Location from, Location target) {
            this(from, target, new BlockTerrain(from.getWorld()));
        }

        Search(Location from, Location target, Terrain terrain) {
            world = from.getWorld();
            this.terrain = terrain;
            start = new Point(from.getBlockX(), from.getY(), from.getBlockZ());
            goal = new Point(target.getBlockX(), target.getY(), target.getBlockZ());
            if(world == null || !world.equals(target.getWorld())) {
                result = List.of();
                return;
            }
            costs.put(start, 0D);
            open.add(new Entry(start, 0, heuristic(start)));
        }

        @Override
        public List<Location> advance() {
            if(result != null) return result;
            long deadline = System.nanoTime() + 2_000_000;
            for(int batch = 0; batch < 128 && System.nanoTime() < deadline; batch++) {
                if(open.isEmpty() || visited++ >= MAX_NODES) return finish(List.of());
                Entry entry = open.remove();
                Point point = entry.point();
                if(entry.cost() > costs.getOrDefault(point, Double.POSITIVE_INFINITY)) continue;
                if(point.x() == goal.x() && point.z() == goal.z() && Math.abs(point.y() - goal.y()) <= .6)
                    return finish(route(point));
                for(int[] direction : DIRECTIONS) {
                    int x = point.x() + direction[0], z = point.z() + direction[1];
                    // Allow detours beyond either endpoint, while keeping an impossible search bounded.
                    if(x < Math.min(start.x(), goal.x()) - 64 || x > Math.max(start.x(), goal.x()) + 64
                        || z < Math.min(start.z(), goal.z()) - 64 || z > Math.max(start.z(), goal.z()) + 64) continue;
                    for(double y : terrain.surfaces(x, z, point.y() - 3, point.y() + 1)) {
                        double crossingHeight = Math.max(point.y(), y);
                        // Clearance over the source and destination prevents jumping through low ceilings.
                        if(!terrain.clear(point.x(), point.z(), point.y(), crossingHeight + 1.8)
                            || !terrain.clear(x, z, y, crossingHeight + 1.8)) continue;
                        Point next = new Point(x, y, z);
                        double cost = entry.cost() + 1 + Math.abs(y - point.y());
                        if(cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                        costs.put(next, cost);
                        parents.put(next, point);
                        open.add(new Entry(next, cost, cost + heuristic(next)));
                    }
                }
            }
            return null;
        }

        private double heuristic(Point point) {
            return Math.abs(point.x() - goal.x()) + Math.abs(point.z() - goal.z())
                + Math.max(0, Math.abs(point.y() - goal.y()) - .6);
        }

        private List<Location> route(Point end) {
            List<Point> points = new ArrayList<>();
            for(Point point = end; point != null; point = parents.get(point)) points.add(point);
            Collections.reverse(points);
            List<Location> route = new ArrayList<>();
            int last = 0;
            for(int i = 1; i < points.size(); i++) {
                Point previous = points.get(i - 1), point = points.get(i);
                Point next = i + 1 < points.size() ? points.get(i + 1) : null;
                // Keep every elevation transition and corner; shorten only straight, flat runs.
                if(next == null || i - last >= 8 || previous.y() != point.y() || next.y() != point.y()
                    || point.x() - previous.x() != next.x() - point.x()
                    || point.z() - previous.z() != next.z() - point.z()) {
                    route.add(new Location(world, point.x() + .5, point.y(), point.z() + .5));
                    last = i;
                }
            }
            return List.copyOf(route);
        }

        private List<Location> finish(List<Location> route) {
            result = route;
            open.clear();
            costs.clear();
            parents.clear();
            return result;
        }
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static final class BlockTerrain implements Terrain {
        private final World world;
        BlockTerrain(World world) { this.world = world; }

        @Override
        public List<Double> surfaces(int x, int z, double low, double high) {
            if(world == null || !world.isChunkLoaded(x >> 4, z >> 4)) return List.of();
            Set<Double> heights = new TreeSet<>(Comparator.reverseOrder());
            for(int y = Math.max(world.getMinHeight(), (int)Math.floor(low) - 1);
                y <= Math.min(world.getMaxHeight() - 1, (int)Math.floor(high)); y++) {
                var block = world.getBlockAt(x, y, z);
                if(hazardous(block.getType())) continue;
                for(BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                    // Bukkit block collision boxes are relative to the block position.
                    double top = y + box.getMaxY();
                    if(top >= low && top <= high && box.getMinX() < .8 && box.getMaxX() > .2
                        && box.getMinZ() < .8 && box.getMaxZ() > .2) heights.add(top);
                }
            }
            return List.copyOf(heights);
        }

        @Override
        public boolean clear(int x, int z, double feet, double top) {
            if(world == null || feet < world.getMinHeight() || top >= world.getMaxHeight()
                || !world.isChunkLoaded(x >> 4, z >> 4)) return false;
            // Include the block below for collision shapes taller than one block, such as fences.
            for(int y = Math.max(world.getMinHeight(), (int)Math.floor(feet) - 1); y < Math.ceil(top); y++) {
                var block = world.getBlockAt(x, y, z);
                if(hazardous(block.getType()) && y + 1 > feet - .01) return false;
                BoundingBox body = new BoundingBox(.2, feet - y + .001, .2, .8, top - y - .001, .8);
                if(block.getCollisionShape().overlaps(body)) return false;
            }
            return true;
        }
    }

    private static boolean hazardous(Material material) {
        return switch(material) {
            case WATER, LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK,
                 CACTUS, SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE -> true;
            default -> false;
        };
    }
}
