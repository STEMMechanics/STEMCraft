package dev.stemcraft.minigame.bridge;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Map;

public record BridgeArenaRecord(
        String arenaId,
        boolean enabled,
        String name,
        World world,
        Location lobby,
        Location spectator,
        SCRegion bridgeRegion,
        SCRegion arenaRegion,
        int minPlayers,
        int maxPlayers,
        List<Material> dropItems,
        Map<String, TeamDef> teams
) {
    public record TeamDef(
            String teamId,
            String displayName,
            Location spawn,
            SCRegion portalRegion
    ) {}
}
