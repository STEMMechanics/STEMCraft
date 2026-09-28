package dev.stemcraft.minigame.minecartrace;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent setup only; round state lives in MinecartRaceArenaHandler.Round.
 */
final class MinecartRaceArenaSettings {
    final List<Location> spawns = new ArrayList<>();
    final List<SCRegion> checkpoints = new ArrayList<>();
    int roundSeconds = 180;
    int countdown = 10;
    int laps = 3;
}
