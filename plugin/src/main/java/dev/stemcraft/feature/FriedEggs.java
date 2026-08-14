package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Adds an edible fried egg made by cooking a vanilla egg. */
public final class FriedEggs extends BaseFeature {
    private static final String ITEM_ID = "fried-egg";
    private static final String FURNACE_RECIPE = "fried-egg";
    private static final String SMOKER_RECIPE = "fried-egg-smoker";
    private static final String CAMPFIRE_RECIPE = "fried-egg-campfire";

    public FriedEggs(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        ItemStack egg = new ItemStack(Material.DRIED_KELP);
        ItemMeta meta = egg.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(getConfigSection().getString("name", "Fried Egg")));
        // Keep reliable vanilla food behaviour while displaying the vanilla egg model.
        meta.setItemModel(NamespacedKey.minecraft("egg"));
        var food = meta.getFood();
        food.setNutrition(Math.max(0, getConfigSection().getInt("nutrition", 3)));
        food.setSaturation((float) Math.max(0D, getConfigSection().getDouble("saturation", 2.4D)));
        food.setCanAlwaysEat(getConfigSection().getBoolean("always-edible", false));
        meta.setFood(food);
        egg.setItemMeta(meta);
        api.items().registerCustomItem(ITEM_ID, egg);
        registerRecipes();
    }

    @Override
    public void onDisable() {
        removeRecipes();
    }

    private void registerRecipes() {
        removeRecipes();
        ItemStack result = api.items().createCustomItem(ITEM_ID);
        if (result == null) return;
        float experience = (float) Math.max(0D, getConfigSection().getDouble("experience", 0.1D));
        api.recipes().addFurnace(FURNACE_RECIPE, Material.EGG, result, experience,
            Math.max(1, getConfigSection().getInt("furnace-ticks", 200)));
        api.recipes().addSmoker(SMOKER_RECIPE, Material.EGG, result, experience,
            Math.max(1, getConfigSection().getInt("smoker-ticks", 100)));
        api.recipes().addCampfire(CAMPFIRE_RECIPE, Material.EGG, result, experience,
            Math.max(1, getConfigSection().getInt("campfire-ticks", 600)));
    }

    private void removeRecipes() {
        api.recipes().remove("stemcraft:" + FURNACE_RECIPE);
        api.recipes().remove("stemcraft:" + SMOKER_RECIPE);
        api.recipes().remove("stemcraft:" + CAMPFIRE_RECIPE);
    }
}
