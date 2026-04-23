package dev.stemcraft.minigame.tntrun;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public record TntRunArenaRecord(
        String id,
        boolean enabled,
        String name,
        String worldName,
        Location lobby,
        Location spectator,
        SCRegion arenaRegion,
        List<Location> startingGrid,
        int minPlayers,
        int maxPlayers,
        int startCountdownSeconds,
        int roundSeconds,
        int endingSeconds,
        int fadeDelayTicks,
        int voidY
) {
    public TntRunArenaRecord {
        lobby = clone(lobby);
        spectator = clone(spectator);
        arenaRegion = copy(arenaRegion);
        startingGrid = copyLocations(startingGrid);
    }

    @Override public Location lobby() { return clone(lobby); }
    @Override public Location spectator() { return clone(spectator); }
    @Override public SCRegion arenaRegion() { return copy(arenaRegion); }
    @Override public List<Location> startingGrid() { return copyLocations(startingGrid); }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    private static Location clone(Location loc) {
        return loc == null ? null : loc.clone();
    }

    private static SCRegion copy(SCRegion region) {
        return region == null ? null : region.copy(); // implement if not exists
    }

    private static List<Location> copyLocations(List<Location> list) {
        if (list == null) return List.of();
        List<Location> copy = new ArrayList<>(list.size());
        for (Location l : list) {
            copy.add(l == null ? null : l.clone());
        }
        return List.copyOf(copy);
    }
}
