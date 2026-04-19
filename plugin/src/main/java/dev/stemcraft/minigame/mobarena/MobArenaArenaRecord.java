package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;

public record MobArenaArenaRecord(
        String arenaId,
        boolean enabled,
        String name,
        World world,
        Location lobby,
        Location spectator,
        String spawnZone,
        int minPlayers,
        int maxPlayers,
        List<SpawnTicket> spawnTicketList,
        Map<String, SCRegion> zones
) {
    public record SpawnTicket(
            EntityType entityType,
            int initialAmount,
            float incrementAmount,
            int initialWave,
            String spawnZone,
            boolean countTowardsMobCount
    ) {
        public enum IncrementType {
            Linear,
            Exponential
        }
    }
}
