package dev.stemcraft.minigame.boatrace;

import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BoatRaceArenaRecord(
        String id,
        boolean enabled,
        String name,
        String worldName,
        Location lobby,
        Location spectator,
        SCRegion arenaRegion,
        SCRegion finishRegion,
        List<SCRegion> stages,
        List<Location> startingGrid,
        int minPlayers,
        int maxPlayers,
        Map<UUID, BestTime> bestTimes
) {
    public BoatRaceArenaRecord {
        lobby = LocationUtil.copy(lobby);
        spectator = LocationUtil.copy(spectator);
        arenaRegion = copyRegion(arenaRegion);
        finishRegion = copyRegion(finishRegion);
        stages = stages == null ? List.of() : stages.stream().map(BoatRaceArenaRecord::copyRegion).toList();
        startingGrid = startingGrid == null ? List.of() : startingGrid.stream().map(LocationUtil::copy).toList();
        bestTimes = bestTimes == null ? Map.of() : Map.copyOf(bestTimes);
    }

    @Override
    public Location lobby() {
        return LocationUtil.copy(lobby);
    }

    @Override
    public Location spectator() {
        return LocationUtil.copy(spectator);
    }

    @Override
    public SCRegion arenaRegion() {
        return copyRegion(arenaRegion);
    }

    @Override
    public SCRegion finishRegion() {
        return copyRegion(finishRegion);
    }

    @Override
    public List<SCRegion> stages() {
        return stages.stream().map(BoatRaceArenaRecord::copyRegion).toList();
    }

    @Override
    public List<Location> startingGrid() {
        return startingGrid.stream().map(LocationUtil::copy).toList();
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    private static SCRegion copyRegion(SCRegion region) {
        return region == null ? null : region.copy();
    }

    public record BestTime(UUID playerId, String playerName, long timeMillis) { }
}