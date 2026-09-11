package dev.stemcraft.service.world;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.world.generation.*;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneratorOwnershipTest {
    private Plugin plugin(String name) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name); when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }
    private GeneratorDefinition definition(String namespace) {
        return new GeneratorDefinition(new NamespacedKey(namespace, "moon"), "Moon", "Test", GeneratorCategory.FLOATING, true, 1);
    }
    @Test void namespacesOwnershipAndDisableAreEnforcedAndRuntimeMetadataSurvives() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var service = new WorldGenerationImpl(mock(STEMCraftAPI.class));
            Plugin owner = plugin("Example"), other = plugin("Other");
            var definition = definition("example");
            ChunkGenerator generator = mock(ChunkGenerator.class);
            service.registerGenerator(owner, definition, options -> generator);
            service.registerGenerator(other, definition("other"), options -> mock(ChunkGenerator.class));
            assertThrows(IllegalArgumentException.class, () -> service.registerGenerator(owner, definition, options -> generator));
            assertThrows(IllegalArgumentException.class, () -> service.registerGenerator(other, definition, options -> generator));
            assertThrows(IllegalArgumentException.class, () -> service.unregisterGenerator(other, definition.key()));
            assertThrows(IllegalArgumentException.class, () -> service.register("example:moon", options -> generator));
            assertSame(generator, service.get("example:moon", ""));
            World world = mock(World.class);
            when(world.getUID()).thenReturn(UUID.randomUUID()); when(world.getGenerator()).thenReturn(generator); when(world.getSeed()).thenReturn(42L);
            assertEquals(definition, service.getGeneratedWorld(world).orElseThrow().generator());
            service.unregisterOwner(owner);
            assertFalse(service.isRegistered("example:moon"));
            assertTrue(service.isRegistered("other:moon"));
            assertTrue(service.isGeneratedWorld(world));
            assertEquals(42, service.getGeneratedWorld(world).orElseThrow().seed());
            assertThrows(UnsupportedOperationException.class, () -> service.getGenerators().clear());
        }
    }
    @Test void disabledOwnersAndOffThreadMutationAreRejected() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            var service = new WorldGenerationImpl(mock(STEMCraftAPI.class));
            Plugin plugin = plugin("Example");
            assertThrows(IllegalStateException.class, () -> service.registerGenerator(plugin, definition("example"), cfg -> mock(ChunkGenerator.class)));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(plugin.isEnabled()).thenReturn(false);
            assertThrows(IllegalArgumentException.class, () -> service.registerGenerator(plugin, definition("example"), cfg -> mock(ChunkGenerator.class)));
        }
    }
    @Test void retainedVersionsResolveTheirOwnFactories() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var service = new WorldGenerationImpl(mock(STEMCraftAPI.class));
            Plugin owner = plugin("Example");
            GeneratorDefinition v1 = definition("example");
            GeneratorDefinition v2 = new GeneratorDefinition(v1.key(), "Moon", "New terrain", v1.category(), true, 2);
            ChunkGenerator oldTerrain = mock(ChunkGenerator.class), newTerrain = mock(ChunkGenerator.class);
            service.registerGenerator(owner, v1, cfg -> oldTerrain);
            service.registerGenerator(owner, v2, cfg -> newTerrain);
            assertEquals(v2, service.getGenerator(v1.key()).orElseThrow());
            assertEquals(v1, service.getGenerator(v1.key(), 1).orElseThrow());
            assertSame(oldTerrain, service.get("example:moon", "", 1));
            assertSame(newTerrain, service.get("example:moon", ""));
            assertThrows(IllegalArgumentException.class, () -> service.get("example:moon", "", 3));
            service.unregisterGenerator(owner, v1.key());
            assertTrue(service.getGenerator(v1.key(), 1).isEmpty());
        }
    }
    @Test void builtinAliasSharesIdentity() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var service = new WorldGenerationImpl(mock(STEMCraftAPI.class));
            service.registerGenerator(plugin("STEMCraft"), definition("stemcraft"), options -> mock(ChunkGenerator.class));
            assertTrue(service.isRegistered("moon")); assertTrue(service.isRegistered("stemcraft:moon"));
            assertEquals(service.definition("moon"), service.definition("stemcraft:moon"));
            assertThrows(IllegalArgumentException.class, () -> service.register("moon", cfg -> mock(ChunkGenerator.class)));
        }
    }
}
