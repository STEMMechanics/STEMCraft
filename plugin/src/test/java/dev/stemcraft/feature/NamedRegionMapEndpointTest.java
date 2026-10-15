package dev.stemcraft.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NamedRegionMapEndpointTest {
    private static final NamedRegionMapEndpoint.Region FOREST = new NamedRegionMapEndpoint.Region("region:forest", "Greenwood", "Forest");

    @Test void metadataContainsOnlyRevisionAndRenderingSettings() {
        var calls = new AtomicInteger();
        var endpoint = endpoint((world, x, z) -> { calls.incrementAndGet(); return FOREST; });
        var response = handle(endpoint, Map.of("world", "survival"));
        JsonObject body = body(response);
        assertEquals(42, body.get("revision").getAsLong());
        assertEquals(32, body.get("tileCells").getAsInt());
        assertEquals(0, calls.get());
        assertFalse(body.has("regions"));
        assertEquals("no-store", ((Map<?, ?>) response.get("headers")).get("Cache-Control"));
    }

    @Test void combinesUniformTileIntoFourCornersWithoutDrawingTileSeams() {
        var calls = new AtomicInteger();
        JsonObject body = body(handle(endpoint((world, x, z) -> { calls.incrementAndGet(); return FOREST; }),
            tile(0, 0, 0)));
        assertEquals(34 * 34, calls.get());
        var regions = body.getAsJsonArray("regions");
        assertEquals(1, regions.size());
        var region = regions.get(0).getAsJsonObject();
        assertEquals("Greenwood", region.get("name").getAsString());
        assertEquals(4, region.getAsJsonArray("rings").get(0).getAsJsonArray().size());
        assertTrue(region.getAsJsonArray("outlines").isEmpty());
        assertEquals(List.of(0, 0, 128, 128), ints(body.getAsJsonArray("bounds")));
    }

    @Test void preservesHolesAndRealBoundariesAlongTileEdges() {
        var endpoint = endpoint((world, x, z) -> x >= 0 && x < 128 && z >= 0 && z < 128
            && !(x >= 40 && x < 80 && z >= 40 && z < 80) ? FOREST : null);
        var region = body(handle(endpoint, tile(0, 0, 0))).getAsJsonArray("regions").get(0).getAsJsonObject();
        assertEquals(2, region.getAsJsonArray("rings").size());
        assertEquals(8, region.getAsJsonArray("outlines").size());
    }

    @Test void negativeTilesStayInsideTheirOwnBounds() {
        var response = handle(endpoint((world, x, z) -> FOREST), tile(0, -1, -2));
        var body = body(response);
        assertEquals(List.of(-128, -256, 0, -128), ints(body.getAsJsonArray("bounds")));
        for (var region : body.getAsJsonArray("regions"))
            for (var ring : region.getAsJsonObject().getAsJsonArray("rings"))
                for (var point : ring.getAsJsonArray()) {
                    var coordinates = ints(point.getAsJsonArray());
                    assertTrue(coordinates.get(0) >= -128 && coordinates.get(0) <= 0);
                    assertTrue(coordinates.get(1) >= -256 && coordinates.get(1) <= -128);
                }
    }

    @Test void zoomingOutKeepsSamplingWorkBoundedAndUnknownTerrainParticipatesInVote() {
        var calls = new AtomicInteger();
        var endpoint = endpoint((world, x, z) -> { calls.incrementAndGet(); return x % 64 == 16 ? FOREST : null; });
        var body = body(handle(endpoint, tile(4, 0, 0)));
        assertEquals(5 * 34 * 34, calls.get());
        assertTrue(body.getAsJsonArray("regions").isEmpty());
    }

    @Test void evenWorstCaseFragmentationHasBoundedGeometryAndPayload() {
        var response = handle(endpoint((world, x, z) -> new NamedRegionMapEndpoint.Region(
            "region:" + x + ":" + z, "Example Forest", "Forest")), tile(0, 0, 0));
        JsonObject body = body(response);
        assertEquals(32 * 32, body.getAsJsonArray("regions").size());
        assertTrue(response.get("body").toString().length() < 400_000);
        for (var region : body.getAsJsonArray("regions"))
            assertEquals(4, region.getAsJsonObject().getAsJsonArray("rings").get(0).getAsJsonArray().size());
    }

    @Test void rejectsUnboundedOrMalformedQueriesBeforeReadingStorage() {
        var calls = new AtomicInteger();
        var endpoint = endpoint((world, x, z) -> { calls.incrementAndGet(); return FOREST; });
        assertEquals(404, handle(endpoint, Map.of("world", "private-world")).get("responseCode"));
        for (var query : List.of(tile(-1, 0, 0), tile(20, 0, 0), tile(0, Integer.MAX_VALUE, 0),
            Map.of("world", "survival", "level", "NaN", "x", "0", "z", "0"),
            Map.of("world", "survival", "level", "0"), Map.of("world", "survival", "x", "0")))
            assertEquals(400, handle(endpoint, query).get("responseCode"));
        assertEquals(0, calls.get());
    }

    @Test void disablingOverlayAlsoDisablesItsDataEndpoint() {
        var endpoint = new NamedRegionMapEndpoint(Set.of("survival"), () -> false, () -> 1L,
            (world, x, z) -> { fail("disabled endpoint must not read cells"); return null; },
            List.of(new NamedRegionMapEndpoint.Style(0xff000000, 0x40000000, 2)), false, 300);
        assertEquals(503, handle(endpoint, tile(0, 0, 0)).get("responseCode"));
        var post = new WebServiceRequest("POST", "", NamedRegionMapEndpoint.PATH, Map.of(), Map.of(), new byte[0], "test");
        assertEquals(405, ((Map<?, ?>) endpoint.handle(post)).get("responseCode"));
    }

    private static NamedRegionMapEndpoint endpoint(NamedRegionMapEndpoint.Lookup lookup) {
        return new NamedRegionMapEndpoint(Set.of("survival"), () -> true, () -> 42L, lookup,
            List.of(new NamedRegionMapEndpoint.Style(0xff000000, 0x40000000, 2)), false, 300);
    }
    private static Map<String, String> tile(int level, int x, int z) {
        return Map.of("world", "survival", "level", "" + level, "x", "" + x, "z", "" + z);
    }
    private static Map<?, ?> handle(NamedRegionMapEndpoint endpoint, Map<String, String> query) {
        return (Map<?, ?>) endpoint.handle(new WebServiceRequest("GET", "", NamedRegionMapEndpoint.PATH,
            query, Map.of(), new byte[0], "test"));
    }
    private static JsonObject body(Map<?, ?> response) {
        assertEquals(200, response.get("responseCode"), response.get("body").toString());
        return JsonParser.parseString(response.get("body").toString()).getAsJsonObject();
    }
    private static List<Integer> ints(com.google.gson.JsonArray array) {
        return array.asList().stream().map(com.google.gson.JsonElement::getAsInt).toList();
    }
}
