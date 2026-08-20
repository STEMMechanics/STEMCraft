package dev.stemcraft.minigame.minefield;

import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.service.region.RegionLocationSupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public record MinefieldArenaRecord(
    String id,
    boolean enabled,
    String name,
    String worldName,
    Location spectator,
    SCRegion arenaRegion,
    SCRegion startRegion,
    SCRegion fieldRegion,
    SCRegion finishRegion,
    int minPlayers,
    int maxPlayers,
    int startCountdownSeconds,
    int endingSeconds,
    double mineRatio,
    Material hiddenBlock,
    Material clearBlock,
    Material adjacentBlock,
    Material triggeredMineBlock,
    int completionBonus
) {
    public MinefieldArenaRecord {
        spectator = spectator == null ? null : spectator.clone();
        arenaRegion = copyRegion(arenaRegion);
        startRegion = copyRegion(startRegion);
        fieldRegion = copyRegion(fieldRegion);
        finishRegion = copyRegion(finishRegion);
    }

    @Override
    public Location spectator() {
        return spectator == null ? null : spectator.clone();
    }

    @Override
    public SCRegion arenaRegion() {
        return copyRegion(arenaRegion);
    }

    @Override
    public SCRegion startRegion() {
        return copyRegion(startRegion);
    }

    @Override
    public SCRegion fieldRegion() {
        return copyRegion(fieldRegion);
    }

    @Override
    public SCRegion finishRegion() {
        return copyRegion(finishRegion);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public Location startSpawn() {
        World world = startRegion == null ? world() : startRegion.getWorld();
        if (world == null || startRegion == null) {
            return spectator == null ? null : spectator.clone();
        }

        Location min = startRegion.getMinimumLocation();
        Location max = startRegion.getMaximumLocation();
        Location center = new Location(
            world,
            (min.getBlockX() + max.getBlockX() + 1) / 2.0d,
            min.getBlockY(),
            (min.getBlockZ() + max.getBlockZ() + 1) / 2.0d
        );
        if (startRegion.contains(center)) {
            return center;
        }

        Location ground = RegionLocationSupport.randomGroundLocation(startRegion);
        if (ground != null) {
            return ground;
        }

        Location fallback = RegionLocationSupport.randomLocation(startRegion);
        return fallback == null ? center : fallback;
    }

    private static SCRegion copyRegion(SCRegion region) {
        return region == null ? null : region.copy();
    }
}
