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

public class WorldEditRegionUtil {
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
