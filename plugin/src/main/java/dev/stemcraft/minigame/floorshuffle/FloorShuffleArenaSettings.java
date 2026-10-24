package dev.stemcraft.minigame.floorshuffle;

import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent setup only; round state lives in FloorShuffleArenaHandler.Round.
 */
final class FloorShuffleArenaSettings {
    final List<Location> spawns = new ArrayList<>();
    SCRegion floor;
    int roundSeconds = 180;
    int countdown = 30;
    int startDelaySeconds = MiniGameConfigSupport.DEFAULT_START_DELAY_SECONDS;
    int lobbyShuffleSeconds = 3;
}
