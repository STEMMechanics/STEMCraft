package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.PortalCreateEvent;

public class RestrictCreative implements STEMCraftFeature {
    private final String permission = "stemcraft.creative.override";

    @Override
    public void onEnable(STEMCraftAPI api) {
        api.registerEvent(PlayerInteractEntityEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE
                    && !event.getPlayer().hasPermission(this.permission)) {
                api.error(player, "RESTRICT_CREATIVE_NO_INTERACT");
                event.setCancelled(true);
            }
        });

        api.registerEvent(InventoryClickEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(permission)) {
                    if (!event.getView().title().equals(player.getOpenInventory().title())) {
                        event.setCancelled(true);
                    }
                }
            }
        });

        api.registerEvent(EntityDeathEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(permission)) {
                    event.getDrops().clear();
                }
            }
        });

        api.registerEvent(PlayerDropItemEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(permission)) {
                api.error(player, "RESTRICT_CREATIVE_NO_DROPS");
                event.setCancelled(true);
            }
        });

        api.registerEvent(EntityPickupItemEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(permission)) {
                    api.error(player, "RESTRICT_CREATIVE_NO_PICKUPS");
                    event.setCancelled(true);
                }
            }
        });

        api.registerEvent(PortalCreateEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(permission)) {
                    api.error(player, "RESTRICT_CREATIVE_NO_PORTALS");
                    event.setCancelled(true);
                }
            }
        });

        api.registerEvent(BlockPlaceEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(permission)) {
                Material blockType = event.getBlockPlaced().getType();
                if (blockType == Material.END_PORTAL_FRAME) {
                    api.error(player, "RESTRICT_CREATIVE_NO_PLACE");
                    event.setCancelled(true);
                }
            }
        });
    }
}
