package dev.stemcraft.minigame.quake;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent setup only; round state lives in QuakeArenaHandler.Round.
 */
final class QuakeArenaSettings {
    final List<Location> spawns = new ArrayList<>();
    int roundSeconds = 180;
    int countdown = 10;
    int killTarget = 20;
    int reloadTicks = 30;
}
