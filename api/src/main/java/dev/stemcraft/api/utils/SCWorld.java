package dev.stemcraft.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class SCWorld {
    final World world;

    public SCWorld(World world) {
        this.world = world;
    }

    public SCWorld evictAllPlayers() {
        World firstWorld = Bukkit.getWorlds().getFirst();

        if(this.world.equals(firstWorld)) {
            throw new IllegalStateException("Cannot evict players from the main world");
        }

        world.getPlayers().forEach(player -> {
            SCPlayer.teleport(player, Bukkit.getWorlds().getFirst().getSpawnLocation());
        });

        return this;
    }

    /**
     * Convert a world time to a real time.
     *
     * @param world The world to convert.
     * @return The converted time.
     */
    public static String convertWorldToRealTime(World world) {
        long time = world.getTime();

        // Shift so 0 ticks = 6:00 AM
        long adjusted = (time + 6000) % 24000;

        int hours24 = (int) (adjusted / 1000);
        int minutes = (int) ((adjusted % 1000) * 60 / 1000);

        String amPm = hours24 >= 12 ? "PM" : "AM";

        int hours12 = hours24 % 12;
        if (hours12 == 0) hours12 = 12;

        return String.format("%d:%02d %s", hours12, minutes, amPm);
    }
}
