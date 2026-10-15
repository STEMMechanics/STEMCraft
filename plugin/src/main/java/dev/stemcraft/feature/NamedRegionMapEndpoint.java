package dev.stemcraft.feature;

import com.google.gson.Gson;
import dev.stemcraft.api.service.web.WebServiceEndpointHandler;
import dev.stemcraft.api.service.web.WebServiceRequest;
import dev.stemcraft.integration.pl3xmap.NamedMapArea.MapPoint;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded, progressively sampled vector tiles. Never traverses the world's cell index. */
final class NamedRegionMapEndpoint implements WebServiceEndpointHandler {
    static final String PATH = "/map/named-regions";
    static final int TILE_CELLS = 32;
    static final int SAMPLE_BLOCKS = 4;
    static final int MAX_LEVEL = 19;
    private static final int WORLD_LIMIT = 30_000_000;
    private static final Gson GSON = new Gson();

    record Region(String id, String name, String type) {}
    record Style(int strokeColour, int fillColour, int lineThickness) {}
    @FunctionalInterface interface Lookup {
        @Nullable Region at(String world, int blockX, int blockZ);
    }

    private final Set<String> worlds;
    private final BooleanSupplier enabled;
    private final LongSupplier revision;
    private final Lookup lookup;
    private final List<Style> styles;
    private final boolean permanentLabels;
    private final int refreshSeconds;

    NamedRegionMapEndpoint(Set<String> worlds, BooleanSupplier enabled, LongSupplier revision,
                           Lookup lookup, List<Style> styles, boolean permanentLabels,
                           int refreshSeconds) {
        this.worlds = Set.copyOf(worlds);
        this.enabled = enabled;
        this.revision = revision;
        this.lookup = lookup;
        this.styles = List.copyOf(styles);
        this.permanentLabels = permanentLabels;
        this.refreshSeconds = refreshSeconds;
    }

    @Override public Object handle(WebServiceRequest request) {
        if (!request.path().equals(PATH)) return response(404, Map.of("error", "Unknown endpoint"));
        if (!request.method().equals("GET")) return response(405, Map.of("error", "Use GET"));
        if (!enabled.getAsBoolean()) return response(503, Map.of("error", "Region overlay is disabled"));
        String world = request.queryParam("world");
        if (world == null || !worlds.contains(world.toLowerCase(Locale.ROOT)))
            return response(404, Map.of("error", "Unknown named-region world"));

        if (request.queryParam("level") == null) {
            if (request.queryParam("x") != null || request.queryParam("z") != null)
                return response(400, Map.of("error", "Specify level, x and z together"));
            return response(200, Map.of("revision", revision.getAsLong(), "tileCells", TILE_CELLS,
                "sampleBlocks", SAMPLE_BLOCKS, "maxLevel", MAX_LEVEL,
                "refreshSeconds", refreshSeconds, "styles", styles,
                "permanentLabels", permanentLabels));
        }
        try {
            int level = Integer.parseInt(request.queryParam("level"));
            int x = Integer.parseInt(request.queryParam("x"));
            int z = Integer.parseInt(request.queryParam("z"));
            if (level < 0 || level > MAX_LEVEL) throw new IllegalArgumentException();
            int scale = SAMPLE_BLOCKS << level;
            long span = (long) scale * TILE_CELLS;
            long minX = x * span, minZ = z * span;
            if (minX >= WORLD_LIMIT || minX + span <= -WORLD_LIMIT
                || minZ >= WORLD_LIMIT || minZ + span <= -WORLD_LIMIT)
                throw new IllegalArgumentException();
            return response(200, tile(world, level, (int) minX, (int) minZ, scale));
        } catch (IllegalArgumentException exception) {
            return response(400, Map.of("error", "Invalid region tile coordinates"));
        }
    }

