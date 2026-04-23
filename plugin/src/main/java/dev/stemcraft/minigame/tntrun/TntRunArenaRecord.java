package dev.stemcraft.minigame.tntrun;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public record TntRunArenaRecord(
    String id,
    boolean enabled,
    String name,
    World world,
    Location lobby,
    Location spectator,
    SCRegion arenaRegion,
    List<SCRegion> floorRegions,
    List<Location> startingGrid,
    int minPlayers,
    int maxPlayers,
    int startCountdownSeconds,
    int roundSeconds,
    int endingSeconds,
    int fadeDelayTicks,
    int voidY
) {}
