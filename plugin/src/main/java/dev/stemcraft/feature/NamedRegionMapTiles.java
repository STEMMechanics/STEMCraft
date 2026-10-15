package dev.stemcraft.feature;

import com.google.gson.Gson;
import dev.stemcraft.integration.pl3xmap.NamedMapArea.MapPoint;
import dev.stemcraft.integration.pl3xmap.NamedMapFiles;
import dev.stemcraft.integration.pl3xmap.NamedMapTilePublisher;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Publishes bounded vector tiles alongside Pl3xMap's ordinary static map files. */
final class NamedRegionMapTiles implements NamedMapTilePublisher {
    static final int TILE_CELLS = 32;
    static final int SAMPLE_BLOCKS = 16;
    static final int MAX_LEVEL = 19;
    private static final int BOUNDARY_BLOCKS = 4;
    private static final int WORLD_LIMIT = 30_000_000;
    private static final Gson GSON = new Gson();
    private static final Pattern LEVEL = Pattern.compile("[0-9]+");
    private static final Pattern TILE_FILE = Pattern.compile("-?[0-9]+_-?[0-9]+\\.json");

    record Region(String id, String name, String type) {}
    record Style(int strokeColour, int fillColour, int lineThickness) {}
    @FunctionalInterface interface Lookup {
        @Nullable Region at(String world, int blockX, int blockZ);
    }

    private final Set<String> worlds;
    private final LongSupplier revision;
    private final Lookup lookup;
    private final Function<String, Set<Long>> baseTiles;
    private final List<Style> styles;
    private final boolean permanentLabels;
    private final int refreshSeconds;
    private final Consumer<Runnable> async;
    private final LongSupplier clock;
    private final Logger logger;
    private final Map<Target, State> states = new HashMap<>();
    private volatile boolean closed;
    private record Target(String world, Path directory) {}
    private static final class State {
        boolean busy;
        long revision = Long.MIN_VALUE;
        long builtAt;
    }

    NamedRegionMapTiles(Set<String> worlds, LongSupplier revision, Lookup lookup,
                        Function<String, Set<Long>> baseTiles, List<Style> styles,
                        boolean permanentLabels, int refreshSeconds, Consumer<Runnable> async,
                        Logger logger) {
        this(worlds, revision, lookup, baseTiles, styles, permanentLabels, refreshSeconds,
            async, logger, System::currentTimeMillis);
    }

    NamedRegionMapTiles(Set<String> worlds, LongSupplier revision, Lookup lookup,
                        Function<String, Set<Long>> baseTiles, List<Style> styles,
                        boolean permanentLabels, int refreshSeconds, Consumer<Runnable> async,
                        Logger logger, LongSupplier clock) {
        this.worlds = Set.copyOf(worlds);
        this.revision = revision;
        this.lookup = lookup;
        this.baseTiles = baseTiles;
        this.styles = List.copyOf(styles);
        this.permanentLabels = permanentLabels;
        this.refreshSeconds = refreshSeconds;
        this.async = async;
        this.logger = logger;
        this.clock = clock;
    }

    @Override public boolean supports(String world) { return worlds.contains(world.toLowerCase(Locale.ROOT)); }

    @Override public synchronized void refresh(String world, Path directory, LongConsumer published) {
        if (closed || !supports(world)) return;
        var target = new Target(world, directory.toAbsolutePath().normalize());
        State state = states.computeIfAbsent(target, ignored -> new State());
        if (state.busy) return;
        long current = revision.getAsLong(), now = clock.getAsLong();
        boolean exists = Files.isRegularFile(target.directory.resolve("metadata.json"));
        if (exists && (state.revision == current || state.revision != Long.MIN_VALUE
            && now < state.builtAt + refreshSeconds * 1000L)) return;
        state.busy = true;
        try {
            async.accept(() -> publish(target, state, current, published));
        } catch (RuntimeException exception) {
            state.busy = false;
            throw exception;
        }
    }

