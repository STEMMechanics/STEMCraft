package dev.stemcraft.feature;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RottenFleshUsesFeatureTest {
    private static final String ROOT = "data-packs/stemcraft-rotten-flesh/";

    @Test
    void bundledRecipesHaveTheIntendedIngredientCountsAndOutputs() {
        YamlConfiguration config = load(ROOT + "configs/custom-items.yml");

        assertFalse(config.isConfigurationSection("recipes.shapeless.rotten_flesh_bone_meal"));
        assertRecipe(config, "rotten_flesh_leather", "LEATHER", 1, 9, "ROTTEN_FLESH");
        assertRecipe(config, "dog_treat", "stemcraft:dog_treat", 2, 4, "ROTTEN_FLESH");
        assertRecipe(config, "rotten_flesh_stew", "stemcraft:rotten_flesh_stew", 1, 1, "ROTTEN_FLESH");
    }

    @Test
    void customItemsDeclareCrossPlatformTextureSources() {
        YamlConfiguration config = load(ROOT + "configs/custom-items.yml");
        assertEquals("stemcraft_rotten_flesh:item/dog_treat", config.getString("custom-items.dog-treat.texture"));
        assertEquals("stemcraft_rotten_flesh:item/zombie_bait", config.getString("custom-items.zombie-bait.texture"));
        assertNotNull(getClass().getClassLoader().getResource(ROOT
            + "contents/stemcraft_rotten_flesh/textures/item/dog_treat.png"));
        assertNotNull(getClass().getClassLoader().getResource(ROOT
            + "contents/stemcraft_rotten_flesh/textures/item/zombie_bait.png"));
    }

    @Test
    void zombieBaitRecipeUsesFleshSpiderEyeAndString() {
        YamlConfiguration config = load(ROOT + "configs/custom-items.yml");
        List<String> ingredients = config.getStringList("recipes.shapeless.zombie_bait.ingredients");
        assertEquals(6, ingredients.size());
        assertEquals(4, ingredients.stream().filter("ROTTEN_FLESH"::equals).count());
        assertTrue(ingredients.contains("SPIDER_EYE"));
        assertTrue(ingredients.contains("STRING"));
    }

    @Test
    void ironGolemPoppyHasAnIndependentTopLevelConfig() {
        YamlConfiguration config = load("config.yml");
        assertTrue(config.isConfigurationSection("iron-golem-poppy"));
        assertTrue(config.getBoolean("iron-golem-poppy.enabled"));
        assertEquals(10.0, config.getDouble("iron-golem-poppy.range"));
        assertFalse(config.isConfigurationSection("rotten-flesh-uses.iron-golem-poppy"));
    }

    @Test
    void rottenFleshCompostingIsAFeatureNotACraftingRecipe() {
        YamlConfiguration config = load("config.yml");
        assertTrue(config.getBoolean("rotten-flesh-uses.composting.enabled"));
        YamlConfiguration items = load(ROOT + "configs/custom-items.yml");
        assertFalse(items.isConfigurationSection("recipes.shapeless.rotten_flesh_bone_meal"));
    }

    private static void assertRecipe(YamlConfiguration config, String id, String result, int amount,
                                     long expectedIngredientCount, String ingredient) {
        String path = "recipes.shapeless." + id;
        assertEquals(result, config.getString(path + ".result"));
        assertEquals(amount, config.getInt(path + ".amount"));
        assertEquals(expectedIngredientCount,
            config.getStringList(path + ".ingredients").stream().filter(ingredient::equals).count());
    }

    private static YamlConfiguration load(String resource) {
        InputStream stream = RottenFleshUsesFeatureTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, "Missing bundled resource " + resource);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
