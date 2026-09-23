package dev.stemcraft.minigame.volcano;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent setup only; round state lives in VolcanoArenaHandler.Round.
 */
final class VolcanoArenaSettings {
    final List<Location> spawns = new ArrayList<>();
    SCRegion floor;
    int roundSeconds = 180;
    int countdown = 10;
}
