package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.api.minigame.MiniGameTeamSelectionInput;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Map;
public record BedWarsArenaRecord(
        String id,
        boolean enabled,
        String name,
        String worldName,
        Location lobby,
        Location spectator,
        SCRegion arenaRegion,
        int minPlayers,
        int maxPlayers,
        int startCountdownSeconds,
        int endingSeconds,
        int teamSize,
        MiniGameTeamSelectionInput teamSelectionInput,
        SCRegion lobbyRegion,
        List<Material> dropItems,
        List<Material> dropSurfaceMaterials,
        Map<String, TeamDef> teams
) {
    public BedWarsArenaRecord {
        lobby = LocationUtil.copy(lobby);
        spectator = LocationUtil.copy(spectator);
        arenaRegion = copyRegion(arenaRegion);
        lobbyRegion = copyRegion(lobbyRegion);
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
    public SCRegion arenaRegion() {
        return copyRegion(arenaRegion);
    }

    public SCRegion lobbyRegion() {
        return copyRegion(lobbyRegion);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    private static SCRegion copyRegion(SCRegion region) {
        return region == null ? null : region.copy();
    }

    public record TeamDef(
            String teamId,
            String displayName,
            Location spawn,
            SCRegion bedRegion
    ) {
        public TeamDef {
            spawn = LocationUtil.copy(spawn);
            bedRegion = copyRegion(bedRegion);
        }

        @Override
        public Location spawn() {
            return LocationUtil.copy(spawn);
        }

        @Override
        public SCRegion bedRegion() {
            return copyRegion(bedRegion);
        }
    }
}
