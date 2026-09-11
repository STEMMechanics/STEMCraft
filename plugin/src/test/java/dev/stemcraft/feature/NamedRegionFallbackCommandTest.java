package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NamedRegionFallbackCommandTest {
    @Test void wildcardListsTheSameFallbacksAsNoFilterAndPreservesTypeFiltering() throws Exception {
        NamedRegions feature = new NamedRegions(mock(STEMCraftAPI.class));
        addArea(feature, "mine", "mineshaft", true);
        addArea(feature, "forest", "forest", true);
        addArea(feature, "named-mine", "mineshaft", false);

        List<String> unfiltered = execute(feature, "");
        assertEquals(unfiltered, execute(feature, "*"));
        assertEquals(unfiltered, execute(feature, "  *  "));
        assertTrue(unfiltered.contains("Fallback-named regions (2):"));
        assertTrue(unfiltered.stream().anyMatch(line -> line.contains("[mine]")));
        assertTrue(unfiltered.stream().anyMatch(line -> line.contains("[forest]")));
        assertFalse(unfiltered.stream().anyMatch(line -> line.contains("[named-mine]")));

        List<String> mines = execute(feature, "MINESHAFT");
        assertTrue(mines.contains("Fallback-named Mineshaft regions (1):"));
        assertTrue(mines.stream().anyMatch(line -> line.contains("[mine]")));
        assertFalse(mines.stream().anyMatch(line -> line.contains("[forest]")));
    }

    private List<String> execute(NamedRegions feature, String filter) throws Exception {
        CommandContext context = mock(CommandContext.class);
        when(context.getArg(0, "info")).thenReturn("fallbacks");
        when(context.getArgsAsString(1, "")).thenReturn(filter);
        List<String> output = new ArrayList<>();
        doAnswer(call -> { output.add(call.getArgument(0)); return null; })
            .when(context).info(anyString(), any(Object[].class));
        Method command = NamedRegions.class.getDeclaredMethod("executeCommand", CommandContext.class);
        command.setAccessible(true);
        command.invoke(feature, context);
        return output;
    }

    @SuppressWarnings("unchecked")
    private void addArea(NamedRegions feature, String id, String type, boolean fallback) throws Exception {
        Method naming = NamedRegions.class.getDeclaredMethod("fallbackName", String.class, String.class);
        naming.setAccessible(true);
        String name = fallback ? (String) naming.invoke(null, type, id) : "The Coppervein Diggings";
        Class<?> area = Class.forName(NamedRegions.class.getName() + "$Area");
        Constructor<?> constructor = area.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object record = constructor.newInstance(id, "survival", "structure", type, name,
            0, 0, 0, 15, 15, 15, false, 0L, true);
        var field = NamedRegions.class.getDeclaredField("areas");
        field.setAccessible(true);
        ((Map<String, Object>) field.get(feature)).put(id, record);
    }
}
