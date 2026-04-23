package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Map;

public record NightfallArenaRecord(
    String arenaId,
    boolean enabled,
    String name,
    World world,
    Location lobby,
    Location spectator,
    Location spawn,
    SCRegion arenaRegion,
    int minPlayers,
    int maxPlayers,
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
) {}
