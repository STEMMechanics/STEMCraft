package dev.stemcraft.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class SCLocation {
    private static final String DELIM = ",";

    static public Location deserialize(String serialized) {
        // world (optional), x, y, z, yaw (optional), pitch (optional)

        String[] parts = serialized.trim().split(DELIM);
        World world = null;
        double x, y, z;
        float yaw = 0f, pitch = 0f;

        if(parts.length < 3) return null;
        int offset = 0;

        if(!isNumber(parts[0])) {
            world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            offset = 1;
        }

        x = Double.parseDouble(parts[offset]);
        y = Double.parseDouble(parts[offset + 1]);
        z = Double.parseDouble(parts[offset + 2]);
        if(parts.length >= 4 + offset) yaw = Float.parseFloat(parts[offset + 3]);
        if(parts.length >= 5 + offset) pitch = Float.parseFloat(parts[offset + 4]);

        return new Location(world, x, y, z, yaw, pitch);
    }

    public static String serialize(Location location, boolean includeWorld, boolean includePitchYaw) {
        if (location == null) return null;

        StringBuilder sb = new StringBuilder();
        if (includeWorld) {
            World world = location.getWorld();
            if (world != null) {
                sb.append(world.getName()).append(DELIM);
            }
        }

        sb.append(location.getX()).append(DELIM)
          .append(location.getY()).append(DELIM)
          .append(location.getZ());

        if (includePitchYaw) {
            sb.append(DELIM).append(location.getYaw())
              .append(DELIM).append(location.getPitch());
        }

        return sb.toString();
    }


    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}