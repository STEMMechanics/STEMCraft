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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Randomizes the initial spawn location for first-time players in configured worlds.
 */
public class RandomFirstSpawn extends BaseFeature {
    private static final int DEFAULT_DELAY_TICKS = 20;
    private static final int DEFAULT_MIN_RADIUS = 1000;
    private static final int DEFAULT_MAX_RADIUS = 4000;
    private static final int DEFAULT_ATTEMPTS = 96;
    private static final int DEFAULT_MIN_DISTANCE_FROM_PLAYERS = 384;
    private static final int DEFAULT_BORDER_BUFFER = 16;
    private static final List<String> DEFAULT_AVOID_BIOMES = List.of(
            "OCEAN",
            "DEEP_OCEAN",
            "FROZEN_OCEAN",
            "RIVER",
            "FROZEN_RIVER"
    );
    private static final List<String> DEFAULT_AVOID_BLOCKS = List.of(
            "WATER",
            "LAVA",
            "CACTUS",
            "MAGMA_BLOCK",
            "FIRE",
            "SOUL_FIRE",
            "CAMPFIRE",
            "SOUL_CAMPFIRE",
            "POWDER_SNOW"
    );

    private final Map<String, WorldRule> worldRules = new HashMap<>();
    private final Set<String> seenWorldEntries = ConcurrentHashMap.newKeySet();
    private final Map<String, StoredSpawn> storedSpawnsByPlayerWorld = new ConcurrentHashMap<>();
    private int delayTicks;

    private record WorldRule(
            int minRadius,
            int maxRadius,
            int attempts,
            int minPlayerDistance,
            int borderBuffer,
            boolean avoidLiquids,
            boolean avoidLeaves,
            Set<Biome> avoidBiomes,
            Set<Material> avoidBlocks
    ) {
        long minPlayerDistanceSq() {
            long d = Math.max(0, minPlayerDistance);
            return d * d;
        }
    }

    private record StoredSpawn(double x, double y, double z, float yaw, float pitch) {
        Location toLocation(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public RandomFirstSpawn(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        loadConfig();
        ensureStorage();
        loadSeenWorldEntries();
        loadStoredSpawns();

        if (worldRules.isEmpty()) {
            return;
        }

        api.events().register(PlayerJoinEvent.class, event -> handleWorldEntry(event.getPlayer(), event.getPlayer().getWorld()));
        api.events().register(PlayerChangedWorldEvent.class, event -> handleWorldEntry(event.getPlayer(), event.getPlayer().getWorld()));
        api.events().register(PlayerRespawnEvent.class, this::handleRespawn, EventPriority.HIGHEST, true);
    }

    private void handleWorldEntry(Player player, World world) {
        loadConfig();
        if (world == null) {
            return;
        }

        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (hasSeenWorld(player.getUniqueId(), worldName)) {
            return;
        }

        WorldRule rule = worldRules.get(world.getName().toLowerCase(Locale.ROOT));

        if (rule == null) {
            return;
        }

        api.tasks().runLater(delayTicks, () -> applyRandomSpawn(player, world, rule));
    }

    private void applyRandomSpawn(Player player, World world, WorldRule rule) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(world.getName())) {
            return;
        }

        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (hasSeenWorld(player.getUniqueId(), worldName)) {
            return;
        }

        Location chosen = findRandomSpawn(world, rule, player.getUniqueId());
        if (chosen == null) {
            chosen = world.getSpawnLocation();
            api.messages().warn("RANDOM_FIRST_SPAWN_FALLBACK", "player", player.getName(), "world", world.getName());
        }

        Location destination = chosen.clone();
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        PlayerUtil.teleport(player, destination);
        markSeenWorld(player.getUniqueId(), worldName, destination);
        api.messages().send(player, api.messages().text(player, "RANDOM_FIRST_SPAWN_TELEPORTED", "world", world.getName()));
    }

