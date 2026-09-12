package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.world.generation.GeneratorMapRenderer;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventHandler;
import net.pl3x.map.core.event.EventListener;
import net.pl3x.map.core.event.world.WorldLoadedEvent;
import net.pl3x.map.core.renderer.Renderer;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;

import java.util.Map;

/** Installs generator-owned policies on the basic map without modifying Pl3xMap configuration. */
public final class Pl3xMapGeneratorRenders implements EventListener {
    private final STEMCraftAPI api;
    private final Map<World, Binding> bindings = new java.util.IdentityHashMap<>();
    private boolean enabled;
    private record Binding(Renderer.Builder previous, Renderer.Builder replacement) { }

    /** @param api owning STEMCraft API, used only on the server thread */
    public Pl3xMapGeneratorRenders(STEMCraftAPI api) { this.api = api; }

    /** Attach existing map worlds and subscribe to later loads, including map reloads. */
    public void enable() {
        if (enabled) return;
        enabled = true;
        Pl3xMap.api().getEventRegistry().register(this);
        Pl3xMap.api().getWorldRegistry().forEach(this::attach);
    }

    /** @param event newly loaded Pl3xMap world; defer Bukkit lookups to the server thread */
    @EventHandler public void onWorldLoaded(WorldLoadedEvent event) {
        api.tasks().nextTick(() -> {
            if (enabled && Pl3xMap.api().getWorldRegistry().get(event.getWorld().getName()) == event.getWorld())
                attach(event.getWorld());
        });
    }

    /** @param event unloaded map world whose renderer references can now be released */
    @EventHandler public void onWorldUnloaded(net.pl3x.map.core.event.world.WorldUnloadedEvent event) {
        api.tasks().nextTick(() -> {
            bindings.remove(event.getWorld());
            if (bindings.keySet().stream().noneMatch(world -> world.getName().equals(event.getWorld().getName())))
                GeneratorRenderer.POLICIES.remove(event.getWorld().getName());
        });
    }

    private void attach(World world) {
        if (bindings.containsKey(world)) return;
        org.bukkit.World bukkit = Bukkit.getWorld(world.getName());
        if (bukkit == null) return;
        GeneratorMapRenderer policy = api.worlds().generator().getGeneratedWorld(bukkit)
            .map(generated -> generated.generator().mapRenderer()).orElse(null);
        if (policy == null) return;
        Renderer.Builder previous = world.getRenderers().get("basic");
        // Respect worlds where the administrator has removed the basic renderer.
        if (previous == null) return;
        Renderer.Builder replacement = new Renderer.Builder("basic", policy.displayName(), GeneratorRenderer.class);
        Map<String, Renderer.Builder> renderers = mutableRenderers(world);
        GeneratorRenderer.POLICIES.put(world.getName(), policy);
        renderers.put("basic", replacement);
        bindings.put(world, new Binding(previous, replacement));
    }

    /**
     * Pl3xMap 26.2-554 exposes an unmodifiable view and no renderer mutation API.
     * Keep this version-specific access inside the optional bridge; never mutate the
     * global renderer registry, which would also change unrelated worlds.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Renderer.Builder> mutableRenderers(World world) {
        try {
            var field = World.class.getDeclaredField("renderers");
            if (!field.trySetAccessible())
                throw new IllegalStateException("Cannot access Pl3xMap world renderers for generator maps");
            return (Map<String, Renderer.Builder>) field.get(world);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported Pl3xMap renderer API for generator maps", exception);
        }
    }

    /** Restore renderers and release all event subscriptions and generator references. */
    public void disable() {
        enabled = false;
        new WorldLoadedEvent(null).getHandlers().removeIf(handler -> handler.getListener() == this);
        new net.pl3x.map.core.event.world.WorldUnloadedEvent(null).getHandlers()
            .removeIf(handler -> handler.getListener() == this);
        bindings.forEach((world, binding) -> {
            mutableRenderers(world).replace("basic", binding.replacement(), binding.previous());
            GeneratorRenderer.POLICIES.remove(world.getName());
        });
        bindings.clear();
    }
}
