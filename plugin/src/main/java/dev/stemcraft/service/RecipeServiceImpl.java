/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.recipe.RecipeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of the RecipeService for managing custom recipes.
 */
public class RecipeServiceImpl extends BaseService implements RecipeService {

    /**
     * Constructor for RecipeServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public RecipeServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Called when the service is being enabled.
     */
    @Override
    public void onEnable() {
        loadFromConfig();
    }

    /**
     * Load recipes from the configuration.
     */
    private void loadFromConfig() {
        ConfigSection recipesSec = getConfigSection();
        if (recipesSec == null) return;

        /* -------- REMOVE -------- */
        for (String raw : recipesSec.getStringList("remove")) {
            if (raw == null || raw.isEmpty()) continue;

            NamespacedKey key;
            if (raw.contains(":")) {
                key = NamespacedKey.fromString(raw);
            } else {
                key = new NamespacedKey("minecraft", raw.toLowerCase(Locale.ROOT));
            }

            if (key == null) {
                api.messages().warn("RECIPE_INVALID", "key", raw);
                continue;
            }

            if (Bukkit.removeRecipe(key)) {
                api.messages().info("RECIPE_REMOVED", "key", key.asString());
            }
        }

        /* -------- STONECUTTER -------- */
        ConfigSection stonecutterSec = recipesSec.getSection("stonecutter");
        if (stonecutterSec != null) {
            for (String inputKey : stonecutterSec.getKeys(false)) {
                Material inputMat = Material.matchMaterial(inputKey.toUpperCase(Locale.ROOT));
                if (inputMat == null) {
                    api.messages().warn("RECIPE_STONECUTTER_UNKNOWN_INPUT", "material", inputKey);
                    continue;
                }

                ConfigSection outputsSec = stonecutterSec.getSection(inputKey);
                if (outputsSec == null) continue;

                for (String outputKey : outputsSec.getKeys(false)) {
                    Material outputMat = Material.matchMaterial(outputKey.toUpperCase(Locale.ROOT));
                    if (outputMat == null) {
                        api.messages().warn("RECIPE_STONECUTTER_UNKNOWN_OUTPUT", "material", outputKey);
                        continue;
                    }

                    int amount = outputsSec.getInt(outputKey, 1);
                    if (amount <= 0) amount = 1;

                    addStonecutter(inputMat, outputMat, amount);
                    api.messages().info("RECIPE_STONECUTTER_RESULT", "input", inputMat.name(), "amount", String.valueOf(amount), "output", outputMat.name());
                }
            }
        }

        /* -------- SHAPED -------- */
        ConfigSection shapedSec = recipesSec.getSection("shaped");
        if (shapedSec != null) {
            for (String id : shapedSec.getKeys(false)) {
                ConfigSection rSec = shapedSec.getSection(id);
                if (rSec == null) continue;

                String resultMatStr = rSec.getString("result");
                Material resultMat = Material.matchMaterial(resultMatStr.toUpperCase(Locale.ROOT));
                if (resultMat == null) {
                    plugin.getLogger().warning("shaped." + id + " unknown result material: " + resultMatStr);
                    continue;
                }
                int amount = rSec.getInt("amount", 1);
                if (amount <= 0) amount = 1;

                List<String> shapeList = rSec.getStringList("shape");
                if (shapeList.isEmpty()) {
                    plugin.getLogger().warning("shaped." + id + " missing shape");
                    continue;
                }
                String[] shape = shapeList.toArray(new String[0]);

                ConfigSection ingSec = rSec.getSection("ingredients");
                if (ingSec == null) {
                    plugin.getLogger().warning("shaped." + id + " missing ingredients");
                    continue;
                }

                Map<Character, Material> ingMap = new HashMap<>();
                for (String key : ingSec.getKeys(false)) {
                    if (key.length() != 1) {
                        plugin.getLogger().warning("shaped." + id + " ingredient key must be 1 char: " + key);
                        continue;
                    }
                    char c = key.charAt(0);
                    String matStr = ingSec.getString(key);
                    Material m = Material.matchMaterial(matStr.toUpperCase(Locale.ROOT));
                    if (m == null) {
                        plugin.getLogger().warning("shaped." + id + " unknown material: " + matStr);
                        continue;
                    }
                    ingMap.put(c, m);
                }

                ItemStack result = new ItemStack(resultMat, amount);
                addShaped(id, result, shape, ingMap);
                api.messages().info("RECIPE_SHAPED_LOADED", "id", id);
            }
        }

        /* -------- SHAPELESS -------- */
        ConfigSection shapelessSec = recipesSec.getSection("shapeless");
        if (shapelessSec != null) {
            for (String id : shapelessSec.getKeys(false)) {
                ConfigSection rSec = shapelessSec.getSection(id);
                if (rSec == null) continue;

                String resultMatStr = rSec.getString("result");
                Material resultMat = Material.matchMaterial(resultMatStr.toUpperCase(Locale.ROOT));
                if (resultMat == null) {
                    plugin.getLogger().warning("shapeless." + id + " unknown result material: " + resultMatStr);
                    continue;
                }
                int amount = rSec.getInt("amount", 1);
                if (amount <= 0) amount = 1;

                List<String> ingList = rSec.getStringList("ingredients");
                if (ingList.isEmpty()) {
                    plugin.getLogger().warning("shapeless." + id + " missing ingredients");
                    continue;
                }

                ItemStack result = new ItemStack(resultMat, amount);
                ShapelessRecipe recipe = new ShapelessRecipe(key(id), result);
                for (String matStr : ingList) {
                    Material m = Material.matchMaterial(matStr.toUpperCase(Locale.ROOT));
                    if (m == null) {
                        plugin.getLogger().warning("shapeless." + id + " unknown material: " + matStr);
                        continue;
                    }
                    recipe.addIngredient(m);
                }
                Bukkit.addRecipe(recipe);
                api.messages().info("RECIPE_SHAPELESS_LOADED", "id" + id);
            }
        }

        /* -------- FURNACE / SMOKER / BLAST / CAMPFIRE -------- */
        loadCookingSection(recipesSec.getSection("furnace"),  "furnace");
        loadCookingSection(recipesSec.getSection("smoker"),   "smoker");
        loadCookingSection(recipesSec.getSection("blast_furnace"), "blast_furnace");
        loadCookingSection(recipesSec.getSection("campfire"), "campfire");

        /* -------- SMITHING TRANSFORM -------- */
        ConfigSection smithTransSec = recipesSec.getSection("smithing_transform");
        if (smithTransSec != null) {
            for (String id : smithTransSec.getKeys(false)) {
                ConfigSection rSec = smithTransSec.getSection(id);
                if (rSec == null) continue;

                String resultMatStr = rSec.getString("result");
                Material resultMat = Material.matchMaterial(resultMatStr.toUpperCase(Locale.ROOT));
                if (resultMat == null) {
                    plugin.getLogger().warning("smithing_transform." + id + " unknown result material: " + resultMatStr);
                    continue;
                }

                String templateStr = rSec.getString("template");
                String baseStr     = rSec.getString("base");
                String addStr      = rSec.getString("addition");

                Material templateMat = Material.matchMaterial(templateStr.toUpperCase(Locale.ROOT));
                Material baseMat     = Material.matchMaterial(baseStr.toUpperCase(Locale.ROOT));
                Material addMat      = Material.matchMaterial(addStr.toUpperCase(Locale.ROOT));

                if (templateMat == null || baseMat == null || addMat == null) {
                    plugin.getLogger().warning("smithing_transform." + id + " invalid materials");
                    continue;
                }

                ItemStack result = new ItemStack(resultMat);
                RecipeChoice template = new RecipeChoice.MaterialChoice(templateMat);
                RecipeChoice base     = new RecipeChoice.MaterialChoice(baseMat);
                RecipeChoice addition = new RecipeChoice.MaterialChoice(addMat);

                addSmithingTransform(id, result, template, base, addition);
                api.messages().info("RECIPE_SMITHING_TRANSFORM_LOADED", "id", id);
            }
        }

        /* -------- SMITHING TRIM -------- */
        ConfigSection smithTrimSec = recipesSec.getSection("smithing_trim");
        if (smithTrimSec != null) {
            for (String id : smithTrimSec.getKeys(false)) {
                ConfigSection rSec = smithTrimSec.getSection(id);
                if (rSec == null) continue;

                String templateStr = rSec.getString("template");
                String baseStr     = rSec.getString("base");
                String matStr      = rSec.getString("material");

                Material templateMat = Material.matchMaterial(templateStr.toUpperCase(Locale.ROOT));
                Material baseMat     = Material.matchMaterial(baseStr.toUpperCase(Locale.ROOT));
                Material materialMat = Material.matchMaterial(matStr.toUpperCase(Locale.ROOT));

                if (templateMat == null || baseMat == null || materialMat == null) {
                    plugin.getLogger().warning("smithing_trim." + id + " invalid materials");
                    continue;
                }

                RecipeChoice template = new RecipeChoice.MaterialChoice(templateMat);
                RecipeChoice base     = new RecipeChoice.MaterialChoice(baseMat);
                RecipeChoice material = new RecipeChoice.MaterialChoice(materialMat);

                addSmithingTrim(id, template, base, material);
                api.messages().info("RECIPE_SMITHING_TRIM_LOADED", "id", id);
            }
        }
    }

