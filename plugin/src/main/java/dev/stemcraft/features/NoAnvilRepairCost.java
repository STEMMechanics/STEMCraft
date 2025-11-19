package dev.stemcraft.features;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;

public class NoAnvilRepairCost implements Listener {
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = (AnvilInventory) event.getInventory();
        inventory.setRepairCost(0);
    }
}
