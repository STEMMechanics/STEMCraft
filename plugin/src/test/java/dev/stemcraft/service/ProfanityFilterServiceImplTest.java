package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.profanity.ProfanityFilterResult;
import dev.stemcraft.api.service.profanity.ProfanitySeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfanityFilterServiceImplTest {
    private ProfanityFilterServiceImpl service;
    private ConfigFile config;

    @BeforeEach
    void setUp() throws Exception {
        service = new ProfanityFilterServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));

        config = mock(ConfigFile.class);
        ConfigSection substitutions = mock(ConfigSection.class);
        Map<String, Object> values = new HashMap<>();
        values.put("enabled", true);
        values.put("mask_character", "*");
        values.put("matching.allow_separated_letters", true);
        values.put("matching.allowed_suffixes", new ArrayList<>(List.of("s", "es", "ed", "er", "ers", "ing", "ings")));
        values.put("allow", new ArrayList<>(List.of("assignment", "classroom", "aerospace")));
        values.put("false_positives", new ArrayList<>(List.of("assistant", "assessment")));
        values.put("block.mild", new ArrayList<>(List.of("damn")));
        values.put("block.moderate", new ArrayList<>(List.of("asshole", "shit")));
        values.put("block.high", new ArrayList<>(List.of("fuck")));
        values.put("block.extreme", new ArrayList<>(List.of("cunt")));

        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(invocation ->
            (boolean) values.getOrDefault(invocation.getArgument(0), invocation.getArgument(1))
        );
        when(config.getString(anyString(), anyString())).thenAnswer(invocation ->
            String.valueOf(values.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)))
        );
        when(config.getStringList(anyString())).thenAnswer(invocation -> {
            Object value = values.get(invocation.getArgument(0));
            if (value instanceof List<?> list) {
                List<String> strings = new ArrayList<>();
                for (Object entry : list) {
                    strings.add(String.valueOf(entry));
                }
                return strings;
            }
            return List.of();
        });
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(config).set(anyString(), any());
        when(config.getSection("substitutions")).thenReturn(substitutions);

        when(substitutions.getKeys(false)).thenReturn(Set.of("@", "$", "1", "!"));
        when(substitutions.getString("@", "")).thenReturn("a");
        when(substitutions.getString("$", "")).thenReturn("s");
        when(substitutions.getString("1", "")).thenReturn("i");
        when(substitutions.getString("!", "")).thenReturn("i");

        Field field = ProfanityFilterServiceImpl.class.getDeclaredField("configFile");
        field.setAccessible(true);
        field.set(service, config);

        service.reloadSettings();
    }

    @Test
    void detectsObfuscatedProfanityAndMasksIt() {
        ProfanityFilterResult result = service.check("What the f-u-c-k was that?");

        assertTrue(result.offensive());
        assertEquals(ProfanitySeverity.HIGH, result.severity());
        assertTrue(result.matchedWords().contains("fuck"));
        assertEquals("What the ******* was that?", result.cleanedText());
    }

    @Test
    void ignoresConfiguredFalsePositives() {
        ProfanityFilterResult result = service.check("The assignment in this classroom is hard.");

        assertFalse(result.offensive());
        assertEquals("The assignment in this classroom is hard.", result.cleanedText());
    }

    @Test
    void respectsMinimumSeverityThreshold() {
        ProfanityFilterResult result = service.check("damn", ProfanitySeverity.HIGH);

        assertFalse(result.offensive());
        assertEquals("damn", result.cleanedText());
    }

    @Test
    void mutateListAddsAndRemovesWords() {
        assertTrue(service.mutateList("high", "jerkface", true));
        assertTrue(service.listValues("high").contains("jerkface"));

        assertTrue(service.mutateList("high", "jerkface", false));
        assertFalse(service.listValues("high").contains("jerkface"));
    }
}
