package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.minigame.ArenaValidationResult;
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
        List<SpawnerRecord> spawnTicketList,
        Map<String, SCRegion> zones
) {
    public record SpawnerRecord(
            EntityType entityType,
            int initialAmount,
            float incrementAmount,
            IncrementType incrementType,
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

