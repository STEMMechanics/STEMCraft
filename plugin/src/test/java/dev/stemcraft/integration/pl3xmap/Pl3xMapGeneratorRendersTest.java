package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.world.generation.GeneratedWorld;
import dev.stemcraft.api.service.world.generation.GeneratorDefinition;
import dev.stemcraft.chunkgen.generator.DeepGenerator;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventRegistry;
import net.pl3x.map.core.event.world.WorldLoadedEvent;
import net.pl3x.map.core.event.world.WorldUnloadedEvent;
import net.pl3x.map.core.registry.WorldRegistry;
import net.pl3x.map.core.renderer.BasicRenderer;
import net.pl3x.map.core.renderer.Renderer;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Pl3xMapGeneratorRendersTest {
    @Test void attachesThroughReadOnlyViewAndRestoresAfterResetAndDisable() throws Exception {
        var api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        var map = mock(Pl3xMap.class);
        var worlds = new WorldRegistry();
        var events = new EventRegistry();
        when(map.getWorldRegistry()).thenReturn(worlds);
        when(map.getEventRegistry()).thenReturn(events);
        var tasks = api.tasks();
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(tasks).nextTick(any(Runnable.class));
        var bukkit = mock(org.bukkit.World.class);
        var definition = mock(GeneratorDefinition.class);
        var policy = DeepGenerator.mapRenderer();
        when(definition.mapRenderer()).thenReturn(policy);
        when(api.worlds().generator().getGeneratedWorld(bukkit)).thenReturn(
            Optional.of(new GeneratedWorld(UUID.randomUUID(), definition, 1)));
        var original = new Renderer.Builder("basic", "Basic", BasicRenderer.class);
        var initial = world("survival_deep", original);
        var ordinary = world("survival", original);
        var withoutBasic = world("disabled_basic", null);
        worlds.register("survival_deep", initial);
        worlds.register("survival", ordinary);
        worlds.register("disabled_basic", withoutBasic);
        var bridge = new Pl3xMapGeneratorRenders(api);
        try (var mapAccess = mockStatic(Pl3xMap.class); var bukkitAccess = mockStatic(Bukkit.class)) {
            mapAccess.when(Pl3xMap::api).thenReturn(map);
            bukkitAccess.when(() -> Bukkit.getWorld("survival_deep")).thenReturn(bukkit);
            bukkitAccess.when(() -> Bukkit.getWorld("disabled_basic")).thenReturn(bukkit);
            try {
                assertThrows(UnsupportedOperationException.class,
                    () -> initial.getRenderers().put("basic", original));
                bridge.enable();
                assertEquals(GeneratorRenderer.class, initial.getRenderers().get("basic").getClazz());
                assertSame(policy, GeneratorRenderer.POLICIES.get("survival_deep"));
                assertSame(original, ordinary.getRenderers().get("basic"));
                assertTrue(withoutBasic.getRenderers().isEmpty());
                assertFalse(GeneratorRenderer.POLICIES.containsKey("disabled_basic"));

                // Map reset replaces the world and queues attachment again.
                events.callEvent(new WorldUnloadedEvent(initial));
                assertFalse(GeneratorRenderer.POLICIES.containsKey("survival_deep"));
                var reloaded = world("survival_deep", original);
                worlds.register("survival_deep", reloaded);
                events.callEvent(new WorldLoadedEvent(reloaded));
                assertEquals(GeneratorRenderer.class, reloaded.getRenderers().get("basic").getClazz());
                bridge.disable();
                assertSame(original, reloaded.getRenderers().get("basic"));
                assertFalse(GeneratorRenderer.POLICIES.containsKey("survival_deep"));
                assertTrue(new WorldLoadedEvent(reloaded).getHandlers().stream()
                    .noneMatch(handler -> handler.getListener() == bridge));
            } finally {
                bridge.disable();
            }
        }
    }

    private static World world(String name, Renderer.Builder basic) throws Exception {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        var renderers = new LinkedHashMap<String, Renderer.Builder>();
        if (basic != null) renderers.put("basic", basic);
        var field = World.class.getDeclaredField("renderers");
        field.setAccessible(true);
        field.set(world, renderers);
        // Exercise the dependency's real unmodifiable getter, not a mutable mock.
        when(world.getRenderers()).thenCallRealMethod();
        return world;
    }
}
