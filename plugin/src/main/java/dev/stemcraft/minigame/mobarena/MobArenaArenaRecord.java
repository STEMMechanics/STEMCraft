package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.minigame.mobarena.MobArenaArenaHandler.MobDeathReason;
import dev.stemcraft.minigame.mobarena.MobArenaSpawnerRecord.IncrementType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/// A record that stores information about a Mob Arena arena (until it is processed into the Arena KV-store).
///
/// @param arenaId                 The ID of the arena.
/// @param enabled                 Whether the arena will be enabled or not.
/// @param name                    The display name for the arena.
/// @param world                   The world that the arena is contained in.
/// @param arenaRegion             The region for the arena.
/// @param lobby                   The location where everyone will spawn in before the arena starts.
/// @param spectator               The location where spectators will spawn in for the arena.
/// @param minPlayers              The minimum players for the arena.
/// @param maxPlayers              The maximum players for the arena.
/// @param spawnerConfigs          The list of spawner configs.
/// @param inventoryLoadout        The preset inventory.
/// @param equipmentLoadout        The preset equipment.
/// @param entityDeathMessagesMode The mode for the entity death messages.
/// @param entityDeathMessages     The death messages for entities.
/// @param playerDeathMessagesMode The mode for the player death messages.
/// @param playerDeathMessages     The death messages for players.
/// @param zones                   A map of all zones (String -> [SCRegion]).
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
        Map<Integer, ItemStack> inventoryLoadout,
        Map<EquipmentSlot, ItemStack> equipmentLoadout,
        MobArenaDeathMessageMode entityDeathMessagesMode,
        Map<MobDeathReason, List<String>> entityDeathMessages,
        MobArenaDeathMessageMode playerDeathMessagesMode,
        List<String> playerDeathMessages,
        Map<String, SCRegion> zones
) {
    /// Creates a MobArenaArenaRecord
    ///
    /// The arguments of `spawnerConfigs` and `zones` are copied, instead of being directly assigned.
    ///
    /// @param arenaId                 The ID of the arena.
    /// @param enabled                 Whether the arena will be enabled or not.
    /// @param name                    The display name for the arena.
    /// @param world                   The world that the arena is contained in.
    /// @param arenaRegion             The region for the arena.
    /// @param lobby                   The location where everyone will spawn in before the arena starts.
    /// @param spectator               The location where spectators will spawn in for the arena.
    /// @param minPlayers              The minimum players for the arena.
    /// @param maxPlayers              The maximum players for the arena.
    /// @param spawnerConfigs          The list of spawner configs.
    /// @param inventoryLoadout        The preset inventory.
    /// @param equipmentLoadout        The preset equipment.
    /// @param entityDeathMessagesMode The mode for the entity death messages.
    /// @param entityDeathMessages     The death messages for entities.
    /// @param playerDeathMessagesMode The mode for the player death messages.
    /// @param playerDeathMessages     The death messages for players.
    /// @param zones                   A map of all zones (String -> [SCRegion]).
    MobArenaArenaRecord {
        spawnerConfigs = new ArrayList<>(spawnerConfigs);
        zones = Map.copyOf(zones);
        entityDeathMessages = entityDeathMessages != null ? Map.copyOf(entityDeathMessages) : new EnumMap<>(MobDeathReason.class);
        playerDeathMessages = playerDeathMessages != null ? List.copyOf(playerDeathMessages) : new ArrayList<>();
        inventoryLoadout = inventoryLoadout != null ? Map.copyOf(inventoryLoadout) : new HashMap<>();
        equipmentLoadout = equipmentLoadout != null ? Map.copyOf(equipmentLoadout) : new EnumMap<>(EquipmentSlot.class);
    }

    /**
     * <p>Converts a {@link MiniGameArena}'s KV Store to a {@code MobArenaArenaRecord}.</p>
     *
     * @param arena The arena to create a {@code MobArenaArenaRecord} from.
     */
    MobArenaArenaRecord(@NotNull final MiniGameArena arena) {
        final Function<MobDeathReason, List<String>> mobDeathReasonListFunction = deathReason -> {
            final String deathReasonLowerCaseName = deathReason.toString().toLowerCase(Locale.ROOT);

            return arena.getList("entity-death-messages." + deathReasonLowerCaseName, String.class);
        };
        final Map<MobDeathReason, List<String>> _entityDeathMessages = Arrays.stream(MobDeathReason.values())
                .filter(deathReason -> mobDeathReasonListFunction.apply(deathReason) != null)
                .collect(
                        Collectors.toMap(
                                Function.identity(),
                                mobDeathReasonListFunction
                        )
                );

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
                arena.getMap("loadout.inventory", Integer.class, ItemStack.class, new HashMap<>()),
                arena.getMap("loadout.equipment", EquipmentSlot.class, ItemStack.class, new EnumMap<>(EquipmentSlot.class)),
                arena.get("entity-death-messages-mode", MobArenaDeathMessageMode.class, MobArenaDeathMessageMode.UNION),
                _entityDeathMessages,
                arena.get("player-death-messages-mode", MobArenaDeathMessageMode.class, MobArenaDeathMessageMode.UNION),
                arena.getList("player-death-messages", String.class),
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