    private Map<String, Object> tile(String world, int level, int minX, int minZ, int scale) {
        Map<Region, Set<Long>> cells = new LinkedHashMap<>();
        // A one-cell halo distinguishes real outlines from artificial tile seams.
        for (int z = -1; z <= TILE_CELLS; z++)
            for (int x = -1; x <= TILE_CELLS; x++) {
                Region region = sample(world, minX + x * scale, minZ + z * scale, scale);
                if (region != null)
                    cells.computeIfAbsent(region, ignored -> new HashSet<>()).add(NamedRegions.pack(x, z));
            }
        List<Object> regions = new ArrayList<>();
        for (var entry : cells.entrySet()) {
            Region region = entry.getKey();
            Set<Long> core = new HashSet<>();
            for (long cell : entry.getValue()) {
                int x = (int) (cell >> 32), z = (int) cell;
                if (x >= 0 && x < TILE_CELLS && z >= 0 && z < TILE_CELLS) core.add(cell);
            }
            if (core.isEmpty()) continue;
            var rings = NamedRegions.boundaryLoops(core).stream()
                .map(NamedRegionMapEndpoint::simplify)
                .map(ring -> ring.stream().map(point -> List.of(
                    minX + point.x() / SAMPLE_BLOCKS * scale,
                    minZ + point.z() / SAMPLE_BLOCKS * scale)).toList()).toList();
            List<Object> outlines = new ArrayList<>();
            for (var ring : NamedRegions.boundaryLoops(entry.getValue())) {
                var simple = simplify(ring);
                for (int i = 0; i < simple.size(); i++) {
                    MapPoint a = simple.get(i), b = simple.get((i + 1) % simple.size());
                    int limit = TILE_CELLS * SAMPLE_BLOCKS;
                    if (Math.max(a.x(), b.x()) < 0 || Math.min(a.x(), b.x()) > limit
                        || Math.max(a.z(), b.z()) < 0 || Math.min(a.z(), b.z()) > limit) continue;
                    int ax = Math.clamp(a.x(), 0, limit), az = Math.clamp(a.z(), 0, limit);
                    int bx = Math.clamp(b.x(), 0, limit), bz = Math.clamp(b.z(), 0, limit);
                    if (ax == bx && az == bz) continue;
                    outlines.add(List.of(List.of(minX + ax / SAMPLE_BLOCKS * scale, minZ + az / SAMPLE_BLOCKS * scale),
                        List.of(minX + bx / SAMPLE_BLOCKS * scale, minZ + bz / SAMPLE_BLOCKS * scale)));
                }
            }
            regions.add(Map.of("id", region.id(), "name", region.name(), "type", region.type(),
                "rings", rings, "outlines", outlines));
        }
        return Map.of("revision", revision.getAsLong(), "level", level,
            "bounds", List.of(minX, minZ, minX + scale * TILE_CELLS, minZ + scale * TILE_CELLS),
            "regions", regions);
    }

    private @Nullable Region sample(String world, int x, int z, int scale) {
        if (scale == SAMPLE_BLOCKS) return at(world, x + scale / 2, z + scale / 2);
        // Five fixed probes bound work at every zoom. Unknown terrain participates in the vote.
        Map<Region, Integer> votes = new HashMap<>();
        int quarter = scale / 4, half = scale / 2;
        for (int dx : new int[]{quarter, scale - quarter})
            for (int dz : new int[]{quarter, scale - quarter})
                votes.merge(at(world, x + dx, z + dz), 1, Integer::sum);
        votes.merge(at(world, x + half, z + half), 1, Integer::sum);
        Region winner = null;
        int count = votes.getOrDefault(null, 0);
        for (var vote : votes.entrySet())
            if (vote.getKey() != null && (vote.getValue() > count
                || vote.getValue() == count && winner != null
                && vote.getKey().id().compareTo(winner.id()) < 0)) {
                winner = vote.getKey();
                count = vote.getValue();
            }
        return winner;
    }

    private @Nullable Region at(String world, int x, int z) {
        return Math.abs((long) x) >= WORLD_LIMIT || Math.abs((long) z) >= WORLD_LIMIT
            ? null : lookup.at(world, x, z);
    }

    /** Remove only collinear points: holes and the sampled outline retain their topology. */
    static List<MapPoint> simplify(List<MapPoint> ring) {
        List<MapPoint> result = new ArrayList<>();
        for (int i = 0; i < ring.size(); i++) {
            MapPoint a = ring.get(Math.floorMod(i - 1, ring.size()));
            MapPoint b = ring.get(i);
            MapPoint c = ring.get((i + 1) % ring.size());
            if ((b.x() - a.x()) * (c.z() - b.z()) != (b.z() - a.z()) * (c.x() - b.x()))
                result.add(b);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> response(int code, Object body) {
        return Map.of("responseCode", code, "contentType", "application/json; charset=utf-8",
            "headers", Map.of("Cache-Control", "no-store", "X-Content-Type-Options", "nosniff"),
            "body", GSON.toJson(body));
    }
}
