package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;

public class NoAnvilRepairCost implements STEMCraftFeature {

    @Override
    public void onEnable(STEMCraftAPI api) {
        api.registerEvent(PrepareAnvilEvent.class, event -> {
            AnvilInventory inventory = (AnvilInventory) event.getInventory();
            inventory.setRepairCost(0);
        });
    }
}
