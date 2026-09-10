package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.service.world.WorldGenerationImpl;
import dev.stemcraft.service.world.WorldServiceImpl;
import org.bukkit.generator.ChunkGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class STEMCraftWorldGeneratorTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void generatorDiscoveryAcceptsMissingIdWithInitializedRegistry(String id) throws Exception {
        WorldGenerationImpl generation = new WorldGenerationImpl(mock(STEMCraftAPI.class));

        assertNull(pluginWithRegistry(generation).getDefaultWorldGenerator("world", id));
    }

    @Test
    void explicitGeneratorIdStillResolves() throws Exception {
        WorldGenerationImpl generation = new WorldGenerationImpl(mock(STEMCraftAPI.class));
        ChunkGenerator generator = mock(ChunkGenerator.class);
        generation.register("test", options -> generator);

        assertSame(generator, pluginWithRegistry(generation).getDefaultWorldGenerator("world", "test"));
    }

    private STEMCraft pluginWithRegistry(WorldGenerationImpl generation) throws Exception {
        STEMCraft plugin = mock(STEMCraft.class, CALLS_REAL_METHODS);
        WorldServiceImpl worlds = mock(WorldServiceImpl.class);
        when(worlds.generator()).thenReturn(generation);
        var field = STEMCraft.class.getDeclaredField("worlds");
        field.setAccessible(true);
        field.set(plugin, worlds);
        return plugin;
    }
}
