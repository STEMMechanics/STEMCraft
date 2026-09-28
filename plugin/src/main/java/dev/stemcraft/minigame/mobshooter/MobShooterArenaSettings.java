package dev.stemcraft.minigame.mobshooter;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent setup only; round state lives in MobShooterArenaHandler.Round.
 */
final class MobShooterArenaSettings {
    final List<Location> spawns = new ArrayList<>();
    final List<Location> targets = new ArrayList<>();
    int roundSeconds = 180;
    int countdown = 10;
    int targetCount = 12;
}
