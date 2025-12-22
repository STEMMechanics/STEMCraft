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
package dev.stemcraft.api.services;

import org.bukkit.Material;
import org.bukkit.inventory.*;

import java.util.Map;

public interface RecipeService {

    /**
     * Removes a recipe by its name/ID.
     */
    void remove(String name);

    /**
     * Adds a shaped crafting recipe.
     */
    void addShaped(String id, ItemStack result, String[] shape, Map<Character, Material> ingredients);

    /**
     * Adds a shapeless crafting recipe.
     */
    void addShapeless(String id, ItemStack result, Material... inputs);

    /**
     * Adds a furnace smelting recipe.
     */
    void addFurnace(String id, Material input, ItemStack output, float exp, int cookTicks);

    /**
     * Adds a smoker recipe.
     */
    void addSmoker(String id, Material input, ItemStack output, float exp, int cookTicks);

    /**
     * Adds a blast furnace recipe.
     */
    void addBlastFurnace(String id, Material input, ItemStack output, float exp, int cookTicks);

    /**
     * Adds a campfire recipe.
     */
    void addCampfire(String id, Material input, ItemStack output, float exp, int cookTicks);

    /**
     * Adds a stonecutter recipe.
     */
    void addStonecutter(Material input, Material output, int amount);

    /**
     * Adds a smithing transform recipe.
     */
    void addSmithingTransform(
            String id,
            ItemStack result,
            RecipeChoice template,
            RecipeChoice base,
            RecipeChoice addition
    );

    /**
     * Adds a smithing trim recipe.
     */
    void addSmithingTrim(
            String id,
            RecipeChoice template,
            RecipeChoice baseArmor,
            RecipeChoice material
    );
}
