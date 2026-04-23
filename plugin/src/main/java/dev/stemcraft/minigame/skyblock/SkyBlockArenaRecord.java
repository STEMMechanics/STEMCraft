package dev.stemcraft.minigame.skyblock;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record SkyBlockArenaRecord(
    String arenaId,
    UUID ownerUuid,
    String ownerName,
    World world,
    Location islandSpawn,
    SkyBlockPlayerState playerState
) {}
