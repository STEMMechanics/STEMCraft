package dev.stemcraft.minigame.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record SkyBlockArenaRecord(
        String arenaId,
        UUID ownerUuid,
        String ownerName,
        String worldName,
        Location islandSpawn,
        SkyBlockPlayerState playerState
) {
    public SkyBlockArenaRecord {
        islandSpawn = islandSpawn == null ? null : islandSpawn.clone();
        playerState = playerState == null ? null : playerState.copy(); // or clone/new ...
    }

    @Override
    public Location islandSpawn() {
        return islandSpawn == null ? null : islandSpawn.clone();
    }

    @Override
    public SkyBlockPlayerState playerState() {
        return playerState == null ? null : playerState.copy(); // or immutable view
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }
}