    private void handleRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }

        Player player = event.getPlayer();
        World deathWorld = player.getWorld();
        String forceSpawn = api.worlds().getSetting(deathWorld, "force-spawn-on-death");
        if ("true".equalsIgnoreCase(forceSpawn)) {
            return;
        }

        String worldName = deathWorld.getName().toLowerCase(Locale.ROOT);
        if (!worldRules.containsKey(worldName)) {
            return;
        }

        StoredSpawn storedSpawn = storedSpawn(player.getUniqueId(), worldName);
        if (storedSpawn == null) {
            return;
        }

        event.setRespawnLocation(storedSpawn.toLocation(deathWorld));
    }

    private Location findRandomSpawn(World world, WorldRule rule, UUID targetPlayerId) {
        Location center = world.getSpawnLocation();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int maxRadius = Math.max(0, rule.maxRadius());
        int minRadius = Math.clamp(rule.minRadius(), 0, maxRadius);

        for (int i = 0; i < Math.max(1, rule.attempts()); i++) {
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            double radius = (minRadius == maxRadius)
                    ? minRadius
                    : random.nextDouble(minRadius, maxRadius + 1.0);

            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int z = centerZ + (int) Math.round(Math.sin(angle) * radius);

            Location location = createCandidate(world, x, z, rule, targetPlayerId);
            if (location != null) {
                return location;
            }
        }

        return null;
    }

    private Location createCandidate(World world, int x, int z, WorldRule rule, UUID targetPlayerId) {
        if (!isInsideWorldBorder(world, x, z, rule.borderBuffer())) {
            return null;
        }

        int highestY = world.getHighestBlockYAt(x, z);
        if (highestY < world.getMinHeight()) {
            return null;
        }

        int feetY = highestY + 1;
        int headY = highestY + 2;
        if (headY >= world.getMaxHeight()) {
            return null;
        }

        Block ground = world.getBlockAt(x, highestY, z);
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, headY, z);

        Material groundType = ground.getType();
        if (rule.avoidBlocks().contains(groundType)) {
            return null;
        }
        if (rule.avoidLeaves() && Tag.LEAVES.isTagged(groundType)) {
            return null;
        }
        if (rule.avoidLiquids() && (ground.isLiquid() || feet.isLiquid() || head.isLiquid())) {
            return null;
        }
        if (!feet.isPassable() || !head.isPassable()) {
            return null;
        }

        Biome biome = ground.getBiome();
        if (rule.avoidBiomes().contains(biome)) {
            return null;
        }

        if (!isFarEnoughFromPlayers(world, x, feetY, z, rule.minPlayerDistanceSq(), targetPlayerId)) {
            return null;
        }

        return new Location(world, x + 0.5, feetY, z + 0.5);
    }

    private boolean isFarEnoughFromPlayers(World world, int x, int y, int z, long minDistanceSq, UUID targetPlayerId) {
        if (minDistanceSq <= 0) {
            return true;
        }

        Location candidate = new Location(world, x + 0.5, y, z + 0.5);
        for (Player player : world.getPlayers()) {
            if (!player.isOnline() || player.getUniqueId().equals(targetPlayerId)) {
                continue;
            }

            if (player.getLocation().distanceSquared(candidate) < minDistanceSq) {
                return false;
            }
        }

        return true;
    }

    private boolean isInsideWorldBorder(World world, int x, int z, int buffer) {
        WorldBorder border = world.getWorldBorder();
        double half = (border.getSize() / 2.0) - Math.max(0, buffer);
        if (half <= 0.0) {
            return false;
        }

        Location center = border.getCenter();
        double dx = Math.abs((x + 0.5) - center.getX());
        double dz = Math.abs((z + 0.5) - center.getZ());
        return dx <= half && dz <= half;
    }

    private void loadConfig() {
        ConfigSection section = getConfigSection();
        delayTicks = Math.max(0, section.getInt("delay-ticks", DEFAULT_DELAY_TICKS));
        worldRules.clear();

        ConfigSection worldsSection = section.getSection("worlds");
        if (worldsSection == null) {
            return;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            ConfigSection worldSection = worldsSection.getSection(worldName);
            if (worldSection == null || !worldSection.getBoolean("enabled", true)) {
                continue;
            }

            int minRadius = worldSection.getInt("min-radius", DEFAULT_MIN_RADIUS);
            int maxRadius = worldSection.getInt("max-radius", DEFAULT_MAX_RADIUS);
            int attempts = worldSection.getInt("attempts", DEFAULT_ATTEMPTS);
            int minDistance = worldSection.getInt("min-distance-from-players", DEFAULT_MIN_DISTANCE_FROM_PLAYERS);
            int borderBuffer = worldSection.getInt("border-buffer", DEFAULT_BORDER_BUFFER);
            boolean avoidLiquids = worldSection.getBoolean("avoid-liquids", true);
            boolean avoidLeaves = worldSection.getBoolean("avoid-leaves", true);

            Set<Biome> avoidBiomes = parseBiomes(worldSection.getStringList("avoid-biomes"), worldName);
            if (avoidBiomes.isEmpty()) {
                avoidBiomes = parseBiomes(DEFAULT_AVOID_BIOMES, worldName);
            }

            Set<Material> avoidBlocks = parseMaterials(worldSection.getStringList("avoid-blocks"), worldName);
            if (avoidBlocks.isEmpty()) {
                avoidBlocks = parseMaterials(DEFAULT_AVOID_BLOCKS, worldName);
            }

            worldRules.put(worldName.toLowerCase(Locale.ROOT), new WorldRule(
                    minRadius,
                    maxRadius,
                    attempts,
                    minDistance,
                    borderBuffer,
                    avoidLiquids,
                    avoidLeaves,
                    avoidBiomes,
                    avoidBlocks
            ));

            if (Bukkit.getWorld(worldName) == null) {
                api.messages().warn("RANDOM_FIRST_SPAWN_WORLD_NOT_FOUND", "world", worldName);
            }
        }
    }

    private Set<Biome> parseBiomes(List<String> raw, String worldName) {
        Set<Biome> out = new HashSet<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }

            String normalized = item.trim().toLowerCase(Locale.ROOT);
            Biome biome = resolveBiome(normalized);
            if (biome == null) {
                api.messages().warn("RANDOM_FIRST_SPAWN_INVALID_BIOME", "biome", item, "world", worldName);
                continue;
            }

            out.add(biome);
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private Biome resolveBiome(String normalized) {
        return Registry.BIOME.get(NamespacedKey.minecraft(normalized));
    }

    private Set<Material> parseMaterials(List<String> raw, String worldName) {
        Set<Material> out = new HashSet<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }

            Material material = Material.matchMaterial(item.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                api.messages().warn("RANDOM_FIRST_SPAWN_INVALID_BLOCK", "block", item, "world", worldName);
                continue;
            }

            out.add(material);
        }

        return out;
    }

    private void ensureStorage() {
        if (api.database().migrationVersion("random-first-spawn") >= 2) {
            return;
        }

        boolean createdSeen = api.database().execute(
            "CREATE TABLE IF NOT EXISTS random_first_spawn_seen (" +
            "uuid TEXT NOT NULL," +
            "world TEXT NOT NULL," +
            "seen_at INTEGER NOT NULL," +
            "PRIMARY KEY(uuid, world)" +
            ");"
        );
        if (!createdSeen) {
            api.messages().error("Failed to create random_first_spawn_seen table.");
            return;
        }

        boolean createdSpawns = api.database().execute(
            "CREATE TABLE IF NOT EXISTS random_first_spawn_spawns (" +
            "uuid TEXT NOT NULL," +
            "world TEXT NOT NULL," +
            "x REAL NOT NULL," +
            "y REAL NOT NULL," +
            "z REAL NOT NULL," +
            "yaw REAL NOT NULL," +
            "pitch REAL NOT NULL," +
            "updated_at INTEGER NOT NULL," +
            "PRIMARY KEY(uuid, world)" +
            ");"
        );
        if (!createdSpawns) {
            api.messages().error("Failed to create random_first_spawn_spawns table.");
            return;
        }

        api.database().setMigrationVersion("random-first-spawn", 2);
    }

    private void loadSeenWorldEntries() {
        seenWorldEntries.clear();
        api.database().queryEach(
            "SELECT uuid, world FROM random_first_spawn_seen",
            null,
            rs -> {
                String uuid = rs.getString("uuid");
                String world = rs.getString("world");
                if (uuid == null || world == null) {
                    return;
                }
                seenWorldEntries.add(uuid.toLowerCase(Locale.ROOT) + "|" + world.toLowerCase(Locale.ROOT));
            }
        );
    }

    private void loadStoredSpawns() {
        storedSpawnsByPlayerWorld.clear();
        api.database().queryEach(
            "SELECT uuid, world, x, y, z, yaw, pitch FROM random_first_spawn_spawns",
            null,
            rs -> {
                String uuid = rs.getString("uuid");
                String world = rs.getString("world");
                if (uuid == null || world == null) {
                    return;
                }

                storedSpawnsByPlayerWorld.put(
                    key(uuid, world),
                    new StoredSpawn(
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        (float) rs.getDouble("yaw"),
                        (float) rs.getDouble("pitch")
                    )
                );
            }
        );
    }

    private boolean hasSeenWorld(UUID uuid, String worldName) {
        return seenWorldEntries.contains(uuid.toString().toLowerCase(Locale.ROOT) + "|" + worldName.toLowerCase(Locale.ROOT));
    }

    private void markSeenWorld(UUID uuid, String worldName, Location assignedSpawn) {
        String uuidValue = uuid.toString().toLowerCase(Locale.ROOT);
        String worldValue = worldName.toLowerCase(Locale.ROOT);
        String key = uuidValue + "|" + worldValue;
        seenWorldEntries.add(key);
        api.database().update(
            "INSERT INTO random_first_spawn_seen (uuid, world, seen_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, world) DO UPDATE SET seen_at = excluded.seen_at",
            ps -> {
                ps.setString(1, uuidValue);
                ps.setString(2, worldValue);
                ps.setLong(3, System.currentTimeMillis());
            }
        );

        storeSpawn(uuidValue, worldValue, assignedSpawn);
    }

    private void storeSpawn(String uuidValue, String worldValue, Location spawn) {
        if (spawn == null) {
            return;
        }

        storedSpawnsByPlayerWorld.put(
            key(uuidValue, worldValue),
            new StoredSpawn(spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch())
        );

        api.database().update(
            "INSERT INTO random_first_spawn_spawns (uuid, world, x, y, z, yaw, pitch, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid, world) DO UPDATE SET " +
                "x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch, updated_at = excluded.updated_at",
            ps -> {
                ps.setString(1, uuidValue);
                ps.setString(2, worldValue);
                ps.setDouble(3, spawn.getX());
                ps.setDouble(4, spawn.getY());
                ps.setDouble(5, spawn.getZ());
                ps.setDouble(6, spawn.getYaw());
                ps.setDouble(7, spawn.getPitch());
                ps.setLong(8, System.currentTimeMillis());
            }
        );
    }

    private StoredSpawn storedSpawn(UUID uuid, String worldName) {
        return storedSpawnsByPlayerWorld.get(key(uuid.toString(), worldName));
    }

    private static String key(String uuidValue, String worldValue) {
        return uuidValue.toLowerCase(Locale.ROOT) + "|" + worldValue.toLowerCase(Locale.ROOT);
    }
}
