package dev.stemcraft.minigame.horserace;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent setup only; round state lives in HorseRaceArenaHandler.Round.
 */
final class HorseRaceArenaSettings {
    final List<Location> spawns = new ArrayList<>();
    final List<SCRegion> checkpoints = new ArrayList<>();
    int roundSeconds = 180;
    int countdown = 10;
    int laps = 3;
}
