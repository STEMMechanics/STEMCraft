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

package dev.stemcraft.api.service.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Service for managing crafting and smelting recipes.
 */
public interface RecipeService {

    /**
     * Removes a recipe by its name/ID.
     *
     * @param name The name/ID of the recipe to remove.
     */
    void remove(@NotNull String name);

    /**
     * Adds a shaped crafting recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param result The resulting ItemStack from the recipe.
     * @param shape An array of strings representing the shape of the recipe.
     * @param ingredients A map of characters to Materials representing the ingredients.
     */
    void addShaped(@NotNull String id, @NotNull ItemStack result, @NotNull String[] shape, @NotNull Map<Character, Material> ingredients);

    /**
     * Adds a shapeless crafting recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param result The resulting ItemStack from the recipe.
     * @param inputs The input Materials required for the recipe.
     */
    void addShapeless(@NotNull String id, @NotNull ItemStack result, @NotNull Material... inputs);

    /**
     * Adds a furnace smelting recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param input The input Material to be smelted.
     * @param output The resulting ItemStack from the smelting process.
     * @param exp The experience gained from smelting.
     * @param cookTicks The time in ticks required to smelt the item.
     */
    void addFurnace(@NotNull String id, @NotNull Material input, @NotNull ItemStack output, float exp, int cookTicks);

    /**
     * Adds a smoker recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param input The input Material to be smoked.
     * @param output The resulting ItemStack from the smoking process.
     * @param exp The experience gained from smoking.
     * @param cookTicks The time in ticks required to smoke the item.
     */
    void addSmoker(@NotNull String id, @NotNull Material input, @NotNull ItemStack output, float exp, int cookTicks);

    /**
     * Adds a blast furnace recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param input The input Material to be processed in the blast furnace.
     * @param output The resulting ItemStack from the blast furnace process.
     * @param exp The experience gained from the blast furnace process.
     * @param cookTicks The time in ticks required for the blast furnace process.
     */
    void addBlastFurnace(@NotNull String id, @NotNull Material input, @NotNull ItemStack output, float exp, int cookTicks);

    /**
     * Adds a campfire recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param input The input Material to be cooked on the campfire.
     * @param output The resulting ItemStack from the campfire cooking process.
     * @param exp The experience gained from campfire cooking.
     * @param cookTicks The time in ticks required to cook the item on the campfire.
     */
    void addCampfire(@NotNull String id, @NotNull Material input, @NotNull ItemStack output, float exp, int cookTicks);

    /**
     * Adds a stonecutter recipe.
     *
     * @param input The input Material for the stonecutter.
     * @param output The resulting Material from the stonecutter.
     * @param amount The amount of output Material produced.
     */
    void addStonecutter(@NotNull Material input, @NotNull Material output, int amount);

    /**
     * Adds a smithing transform recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param result The resulting ItemStack from the smithing process.
     * @param template The template RecipeChoice for the smithing recipe.
     * @param base The base RecipeChoice for the smithing recipe.
     * @param addition The addition RecipeChoice for the smithing recipe.
     */
    void addSmithingTransform(
            @NotNull String id,
            @NotNull ItemStack result,
            @NotNull RecipeChoice template,
            @NotNull RecipeChoice base,
            @NotNull RecipeChoice addition
    );

    /**
     * Adds a smithing trim recipe.
     *
     * @param id The unique identifier for the recipe.
     * @param template The template RecipeChoice for the smithing trim.
     * @param baseArmor The base armor RecipeChoice for the smithing trim.
     * @param material The material RecipeChoice for the smithing trim.
     */
    void addSmithingTrim(
            @NotNull String id,
            @NotNull RecipeChoice template,
            @NotNull RecipeChoice baseArmor,
            @NotNull RecipeChoice material
    );
}
