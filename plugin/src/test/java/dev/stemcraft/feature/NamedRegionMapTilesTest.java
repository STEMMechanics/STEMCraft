package dev.stemcraft.feature;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamedRegionMapTilesTest {
    @TempDir Path directory;
    private static final Gson GSON = new Gson();
    private static final NamedRegionMapTiles.Region FOREST = new NamedRegionMapTiles.Region("region:forest", "Greenwood", "Forest");
    private static final List<NamedRegionMapTiles.Style> STYLES = List.of(new NamedRegionMapTiles.Style(0xff000000, 0x40000000, 2));

    @Test void metadataContainsOnlyRevisionAndRenderingSettings() {
        var calls = new AtomicInteger();
        var tiles = tiles((world, x, z) -> { calls.incrementAndGet(); return FOREST; });
        JsonObject body = json(tiles.metadata(42));
        assertEquals(42, body.get("revision").getAsLong());
        assertEquals(32, body.get("tileCells").getAsInt());
        assertEquals(16, body.get("sampleBlocks").getAsInt());
        assertEquals(0, calls.get());
        assertFalse(body.has("regions"));
    }

    @Test void combinesUniformTileIntoFourCornersWithoutDrawingTileSeams() {
        var calls = new AtomicInteger();
        JsonObject body = json(tiles((world, x, z) -> { calls.incrementAndGet(); return FOREST; }).tile("survival", 0, 0, 0));
        assertEquals(34 * 34, calls.get());
        var regions = body.getAsJsonArray("regions");
        assertEquals(1, regions.size());
        var region = regions.get(0).getAsJsonObject();
        assertEquals("Greenwood", region.get("name").getAsString());
        assertEquals(4, region.getAsJsonArray("rings").get(0).getAsJsonArray().size());
        assertTrue(region.getAsJsonArray("outlines").isEmpty());
        assertEquals(List.of(0, 0, 512, 512), ints(body.getAsJsonArray("bounds")));
    }

    @Test void preservesHolesAndRealBoundariesAlongTileEdges() {
        var tiles = tiles((world, x, z) -> x >= 0 && x < 512 && z >= 0 && z < 512
            && !(x >= 160 && x < 320 && z >= 160 && z < 320) ? FOREST : null);
        var region = json(tiles.tile("survival", 0, 0, 0)).getAsJsonArray("regions").get(0).getAsJsonObject();
        assertEquals(2, region.getAsJsonArray("rings").size());
        assertEquals(8, region.getAsJsonArray("outlines").size());
    }

    @Test void negativeTilesStayInsideTheirOwnBounds() {
        var body = json(tiles((world, x, z) -> FOREST).tile("survival", 0, -1, -2));
        assertEquals(List.of(-512, -1024, 0, -512), ints(body.getAsJsonArray("bounds")));
        for (var region : body.getAsJsonArray("regions"))
            for (var ring : region.getAsJsonObject().getAsJsonArray("rings"))
                for (var point : ring.getAsJsonArray()) {
                    var coordinates = ints(point.getAsJsonArray());
                    assertTrue(coordinates.get(0) >= -512 && coordinates.get(0) <= 0);
                    assertTrue(coordinates.get(1) >= -1024 && coordinates.get(1) <= -512);
                }
    }

    @Test void zoomingOutKeepsSamplingWorkBoundedAndUnknownTerrainParticipatesInVote() {
        var calls = new AtomicInteger();
        var tiles = tiles((world, x, z) -> { calls.incrementAndGet(); return x % 256 == 64 ? FOREST : null; });
        var body = json(tiles.tile("survival", 4, 0, 0));
        assertEquals(5 * 34 * 34, calls.get());
        assertTrue(body.getAsJsonArray("regions").isEmpty());
    }

    @Test void evenWorstCaseFragmentationHasBoundedGeometryAndPayload() {
        var body = json(tiles((world, x, z) -> new NamedRegionMapTiles.Region(
            "region:" + x + ":" + z, "Example Forest", "Forest")).tile("survival", 0, 0, 0));
        assertEquals(32 * 32, body.getAsJsonArray("regions").size());
        assertTrue(body.toString().length() < 400_000);
        for (var region : body.getAsJsonArray("regions"))
            assertEquals(4, region.getAsJsonObject().getAsJsonArray("rings").get(0).getAsJsonArray().size());
    }

    @Test void publishesStaticFilesInBackgroundThenNotifiesClientsAndThrottlesChanges() throws Exception {
        var jobs = new ArrayList<Runnable>();
        var revision = new AtomicLong(42);
        var now = new AtomicLong(1000);
        var tiles = new NamedRegionMapTiles(Set.of("survival"), revision::get, (world, x, z) -> FOREST,
            world -> Set.of(NamedRegions.pack(0, 0)), STYLES, false, 300, jobs::add, mock(Logger.class), now::get);
        var notifications = new ArrayList<Long>();
        tiles.refresh("survival", directory, value -> {
            assertTrue(Files.isRegularFile(directory.resolve("metadata.json")));
            assertTrue(Files.isRegularFile(directory.resolve("0/0_0.json")));
            notifications.add(value);
        });
        tiles.refresh("survival", directory, notifications::add);
        assertEquals(1, jobs.size());
        assertFalse(Files.exists(directory.resolve("metadata.json")));
        jobs.removeFirst().run();
        assertEquals(List.of(42L), notifications);
        assertEquals(42, read("metadata.json").get("revision").getAsLong());
        assertFalse(read("0/0_0.json").has("revision"));
        Path geometry = directory.resolve("0/0_0.json");
        var timestamp = FileTime.fromMillis(1000);
        Files.setLastModifiedTime(geometry, timestamp);
        revision.incrementAndGet();
        tiles.refresh("survival", directory, notifications::add);
        assertTrue(jobs.isEmpty());
        now.addAndGet(300_000);
        tiles.refresh("survival", directory, notifications::add);
        assertEquals(1, jobs.size());
        jobs.removeFirst().run();
        assertEquals(List.of(42L, 43L), notifications);
        assertEquals(timestamp, Files.getLastModifiedTime(geometry));
        tiles.refresh("survival", directory, notifications::add);
        assertTrue(jobs.isEmpty());
    }

    @Test void distantDiscoveriesDoNotGenerateTheUndiscoveredGapBetweenThem() throws Exception {
        var tiles = new NamedRegionMapTiles(Set.of("survival"), () -> 1L, (world, x, z) -> FOREST,
            world -> Set.of(NamedRegions.pack(-50_000, -50_000), NamedRegions.pack(50_000, 50_000)),
            STYLES, false, 300, Runnable::run, mock(Logger.class));
        tiles.refresh("survival", directory, ignored -> {});
        assertTrue(Files.isRegularFile(directory.resolve("0/-50000_-50000.json")));
        assertTrue(Files.isRegularFile(directory.resolve("0/50000_50000.json")));
        assertFalse(Files.exists(directory.resolve("0/0_0.json")));
        try (var files = Files.walk(directory)) {
            assertTrue(files.filter(Files::isRegularFile).count() <= 2 * (NamedRegionMapTiles.MAX_LEVEL + 1) + 1);
        }
        assertTrue(read("metadata.json").toString().length() < 1000);
    }

    @Test void closingCancelsQueuedPublicationAndOtherWorldsNeverReadStorage() throws Exception {
        var jobs = new ArrayList<Runnable>();
        var calls = new AtomicInteger();
        var tiles = new NamedRegionMapTiles(Set.of("survival"), () -> 1L,
            (world, x, z) -> { calls.incrementAndGet(); return FOREST; },
            world -> Set.of(NamedRegions.pack(0, 0)), STYLES, false, 300, jobs::add, mock(Logger.class));
        tiles.refresh("private-world", directory, ignored -> fail());
        assertTrue(jobs.isEmpty());
        tiles.refresh("survival", directory, ignored -> fail());
        tiles.close();
        jobs.removeFirst().run();
        assertEquals(0, calls.get());
        assertFalse(Files.exists(directory.resolve("metadata.json")));
    }

    @Test void failedTileWriteKeepsMetadataUnpublishedAndCanBeRetried() throws Exception {
        Files.createDirectories(directory.resolve("1/0_0.json"));
        var logger = mock(Logger.class);
        var tiles = new NamedRegionMapTiles(Set.of("survival"), () -> 1L, (world, x, z) -> FOREST,
            world -> Set.of(NamedRegions.pack(0, 0)), STYLES, false, 300, Runnable::run, logger);
        var notifications = new ArrayList<Long>();
        tiles.refresh("survival", directory, notifications::add);
        assertTrue(notifications.isEmpty());
        assertFalse(Files.exists(directory.resolve("metadata.json")));
        verify(logger).log(eq(java.util.logging.Level.WARNING), contains("Could not publish"), any(Exception.class));
        Files.delete(directory.resolve("1/0_0.json"));
        tiles.refresh("survival", directory, notifications::add);
        assertEquals(List.of(1L), notifications);
        assertEquals(1, read("metadata.json").get("revision").getAsLong());
    }

    @Test void emptyDiscoveryPublishesOnlySmallMetadataWithoutSamplingTerrain() throws Exception {
        var tiles = new NamedRegionMapTiles(Set.of("survival"), () -> 1L,
            (world, x, z) -> { fail("No discovered chunks to sample"); return null; },
            world -> Set.of(), STYLES, false, 300, Runnable::run, mock(Logger.class));
        tiles.refresh("survival", directory, ignored -> {});
        assertTrue(Files.isRegularFile(directory.resolve("metadata.json")));
        assertFalse(Files.exists(directory.resolve("0")));
    }

    @Test void removingDiscoveryDeletesOnlyObsoleteOwnedTilesBeforePublishingNewMetadata() throws Exception {
        var coordinates = new HashSet<>(Set.of(NamedRegions.pack(0, 0), NamedRegions.pack(-1, -1)));
        var revision = new AtomicLong(1);
        var now = new AtomicLong(1000);
        var tiles = new NamedRegionMapTiles(Set.of("survival"), revision::get, (world, x, z) -> FOREST,
            world -> coordinates, STYLES, false, 300, Runnable::run, mock(Logger.class), now::get);
        tiles.refresh("survival", directory, ignored -> {});
        assertTrue(Files.exists(directory.resolve("0/-1_-1.json")));
        Path unrelated = directory.resolve("0/notes.json");
        Files.writeString(unrelated, "keep this file");
        coordinates.remove(NamedRegions.pack(-1, -1));
        revision.incrementAndGet(); now.addAndGet(300_000);
        tiles.refresh("survival", directory, ignored -> {
            assertFalse(Files.exists(directory.resolve("0/-1_-1.json")));
            assertTrue(Files.exists(directory.resolve("0/0_0.json")));
        });
        assertEquals(2, read("metadata.json").get("revision").getAsLong());
        assertEquals("keep this file", Files.readString(unrelated));
        coordinates.clear(); revision.incrementAndGet(); now.addAndGet(300_000);
        tiles.refresh("survival", directory, ignored -> {});
        assertFalse(Files.exists(directory.resolve("0/0_0.json")));
        assertEquals(3, read("metadata.json").get("revision").getAsLong());
        assertTrue(Files.exists(unrelated));
    }

    private static NamedRegionMapTiles tiles(NamedRegionMapTiles.Lookup lookup) {
        return new NamedRegionMapTiles(Set.of("survival"), () -> 42L, lookup,
            world -> Set.of(), STYLES, false, 300, Runnable::run, mock(Logger.class));
    }
    private JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(directory.resolve(path))).getAsJsonObject();
    }
    private static JsonObject json(Object value) { return GSON.toJsonTree(value).getAsJsonObject(); }
    private static List<Integer> ints(com.google.gson.JsonArray array) {
        return array.asList().stream().map(com.google.gson.JsonElement::getAsInt).toList();
    }
}