    private void publish(Target target, State state, long current, LongConsumer published) {
        try {
            if (!active()) return;
            Set<Long> coordinates = new HashSet<>(baseTiles.apply(target.world));
            Set<Path> expected = new HashSet<>();
            for (int level = 0; level <= MAX_LEVEL && !coordinates.isEmpty(); level++) {
                Set<Long> parents = new HashSet<>();
                for (long packed : coordinates) {
                    if (!active()) return;
                    int x = (int) (packed >> 32), z = (int) packed;
                    Map<String, Object> geometry = tile(target.world, level, x, z);
                    if (!active()) return;
                    Path file = Path.of(level + "/" + x + "_" + z + ".json");
                    write(target.directory.resolve(file), geometry);
                    expected.add(file);
                    parents.add(NamedRegions.pack(Math.floorDiv(x, 2), Math.floorDiv(z, 2)));
                }
                coordinates = parents;
            }
            if (!active()) return;
            removeObsoleteTiles(target.directory, expected);
            if (!active()) return;
            // Publish metadata last: its revision tells clients that the new tiles are ready.
            write(target.directory.resolve("metadata.json"), metadata(current));
            synchronized (this) {
                if (closed) return;
                state.revision = current;
                state.builtAt = clock.getAsLong();
            }
            published.accept(current);
        } catch (IOException | RuntimeException exception) {
            if (!closed) logger.log(Level.WARNING, "Could not publish named-region map tiles for " + target.world, exception);
        } finally {
            synchronized (this) { state.busy = false; }
        }
    }

    private boolean active() { return !closed && !Thread.currentThread().isInterrupted(); }
    @Override public synchronized void close() { closed = true; states.clear(); }

    private void removeObsoleteTiles(Path directory, Set<Path> expected) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.walk(directory)) {
            var iterator = files.iterator();
            while (active() && iterator.hasNext()) {
                Path file = iterator.next(), relative = directory.relativize(file);
                if (relative.getNameCount() == 2 && LEVEL.matcher(relative.getName(0).toString()).matches()
                    && TILE_FILE.matcher(relative.getFileName().toString()).matches()
                    && Files.isRegularFile(file) && !expected.contains(relative)) Files.deleteIfExists(file);
            }
        }
    }

    Map<String, Object> metadata(long current) {
        return Map.of("revision", current, "tileCells", TILE_CELLS,
            "sampleBlocks", SAMPLE_BLOCKS, "maxLevel", MAX_LEVEL,
            "refreshSeconds", refreshSeconds, "styles", styles,
            "permanentLabels", permanentLabels);
    }

    Map<String, Object> tile(String world, int level, int tileX, int tileZ) {
        int scale = SAMPLE_BLOCKS << level;
        int minX = tileX * TILE_CELLS * scale, minZ = tileZ * TILE_CELLS * scale;
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
                .map(NamedRegionMapTiles::simplify)
                .map(ring -> ring.stream().map(point -> List.of(
                    minX + point.x() / BOUNDARY_BLOCKS * scale,
                    minZ + point.z() / BOUNDARY_BLOCKS * scale)).toList()).toList();
            List<Object> outlines = new ArrayList<>();
            for (var ring : NamedRegions.boundaryLoops(entry.getValue())) {
                var simple = simplify(ring);
                for (int i = 0; i < simple.size(); i++) {
                    MapPoint a = simple.get(i), b = simple.get((i + 1) % simple.size());
                    int limit = TILE_CELLS * BOUNDARY_BLOCKS;
                    if (Math.max(a.x(), b.x()) < 0 || Math.min(a.x(), b.x()) > limit
                        || Math.max(a.z(), b.z()) < 0 || Math.min(a.z(), b.z()) > limit) continue;
                    int ax = Math.clamp(a.x(), 0, limit), az = Math.clamp(a.z(), 0, limit);
                    int bx = Math.clamp(b.x(), 0, limit), bz = Math.clamp(b.z(), 0, limit);
                    if (ax == bx && az == bz) continue;
                    outlines.add(List.of(List.of(minX + ax / BOUNDARY_BLOCKS * scale, minZ + az / BOUNDARY_BLOCKS * scale),
                        List.of(minX + bx / BOUNDARY_BLOCKS * scale, minZ + bz / BOUNDARY_BLOCKS * scale)));
                }
            }
            regions.add(Map.of("id", region.id(), "name", region.name(), "type", region.type(),
                "rings", rings, "outlines", outlines));
        }
        return Map.of("level", level,
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

    private static void write(Path path, Object value) throws IOException {
        NamedMapFiles.writeIfChanged(path, GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
    }
}
