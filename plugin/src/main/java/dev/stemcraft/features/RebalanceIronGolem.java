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

package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Feature that rebalances the drops from Iron Golems.
 * Instead of dropping iron ingots, they will drop a configurable number of iron nuggets.
 */
public final class RebalanceIronGolem extends BaseFeature {
    private static final int DEFAULT_MIN_DROPS = 3;
    private static final int DEFAULT_MAX_DROPS = 5;

    /**
     * Constructor for RebalanceIronGolem.
     */
    public RebalanceIronGolem(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is being enabled.
     */
    @Override
    public void onEnable() {
        int min = Math.max(0, getConfigSection().getInt("min-drops", DEFAULT_MIN_DROPS));
        int max = Math.max(0, getConfigSection().getInt("max-drops", DEFAULT_MAX_DROPS));

        final int minDrops = Math.min(min, max);
        final int maxDrops = Math.max(min, max);

        api.events().register(EntityDeathEvent.class, event -> {
            if (event.getEntityType() != EntityType.IRON_GOLEM) return;

            event.getDrops().clear();

            int amount = ThreadLocalRandom.current()
                    .nextInt(minDrops, maxDrops + 1); // inclusive range [min, max]

            event.getDrops().add(new ItemStack(Material.IRON_NUGGET, amount));
        });
    }
}
