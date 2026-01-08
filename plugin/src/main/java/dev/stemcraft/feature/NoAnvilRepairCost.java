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
import org.bukkit.event.inventory.PrepareAnvilEvent;

/**
 * Feature that removes the repair cost in anvils.
 */
public class NoAnvilRepairCost extends BaseFeature {

    /**
     * Constructor for the NoAnvilRepairCost feature.
     *
     * @param api The STEMCraftAPI instance.
     */
    public NoAnvilRepairCost(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Registers the event listener to remove anvil repair costs.
     */
    @Override
    public void onEnable() {
        api.events().register(PrepareAnvilEvent.class, event -> {
            //noinspection UnstableApiUsage
            event.getView().setRepairCost(0);
        });
    }
}