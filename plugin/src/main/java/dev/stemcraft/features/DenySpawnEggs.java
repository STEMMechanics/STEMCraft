package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

public class DenySpawnEggs implements STEMCraftFeature {
    private final static String PERMISSION = "stemcraft.allow_spawn_eggs";

    @Override
    public void onEnable(STEMCraftAPI api) {
        api.registerEvent(PlayerInteractEvent.class, event -> {
            if (event.getItem() == null) return;
            if (!event.getItem().getType().toString().endsWith("_SPAWN_EGG")) return;

            Player player = event.getPlayer();

            if (!player.hasPermission(PERMISSION)) {
                event.setCancelled(true);
                api.error(player, "DENY_SPAWN_EGG");
            }
        });
    }
}
