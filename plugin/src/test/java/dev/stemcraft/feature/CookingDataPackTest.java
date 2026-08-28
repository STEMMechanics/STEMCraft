package dev.stemcraft.feature;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookingDataPackTest {
    private static final String ROOT = "data-packs/stemcraft-cooking/";

    @Test
    void cookingPackDeclaresExactlySixtyFourItemsWithTextures() {
        YamlConfiguration items = load("configs/items.yml");
        ConfigurationSection section = items.getConfigurationSection("custom-items");
        assertNotNull(section);
        assertEquals(64, section.getKeys(false).size());
        for (String id : section.getKeys(false)) {
            assertNotNull(Material.matchMaterial(section.getString(id + ".material", "")), id + " backing material");
            String texture = section.getString(id + ".texture");
            assertNotNull(texture, id + " texture declaration");
            assertNotNull(getClass().getClassLoader().getResource(ROOT + "contents/stemcraft_cooking/textures/"
                + texture + ".png"), id + " texture source");
        }
    }

    @Test
    void riceIsADataDrivenFloodedCropAndForageIsNaturalOnly() {
        YamlConfiguration agriculture = load("configs/agriculture.yml");
        assertEquals("stemcraft:rice_shoots", agriculture.getString("agriculture.crops.rice.seed"));
        assertEquals("MUD", agriculture.getString("agriculture.crops.rice.soil"));
        assertTrue(agriculture.getBoolean("agriculture.crops.rice.water-above"));
        assertEquals(4, agriculture.getStringList("agriculture.crops.rice.stages").size());
        ConfigurationSection forage = agriculture.getConfigurationSection("agriculture.foraging");
        assertNotNull(forage);
        assertFalse(forage.getKeys(false).isEmpty());
        forage.getKeys(false).forEach(id -> assertTrue(forage.getBoolean(id + ".natural-only"), id));
    }

    @Test
    void everyCookingRecipeCustomReferenceResolves() {
        YamlConfiguration items = load("configs/items.yml");
        YamlConfiguration recipes = load("configs/recipes.yml");
        Set<String> ids = new HashSet<>(items.getConfigurationSection("custom-items").getKeys(false));
        ids.add("fried-egg");
        ConfigurationSection root = recipes.getConfigurationSection("recipes");
        assertNotNull(root);
        for (String path : root.getKeys(true)) {
            Object raw = root.get(path);
            if (raw instanceof String value) assertReference(ids, path, value);
            else if (raw instanceof Iterable<?> values) for (Object value : values)
                if (value instanceof String text) assertReference(ids, path, text);
        }
    }

    @Test
    void hasteIsAvailableAsFoodAndThreePotionMixes() {
        YamlConfiguration items = load("configs/items.yml");
        assertEquals("HASTE", items.getMapList("custom-items.miners-breakfast.food.effects").getFirst().get("type"));
        YamlConfiguration recipes = load("configs/recipes.yml");
        assertEquals("AMETHYST_SHARD", recipes.getString("recipes.brewing.haste.ingredient"));
        assertEquals(0, recipes.getInt("recipes.brewing.haste.amplifier"));
        assertEquals(1, recipes.getInt("recipes.brewing.strong_haste.amplifier"));
        assertEquals("REDSTONE", recipes.getString("recipes.brewing.long_haste.ingredient"));
    }

    private static void assertReference(Set<String> ids, String path, String value) {
        if (value.startsWith("stemcraft:"))
            assertTrue(ids.contains(value.substring("stemcraft:".length()).replace('_', '-')), path + " -> " + value);
    }

    private static YamlConfiguration load(String relative) {
        String resource = ROOT + relative;
        InputStream stream = CookingDataPackTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, "Missing bundled resource " + resource);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
