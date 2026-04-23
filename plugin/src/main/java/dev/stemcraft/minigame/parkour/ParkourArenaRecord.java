package dev.stemcraft.minigame.parkour;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;

public record ParkourArenaRecord(
        String id,
        boolean enabled,
        String name,
        String worldName,
        SCRegion lobbyRegion,
        SCRegion arenaRegion,
        SCRegion finishRegion,
        Map<UUID, BestTime> bestTimes
) {
    public ParkourArenaRecord {
        lobbyRegion = copyRegion(lobbyRegion);
        arenaRegion = copyRegion(arenaRegion);
        finishRegion = copyRegion(finishRegion);
        bestTimes = bestTimes == null ? Map.of() : Map.copyOf(bestTimes);
    }

    @Override
    public SCRegion lobbyRegion() {
        return copyRegion(lobbyRegion);
    }

    @Override
    public SCRegion arenaRegion() {
        return copyRegion(arenaRegion);
    }

    @Override
    public SCRegion finishRegion() {
        return copyRegion(finishRegion);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    private static SCRegion copyRegion(SCRegion region) {
        return region == null ? null : region.copy();
    }

    public record BestTime(UUID playerId, String playerName, long timeMillis) { }
}