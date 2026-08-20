package dev.stemcraft.minigame.minefield;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public record MinefieldArenaRecord(
    String id,
    boolean enabled,
    String name,
    String worldName,
    Location spectator,
    SCRegion arenaRegion,
    SCRegion startRegion,
    SCRegion fieldRegion,
    SCRegion finishRegion,
    int minPlayers,
    int maxPlayers,
    int startCountdownSeconds,
    int endingSeconds,
    int configuredMineCount,
    int lives,
    Material hiddenBlock,
    Material clearBlock,
    Material adjacentBlock,
    Material markerBlock,
    Material triggeredMineBlock,
    long bestTimeMillis
) {
    public MinefieldArenaRecord {
        spectator = spectator == null ? null : spectator.clone();
        arenaRegion = copyRegion(arenaRegion);
        startRegion = copyRegion(startRegion);
        fieldRegion = copyRegion(fieldRegion);
        finishRegion = copyRegion(finishRegion);
    }

    @Override
    public Location spectator() {
        return spectator == null ? null : spectator.clone();
    }

    @Override
    public SCRegion arenaRegion() {
        return copyRegion(arenaRegion);
    }

    @Override
    public SCRegion startRegion() {
        return copyRegion(startRegion);
    }

    @Override
    public SCRegion fieldRegion() {
        return copyRegion(fieldRegion);
    }

    @Override
    public SCRegion finishRegion() {
        return copyRegion(finishRegion);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public Location startSpawn() {
        if (startRegion == null) {
            return spectator == null ? null : spectator.clone();
        }
        return MinefieldMiniGame.resolveSpawn(startRegion, spectator);
    }

    private static SCRegion copyRegion(SCRegion region) {
        return region == null ? null : region.copy();
    }
}