    /**
     * Load cooking recipes from a configuration section.
     *
     * @param sec The configuration section containing the recipes.
     * @param type The type of cooking recipe (furnace, smoker, blast_furnace, campfire).
     */
    private void loadCookingSection(ConfigSection sec, String type) {
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigSection rSec = sec.getSection(id);
            if (rSec == null) continue;

            String inputStr  = rSec.getString("input");
            String resultStr = rSec.getString("result");
            int amount       = rSec.getInt("amount", 1);
            float exp        = (float) rSec.getDouble("exp", 0.0);
            int time         = rSec.getInt("time", 200);

            Material inputMat  = Material.matchMaterial(inputStr.toUpperCase(Locale.ROOT));
            Material resultMat = Material.matchMaterial(resultStr.toUpperCase(Locale.ROOT));

            if (inputMat == null || resultMat == null) {
                plugin.getLogger().warning(type + "." + id + " invalid materials");
                continue;
            }
            if (amount <= 0) amount = 1;

            ItemStack result = new ItemStack(resultMat, amount);

            switch (type) {
                case "furnace" -> addFurnace(id, inputMat, result, exp, time);
                case "smoker" -> addSmoker(id, inputMat, result, exp, time);
                case "blast_furnace" -> addBlastFurnace(id, inputMat, result, exp, time);
                case "campfire" -> addCampfire(id, inputMat, result, exp, time);
            }
            api.messages().info("RECIPE_COOKING_LOADED", "type", type, "id", id);
        }
    }

    /**
     * Remove a recipe by its namespaced key.
     *
     * @param name The namespaced key of the recipe to remove.
     */
    @Override
    public void remove(@NonNull @NonNull String name) {
        NamespacedKey namespaceItem = NamespacedKey.fromString(name);
        if (namespaceItem != null) {
            Bukkit.removeRecipe(namespaceItem);
        }
    }

    /**
     * Create a NamespacedKey for the plugin with the given id.
     *
     * @param id The id for the NamespacedKey.
     * @return The created NamespacedKey.
     */
    private NamespacedKey key(String id) {
        return new NamespacedKey(plugin, id.toLowerCase());
    }

    // Shaped: shape like new String[]{"ABC", "A A", " B "}
    /**
     * Add a shaped crafting recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param result The resulting ItemStack from the recipe.
     * @param shape The shape of the recipe as an array of strings.
     * @param ingredients A map of characters to Materials representing the ingredients.
     */
    @Override
    public void addShaped(@NotNull String id, @NotNull ItemStack result, @NotNull String[] shape, @NotNull Map<Character, Material> ingredients) {
        ShapedRecipe recipe = new ShapedRecipe(key(id), result);
        recipe.shape(shape);

        for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }

        Bukkit.addRecipe(recipe);
    }

    /**
     * Add a shapeless crafting recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param result The resulting ItemStack from the recipe.
     * @param inputs The input Materials for the recipe.
     */
    @Override
    public void addShapeless(@NotNull String id, @NotNull ItemStack result, @NotNull Material... inputs) {
        ShapelessRecipe recipe = new ShapelessRecipe(key(id), result);
        for (Material mat : inputs) {
            recipe.addIngredient(mat);
        }
        Bukkit.addRecipe(recipe);
    }

    /**
     * Add a furnace cooking recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param input The input Material for the recipe.
     * @param output The resulting ItemStack from the recipe.
     * @param exp The experience gained from the recipe.
     * @param cookTicks The cooking time in ticks.
     */
    @Override
    public void addFurnace(@NotNull String id, @NotNull Material input, @NotNull ItemStack output, float exp, int cookTicks) {
        FurnaceRecipe recipe = new FurnaceRecipe(key(id), output, input, exp, cookTicks);
        recipe.setCategory(CookingBookCategory.MISC);
        Bukkit.addRecipe(recipe);
    }

    /**
     * Add a smoker cooking recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param input The input Material for the recipe.
     * @param output The resulting ItemStack from the recipe.
     * @param exp The experience gained from the recipe.
     * @param cookTicks The cooking time in ticks.
     */
    @Override
    public void addSmoker(@NotNull String id, @NotNull Material input, @NotNull ItemStack output, float exp, int cookTicks) {
        SmokingRecipe recipe = new SmokingRecipe(key(id), output, input, exp, cookTicks);
        recipe.setCategory(CookingBookCategory.FOOD);
        Bukkit.addRecipe(recipe);
    }

    /**
     * Add a blast furnace cooking recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param input The input Material for the recipe.
     * @param output The resulting ItemStack from the recipe.
     * @param exp The experience gained from the recipe.
     * @param cookTicks The cooking time in ticks.
     */
    @Override
    public void addBlastFurnace(@NotNull String id, @NotNull Material input, @NotNull ItemStack output, float exp, int cookTicks) {
        BlastingRecipe recipe = new BlastingRecipe(key(id), output, input, exp, cookTicks);
        recipe.setCategory(CookingBookCategory.BLOCKS);
        Bukkit.addRecipe(recipe);
    }

    /**
     * Add a campfire cooking recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param input The input Material for the recipe.
     * @param output The resulting ItemStack from the recipe.
     * @param exp The experience gained from the recipe.
     * @param cookTicks The cooking time in ticks.
     */
    @Override
    public void addCampfire(@NotNull String id, @NotNull Material input, @NotNull ItemStack output, float exp, int cookTicks) {
        CampfireRecipe recipe = new CampfireRecipe(key(id), output, input, exp, cookTicks);
        recipe.setCategory(CookingBookCategory.FOOD);
        Bukkit.addRecipe(recipe);
    }

    /**
     * Add a stonecutter recipe.
     *
     * @param input The input Material for the recipe.
     * @param output The output Material from the recipe.
     * @param amount The amount of the output Material.
     */
    @Override
    public void addStonecutter(@NotNull Material input, @NotNull Material output, int amount) {
        ItemStack result = new ItemStack(output, amount);
        String id = "stonecut_" + input.name().toLowerCase() + "_to_" + output.name().toLowerCase();
        StonecuttingRecipe recipe = new StonecuttingRecipe(key(id), result, input);
        Bukkit.addRecipe(recipe);
    }

    // Smithing Transform: template + base + addition -> new item
    // Example: netherite upgrade (template, diamond chestplate, netherite ingot)

    /**
     * Add a smithing transform recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param result The resulting ItemStack from the smithing process.
     * @param template The template RecipeChoice for the smithing recipe.
     * @param base The base RecipeChoice for the smithing recipe.
     * @param addition The addition RecipeChoice for the smithing recipe.
     */
    @Override
    public void addSmithingTransform(
            @NonNull @NonNull String id,
            @NonNull @NonNull ItemStack result,
            @NonNull @NonNull RecipeChoice template,
            @NonNull @NonNull RecipeChoice base,
            @NonNull @NonNull RecipeChoice addition
    ) {
        SmithingTransformRecipe recipe =
                new SmithingTransformRecipe(key(id), result, template, base, addition);
        Bukkit.addRecipe(recipe);
    }

    // Smithing Trim: template + armor + material -> trimmed armor
    /**
     * Add a smithing trim recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param template The template RecipeChoice for the smithing trim recipe.
     * @param baseArmor The base armor RecipeChoice for the smithing trim recipe.
     * @param material The material RecipeChoice for the smithing trim recipe.
     */
    @Override
    public void addSmithingTrim(
            @NonNull @NonNull String id,
            @NonNull @NonNull RecipeChoice template,
            @NonNull @NonNull RecipeChoice baseArmor,
            @NonNull @NonNull RecipeChoice material
    ) {
        @SuppressWarnings("removal")
        SmithingTrimRecipe recipe =
                new SmithingTrimRecipe(key(id), template, baseArmor, material);
        Bukkit.addRecipe(recipe);
    }
}
