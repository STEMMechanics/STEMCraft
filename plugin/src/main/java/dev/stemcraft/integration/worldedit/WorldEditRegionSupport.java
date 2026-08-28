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

package dev.stemcraft.integration.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for working with WorldEdit selections.
 */
public final class WorldEditRegionSupport {

    private WorldEditRegionSupport() { }

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

        return snapshot(region, player.getWorld());
    }

    /**
     * Gets the player's WorldEdit selection for preview purposes.
     * Falls back to incomplete selector state if a full selection is not yet available.
     *
     * @param player The player whose preview selection to retrieve.
     * @return The preview region, or null if nothing useful can be rendered.
     */
    public static @Nullable SCRegion getWEPreviewSelection(Player player) {
        SCRegion complete = getWESelection(player);
        if (complete != null) {
            return complete;
        }

        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(wePlayer);
        com.sk89q.worldedit.world.World weWorld = wePlayer.getWorld();
        RegionSelector selector = session.getRegionSelector(weWorld);
        if (selector == null) {
            return null;
        }

        Region incomplete = selector.getIncompleteRegion();
        if (!(incomplete instanceof CuboidRegion) && !(incomplete instanceof Polygonal2DRegion)) {
            return null;
        }

        return snapshot(incomplete, player.getWorld());
    }

    /**
     * Gets the player's primary WorldEdit position, if set.
     *
     * @param player The player whose primary position to retrieve.
     * @return The primary selection block center, or null if none is defined.
     */
    public static @Nullable Location getWEPrimaryPosition(Player player) {
        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(wePlayer);
        com.sk89q.worldedit.world.World weWorld = wePlayer.getWorld();
        RegionSelector selector = session.getRegionSelector(weWorld);
        if (selector == null) {
            return null;
        }

        BlockVector3 primary;
        try {
            primary = selector.getPrimaryPosition();
        } catch (IncompleteRegionException e) {
            return null;
        }

        return new Location(player.getWorld(), primary.x() + 0.5, primary.y() + 0.5, primary.z() + 0.5);
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

        com.sk89q.worldedit.world.World weWorld = region.getWorld() != null
                ? BukkitAdapter.adapt(region.getWorld())
                : wePlayer.getWorld();

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

    /**
     * Sets the WorldEdit selection for the specified player to a single block.
     *
     * @param player The player whose selection to set.
     * @param location The location to select.
     */
    public static void setWESelection(Player player, Location location) {
        if (location.getWorld() == null) {
            return;
        }

        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(wePlayer);

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());
        BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        RegionSelector selector = new CuboidRegionSelector(weWorld, point, point);

        session.setRegionSelector(weWorld, selector);
        session.dispatchCUISelection(wePlayer);
    }

    /**
     * Clears the WorldEdit selection for the specified player.
     *
     * @param player The player whose selection should be cleared.
     */
    public static void clearWESelection(Player player) {
        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(wePlayer);

        com.sk89q.worldedit.world.World weWorld = wePlayer.getWorld();
        RegionSelector selector = session.getRegionSelector(weWorld);
        if (selector == null) {
            selector = new CuboidRegionSelector(weWorld);
            session.setRegionSelector(weWorld, selector);
        }

        selector.clear();
        session.dispatchCUISelection(wePlayer);
    }

    private static SCRegion snapshot(Region region, org.bukkit.World world) {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        if (region instanceof CuboidRegion cuboid) {
            Region copy = new CuboidRegion(weWorld, cuboid.getMinimumPoint(), cuboid.getMaximumPoint());
            return new SCRegion(copy, world);
        }

        if (region instanceof Polygonal2DRegion polygon) {
            List<BlockVector2> points = new ArrayList<>(polygon.getPoints());
            Region copy = new Polygonal2DRegion(weWorld, points, polygon.getMinimumY(), polygon.getMaximumY());
            return new SCRegion(copy, world);
        }

        throw new IllegalArgumentException("Unsupported region type: " + region.getClass().getSimpleName());
    }
}
