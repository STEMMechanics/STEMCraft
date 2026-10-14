package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.feature.BaseFeature;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class STEMCraftClassDiscoveryTest {
    @TempDir Path directory;

    @Test
    void aMissingOptionalClassIsReportedAndDoesNotAbortDiscovery() throws Exception {
        String broken = "dev.stemcraft.feature.MissingOptionalIntegration";
        String healthy = HealthyFeature.class.getName();
        Path jar = directory.resolve("features.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String name : List.of(broken, healthy)) {
                output.putNextEntry(new JarEntry(name.replace('.', '/') + ".class"));
                output.closeEntry();
            }
        }
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals(broken)) {
                    throw new NoClassDefFoundError("org/geysermc/cumulus/form/util/FormBuilder");
                }
                return super.loadClass(name);
            }
        };
        STEMCraft plugin = mock(STEMCraft.class, CALLS_REAL_METHODS);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        field(JavaPlugin.class, "file").set(plugin, jar.toFile());
        field(JavaPlugin.class, "classLoader").set(plugin, loader);
        field(STEMCraft.class, "loadFailures").set(plugin, new LinkedHashMap<String, String>());
        List<BaseFeature> loaded = new ArrayList<>();
        var discover = STEMCraft.class.getDeclaredMethod("iterateClasses", String.class, Class.class,
            Consumer.class, Class[].class, Object[].class);
        discover.setAccessible(true);

        assertDoesNotThrow(() -> discover.invoke(plugin, "dev/stemcraft", BaseFeature.class,
            (Consumer<BaseFeature>) loaded::add, new Class<?>[]{STEMCraftAPI.class}, new Object[]{mock(STEMCraftAPI.class)}));

        assertEquals(1, loaded.size());
        assertInstanceOf(HealthyFeature.class, loaded.getFirst());
        assertEquals("NoClassDefFoundError: org/geysermc/cumulus/form/util/FormBuilder", plugin.loadFailures().get(broken));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static final class HealthyFeature extends BaseFeature {
        public HealthyFeature(STEMCraftAPI api) {
            super(api);
        }
    }
}
