package dev.stemcraft.service.region;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/** Plugin-side world lookups for API region values. */
public final class RegionLocationSupport {
    private RegionLocationSupport() { }

    public static @Nullable Location randomLocation(SCRegion scRegion) {
        World world = scRegion.getWorld();
        if (world == null) return null;
        Region region = scRegion.getRegion();
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempts = 0; attempts < 1000; attempts++) {
            int x = random.nextInt(minimum.x(), maximum.x() + 1);
            int y = random.nextInt(minimum.y(), maximum.y() + 1);
            int z = random.nextInt(minimum.z(), maximum.z() + 1);
            if (region.contains(BlockVector3.at(x, y, z)))
                return new Location(world, x + 0.5D, y + 0.5D, z + 0.5D);
        }
        return null;
    }

    public static @Nullable Location randomGroundLocation(SCRegion region) {
        for (int attempts = 0; attempts < 200; attempts++) {
            Location base = randomLocation(region);
            if (base == null) continue;
            World world = base.getWorld();
            int x = base.getBlockX();
            int z = base.getBlockZ();
            for (int y = base.getBlockY(); y > world.getMinHeight(); y--) {
                Block ground = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                if (ground.getType().isSolid() && feet.getType().isAir() && head.getType().isAir())
                    return new Location(world, x + 0.5D, y, z + 0.5D);
            }
        }
        return null;
    }
}
