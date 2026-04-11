package dev.stemcraft.minigame.boatrace;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public record BoatRaceArenaRecord(
    String id,
    boolean enabled,
    String name,
    World world,
    Location lobby,
    Location spectator,
    SCRegion arenaRegion,
    SCRegion finishRegion,
    List<SCRegion> stages,
    List<Location> startingGrid,
    int minPlayers,
    int maxPlayers
) {
}
