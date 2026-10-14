package dev.stemcraft.feature.stembot;

import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/** Directions to the closest new-generation chunk beyond a configured legacy footprint. */
public record BotWildernessGuide(Map<String, Area> worlds, Messages messages) {
    private static final Pattern QUESTION = Pattern.compile(
        "(?i).*\\b(?:wilderness|new\\s+(?:terrain|chunks|forest)|dappled(?:\\s+forest)?|26\\.3)\\b.*");
    public BotWildernessGuide {
        worlds = Map.copyOf(worlds);
    }

    public record Area(Bounds bounds, String oldVersion, String newVersion) { }
    public record Advice(String message, Route route) { }
    public record Route(String direction, double x, double z, double distance) { }

    /** Inclusive block corners, expanded to the complete chunks containing them. */
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public Bounds {
            int x1 = coordinate(minX), z1 = coordinate(minZ);
            int x2 = coordinate(maxX), z2 = coordinate(maxZ);
            minX = Math.floorDiv(Math.min(x1, x2), 16) * 16;
            minZ = Math.floorDiv(Math.min(z1, z2), 16) * 16;
            maxX = Math.floorDiv(Math.max(x1, x2), 16) * 16 + 15;
            maxZ = Math.floorDiv(Math.max(z1, z2), 16) * 16 + 15;
        }

        public boolean contains(double x, double z) {
            return x >= minX && x < maxX + 1.0 && z >= minZ && z < maxZ + 1.0;
        }

        /** First new block centres on each edge; equal distances prefer north, east, south, west. */
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
                "Head {direction} about {distance} blocks, towards X {x}, Z {z}. That is the closest way past the old {old-version} generation boundary; newly generated chunks beyond it use {new-version}.",
                "You are already beyond the old {old-version} generation boundary. Newly generated chunks here use {new-version}. Keep exploring for the new wilderness features.",
                "I do not have the old terrain boundary mapped for this world yet. Ask a staff member to map it.",
                "The world border blocks the way out of the mapped {old-version} terrain. Ask staff where the new wilderness is accessible.");
        }
    }

    public static BotWildernessGuide empty() {
        return new BotWildernessGuide(Map.of(), Messages.defaults());
    }

    public boolean recognises(String world, String input) {
        return worlds.containsKey(world.toLowerCase(Locale.ROOT)) && QUESTION.matcher(input).matches();
    }

    public static BotWildernessGuide read(ConfigSection config) {
        Map<String, Area> areas = new LinkedHashMap<>();
        ConfigSection worldSettings = config.getSection("worlds", false);
        if (worldSettings != null) {
            for (String world : worldSettings.getKeys(false)) {
                ConfigSection entry = worldSettings.getSection(world, false);
                if (entry == null) continue;
                String path = "worlds." + world + ".wilderness.old-generation-bounds";
                Object raw = entry.get("wilderness.old-generation-bounds");
                if (raw == null || raw instanceof String text && text.isBlank()
                    || raw instanceof List<?> values && values.isEmpty()) continue;
                List<?> values;
                if (raw instanceof String text) values = List.of(text.trim().split("[,;\\s]+"));
                else if (raw instanceof List<?> list) values = list;
                else throw new IllegalArgumentException(path + " must be [x1, z1, x2, z2] or x1,z1;x2,z2");
                if (values.size() != 4) throw new IllegalArgumentException(path + " requires four block coordinates");
                Bounds bounds;
                try {
                    bounds = new Bounds(coordinate(values.get(0)), coordinate(values.get(1)),
                        coordinate(values.get(2)), coordinate(values.get(3)));
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException(path + ": " + invalid.getMessage(), invalid);
                }
                String oldVersion = entry.getString("wilderness.old-version", "26.2").trim();
                String newVersion = entry.getString("wilderness.new-version", "26.3").trim();
                if (oldVersion.isEmpty() || newVersion.isEmpty())
                    throw new IllegalArgumentException("worlds." + world + ".wilderness version labels must not be blank");
                areas.put(world.toLowerCase(Locale.ROOT), new Area(bounds, oldVersion, newVersion));
            }
        }
        Messages defaults = Messages.defaults();
        Messages messages = new Messages(
            config.getString("messages.wilderness.inside", defaults.inside()),
            config.getString("messages.wilderness.outside", defaults.outside()),
            config.getString("messages.wilderness.unconfigured", defaults.unconfigured()),
            config.getString("messages.wilderness.blocked", defaults.blocked()));
        return new BotWildernessGuide(areas, messages);
    }

    public Advice advise(Location player) {
        World world = player.getWorld();
        if (world == null) return new Advice(messages.unconfigured(), null);
        Area area = worlds.get(world.getName().toLowerCase(Locale.ROOT));
        if (area == null) return new Advice(messages.unconfigured(), null);
        if (!area.bounds().contains(player.getX(), player.getZ()))
            return new Advice(format(messages.outside(), area, null), null);
        WorldBorder border = world.getWorldBorder();
        Route route = area.bounds().nearestExit(player.getX(), player.getZ(),
            (x, z) -> border.isInside(new Location(world, x, player.getY(), z)));
        return route == null ? new Advice(format(messages.blocked(), area, null), null)
            : new Advice(format(messages.inside(), area, route), route);
    }

    private static String format(String template, Area area, Route route) {
        String text = template.replace("{old-version}", area.oldVersion())
            .replace("{new-version}", area.newVersion());
        if (route != null) text = text.replace("{direction}", route.direction())
            .replace("{distance}", Long.toString((long) Math.ceil(route.distance())))
            .replace("{x}", Integer.toString((int) Math.floor(route.x())))
            .replace("{z}", Integer.toString((int) Math.floor(route.z())));
        return text;
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
