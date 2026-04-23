package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Map;

public record NightfallArenaRecord(
        String arenaId,
        boolean enabled,
        String name,
        String worldName,
        Location lobby,
        Location spectator,
        Location spawn,
        SCRegion arenaRegion,
        int minPlayers,
        int maxPlayers,
        int startCountdownSeconds,
        int endingSeconds,
        int lives,
        int prepSeconds,
        double dayTimeSpeedMultiplier,
        double nightTimeSpeedMultiplier,
        int dropMinSeconds,
        int dropMaxSeconds,
        int zombieBaseNightlySpawns,
        int zombieNightlySpawnIncrease,
        int zombieWaveSize,
        int zombieWaveIntervalSeconds,
        int zombieSpawnRadiusMin,
        int zombieSpawnRadiusMax,
        int bloodMoonChancePercent,
        List<Location> generatorLocations,
        Map<Integer, List<Material>> dropItems,
        boolean pendingWorldRollback,
        String savedTimeSetting,
        String savedWeatherSetting
) {
    public NightfallArenaRecord {
        lobby = copyLocation(lobby);
        spectator = copyLocation(spectator);
        spawn = copyLocation(spawn);
        arenaRegion = copyRegion(arenaRegion);

        generatorLocations = generatorLocations == null
                ? List.of()
                : generatorLocations.stream().map(NightfallArenaRecord::copyLocation).toList();

        dropItems = dropItems == null
                ? Map.of()
                : dropItems.entrySet().stream()
                  .collect(java.util.stream.Collectors.toUnmodifiableMap(
                          Map.Entry::getKey,
                          e -> List.copyOf(e.getValue())
                  ));
    }

    @Override
    public Location lobby() {
        return copyLocation(lobby);
    }

    @Override
    public Location spectator() {
        return copyLocation(spectator);
    }

    @Override
    public Location spawn() {
        return copyLocation(spawn);
    }

    @Override
    public SCRegion arenaRegion() {
        return copyRegion(arenaRegion);
    }

    @Override
    public List<Location> generatorLocations() {
        return generatorLocations.stream().map(NightfallArenaRecord::copyLocation).toList();
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    @Override
    public Map<Integer, List<Material>> dropItems() {
        return dropItems.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue())
                ));
    }

    private static Location copyLocation(Location location) {
        return location == null ? null : LocationUtil.copy(location);
    }

    private static SCRegion copyRegion(SCRegion region) {
        return region == null ? null : region.copy();
    }
}
