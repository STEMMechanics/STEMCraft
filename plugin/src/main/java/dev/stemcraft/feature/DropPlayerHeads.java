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
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Feature that allows players to drop their heads upon death when killed by another player.
 */
public class DropPlayerHeads extends BaseFeature {

    /**
     * Constructor for DropPlayerHeads feature.
     *
     * @param api The STEMCraft API instance.
     */
    public DropPlayerHeads(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Registers the event listener for player deaths to drop player heads.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            LivingEntity killer = player.getKiller();

            if (killer instanceof Player killerPlayer) {
                if (killerPlayer.getUniqueId().equals(player.getUniqueId())) return;
                if (killerPlayer.getGameMode() != GameMode.SURVIVAL) return;

                if (player.getGameMode() == GameMode.SURVIVAL) {
                    ItemStack playerHead = PlayerUtil.getHead(player);
                    event.getDrops().add(playerHead);
                }
            }
        });
    }
}