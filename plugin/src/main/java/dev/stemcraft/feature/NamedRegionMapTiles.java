package dev.stemcraft.feature;

import com.google.gson.Gson;
import dev.stemcraft.integration.pl3xmap.NamedMapArea;
import dev.stemcraft.integration.pl3xmap.NamedMapFiles;
import dev.stemcraft.integration.pl3xmap.NamedMapTilePublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Publishes the native full-region polygons as viewport-selected static files. */
final class NamedRegionMapTiles implements NamedMapTilePublisher {
    private static final int SAMPLE_BLOCKS = 4;
    private static final Gson GSON = new Gson();

    record Style(int strokeColour, int fillColour, int lineThickness) { }
    @FunctionalInterface interface Source { Collection<NamedMapArea> regions(String world); }

    private record Target(String world, Path directory) { }
    private record RegionFeature(String id, String name, String type, List<List<List<Integer>>> rings) { }
    private record RegionIndex(String id, String file, List<Integer> bounds,
                               String name, String type, String contentHash) { }

    private final Set<String> worlds;
    private final Source source;
    private final List<Style> styles;
    private final boolean permanentLabels;
    private final Consumer<Runnable> async;
    private final Logger logger;
    private final String revision = UUID.randomUUID().toString().replace("-", "");
    private final Queue<Target> queue = new ConcurrentLinkedQueue<>();
    private final Set<Target> queued = new HashSet<>();
    private final Set<Target> inProgress = new HashSet<>();
    private final Map<Target, String> published = new HashMap<>();
    private boolean workerScheduled;
    private volatile boolean closed;

    NamedRegionMapTiles(Set<String> worlds, Source source, List<Style> styles,
                        boolean permanentLabels, Consumer<Runnable> async, Logger logger) {
        this.worlds = worlds.stream().map(name -> name.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.source = source;
        this.styles = List.copyOf(styles);
        this.permanentLabels = permanentLabels;
        this.async = async;
        this.logger = logger;
    }

    @Override public boolean supports(String world) {
        return worlds.contains(world.toLowerCase(Locale.ROOT));
    }

    @Override public void refresh(String world, Path directory) {
        if (closed || !supports(world)) return;
        Target target = new Target(world, directory.toAbsolutePath().normalize());
        boolean schedule = false;
        synchronized (this) {
            if (closed || Objects.equals(published.get(target), revision)
                || inProgress.contains(target) || !queued.add(target)) return;
            queue.add(target);
            if (!workerScheduled) {
                workerScheduled = true;
                schedule = true;
            }
        }
        if (!schedule) return;
        try {
            async.accept(this::drain);
        } catch (RuntimeException exception) {
            synchronized (this) {
                workerScheduled = false;
                queued.clear();
                queue.clear();
            }
            logger.log(Level.WARNING, "Could not schedule named-region polygon snapshot generation", exception);
        }
    }

    private void drain() {
        while (!closed && !Thread.currentThread().isInterrupted()) {
            Target target;
            synchronized (this) {
                target = queue.poll();
                if (target == null) {
                    workerScheduled = false;
                    return;
                }
                queued.remove(target);
                inProgress.add(target);
            }
            try {
                publish(target);
                synchronized (this) {
                    if (!closed) published.put(target, revision);
                }
            } catch (IOException | RuntimeException exception) {
                logger.log(Level.WARNING,
                    "Could not publish full named-region polygons for " + target.world(), exception);
            } finally {
                synchronized (this) { inProgress.remove(target); }
            }
        }
        synchronized (this) {
            workerScheduled = false;
            queue.clear();
            queued.clear();
            inProgress.clear();
        }
    }

    private void publish(Target target) throws IOException {
        if (!active()) return;
        clearLegacyTileLayout(target.directory());
        Path snapshots = target.directory().resolve("snapshots");
        Path snapshot = snapshots.resolve(revision);
        List<RegionIndex> indexRegions = new ArrayList<>();

        List<NamedMapArea> regions = new ArrayList<>(source.regions(target.world()));
        regions.removeIf(area -> area.id().startsWith("structure:") || area.boundaries().isEmpty());
        regions.sort(Comparator.comparing(NamedMapArea::id));

        for (NamedMapArea area : regions) {
            if (!active()) return;
            Bounds bounds = bounds(area);
            if (bounds == null) continue;
            String fileName = UUID.nameUUIDFromBytes(area.id().getBytes(StandardCharsets.UTF_8)) + ".json";
            String relativeFile = "regions/" + fileName;
            Path relativePath = Path.of("regions", fileName);
            List<List<List<Integer>>> rings = area.boundaries().stream()
                .map(NamedRegionMapTiles::canonicalRing)
                .sorted(Comparator.comparingInt((List<List<Integer>> ring) -> ring.getFirst().get(0))
                    .thenComparingInt(ring -> ring.getFirst().get(1)).thenComparingInt(List::size))
                .toList();
            byte[] featureJson = GSON.toJson(new RegionFeature(area.id(), area.name(), area.type(), rings))
                .getBytes(StandardCharsets.UTF_8);
            // featureJson is already serialized JSON; write its UTF-8 bytes directly.
            // Passing it through write(Path, Object) would Gson-serialize byte[] as a
            // numeric array, so the browser would receive [123, 34, ...] instead of
            // the region object.
            NamedMapFiles.writeIfChanged(snapshot.resolve(relativePath), featureJson);
            indexRegions.add(new RegionIndex(area.id(), relativeFile, bounds.toList(), area.name(), area.type(),
                contentHash(featureJson)));
        }

        if (!active()) return;
        write(snapshot.resolve("index.json"), Map.of("revision", revision, "regions", indexRegions));
        // Switch the browser to this completed snapshot only after all full polygons are written.
        write(target.directory().resolve("metadata.json"), metadata("snapshots/" + revision));
        try {
            removeOldSnapshots(snapshots, revision);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not remove old named-region snapshots for " + target.world(), exception);
        }
    }

    private static Bounds bounds(NamedMapArea area) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (List<NamedMapArea.MapPoint> ring : area.boundaries()) {
            for (NamedMapArea.MapPoint point : ring) {
                minX = Math.min(minX, point.x());
                minZ = Math.min(minZ, point.z());
                maxX = Math.max(maxX, point.x());
                maxZ = Math.max(maxZ, point.z());
            }
        }
        return minX == Integer.MAX_VALUE ? null : new Bounds(minX, minZ, maxX, maxZ);
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
        List<Integer> toList() { return List.of(minX, minZ, maxX, maxZ); }
    }

