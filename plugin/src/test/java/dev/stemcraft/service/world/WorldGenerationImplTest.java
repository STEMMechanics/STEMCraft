package dev.stemcraft.service.world;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class WorldGenerationImplTest {
    @Test
    void listIncludesOnlyRegisteredGeneratorsAndPluginsThatProvideDefaultGenerators() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        WorldGenerationImpl generation = new WorldGenerationImpl(api);
        generation.register("void", options -> mock(ChunkGenerator.class));

        Plugin plainPlugin = plugin("Vault", true, null);
        Plugin generatorPlugin = plugin("PlotSquared", true, mock(ChunkGenerator.class));
        Plugin disabledGeneratorPlugin = plugin("DisabledGen", false, mock(ChunkGenerator.class));
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugins()).thenReturn(new Plugin[] {
            plainPlugin,
            generatorPlugin,
            disabledGeneratorPlugin
        });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            assertEquals(List.of("PlotSquared", "void"), generation.list());
        }
    }

    @Test
    void resolvesExternalPluginGeneratorWithOptionalId() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        WorldGenerationImpl generation = new WorldGenerationImpl(api);
        ChunkGenerator chunkGenerator = mock(ChunkGenerator.class);
        Plugin generatorPlugin = plugin("PlotSquared", true, chunkGenerator);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("PlotSquared")).thenReturn(generatorPlugin);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            Optional<ChunkGenerator> resolved = generation.resolveExternal("challenge_treehouse", "PlotSquared", "classic");

            assertTrue(resolved.isPresent());
            assertSame(chunkGenerator, resolved.orElseThrow());
            assertTrue(generation.isAvailable("PlotSquared:classic", "challenge_treehouse"));
        }
    }

    @Test
    void unavailableExternalGeneratorIsNotAvailable() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        WorldGenerationImpl generation = new WorldGenerationImpl(api);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin(any())).thenReturn(null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            assertTrue(generation.resolveExternal("world", "MissingPlugin", "").isEmpty());
            assertFalse(generation.isAvailable("MissingPlugin", "world"));
        }
    }

    private Plugin plugin(String name, boolean enabled, ChunkGenerator generator) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.isEnabled()).thenReturn(enabled);
        when(plugin.getDefaultWorldGenerator(any(), eq(null))).thenReturn(generator);
        when(plugin.getDefaultWorldGenerator(any(), eq("classic"))).thenReturn(generator);
        return plugin;
    }
}
