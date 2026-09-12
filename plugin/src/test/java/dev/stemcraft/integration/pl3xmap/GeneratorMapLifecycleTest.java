package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.world.generation.*;
import dev.stemcraft.chunkgen.generator.DeepGenerator;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventRegistry;
import net.pl3x.map.core.event.world.WorldLoadedEvent;
import net.pl3x.map.core.registry.WorldRegistry;
import net.pl3x.map.core.renderer.Renderer;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneratorMapLifecycleTest {
    @Test void onlyGeneratorWorldsAreChangedAndReloadedWorldsGetTheirOwnBinding() {
        var api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        var map = mock(Pl3xMap.class, RETURNS_DEEP_STUBS);
        var registry = new WorldRegistry();
        when(map.getWorldRegistry()).thenReturn(registry);
        when(map.getEventRegistry()).thenReturn(new EventRegistry());
        var bukkitWorld = mock(org.bukkit.World.class);
        var definition = new GeneratorDefinition(new NamespacedKey("stemcraft", "deep"), "Deep", "Caves",
            GeneratorCategory.CAVE, true, 1, DeepGenerator.mapRenderer());
        when(api.worlds().generator().getGeneratedWorld(bukkitWorld)).thenReturn(
            Optional.of(new GeneratedWorld(UUID.randomUUID(), definition, 1)));
        var tasks = api.tasks();
        doAnswer(invocation -> { invocation.getArgument(0, Runnable.class).run(); return null; }).when(tasks).nextTick(any());
        World first = world("deep");
        World ordinary = world("ordinary");
        registry.register("deep", first);
        registry.register("ordinary", ordinary);
        var original = first.getRenderers().get("basic");
        try (var access = mockStatic(Pl3xMap.class); var bukkit = mockStatic(Bukkit.class)) {
            access.when(Pl3xMap::api).thenReturn(map);
            bukkit.when(() -> Bukkit.getWorld("deep")).thenReturn(bukkitWorld);
            var bridge = new Pl3xMapGeneratorRenders(api);
            try {
                bridge.enable();
                assertEquals(GeneratorRenderer.class, first.getRenderers().get("basic").getClazz());
                assertEquals(net.pl3x.map.core.renderer.BasicRenderer.class, ordinary.getRenderers().get("basic").getClazz());
                World reloaded = world("deep");
                registry.register("deep", reloaded);
                map.getEventRegistry().callEvent(new WorldLoadedEvent(reloaded));
                assertEquals(GeneratorRenderer.class, reloaded.getRenderers().get("basic").getClazz());
            } finally { bridge.disable(); }
            assertSame(original, first.getRenderers().get("basic"));
            assertTrue(GeneratorRenderer.POLICIES.isEmpty());
        }
    }
    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        Map<String, Renderer.Builder> renderers = new HashMap<>();
        renderers.put("basic", new Renderer.Builder("basic", "Basic", net.pl3x.map.core.renderer.BasicRenderer.class));
        when(world.getRenderers()).thenReturn(renderers);
        return world;
    }
}
