package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.minigame.mobarena.MobArenaSpawnerRecord.IncrementType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>A record that stores information about a Mob Arena arena (until it is processed into the Arena KV-store).</p>
 *
 * @param arenaId        The ID of the arena.
 * @param enabled        Whether the arena will be enabled or not.
 * @param name           The display name for the arena.
 * @param world          The world that the arena is contained in.
 * @param arenaRegion    The region for the arena.
 * @param lobby          The location where everyone will spawn in before the arena starts.
 * @param spectator      The location where spectators will spawn in for the arena.
 * @param minPlayers     The minimum players for the arena.
 * @param maxPlayers     The maximum players for the arena.
 * @param spawnerConfigs The list of spawner configs.
 * @param zones          A map of all zones (String -> {@link SCRegion}).
 */
record MobArenaArenaRecord(
        String arenaId,
        boolean enabled,
        String name,
        World world,
        SCRegion arenaRegion,
        Location lobby,
        Location spectator,
        int minPlayers,
        int maxPlayers,
        List<MobArenaSpawnerRecord> spawnerConfigs,
        Map<String, SCRegion> zones
) {
    /**
     * <p>Creates a MobArenaArenaRecord</p>
     *
     * <p>The arguments of {@code spawnerConfigs} and {@code zones} are copied, instead of being directly assigned.</p>
     *
     * @param arenaId        The ID of the arena.
     * @param enabled        Whether the arena will be enabled or not.
     * @param name           The display name for the arena.
     * @param world          The world that the arena is contained in.
     * @param arenaRegion    The region for the arena.
     * @param lobby          The location where everyone will spawn in before the arena starts.
     * @param spectator      The location where spectators will spawn in for the arena.
     * @param minPlayers     The minimum players for the arena.
     * @param maxPlayers     The maximum players for the arena.
     * @param spawnerConfigs The list of spawner configs.
     * @param zones          A map of all zones (String -> {@link SCRegion}).
     */
    MobArenaArenaRecord {
        spawnerConfigs = List.copyOf(spawnerConfigs);
        zones = Map.copyOf(zones);
    }

    /**
     * <p>Converts a {@link MiniGameArena}'s KV Store to a {@code MobArenaArenaRecord}.</p>
     *
     * @param arena The arena to create a {@code MobArenaArenaRecord} from.
     */
    MobArenaArenaRecord(@NotNull final MiniGameArena arena) {
        this(
                arena.id(),
                arena.getStatus() != ArenaStatus.DISABLED,
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

            spawnerConfigs.add(new MobArenaSpawnerRecord(
                    arena.get(spawnerConfigPrefix + "entityType", EntityType.class),
                    arena.get(spawnerConfigPrefix + "initialAmount", Integer.class),
                    arena.get(spawnerConfigPrefix + "incrementAmount", Double.class),
                    arena.get(spawnerConfigPrefix + "incrementType", IncrementType.class),
                    arena.get(spawnerConfigPrefix + "initialWave", Integer.class),
                    arena.get(spawnerConfigPrefix + "spawnZone", String.class),
                    arena.get(spawnerConfigPrefix + "countTowardsMobCount", Boolean.class)
            ));
        }
    }

}

