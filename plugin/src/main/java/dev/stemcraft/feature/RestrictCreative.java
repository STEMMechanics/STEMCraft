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

package dev.stemcraft.feature;

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

/**
 * Feature that restricts actions for players in creative mode without proper permission.
 */
public class RestrictCreative extends BaseFeature {
    private static final long PICKUP_WARNING_DEBOUNCE_TICKS = 30L;

    private final String PERMISSION = "stemcraft.creative.override";

    /**
     * Constructor for RestrictCreative.
     *
     * @param api The STEMCraft API instance.
     */
    public RestrictCreative(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is being enabled.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerInteractEntityEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE
                    && !event.getPlayer().hasPermission(this.PERMISSION)) {
                api.messages().error(player, "RESTRICT_CREATIVE_NO_INTERACT");
                event.setCancelled(true);
            }
        });

        api.events().register(InventoryClickEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION)) {
                    if (!event.getView().title().equals(player.getOpenInventory().title())) {
                        event.setCancelled(true);
                    }
                }
            }
        });

        api.events().register(EntityDeathEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION)) {
                    event.getDrops().clear();
                }
            }
        });

        api.events().register(PlayerDropItemEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION)) {
                api.messages().error(player, "RESTRICT_CREATIVE_NO_DROPS");
                event.setCancelled(true);
            }
        });

        api.events().register(EntityPickupItemEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION)) {
                    api.tasks().debounce(
                            "restrict-creative:pickup-warning:" + player.getUniqueId(),
                            PICKUP_WARNING_DEBOUNCE_TICKS,
                            () -> api.messages().error(player, "RESTRICT_CREATIVE_NO_PICKUPS")
                    );
                    event.setCancelled(true);
                }
            }
        });

        api.events().register(PortalCreateEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION)) {
                    api.messages().error(player, "RESTRICT_CREATIVE_NO_PORTALS");
                    event.setCancelled(true);
                }
            }
        });

        api.events().register(BlockPlaceEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION)) {
                Material blockType = event.getBlockPlaced().getType();
                if (blockType == Material.END_PORTAL_FRAME) {
                    api.messages().error(player, "RESTRICT_CREATIVE_NO_PLACE");
                    event.setCancelled(true);
                }
            }
        });
    }
}
