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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Pl3xMapNamedRegionsTest {
    @TempDir Path tempDir;
    @Test void registersExistingAndLaterWorldsAndRestoresLayersAfterMapReload() throws Exception {
        var api = mock(Pl3xMap.class, RETURNS_DEEP_STUBS);
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
            () -> 1L, true, "Biome Regions", "Discovered Structures", 300, 24);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                integration.enable();
                assertLayers(initial);
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
                events.callEvent(new Pl3xMapEnabledEvent());
                assertLayers(initial);
                assertLayers(later);
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

    @Test void neverPublishesBiomeGeometryAndSendsOnlyChangedRevisionNotifications() throws Exception {
        var api = mock(Pl3xMap.class, RETURNS_DEEP_STUBS);
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
        var revision = new AtomicLong(1);
        @SuppressWarnings("unchecked") var areas = (java.util.function.Supplier<java.util.Collection<NamedMapArea>>) mock(java.util.function.Supplier.class);
        var integration = new Pl3xMapNamedRegions(plugin, areas, revision::get,
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
                verify(world.getServerSentEventHandler(), times(1)).send("stemcraft-regions", "{\"revision\":1}");
                revision.incrementAndGet();
                assertTrue(layer.getMarkers().isEmpty());
                verify(world.getServerSentEventHandler()).send("stemcraft-regions", "{\"revision\":2}");
                verify(world.getServerSentEventHandler(), never()).send(eq("markers"), anyString());
            } finally { integration.disable(); }
        }
    }

    @Test void canRemoveBiomeLayerWithoutRemovingStructureIcons() throws Exception {
        var api = mock(Pl3xMap.class, RETURNS_DEEP_STUBS);
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
            () -> 1L, false, "Regions", "Structures", 300, 24);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(api);
            try {
                integration.enable();
                assertNull(world.getLayerRegistry().get("stemcraft_named_regions"));
                Layer structures = world.getLayerRegistry().get("stemcraft_named_structures");
                assertNotNull(structures);
                assertEquals(1, structures.getMarkers().size());
            } finally { integration.disable(); }
        }
    }

    private World world(String name) {
        World world = mock(World.class, RETURNS_DEEP_STUBS);
        when(world.getName()).thenReturn(name);
        when(world.getLayerRegistry()).thenReturn(new Registry<Layer>());
        when(world.getMarkersDirectory()).thenReturn(tempDir.resolve(name).resolve("markers"));
        return world;
    }

    private static void assertLayers(World world) {
        assertNotNull(world.getLayerRegistry().get("stemcraft_named_regions"));
        assertNotNull(world.getLayerRegistry().get("stemcraft_named_structures"));
        assertEquals(2, world.getLayerRegistry().size());
    }
}
