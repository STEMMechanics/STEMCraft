package dev.stemcraft.api.services;

import org.bukkit.Material;
import org.bukkit.inventory.*;

import java.util.Map;

public interface RecipeService {
    void remove(String name);
    void addShaped(String id, ItemStack result, String[] shape, Map<Character, Material> ingredients);
    void addShapeless(String id, ItemStack result, Material... inputs);
    void addFurnace(String id, Material input, ItemStack output, float exp, int cookTicks);
    void addSmoker(String id, Material input, ItemStack output, float exp, int cookTicks);
    void addBlastFurnace(String id, Material input, ItemStack output, float exp, int cookTicks);
    void addCampfire(String id, Material input, ItemStack output, float exp, int cookTicks);
    void addStonecutter(Material input, Material output, int amount);
    void addSmithingTransform(
            String id,
            ItemStack result,
            RecipeChoice template,
            RecipeChoice base,
            RecipeChoice addition
    );
    void addSmithingTrim(
            String id,
            RecipeChoice template,
            RecipeChoice baseArmor,
            RecipeChoice material
    );
}
