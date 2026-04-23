package dev.stemcraft.minigame.bridge;

import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Map;

public record BridgeArenaRecord(
        String arenaId,
        boolean enabled,
        String name,
        String worldName,
        Location lobby,
        Location spectator,
        SCRegion bridgeRegion,
        SCRegion arenaRegion,
        int minPlayers,
        int maxPlayers,
        int startCountdownSeconds,
        int endingSeconds,
        List<Material> dropItems,
        List<Material> dropSurfaceMaterials,
        Map<String, TeamDef> teams
) {
    public BridgeArenaRecord {
        lobby = LocationUtil.copy(lobby);
        spectator = LocationUtil.copy(spectator);
        bridgeRegion = copyRegion(bridgeRegion);
        arenaRegion = copyRegion(arenaRegion);
        dropItems = dropItems == null ? List.of() : List.copyOf(dropItems);
        dropSurfaceMaterials = dropSurfaceMaterials == null ? List.of() : List.copyOf(dropSurfaceMaterials);
        teams = teams == null ? Map.of() : Map.copyOf(teams);
    }

    @Override
    public Location lobby() {
        return LocationUtil.copy(lobby);
    }

    @Override
    public Location spectator() {
        return LocationUtil.copy(spectator);
    }

    @Override
    public SCRegion bridgeRegion() {
        return copyRegion(bridgeRegion);
    }

    @Override
    public SCRegion arenaRegion() {
        return copyRegion(arenaRegion);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public record TeamDef(
            String teamId,
            String displayName,
            Location spawn,
            SCRegion portalRegion
    ) {
        public TeamDef {
            spawn = LocationUtil.copy(spawn);
            portalRegion = copyRegion(portalRegion);
        }

        @Override
        public Location spawn() {
            return LocationUtil.copy(spawn);
        }

        @Override
        public SCRegion portalRegion() {
            return copyRegion(portalRegion);
        }
    }

    private static SCRegion copyRegion(SCRegion region) {
        return region == null ? null : region.copy();
    }
}
