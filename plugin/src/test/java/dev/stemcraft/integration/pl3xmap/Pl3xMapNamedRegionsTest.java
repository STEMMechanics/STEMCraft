package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.STEMCraft;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventRegistry;
import net.pl3x.map.core.event.server.Pl3xMapEnabledEvent;
import net.pl3x.map.core.event.world.WorldLoadedEvent;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.registry.Registry;
import net.pl3x.map.core.registry.WorldRegistry;
import net.pl3x.map.core.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Pl3xMapNamedRegionsTest {
    @TempDir Path tempDir;
    @Test void registersExistingAndLaterWorldsAndRestoresLayersAfterMapReload() throws Exception {
        var api = api();
        var worlds = new WorldRegistry();
        var events = new EventRegistry();
        when(api.getWorldRegistry()).thenReturn(worlds);
        when(api.getEventRegistry()).thenReturn(events);
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
            Files.newInputStream(Path.of("src/main/resources", invocation.getArgument(0, String.class))));
        World initial = world("survival");
        worlds.register("survival", initial);
        var integration = new Pl3xMapNamedRegions(plugin, List::of,
            publisher(), true, "Biome Regions", "Discovered Structures", 300, 24);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                integration.enable();
                assertLayers(initial);
                assertTrue(Files.readString(web().resolve("index.html")).contains("id=\"stemcraft-regions\""));
                assertTrue(Files.isRegularFile(web().resolve("stemcraft-regions.js")));
                integration.enable();
                assertEquals(1, new WorldLoadedEvent(initial).getHandlers().stream()
                    .filter(handler -> handler.getListener() == integration).count());
                World later = world("survival_deep");
                worlds.register("survival_deep", later);
                events.callEvent(new WorldLoadedEvent(later));
                assertLayers(later);

                initial.getLayerRegistry().unregister();
                later.getLayerRegistry().unregister();
                clearInvocations(api.getIconRegistry());
                Files.writeString(web().resolve("index.html"), "<html><body>Regenerated map</body></html>");
                Files.delete(web().resolve("stemcraft-regions.js"));
                events.callEvent(new Pl3xMapEnabledEvent());
                assertLayers(initial);
                assertLayers(later);
                assertTrue(Files.readString(web().resolve("index.html")).contains("Regenerated map"));
                assertTrue(Files.readString(web().resolve("index.html")).contains("id=\"stemcraft-regions\""));
                assertTrue(Files.isRegularFile(web().resolve("stemcraft-regions.js")));
                verify(api.getIconRegistry()).register(eq("stemcraft_named_structure_generic"), any());

                integration.disable();
                assertEquals(0, initial.getLayerRegistry().size());
                assertEquals(0, later.getLayerRegistry().size());
                events.callEvent(new WorldLoadedEvent(later));
                assertEquals(0, later.getLayerRegistry().size());
                assertTrue(new WorldLoadedEvent(later).getHandlers().stream()
                    .noneMatch(handler -> handler.getListener() == integration));
            } finally { integration.disable(); }
        }
    }

    @Test void neverPublishesBiomeGeometryAndNotifiesOnlyAfterTilePublicationCompletes() throws Exception {
        var api = api();
        var worlds = new WorldRegistry();
        when(api.getWorldRegistry()).thenReturn(worlds);
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
            Files.newInputStream(Path.of("src/main/resources", invocation.getArgument(0, String.class))));
        World world = world("survival");
        worlds.register("survival", world);
        Path legacy = world.getMarkersDirectory().resolve("stemcraft_named_regions.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "old whole-world geometry");
        var tiles = publisher();
        var completions = new ArrayList<LongConsumer>();
        doAnswer(call -> { completions.add(call.getArgument(2)); return null; }).when(tiles).refresh(anyString(), any(), any());
        @SuppressWarnings("unchecked") var areas = (java.util.function.Supplier<java.util.Collection<NamedMapArea>>) mock(java.util.function.Supplier.class);
        var integration = new Pl3xMapNamedRegions(plugin, areas, tiles,
            true, "Regions", "Structures", 0, 24);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                integration.enable();
                assertEquals("[]", Files.readString(legacy));
                Layer layer = world.getLayerRegistry().get("stemcraft_named_regions");
                assertFalse(layer.isLiveUpdate());
                assertTrue(layer.getMarkers().isEmpty());
                assertTrue(layer.getMarkers().isEmpty());
                verifyNoInteractions(areas);
                verifyNoInteractions(world.getServerSentEventHandler());
                completions.getFirst().accept(1L);
                verify(world.getServerSentEventHandler(), times(1)).send("stemcraft-regions", "{\"revision\":1}");
                completions.getLast().accept(2L);
                verify(world.getServerSentEventHandler()).send("stemcraft-regions", "{\"revision\":2}");
                verify(world.getServerSentEventHandler(), never()).send(eq("markers"), anyString());
                integration.disable();
                completions.getLast().accept(3L);
                verify(world.getServerSentEventHandler(), never()).send("stemcraft-regions", "{\"revision\":3}");
                verify(tiles).close();
            } finally { integration.disable(); }
        }
    }

    @Test void canRemoveBiomeLayerWithoutRemovingStructureIcons() throws Exception {
        var api = api();
        var worlds = new WorldRegistry();
        when(api.getWorldRegistry()).thenReturn(worlds);
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
            Files.newInputStream(Path.of("src/main/resources", invocation.getArgument(0, String.class))));
        World world = world("survival");
        worlds.register("survival", world);
        var point = new NamedMapArea.MapPoint(0, 0);
        var integration = new Pl3xMapNamedRegions(plugin, () -> List.of(
            new NamedMapArea("structure:1", "survival", "A Village", "Village", List.of(List.of(point)))),
            publisher(), false, "Regions", "Structures", 300, 24);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                integration.enable();
                assertNull(world.getLayerRegistry().get("stemcraft_named_regions"));
                assertFalse(Files.exists(web().resolve("stemcraft-regions.js")));
                Layer structures = world.getLayerRegistry().get("stemcraft_named_structures");
                assertNotNull(structures);
                assertEquals(1, structures.getMarkers().size());
            } finally { integration.disable(); }
        }
    }

    @Test void allowsAutomaticWebFileInstallationToBeDisabledForIndependentHosting() throws Exception {
        var api = api();
        var worlds = new WorldRegistry();
        when(api.getWorldRegistry()).thenReturn(worlds);
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
            Files.newInputStream(Path.of("src/main/resources", invocation.getArgument(0, String.class))));
        var integration = new Pl3xMapNamedRegions(plugin, List::of, publisher(),
            true, "Regions", "Structures", 300, 24, false);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                integration.enable();
                assertEquals("<html><body>Map</body></html>", Files.readString(web().resolve("index.html")));
                assertFalse(Files.exists(web().resolve("stemcraft-regions.js")));
                api.getEventRegistry().callEvent(new Pl3xMapEnabledEvent());
                assertFalse(Files.exists(web().resolve("stemcraft-regions.js")));
            } finally { integration.disable(); }
        }
    }

    @Test void failedWebFileInstallationDoesNotDisableMapLayersOrStructureIcons() throws Exception {
        var api = api();
        var worlds = new WorldRegistry();
        when(api.getWorldRegistry()).thenReturn(worlds);
        World world = world("survival");
        worlds.register("survival", world);
        Files.delete(web().resolve("index.html"));
        var plugin = mock(STEMCraft.class);
        var logger = mock(java.util.logging.Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
            Files.newInputStream(Path.of("src/main/resources", invocation.getArgument(0, String.class))));
        var integration = new Pl3xMapNamedRegions(plugin, List::of, publisher(),
            true, "Regions", "Structures", 300, 24);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                integration.enable();
                assertLayers(world);
                verify(logger).log(eq(java.util.logging.Level.WARNING), contains("Could not install"), any(java.io.IOException.class));
                verify(api.getIconRegistry()).register(eq("stemcraft_named_structure_generic"), any());
                Files.writeString(web().resolve("index.html"), "<html><body>Restored map</body></html>");
                api.getEventRegistry().callEvent(new Pl3xMapEnabledEvent());
                assertTrue(Files.readString(web().resolve("index.html")).contains("id=\"stemcraft-regions\""));
            } finally { integration.disable(); }
        }
    }

    @Test void worldsOutsideNamedRegionConfigurationDoNotLoadStaleBiomeFiles() throws Exception {
        var api = api();
        var worlds = new WorldRegistry();
        when(api.getWorldRegistry()).thenReturn(worlds);
        World hub = world("hub");
        worlds.register("hub", hub);
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
            Files.newInputStream(Path.of("src/main/resources", invocation.getArgument(0, String.class))));
        var tiles = publisher();
        when(tiles.supports("hub")).thenReturn(false);
        var integration = new Pl3xMapNamedRegions(plugin, List::of, tiles, true, "Regions", "Structures", 300, 24);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                integration.enable();
                assertNull(hub.getLayerRegistry().get("stemcraft_named_regions"));
                assertNotNull(hub.getLayerRegistry().get("stemcraft_named_structures"));
                verify(tiles, never()).refresh(anyString(), any(), any());
            } finally { integration.disable(); }
        }
    }

    private static NamedMapTilePublisher publisher() {
        var publisher = mock(NamedMapTilePublisher.class);
        when(publisher.supports(anyString())).thenReturn(true);
        return publisher;
    }

    private Pl3xMap api() throws Exception {
        var api = mock(Pl3xMap.class, RETURNS_DEEP_STUBS);
        when(api.getMainDir()).thenReturn(tempDir);
        when(api.getEventRegistry()).thenReturn(new EventRegistry());
        Files.createDirectories(web());
        Files.writeString(web().resolve("index.html"), "<html><body>Map</body></html>");
        Files.writeString(web().resolve("pl3xmap.js"), "// Pl3xMap");
        return api;
    }

    private Path web() { return tempDir.resolve("web"); }

    private World world(String name) {
        World world = mock(World.class, RETURNS_DEEP_STUBS);
        when(world.getName()).thenReturn(name);
        when(world.getLayerRegistry()).thenReturn(new Registry<Layer>());
        when(world.getTilesDirectory()).thenReturn(tempDir.resolve(name));
        when(world.getMarkersDirectory()).thenReturn(tempDir.resolve(name).resolve("markers"));
        return world;
    }

    private static void assertLayers(World world) {
        assertNotNull(world.getLayerRegistry().get("stemcraft_named_regions"));
        assertNotNull(world.getLayerRegistry().get("stemcraft_named_structures"));
        assertEquals(2, world.getLayerRegistry().size());
    }
}
