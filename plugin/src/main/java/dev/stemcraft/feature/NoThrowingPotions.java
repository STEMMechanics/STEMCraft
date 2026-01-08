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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Feature that prevents players from throwing splash and lingering potions
 * unless they have a specific permission.
 */
public class NoThrowingPotions extends BaseFeature {
    private final static String PERMISSION = "stemcraft.allow_throwing_potions";

    /**
     * Constructor for NoThrowingPotions feature.
     *
     * @param api The STEMCraft API instance.
     */
    public NoThrowingPotions(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Registers the event listener to prevent throwing potions.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerInteractEvent.class, event -> {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getItem() == null) return;

            Player player = event.getPlayer();
            Material type = event.getItem().getType();

            if(type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
                if (!player.hasPermission(PERMISSION)) {
                    event.setCancelled(true);
                    api.messages().error(player, "DENY_THROWING_POTION");
                }
            }
        });
    }
}
