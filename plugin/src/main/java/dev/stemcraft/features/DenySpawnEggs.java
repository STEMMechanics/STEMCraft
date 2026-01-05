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
package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

public class DenySpawnEggs extends BaseFeature {
    private final static String PERMISSION = "stemcraft.allow_spawn_eggs";

    /**
     * Constructor for DenySpawnEggs feature.
     */
    public DenySpawnEggs(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Registers the event listener to deny spawn egg usage.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerInteractEvent.class, event -> {
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
