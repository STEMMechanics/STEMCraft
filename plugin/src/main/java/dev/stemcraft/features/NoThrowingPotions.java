package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class NoThrowingPotions implements STEMCraftFeature {
    private final static String PERMISSION = "stemcraft.allow_throwing_potions";

    @Override
    public void onEnable(STEMCraftAPI api) {
        api.registerEvent(PlayerInteractEvent.class, event -> {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getItem() == null) return;

            Player player = event.getPlayer();
            Material type = event.getItem().getType();

            if(type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
                if (!player.hasPermission(PERMISSION)) {
                    event.setCancelled(true);
                    api.error(player, "DENY_THROWING_POTION");
                }
            }
        });
    }
}
