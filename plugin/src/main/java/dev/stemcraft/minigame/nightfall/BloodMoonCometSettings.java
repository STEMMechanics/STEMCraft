package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.api.service.comet.CometLoot;
import org.bukkit.Material;

import java.util.List;

public record BloodMoonCometSettings(
    boolean enabled,
    int startNight,
    int chancePercent,
    int chanceIncreasePerNight,
    int maximumChancePercent,
    int maximumPerNight,
    int minimumPlayerDistance,
    int maximumPlayerDistance,
    int arenaEdgeBuffer,
    int pathSafetyLength,
    List<CometLoot> loot
) {
    public BloodMoonCometSettings {
        loot = loot == null ? List.of() : List.copyOf(loot);
    }

    public static BloodMoonCometSettings defaults() {
        return new BloodMoonCometSettings(
            true, 8, 20, 10, 60, 1, 25, 50, 30, 120,
            List.of(
                new CometLoot(Material.GOLD_BLOCK, 2, 8),
                new CometLoot(Material.EMERALD_BLOCK, 1, 4),
                new CometLoot(Material.IRON_BLOCK, 3, 10),
                new CometLoot(Material.DIAMOND_BLOCK, 0, 2)
            ));
    }

    @Override
    public List<CometLoot> loot() {
        return List.copyOf(loot);
    }
}
