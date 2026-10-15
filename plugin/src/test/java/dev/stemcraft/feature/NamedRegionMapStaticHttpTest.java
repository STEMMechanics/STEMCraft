package dev.stemcraft.feature;

import com.google.gson.JsonParser;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.integration.pl3xmap.Pl3xMapNamedRegions;
import libs.io.undertow.Undertow;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.configuration.Config;
import net.pl3x.map.core.event.EventRegistry;
import net.pl3x.map.core.httpd.HttpdServer;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.registry.Registry;
import net.pl3x.map.core.registry.WorldRegistry;
import net.pl3x.map.core.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise the actual Pl3xMap static server, with no STEMCraft web server or proxy route. */
class NamedRegionMapStaticHttpTest {
    @TempDir Path web;

    @Test void pluginInstallsClientAndPublishesFilesServedByPl3xMapsExistingWebServer() throws Exception {
        var api = mock(Pl3xMap.class, RETURNS_DEEP_STUBS);
        var worlds = new WorldRegistry();
        when(api.getWorldRegistry()).thenReturn(worlds);
        when(api.getEventRegistry()).thenReturn(new EventRegistry());
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource(anyString())).thenAnswer(call ->
            getClass().getResourceAsStream("/" + call.getArgument(0, String.class)));
        Files.writeString(web.resolve("index.html"), "<html><body><div id='map'></div></body></html>");
        Files.writeString(web.resolve("pl3xmap.js"), "// generated map client");
        World world = mock(World.class, RETURNS_DEEP_STUBS);
        when(world.getName()).thenReturn("survival");
        when(world.getLayerRegistry()).thenReturn(new Registry<Layer>());
        when(world.getTilesDirectory()).thenReturn(web.resolve("tiles/survival"));
        when(world.getMarkersDirectory()).thenReturn(web.resolve("tiles/survival/markers"));
        worlds.register("survival", world);
        var tiles = new NamedRegionMapTiles(Set.of("survival"), () -> 7L,
            (name, x, z) -> new NamedRegionMapTiles.Region("region:forest", "Greenwood", "Forest"),
            name -> Set.of(NamedRegions.pack(0, 0)),
            List.of(new NamedRegionMapTiles.Style(0xff008000, 0x40008000, 2)),
            false, 300, Runnable::run, mock(Logger.class));
        var integration = new Pl3xMapNamedRegions(plugin, List::of, tiles,
            true, "Regions", "Structures", 300, 24);

        String previousDirectory = Config.WEB_DIR, previousBind = Config.HTTPD_BIND;
        int previousPort = Config.HTTPD_PORT;
        boolean previousEnabled = Config.HTTPD_ENABLED;
        var server = new HttpdServer();
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                Config.WEB_DIR = web.toString();
                Config.HTTPD_ENABLED = true;
                Config.HTTPD_BIND = "127.0.0.1";
                Config.HTTPD_PORT = 0;
                integration.enable();
                server.startServer();
                var field = HttpdServer.class.getDeclaredField("server");
                field.setAccessible(true);
                Undertow running = (Undertow) field.get(server);
                assertNotNull(running, "Pl3xMap's web server must start");
                int port = ((InetSocketAddress) running.getListenerInfo().getFirst().getAddress()).getPort();
                try (HttpClient client = HttpClient.newHttpClient()) {
                    String base = "http://127.0.0.1:" + port;
                    var index = get(client, base + "/index.html");
                    assertEquals(200, index.statusCode());
                    assertTrue(index.body().contains("id=\"stemcraft-regions\""));
                    assertFalse(index.body().contains("data-endpoint="));
                    var script = get(client, base + "/stemcraft-regions.js");
                    assertEquals(200, script.statusCode());
                    assertTrue(script.body().contains("metadata.json"));
                    var metadata = get(client, base + "/tiles/survival/stemcraft-regions/metadata.json?v=7");
                    assertEquals(200, metadata.statusCode());
                    assertTrue(metadata.headers().firstValue("Cache-Control").orElse("").contains("no-cache"));
                    assertEquals(7, JsonParser.parseString(metadata.body()).getAsJsonObject().get("revision").getAsLong());
                    var geometry = get(client, base + "/tiles/survival/stemcraft-regions/0/0_0.json?v=7");
                    assertEquals(200, geometry.statusCode());
                    assertEquals("Greenwood", JsonParser.parseString(geometry.body()).getAsJsonObject()
                        .getAsJsonArray("regions").get(0).getAsJsonObject().get("name").getAsString());
                    assertEquals("[]", get(client, base + "/tiles/survival/markers/stemcraft_named_regions.json").body());
                    assertEquals(404, get(client, base + "/stemcraft/named-regions").statusCode());
                }
            } finally {
                server.stopServer();
                integration.disable();
                Config.WEB_DIR = previousDirectory;
                Config.HTTPD_BIND = previousBind;
                Config.HTTPD_PORT = previousPort;
                Config.HTTPD_ENABLED = previousEnabled;
            }
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
