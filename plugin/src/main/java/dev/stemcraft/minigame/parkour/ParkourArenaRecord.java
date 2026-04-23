package dev.stemcraft.minigame.parkour;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;

public record ParkourArenaRecord(
    String id,
    boolean enabled,
    String name,
    World world,
    SCRegion lobbyRegion,
    SCRegion arenaRegion,
    SCRegion finishRegion,
    Map<UUID, BestTime> bestTimes
) {
    public record BestTime(UUID playerId, String playerName, long timeMillis) { }
}