    private Map<String, Object> metadata(String snapshot) {
        return Map.of("revision", revision, "sampleBlocks", SAMPLE_BLOCKS,
            "styles", styles, "permanentLabels", permanentLabels, "snapshot", snapshot);
    }

    private static String contentHash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static List<List<Integer>> canonicalRing(List<NamedMapArea.MapPoint> ring) {
        if (ring.isEmpty()) return List.of();
        int start = 0;
        for (int index = 1; index < ring.size(); index++) {
            NamedMapArea.MapPoint candidate = ring.get(index), current = ring.get(start);
            if (candidate.x() < current.x() || candidate.x() == current.x() && candidate.z() < current.z())
                start = index;
        }
        List<List<Integer>> points = new ArrayList<>(ring.size());
        for (int offset = 0; offset < ring.size(); offset++) {
            NamedMapArea.MapPoint point = ring.get((start + offset) % ring.size());
            points.add(List.of(point.x(), point.z()));
        }
        return List.copyOf(points);
    }

    private static void clearLegacyTileLayout(Path directory) throws IOException {
        Files.deleteIfExists(directory.resolve("index.json"));
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (!Files.isDirectory(entry) || !entry.getFileName().toString().matches("-?[0-9]+")) continue;
                deleteTree(entry);
            }
        }
    }

    private static void removeOldSnapshots(Path directory, String current) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!Files.isDirectory(entry) || name.equals(current)
                    || !name.matches("[0-9a-f]{32}")) continue;
                deleteTree(entry);
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void write(Path path, Object value) throws IOException {
        NamedMapFiles.writeIfChanged(path, GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    private boolean active() { return !closed && !Thread.currentThread().isInterrupted(); }

    @Override public synchronized void close() {
        closed = true;
        queue.clear();
        queued.clear();
        inProgress.clear();
    }
}
