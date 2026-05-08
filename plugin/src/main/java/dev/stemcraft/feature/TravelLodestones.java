/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Travel lodestone feature backed by an in-memory index with persistent storage.
 */
public class TravelLodestones extends BaseFeature {
    private static final String MIGRATION_NAME = "travel-lodestones";

    private final Map<String, TravelLodestoneSupport.TravelLodestoneRecord> lodestonesByKey = new HashMap<>();
    private final Map<Material, Set<TravelLodestoneSupport.TravelLodestoneRecord>> lodestonesByType = new EnumMap<>(Material.class);

    private double explosionChance = 0.05D;
    private float explosionPower = 2.5F;
    private double explosionDamage = 8.0D;
    private int teleportSearchRadius = 5;
    private Sound activationSound = Sound.BLOCK_BEACON_ACTIVATE;
    private Sound travelSound = Sound.BLOCK_PORTAL_TRAVEL;

    /**
     * Constructor.
     *
     * @param api The STEMCraft API instance.
     */
    public TravelLodestones(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is enabled.
     */
    @Override
    public void onEnable() {
        loadConfig();
        ensureStorage();
        loadAllLoadedWorlds();

        api.events().register(WorldLoadEvent.class, event -> loadWorld(event.getWorld()));
        api.events().register(WorldUnloadEvent.class, event -> unloadWorld(event.getWorld().getUID()));

        api.events().register(BlockPlaceEvent.class, event -> {
            TravelLodestoneSupport.Structure structure = TravelLodestoneSupport.detectStructure(event.getBlockPlaced());
            if (structure == null) {
                return;
            }

            activateOrExplode(structure, event.getPlayer());
        }, EventPriority.MONITOR, true);

        api.events().register(BlockBreakEvent.class, event -> {
            TravelLodestoneSupport.TravelLodestoneRecord record = findTrackedStructure(event.getBlock());
            if (record != null) {
                removeRecord(record);
            }
        }, EventPriority.MONITOR, true);

        api.events().register(EntityExplodeEvent.class, event -> removeDestroyed(event.blockList()), EventPriority.MONITOR, true);
        api.events().register(BlockExplodeEvent.class, event -> removeDestroyed(event.blockList()), EventPriority.MONITOR, true);

        api.events().register(PlayerInteractEvent.class, event -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
                return;
            }

            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || clickedBlock.getType() != Material.LODESTONE) {
                return;
            }

            TravelLodestoneSupport.TravelLodestoneRecord source = lodestonesByKey.get(key(clickedBlock));
            if (source == null) {
                return;
            }

            event.setCancelled(true);
            handleTravel(event.getPlayer(), source);
        }, EventPriority.NORMAL, true);
    }

    @Override
    public void onReload() {
        super.onReload();
        loadConfig();
    }

    @Override
    public void onDisable() {
        lodestonesByKey.clear();
        lodestonesByType.clear();
    }

    private void loadConfig() {
        ConfigSection config = getConfigSection();
        explosionChance = clamp(config.getDouble("explosion-chance", 0.05D));
        explosionPower = (float) Math.max(0.0D, config.getDouble("explosion-power", 2.5D));
        explosionDamage = Math.max(0.0D, config.getDouble("explosion-damage", 8.0D));
        teleportSearchRadius = Math.max(1, config.getInt("teleport-search-radius", 5));
        activationSound = parseSound(config.getString("activation-sound", "BLOCK_BEACON_ACTIVATE"), Sound.BLOCK_BEACON_ACTIVATE);
        travelSound = parseSound(config.getString("travel-sound", "BLOCK_PORTAL_TRAVEL"), Sound.BLOCK_PORTAL_TRAVEL);
    }

    private void ensureStorage() {
        if (api.database().migrationVersion(MIGRATION_NAME) >= 1) {
            return;
        }

        api.database().execute("""
                CREATE TABLE IF NOT EXISTS travel_lodestones (
                  world_uuid TEXT NOT NULL,
                  world_name TEXT NOT NULL,
                  x INTEGER NOT NULL,
                  y INTEGER NOT NULL,
                  z INTEGER NOT NULL,
                  support_material TEXT NOT NULL,
                  PRIMARY KEY (world_uuid, x, y, z)
                );
                """);
        api.database().setMigrationVersion(MIGRATION_NAME, 1);
    }

    private void loadAllLoadedWorlds() {
        lodestonesByKey.clear();
        lodestonesByType.clear();

        for (World world : Bukkit.getWorlds()) {
            loadWorld(world);
        }
    }

    private void loadWorld(@NotNull World world) {
        unloadWorld(world.getUID());

        api.database().queryEach(
                "SELECT world_uuid, world_name, x, y, z, support_material FROM travel_lodestones WHERE world_uuid = ?",
                ps -> ps.setString(1, world.getUID().toString()),
                rs -> {
                    Material supportMaterial = Material.matchMaterial(rs.getString("support_material"));
                    if (supportMaterial == null || !TravelLodestoneSupport.VALID_SUPPORT_BLOCKS.contains(supportMaterial)) {
                        deleteRow(world.getUID(), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                        return;
                    }

                    TravelLodestoneSupport.TravelLodestoneRecord record = new TravelLodestoneSupport.TravelLodestoneRecord(
                            UUID.fromString(rs.getString("world_uuid")),
                            rs.getString("world_name"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            supportMaterial
                    );

                    if (!TravelLodestoneSupport.isStructureActive(world, record)) {
                        deleteRow(record);
                        return;
                    }

                    cacheRecord(record);
                }
        );
    }

    private void unloadWorld(@NotNull UUID worldId) {
        Set<String> keysToRemove = new HashSet<>();
        for (TravelLodestoneSupport.TravelLodestoneRecord record : lodestonesByKey.values()) {
            if (!record.worldId().equals(worldId)) {
                continue;
            }
            keysToRemove.add(record.key());
        }

        for (String key : keysToRemove) {
            TravelLodestoneSupport.TravelLodestoneRecord removed = lodestonesByKey.remove(key);
            if (removed == null) {
                continue;
            }

            Set<TravelLodestoneSupport.TravelLodestoneRecord> typed = lodestonesByType.get(removed.supportMaterial());
            if (typed != null) {
                typed.remove(removed);
                if (typed.isEmpty()) {
                    lodestonesByType.remove(removed.supportMaterial());
                }
            }
        }
    }

    private void activateOrExplode(@NotNull TravelLodestoneSupport.Structure structure, @NotNull Player player) {
        TravelLodestoneSupport.TravelLodestoneRecord record = structure.toRecord();
        if (lodestonesByKey.containsKey(record.key())) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() < explosionChance) {
            explodeDuringActivation(structure, player);
            return;
        }

        cacheRecord(record);
        upsertRecord(record);

        World world = structure.lodestoneBlock().getWorld();
        Location center = structure.lodestoneBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        world.playSound(center, activationSound, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    private void explodeDuringActivation(@NotNull TravelLodestoneSupport.Structure structure, @NotNull Player player) {
        Block lodestoneBlock = structure.lodestoneBlock();
        World world = lodestoneBlock.getWorld();
        Location center = lodestoneBlock.getLocation().add(0.5D, 0.5D, 0.5D);

        for (Block block : structure.blocks()) {
            block.setType(Material.AIR, false);
        }

        world.createExplosion(center, explosionPower, false, false, player);
        if (!player.isDead()) {
            player.damage(explosionDamage, player);
        }
    }

    private void handleTravel(@NotNull Player player, @NotNull TravelLodestoneSupport.TravelLodestoneRecord source) {
        Set<TravelLodestoneSupport.TravelLodestoneRecord> candidates = lodestonesByType.get(source.supportMaterial());
        if (candidates == null || candidates.isEmpty()) {
            api.messages().warn(player, "No matching travel lodestone destination found.");
            return;
        }

        TravelLodestoneSupport.TravelLodestoneRecord destination =
                TravelLodestoneSupport.findClosestSameWorldDestination(source, candidates);
        if (destination == null) {
            api.messages().warn(player, "No matching travel lodestone destination found.");
            return;
        }

        World destinationWorld = TravelLodestoneSupport.TravelLodestoneRecord.world(destination);
        if (destinationWorld == null) {
            removeRecord(destination);
            api.messages().warn(player, "Travel lodestone destination is currently unavailable.");
            return;
        }

        Location location = TravelLodestoneSupport.findSafeTeleportLocation(destinationWorld, destination, teleportSearchRadius);
        if (location == null) {
            if (!player.isDead()) {
                player.setHealth(0.0D);
            }
            return;
        }

        player.playSound(player.getLocation(), travelSound, SoundCategory.PLAYERS, 1.0F, 1.0F);
        PlayerUtil.teleport(player, location);
    }

    private void removeDestroyed(@NotNull Iterable<Block> blocks) {
        Set<String> removed = new HashSet<>();
        for (Block block : blocks) {
            TravelLodestoneSupport.TravelLodestoneRecord record = findTrackedStructure(block);
            if (record == null || !removed.add(record.key())) {
                continue;
            }
            removeRecord(record);
        }
    }

    private @Nullable TravelLodestoneSupport.TravelLodestoneRecord findTrackedStructure(@NotNull Block block) {
        return TravelLodestoneSupport.recordForStructureBlock(block, candidate -> lodestonesByKey.get(key(candidate)));
    }

    private void cacheRecord(@NotNull TravelLodestoneSupport.TravelLodestoneRecord record) {
        lodestonesByKey.put(record.key(), record);
        lodestonesByType.computeIfAbsent(record.supportMaterial(), unused -> new HashSet<>()).add(record);
    }

    private void removeRecord(@NotNull TravelLodestoneSupport.TravelLodestoneRecord record) {
        lodestonesByKey.remove(record.key());
        Set<TravelLodestoneSupport.TravelLodestoneRecord> typed = lodestonesByType.get(record.supportMaterial());
        if (typed != null) {
            typed.remove(record);
            if (typed.isEmpty()) {
                lodestonesByType.remove(record.supportMaterial());
            }
        }
        deleteRow(record);
    }

    private void upsertRecord(@NotNull TravelLodestoneSupport.TravelLodestoneRecord record) {
        api.database().update(
                """
                INSERT INTO travel_lodestones (world_uuid, world_name, x, y, z, support_material)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(world_uuid, x, y, z) DO UPDATE SET
                  world_name = excluded.world_name,
                  support_material = excluded.support_material
                """,
                ps -> {
                    ps.setString(1, record.worldId().toString());
                    ps.setString(2, record.worldName());
                    ps.setInt(3, record.x());
                    ps.setInt(4, record.y());
                    ps.setInt(5, record.z());
                    ps.setString(6, record.supportMaterial().name());
                }
        );
    }

    private void deleteRow(@NotNull TravelLodestoneSupport.TravelLodestoneRecord record) {
        deleteRow(record.worldId(), record.x(), record.y(), record.z());
    }

    private void deleteRow(@NotNull UUID worldId, int x, int y, int z) {
        api.database().update(
                "DELETE FROM travel_lodestones WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?",
                ps -> {
                    ps.setString(1, worldId.toString());
                    ps.setInt(2, x);
                    ps.setInt(3, y);
                    ps.setInt(4, z);
                }
        );
    }

    private static Sound parseSound(@Nullable String raw, @NotNull Sound fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            String soundName = raw.trim().toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.minecraft(soundName);

            return Registry.SOUNDS.get(key);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value) {
        return Math.clamp(1.0, 0.0, value);
    }

    private static String key(@NotNull Block block) {
        return TravelLodestoneSupport.key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
