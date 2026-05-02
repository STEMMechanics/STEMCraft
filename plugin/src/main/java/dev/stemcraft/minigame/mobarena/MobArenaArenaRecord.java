package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MobArenaArenaRecord(
        String arenaId,
        boolean enabled,
        String name,
        World world,
        SCRegion arenaRegion,
        Location lobby,
        Location spectator,
        int minPlayers,
        int maxPlayers,
        List<SpawnerRecord> spawnTicketList,
        Map<String, SCRegion> zones
) {
    public MobArenaArenaRecord(MiniGameArena arena) {
        this(
                arena.id(),
                arena.getStatus() != MiniGameArena.ArenaStatus.DISABLED,
                arena.getName(),
                arena.world(),
                arena.getRegion(),
                arena.getLobbySpawn(),
                arena.getSpectatorSpawn(),
                arena.getMinPlayers(),
                arena.getMaxPlayers(),
                new ArrayList<>(),
                arena.getMap("zones", String.class, SCRegion.class, new HashMap<>())
        );

        for (int i = 0; i < arena.get("spawner-configs.max", Integer.class, 0); i++) {
            final String spawnerConfigPrefix = "spawner-configs." + i + ".";

            spawnTicketList.add(new SpawnerRecord(
                    arena.get(spawnerConfigPrefix + "entityType", EntityType.class),
                    arena.get(spawnerConfigPrefix + "initialAmount", Integer.class),
                    arena.get(spawnerConfigPrefix + "incrementAmount", Double.class),
                    arena.get(spawnerConfigPrefix + "incrementType", SpawnerRecord.IncrementType.class),
                    arena.get(spawnerConfigPrefix + "initialWave", Integer.class),
                    arena.get(spawnerConfigPrefix + "spawnZone", String.class),
                    arena.get(spawnerConfigPrefix + "countTowardsMobCount", Boolean.class)
            ));
        }
    }

    public record SpawnerRecord(
            EntityType entityType,
            int initialAmount,
            double incrementAmount,
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

