package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.config.ConfigFileImpl;
import dev.stemcraft.config.ConfigSectionImpl;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamedRegionNameExpansionTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"desert", "forest", "jungle", "ocean", "mountains", "snow", "swamp",
        "badlands", "savanna", "taiga", "mushroom-fields", "plains", "mineshaft", "village",
        "shipwreck", "ruined-portal", "default", "fortress", "stronghold", "ocean-ruins",
        "abandoned-camp-dappled-forest", "future-structure"})
    void addsAtLeast800NewDistinctNamesBeyondEachLegacyPool(String type) {
        var yaml = defaults();
        var config = section(yaml);
        List<String> expanded = NamedRegions.generatedNames(config, type);
        yaml.set("named-regions.names.additional-sources", null);
        List<String> legacy = NamedRegions.generatedNames(config, type);
        assertTrue(expanded.containsAll(legacy), type + " must preserve all existing candidates");

        Set<String> additions = normalised(expanded);
        assertEquals(expanded.size(), additions.size(), type + " must deduplicate names ignoring case");
        additions.removeAll(normalised(legacy));
        assertTrue(additions.size() >= 800, type + " only adds " + additions.size() + " new names");
        assertTrue(expanded.stream().allMatch(name -> name.split("\\s+").length <= NamedRegions.nameWordLimit(type)),
            type + " must retain its biome/structure word limit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"forest", "plains"})
    @SuppressWarnings("unchecked")
    void exhaustedLegacyPoolStillAssignsANaturalName(String type) throws Exception {
        var yaml = defaults();
        var config = section(yaml);
        var expanded = normalised(NamedRegions.generatedNames(config, type));
        var additions = yaml.getConfigurationSection("named-regions.names.additional-sources").getValues(false);
        yaml.set("named-regions.names.additional-sources", null);
        var legacy = normalised(NamedRegions.generatedNames(config, type));
        yaml.createSection("named-regions.names.additional-sources", additions);

        var feature = spy(new NamedRegions(mock(STEMCraftAPI.class)));
        doReturn(config).when(feature).getConfigSection();
        var activeField = NamedRegions.class.getDeclaredField("activeNames");
        activeField.setAccessible(true);
        ((Set<String>) activeField.get(feature)).addAll(legacy);
        var nameMethod = NamedRegions.class.getDeclaredMethod("name", String.class, String.class);
        nameMethod.setAccessible(true);
        String id = "region:old-pool-exhausted";
        String name = (String) nameMethod.invoke(feature, type, id);

        assertFalse(NamedRegions.isFallbackName(name, type, id));
        assertTrue(expanded.contains(name.toLowerCase(Locale.ROOT)));
        assertFalse(legacy.contains(name.toLowerCase(Locale.ROOT)));
    }

    @Test
    void additionalRootsRespectCustomFormsDefaultFallbackAndExplicitOptOuts() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            named-regions:
              names:
                sources:
                  forest: [Customwood]
                  default: [Customstone]
                additional-sources:
                  forest: [Newwood]
                  default: [Newstone]
                forms:
                  forest: ['{root} Grove']
                  default: ['{root} Hold']
            """);
        var config = section(yaml);
        var forest = NamedRegions.generatedNames(config, "forest");
        assertTrue(forest.contains("Customwood Grove"));
        assertTrue(forest.contains("Newwood Grove"));
        assertFalse(forest.contains("Newstone Grove"));
        assertTrue(NamedRegions.generatedNames(config, "fortress").contains("Newstone Hold"));
        yaml.set("named-regions.names.additional-sources.forest", List.of());
        forest = NamedRegions.generatedNames(config, "forest");
        assertTrue(forest.contains("Customwood Grove"));
        assertFalse(forest.contains("Newwood Grove"));
        assertFalse(forest.contains("Newstone Grove"));
        assertEquals(List.of("Customwood"), config.getStringList("names.sources.forest"));
        assertEquals(List.of("{root} Grove"), config.getStringList("names.forms.forest"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"named-regions", "named_regions", "features.named-regions", "features.named_regions"})
    void installsMissingAdditionsIntoOlderConfigWithoutReplacingCustomNamingSettings(String path) {
        ConfigFileImpl file = spy(new ConfigFileImpl());
        assertTrue(file.load(tempDir.toFile(), "config.yml", true));
        String names = path + ".names.";
        file.set(names + "sources.forest", List.of("Customwood"));
        file.set(names + "forms.forest", List.of("{root} Grove"));
        file.set(names + "pools.forest", List.of("Staff Forest"));
        file.set(names + "retirement-days", 12);
        file.save();
        clearInvocations(file);
        var api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        when(api.config().load("config.yml")).thenReturn(file);
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource("config.yml")).thenAnswer(invocation -> getClass().getResourceAsStream("/config.yml"));
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var feature = new NamedRegions(api);
        try (var pluginAccess = mockStatic(STEMCraft.class)) {
            pluginAccess.when(STEMCraft::getPlugin).thenReturn(plugin);
            feature.restoreMissingNameConfiguration();
            assertFalse(file.getStringList(names + "additional-sources.forest").isEmpty());
            assertEquals(path.replace('_', '-'), feature.getResolvedConfigPath());
            assertTrue(file.getKeys(true).contains(names + "additional-sources.forest"));
            if (!path.equals("named-regions")) assertFalse(file.getKeys(false).contains("named-regions"));
            verify(file).save();
            feature.restoreMissingNameConfiguration();
            verify(file, times(1)).save();
            file.set(names + "additional-sources.forest", List.of());
            feature.restoreMissingNameConfiguration();
            assertTrue(file.getStringList(names + "additional-sources.forest").isEmpty());
        }

        ConfigFileImpl persisted = new ConfigFileImpl();
        assertTrue(persisted.load(tempDir.toFile(), "config.yml", false));
        assertTrue(persisted.getKeys(true).contains(names + "additional-sources.forest"));
        assertFalse(persisted.getStringList(names + "additional-sources.forest").isEmpty());
        assertEquals(List.of("Customwood"), persisted.getStringList(names + "sources.forest"));
        assertEquals(List.of("{root} Grove"), persisted.getStringList(names + "forms.forest"));
        assertEquals(List.of("Staff Forest"), persisted.getStringList(names + "pools.forest"));
        assertEquals(12, persisted.getInt(names + "retirement-days"));
    }

    private YamlConfiguration defaults() {
        var stream = getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static ConfigSection section(YamlConfiguration yaml) {
        return new ConfigSectionImpl(null, yaml.getConfigurationSection("named-regions"));
    }

    private static Set<String> normalised(List<String> names) {
        Set<String> result = new HashSet<>();
        for (String name : names) result.add(name.toLowerCase(Locale.ROOT));
        return result;
    }
}
