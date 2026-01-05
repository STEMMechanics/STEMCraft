package dev.stemcraft.api.util;

import org.bukkit.World;

public class WorldUtil {

    /**
     * Returns the base name of a world, stripping any dimension suffixes.
     */
    public static String baseName(String worldName) {
        if(worldName == null) return null;
        if(worldName.endsWith("_the_end")) {
            return worldName.substring(0, worldName.length() - "_the_end".length());
        } else if(worldName.endsWith("_nether")) {
            return worldName.substring(0, worldName.length() - "_nether".length());
        } else {
            return worldName;
        }
    }

    public static String baseName(World world) {
        if(world == null) return null;
        return baseName(world.getName());
    }
}