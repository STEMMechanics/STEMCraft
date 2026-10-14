package dev.stemcraft.feature.stembot;

import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/** Directions out of a supplied rectangular X/Z region in the player's current world. */
public record BotRegionGuide(Map<String, Region> regions) {
    public BotRegionGuide {
        regions = Map.copyOf(regions);
    }

    public record Region(Bounds bounds, Messages messages) { }
    public record Advice(String message, Route route) { }
    public record Route(String direction, double x, double z, double distance) { }

    /** Inclusive block corners, independent of world and generation version. */
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public Bounds {
            int x1 = coordinate(minX), z1 = coordinate(minZ);
            int x2 = coordinate(maxX), z2 = coordinate(maxZ);
            minX = Math.min(x1, x2);
            minZ = Math.min(z1, z2);
            maxX = Math.max(x1, x2);
            maxZ = Math.max(z1, z2);
        }

        public Bounds alignedToChunks() {
            return new Bounds(Math.floorDiv(minX, 16) * 16, Math.floorDiv(minZ, 16) * 16,
                Math.floorDiv(maxX, 16) * 16 + 15, Math.floorDiv(maxZ, 16) * 16 + 15);
        }

        public boolean contains(double x, double z) {
            return x >= minX && x < maxX + 1.0 && z >= minZ && z < maxZ + 1.0;
        }

        /** First block centres outside each edge; equal distances prefer north, east, south, west. */
        public Route nearestExit(double x, double z, BiPredicate<Double, Double> accessible) {
            if (!contains(x, z)) return null;
            List<Route> candidates = List.of(
                route("north", x, minZ - 0.5, x, z),
                route("east", maxX + 1.5, z, x, z),
                route("south", x, maxZ + 1.5, x, z),
                route("west", minX - 0.5, z, x, z));
            Route closest = null;
            for (Route candidate : candidates) {
                if (accessible.test(candidate.x(), candidate.z())
                    && (closest == null || candidate.distance() < closest.distance())) {
                    closest = candidate;
                }
            }
            return closest;
        }

        private static Route route(String direction, double x, double z, double fromX, double fromZ) {
            return new Route(direction, x, z, Math.hypot(x - fromX, z - fromZ));
        }
    }

    public record Messages(String inside, String outside, String unconfigured, String blocked) {
        static Messages defaults() {
            return new Messages(
                "Head {direction} about {distance} blocks, towards X {x}, Z {z}. That is the closest way out of the mapped region.",
                "You are already outside the mapped region.",
                "I do not have this region mapped yet. Ask a staff member to map it.",
                "The world border blocks the way out of the mapped region.");
        }
    }

    public static BotRegionGuide empty() {
        return new BotRegionGuide(Map.of());
    }

    public static BotRegionGuide read(ConfigSection config) {
        Map<String, Region> regions = new LinkedHashMap<>();
        ConfigSection settings = config.getSection("regions", false);
        if (settings == null) return empty();
        Messages defaults = Messages.defaults();
        for (String id : settings.getKeys(false)) {
            if (!id.matches("[a-z0-9][a-z0-9_-]{0,63}"))
                throw new IllegalArgumentException("regions." + id + " has an invalid region id");
            ConfigSection entry = settings.getSection(id, false);
            if (entry == null) throw new IllegalArgumentException("regions." + id + " must be a section");
            Bounds bounds = readBounds(entry.get("bounds"), "regions." + id + ".bounds");
            if (bounds != null && entry.getBoolean("align-to-chunks", false)) {
                try {
                    bounds = bounds.alignedToChunks();
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException("regions." + id + ".bounds: " + invalid.getMessage(), invalid);
                }
            }
            Messages messages = new Messages(
                entry.getString("messages.inside", defaults.inside()),
                entry.getString("messages.outside", defaults.outside()),
                entry.getString("messages.unconfigured", defaults.unconfigured()),
                entry.getString("messages.blocked", defaults.blocked()));
            regions.put(id, new Region(bounds, messages));
        }
        return new BotRegionGuide(regions);
    }

    private static Bounds readBounds(Object raw, String path) {
        if (raw == null || raw instanceof String text && text.isBlank()
            || raw instanceof List<?> values && values.isEmpty()) return null;
        List<?> values;
        if (raw instanceof String text) values = List.of(text.trim().split("[,;\\s]+"));
        else if (raw instanceof List<?> list) values = list;
        else throw new IllegalArgumentException(path + " must be [x1, z1, x2, z2] or x1,z1;x2,z2");
        if (values.size() != 4) throw new IllegalArgumentException(path + " requires four block coordinates");
        try {
            return new Bounds(coordinate(values.get(0)), coordinate(values.get(1)),
                coordinate(values.get(2)), coordinate(values.get(3)));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(path + ": " + invalid.getMessage(), invalid);
        }
    }

    public Advice advise(String id, Location player) {
        Region region = regions.get(id);
        if (region == null) throw new IllegalArgumentException("Unknown STEMBot region: " + id);
        Messages messages = region.messages();
        World world = player.getWorld();
        if (world == null || region.bounds() == null) return new Advice(messages.unconfigured(), null);
        if (!region.bounds().contains(player.getX(), player.getZ()))
            return new Advice(messages.outside(), null);
        WorldBorder border = world.getWorldBorder();
        Route route = region.bounds().nearestExit(player.getX(), player.getZ(),
            (x, z) -> border.isInside(new Location(world, x, player.getY(), z)));
        return route == null ? new Advice(messages.blocked(), null)
            : new Advice(format(messages.inside(), route), route);
    }

    private static String format(String template, Route route) {
        return template.replace("{direction}", route.direction())
            .replace("{distance}", Long.toString((long) Math.ceil(route.distance())))
            .replace("{x}", Integer.toString((int) Math.floor(route.x())))
            .replace("{z}", Integer.toString((int) Math.floor(route.z())));
    }

    private static int coordinate(Object value) {
        double number;
        if (value instanceof Number numeric) number = numeric.doubleValue();
        else if (value instanceof String text) number = Double.parseDouble(text);
        else throw new IllegalArgumentException("coordinates must be whole block numbers");
        if (!Double.isFinite(number) || number != Math.rint(number) || Math.abs(number) > 30_000_000)
            throw new IllegalArgumentException("coordinates must be whole block numbers within +/-30000000");
        return (int) number;
    }
}
