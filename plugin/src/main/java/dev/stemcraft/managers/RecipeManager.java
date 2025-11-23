/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.RecipeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.*;
import org.bukkit.inventory.recipe.CookingBookCategory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecipeManager implements RecipeService {
    private final STEMCraft plugin;

    public RecipeManager(STEMCraft plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        loadFromConfig(plugin.config());
    }

    public void loadFromConfig(FileConfiguration config) {
        ConfigurationSection recipesSec = config.getConfigurationSection("recipes");
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
                plugin.getLogger().warning("Invalid recipe key in recipes.remove: " + raw);
                continue;
            }

            if (Bukkit.removeRecipe(key)) {
                plugin.getLogger().info("Removed recipe: " + key);
            }
        }

        /* -------- STONECUTTER -------- */
        ConfigurationSection stonecutterSec = recipesSec.getConfigurationSection("stonecutter");
        if (stonecutterSec != null) {
            for (String inputKey : stonecutterSec.getKeys(false)) {
                Material inputMat = Material.matchMaterial(inputKey.toUpperCase(Locale.ROOT));
                if (inputMat == null) {
                    plugin.getLogger().warning("Unknown stonecutter input: " + inputKey);
                    continue;
                }

                ConfigurationSection outputsSec = stonecutterSec.getConfigurationSection(inputKey);
                if (outputsSec == null) continue;

                for (String outputKey : outputsSec.getKeys(false)) {
                    Material outputMat = Material.matchMaterial(outputKey.toUpperCase(Locale.ROOT));
                    if (outputMat == null) {
                        plugin.getLogger().warning("Unknown stonecutter output: " + outputKey);
                        continue;
                    }

                    int amount = outputsSec.getInt(outputKey, 1);
                    if (amount <= 0) amount = 1;

                    addStonecutter(inputMat, outputMat, amount);
                    plugin.getLogger().info("Stonecutter: " + inputMat + " -> " + amount + "x " + outputMat);
                }
            }
        }

        /* -------- SHAPED -------- */
        ConfigurationSection shapedSec = recipesSec.getConfigurationSection("shaped");
        if (shapedSec != null) {
            for (String id : shapedSec.getKeys(false)) {
                ConfigurationSection rSec = shapedSec.getConfigurationSection(id);
                if (rSec == null) continue;

                String resultMatStr = rSec.getString("result");
                if (resultMatStr == null) {
                    plugin.getLogger().warning("shaped." + id + " missing result");
                    continue;
                }
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

                ConfigurationSection ingSec = rSec.getConfigurationSection("ingredients");
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
                    if (matStr == null) continue;
                    Material m = Material.matchMaterial(matStr.toUpperCase(Locale.ROOT));
                    if (m == null) {
                        plugin.getLogger().warning("shaped." + id + " unknown material: " + matStr);
                        continue;
                    }
                    ingMap.put(c, m);
                }

                ItemStack result = new ItemStack(resultMat, amount);
                addShaped(id, result, shape, ingMap);
                plugin.getLogger().info("Shaped recipe loaded: " + id);
            }
        }

        /* -------- SHAPELESS -------- */
        ConfigurationSection shapelessSec = recipesSec.getConfigurationSection("shapeless");
        if (shapelessSec != null) {
            for (String id : shapelessSec.getKeys(false)) {
                ConfigurationSection rSec = shapelessSec.getConfigurationSection(id);
                if (rSec == null) continue;

                String resultMatStr = rSec.getString("result");
                if (resultMatStr == null) {
                    plugin.getLogger().warning("shapeless." + id + " missing result");
                    continue;
                }
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
                plugin.getLogger().info("Shapeless recipe loaded: " + id);
            }
        }

        /* -------- FURNACE / SMOKER / BLAST / CAMPFIRE -------- */
        loadCookingSection(recipesSec.getConfigurationSection("furnace"),  "furnace");
        loadCookingSection(recipesSec.getConfigurationSection("smoker"),   "smoker");
        loadCookingSection(recipesSec.getConfigurationSection("blast_furnace"), "blast_furnace");
        loadCookingSection(recipesSec.getConfigurationSection("campfire"), "campfire");

        /* -------- SMITHING TRANSFORM -------- */
        ConfigurationSection smithTransSec = recipesSec.getConfigurationSection("smithing_transform");
        if (smithTransSec != null) {
            for (String id : smithTransSec.getKeys(false)) {
                ConfigurationSection rSec = smithTransSec.getConfigurationSection(id);
                if (rSec == null) continue;

                String resultMatStr = rSec.getString("result");
                if (resultMatStr == null) {
                    plugin.getLogger().warning("smithing_transform." + id + " missing result");
                    continue;
                }
                Material resultMat = Material.matchMaterial(resultMatStr.toUpperCase(Locale.ROOT));
                if (resultMat == null) {
                    plugin.getLogger().warning("smithing_transform." + id + " unknown result material: " + resultMatStr);
                    continue;
                }

                String templateStr = rSec.getString("template");
                String baseStr     = rSec.getString("base");
                String addStr      = rSec.getString("addition");

                if (templateStr == null || baseStr == null || addStr == null) {
                    plugin.getLogger().warning("smithing_transform." + id + " missing template/base/addition");
                    continue;
                }

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
                plugin.getLogger().info("Smithing transform loaded: " + id);
            }
        }

        /* -------- SMITHING TRIM -------- */
        ConfigurationSection smithTrimSec = recipesSec.getConfigurationSection("smithing_trim");
        if (smithTrimSec != null) {
            for (String id : smithTrimSec.getKeys(false)) {
                ConfigurationSection rSec = smithTrimSec.getConfigurationSection(id);
                if (rSec == null) continue;

                String templateStr = rSec.getString("template");
                String baseStr     = rSec.getString("base");
                String matStr      = rSec.getString("material");

                if (templateStr == null || baseStr == null || matStr == null) {
                    plugin.getLogger().warning("smithing_trim." + id + " missing template/base/material");
                    continue;
                }

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
                plugin.getLogger().info("Smithing trim loaded: " + id);
            }
        }
    }

    /* helper for furnace/smoker/blast_furnace/campfire sections */
    private void loadCookingSection(ConfigurationSection sec, String type) {
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection rSec = sec.getConfigurationSection(id);
            if (rSec == null) continue;

            String inputStr  = rSec.getString("input");
            String resultStr = rSec.getString("result");
            int amount       = rSec.getInt("amount", 1);
            float exp        = (float) rSec.getDouble("exp", 0.0);
            int time         = rSec.getInt("time", 200);

            if (inputStr == null || resultStr == null) {
                plugin.getLogger().warning(type + "." + id + " missing input/result");
                continue;
            }

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
            plugin.getLogger().info("Cooking recipe loaded: " + type + "." + id);
        }
    }

    @Override
    public void remove(String name) {
        NamespacedKey namespaceItem = NamespacedKey.fromString(name);
        Bukkit.removeRecipe(namespaceItem);
    }

    private NamespacedKey key(String id) {
        return new NamespacedKey(plugin, id.toLowerCase());
    }

    /* ==========================
       CRAFTING TABLE
       ========================== */

    // Shaped: shape like new String[]{"ABC", "A A", " B "}
    @Override
    public void addShaped(String id, ItemStack result, String[] shape, Map<Character, Material> ingredients) {
        ShapedRecipe recipe = new ShapedRecipe(key(id), result);
        recipe.shape(shape);

        for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }

        Bukkit.addRecipe(recipe);
    }

    @Override
    public void addShapeless(String id, ItemStack result, Material... inputs) {
        ShapelessRecipe recipe = new ShapelessRecipe(key(id), result);
        for (Material mat : inputs) {
            recipe.addIngredient(mat);
        }
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void addFurnace(String id, Material input, ItemStack output, float exp, int cookTicks) {
        FurnaceRecipe recipe = new FurnaceRecipe(key(id), output, input, exp, cookTicks);
        recipe.setCategory(CookingBookCategory.MISC);
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void addSmoker(String id, Material input, ItemStack output, float exp, int cookTicks) {
        SmokingRecipe recipe = new SmokingRecipe(key(id), output, input, exp, cookTicks);
        recipe.setCategory(CookingBookCategory.FOOD);
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void addBlastFurnace(String id, Material input, ItemStack output, float exp, int cookTicks) {
        BlastingRecipe recipe = new BlastingRecipe(key(id), output, input, exp, cookTicks);
        recipe.setCategory(CookingBookCategory.BLOCKS);
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void addCampfire(String id, Material input, ItemStack output, float exp, int cookTicks) {
        CampfireRecipe recipe = new CampfireRecipe(key(id), output, input, exp, cookTicks);
        recipe.setCategory(CookingBookCategory.FOOD);
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void addStonecutter(Material input, Material output, int amount) {
        ItemStack result = new ItemStack(output, amount);
        String id = "stonecut_" + input.name().toLowerCase() + "_to_" + output.name().toLowerCase();
        StonecuttingRecipe recipe = new StonecuttingRecipe(key(id), result, input);
        Bukkit.addRecipe(recipe);
    }

    // Smithing Transform: template + base + addition -> new item
    // Example: netherite upgrade (template, diamond chestplate, netherite ingot)
    @Override
    public void addSmithingTransform(
            String id,
            ItemStack result,
            RecipeChoice template,
            RecipeChoice base,
            RecipeChoice addition
    ) {
        SmithingTransformRecipe recipe =
                new SmithingTransformRecipe(key(id), result, template, base, addition);
        Bukkit.addRecipe(recipe);
    }

    // Smithing Trim: template + armor + material -> trimmed armor
    @Override
    public void addSmithingTrim(
            String id,
            RecipeChoice template,
            RecipeChoice baseArmor,
            RecipeChoice material
    ) {
        SmithingTrimRecipe recipe =
                new SmithingTrimRecipe(key(id), template, baseArmor, material);
        Bukkit.addRecipe(recipe);
    }
}
