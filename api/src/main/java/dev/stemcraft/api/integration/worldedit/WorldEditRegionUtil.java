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

package dev.stemcraft.api.integration.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.entity.Player;

/**
 * Utility class for working with WorldEdit selections.
 */
public class WorldEditRegionUtil {

    /**
     * Gets the WorldEdit selection for the specified player.
     *
     * @param player The player whose selection to retrieve.
     * @return The SCRegion representing the player's selection, or null if no valid selection exists.
     */
    public static SCRegion getWESelection(Player player) {
        // assume you already checked that WorldEdit is installed/enabled
        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);

        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(wePlayer);

        com.sk89q.worldedit.world.World weWorld = wePlayer.getWorld(); // or BukkitAdapter.adapt(player.getWorld());

        Region region;
        try {
            region = session.getSelection(weWorld);
        } catch (IncompleteRegionException e) {
            // no full selection set
            return null;
        }

        // optionally restrict to types you support for serialize()
        if (!(region instanceof CuboidRegion) && !(region instanceof Polygonal2DRegion)) {
            // unsupported region type for now
            return null;
        }

        return new SCRegion(region, player.getWorld());
    }

    /**
     * Sets the WorldEdit selection for the specified player.
     *
     * @param player The player whose selection to set.
     * @param region The SCRegion to set as the player's selection.
     */
    public static void setWESelection(Player player, SCRegion region) {
        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
        Region weRegion = region.getRegion();

        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(wePlayer);

        com.sk89q.worldedit.world.World weWorld = wePlayer.getWorld();

        RegionSelector selector;

        if (weRegion instanceof CuboidRegion cuboid) {
            selector = new CuboidRegionSelector(
                    weWorld,
                    cuboid.getMinimumPoint(),
                    cuboid.getMaximumPoint()
            );
        } else if (weRegion instanceof Polygonal2DRegion poly) {
            selector = new Polygonal2DRegionSelector(
                    weWorld,
                    poly.getPoints(),
                    poly.getMinimumY(),
                    poly.getMaximumY()
            );
        } else {
            return; // unsupported type for now
        }

        session.setRegionSelector(weWorld, selector);
        session.dispatchCUISelection(wePlayer);
    }
}