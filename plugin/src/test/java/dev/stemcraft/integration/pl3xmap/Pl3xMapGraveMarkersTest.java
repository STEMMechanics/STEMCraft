package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.STEMCraft;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.registry.Registry;
import net.pl3x.map.core.registry.WorldRegistry;
import net.pl3x.map.core.world.World;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Pl3xMapGraveMarkersTest {
    @Test
    void reusesUnchangedMarkersAndPublishesCreatedAndRemovedGraves() throws Exception {
        var map = mock(Pl3xMap.class, RETURNS_DEEP_STUBS);
        var worlds = new WorldRegistry();
        World world = mock(World.class);
        when(world.getName()).thenReturn("survival");
        when(world.getLayerRegistry()).thenReturn(new Registry<Layer>());
        worlds.register("survival", world);
        when(map.getWorldRegistry()).thenReturn(worlds);
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource(anyString())).thenAnswer(_ ->
                Files.newInputStream(Path.of("src/main/resources/pl3xmap/gravestone.png")));
        var grave = new GraveMapMarker(UUID.randomUUID(), UUID.randomUUID(), "Project_HSI", "survival", 2, 64, 3);
        var otherWorld = new GraveMapMarker(UUID.randomUUID(), UUID.randomUUID(), "Remi", "other", 9, 64, 9);
        var snapshot = new AtomicReference<Collection<GraveMapMarker>>(List.of(grave, otherWorld));
        var integration = new Pl3xMapGraveMarkers(plugin, snapshot::get);
        try (var access = mockStatic(Pl3xMap.class)) {
            access.when(Pl3xMap::api).thenReturn(map);
            try {
                integration.enable();
                Layer layer = world.getLayerRegistry().get("stemcraft_graves");
                var first = layer.getMarkers();
                var second = layer.getMarkers();
                assertSame(first, second, "Unchanged graves must not rebuild marker objects every poll");
                assertEquals(1, first.size());
                assertEquals(10, layer.getUpdateInterval());
                snapshot.set(List.of(grave, new GraveMapMarker(UUID.randomUUID(), UUID.randomUUID(),
                        "Remi", "survival", 4, 64, 5)));
                var added = layer.getMarkers();
                assertNotSame(first, added);
                assertEquals(2, added.size());
                snapshot.set(List.of());
                assertTrue(layer.getMarkers().isEmpty());
                assertSame(layer.getMarkers(), layer.getMarkers());
            } finally {
                integration.disable();
            }
            assertEquals(0, world.getLayerRegistry().size());
        }
    }
}
