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

import org.bukkit.Bukkit;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Example feature that registers a custom world flag through the STEMCraft API
 * and overrides leaf decay random ticking without changing the world's global
 * random tick speed.
 */
public final class LeafDecayRandomTickFeature extends BaseFeature {
    private static final String TASK_ID = "feature:leaf-decay-random-tick";

    private final Map<UUID, Map<Long, Long>> decayCandidates = new HashMap<>();
    private final Map<UUID, Set<Long>> allowedDecayTicks = new HashMap<>();
    private final Map<String, PendingReplant> pendingReplants = new HashMap<>();

    private int scanRadius;
    private int defaultTickSpeed;
    private long candidateRetentionTicks;
    private boolean replantEnabled;
    private long replantDelayMillis;
    private long replantLifetimeMillis;
    private int replantPlayerRadius;
    private int replantMinOffset;
    private int replantMaxOffset;
    private int replantRepurposeRadius;
    private long tickCounter;

    public LeafDecayRandomTickFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        reloadSettings();
        ensureReplantStorage();
        loadPendingReplants();

        api.worlds().registerSettingHandler(new LeafDecayTickSpeedSetting());

        api.events().register(BlockBreakEvent.class, event -> {
            if (event.isCancelled()) {
                return;
            }
            trackFromLogRemoval(event.getBlock());
            scheduleTreeReplant(event.getBlock());
        }, EventPriority.MONITOR, true);

        api.events().register(BlockPlaceEvent.class, event -> {
            if (!Tag.SAPLINGS.isTagged(event.getBlockPlaced().getType())) cancelReplantsNear(event.getBlockPlaced());
        }, EventPriority.MONITOR, true);

        api.events().register(BlockBurnEvent.class, event -> trackFromLogRemoval(event.getBlock()), EventPriority.MONITOR, true);

        api.events().register(BlockExplodeEvent.class, event -> event.blockList().forEach(this::trackFromLogRemoval), EventPriority.MONITOR, true);
        api.events().register(EntityExplodeEvent.class, event -> event.blockList().forEach(this::trackFromLogRemoval), EventPriority.MONITOR, true);

        api.events().register(LeavesDecayEvent.class, event -> {
            World world = event.getBlock().getWorld();
            if (!isOverrideActive(world)) {
                return;
            }

            long key = pack(event.getBlock());
            if (isAllowedDecay(world.getUID(), key)) {
                return;
            }

            addCandidate(event.getBlock(), tickCounter + candidateRetentionTicks);
            event.setCancelled(true);
        }, EventPriority.HIGHEST, false);

        api.events().register(WorldUnloadEvent.class, event -> {
            UUID worldId = event.getWorld().getUID();
            decayCandidates.remove(worldId);
            allowedDecayTicks.remove(worldId);
        }, EventPriority.MONITOR, false);

        api.tasks().repeating(TASK_ID, 1L, this::processDecayTicks);
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
    }

    @Override
    public void onDisable() {
        api.tasks().cancel(TASK_ID);
        decayCandidates.clear();
        allowedDecayTicks.clear();
        pendingReplants.clear();
    }

    private void reloadSettings() {
        scanRadius = Math.max(1, getConfigSection().getInt("scan_radius", 7));
        defaultTickSpeed = Math.max(0, getConfigSection().getInt("default_tick_speed", 8));
        candidateRetentionTicks = Math.max(20L, getConfigSection().getLong("candidate_retention_ticks", 1200L));
        replantEnabled = getConfigSection().getBoolean("replant.enabled", true);
        replantDelayMillis = Math.max(20L, getConfigSection().getLong("replant.delay-seconds", 90L)) * 1000L;
        replantLifetimeMillis = Math.max(60L, getConfigSection().getLong("replant.candidate-lifetime-seconds", 600L)) * 1000L;
        replantPlayerRadius = Math.max(8, getConfigSection().getInt("replant.player-clear-radius", 24));
        replantMinOffset = Math.max(0, getConfigSection().getInt("replant.minimum-offset", 3));
        replantMaxOffset = Math.max(replantMinOffset, getConfigSection().getInt("replant.maximum-offset", 7));
        replantRepurposeRadius = Math.max(1, getConfigSection().getInt("replant.repurpose-radius", 8));
    }

    private void processDecayTicks() {
        tickCounter++;
        if (tickCounter % 20L == 0L) processPendingReplants();

        for (World world : Bukkit.getWorlds()) {
            String configured = api.worlds().getSetting(world, LeafDecayTickSpeedSetting.KEY);
            if (!isOverrideActive(world)) {
                decayCandidates.remove(world.getUID());
                allowedDecayTicks.remove(world.getUID());
                continue;
            }

            int tickSpeed = configured == null || configured.equals("unset") ? defaultTickSpeed : Integer.parseInt(configured);
            Map<Long, Long> candidates = decayCandidates.get(world.getUID());
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }

            if (tickCounter % 20L == 0L) {
                purgeExpiredCandidates(world, candidates);
                if (candidates.isEmpty()) {
                    decayCandidates.remove(world.getUID());
                    allowedDecayTicks.remove(world.getUID());
                    continue;
                }
            }

            List<Long> keys = new ArrayList<>(candidates.keySet());
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < tickSpeed && !keys.isEmpty(); i++) {
                long key = keys.get(random.nextInt(keys.size()));
                Block block = unpack(world, key);
                if (!isTrackedLeaf(block)) {
                    candidates.remove(key);
                    continue;
                }

                markAllowedDecay(world.getUID(), key);
                try {
                    block.randomTick();
                } finally {
                    unmarkAllowedDecay(world.getUID(), key);
                }

                if (!isTrackedLeaf(block)) {
                    candidates.remove(key);
                }
            }

            if (candidates.isEmpty()) {
                decayCandidates.remove(world.getUID());
                allowedDecayTicks.remove(world.getUID());
            }
        }
    }

    private void purgeExpiredCandidates(World world, Map<Long, Long> candidates) {
        candidates.entrySet().removeIf(entry -> entry.getValue() < tickCounter || !isTrackedLeaf(unpack(world, entry.getKey())));
    }

    private void trackFromLogRemoval(Block block) {
        if (!Tag.LOGS.isTagged(block.getType())) {
            return;
        }

        World world = block.getWorld();
        if (!isOverrideActive(world)) {
            return;
        }

        long expiresAt = tickCounter + candidateRetentionTicks;
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int y = -scanRadius; y <= scanRadius; y++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    Block nearby = block.getRelative(x, y, z);
                    if (isTrackedLeaf(nearby)) {
                        addCandidate(nearby, expiresAt);
                    }
                }
            }
        }
    }

    private void ensureReplantStorage() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS tree_replant_pending (
              world_name TEXT NOT NULL, base_x INTEGER NOT NULL, base_y INTEGER NOT NULL, base_z INTEGER NOT NULL,
              sapling TEXT NOT NULL, earliest_at INTEGER NOT NULL, expires_at INTEGER NOT NULL,
              PRIMARY KEY (world_name, base_x, base_y, base_z)
            );
            """);
    }

    private void loadPendingReplants() {
        pendingReplants.clear();
        long now = System.currentTimeMillis();
        api.database().queryEach("SELECT world_name,base_x,base_y,base_z,sapling,earliest_at,expires_at FROM tree_replant_pending", null, result -> {
            try {
                PendingReplant pending = new PendingReplant(result.getString("world_name"), result.getInt("base_x"),
                    result.getInt("base_y"), result.getInt("base_z"), Material.valueOf(result.getString("sapling")),
                    result.getLong("earliest_at"), result.getLong("expires_at"));
                if (pending.expiresAt() > now) pendingReplants.put(pending.key(), pending);
            } catch (IllegalArgumentException ignored) {
                // Ignore obsolete material names left by a server downgrade.
            }
        });
        api.database().update("DELETE FROM tree_replant_pending WHERE expires_at <= ?", statement -> statement.setLong(1, now));
    }

    private void scheduleTreeReplant(Block brokenLog) {
        if (!replantEnabled) return;
        Material sapling = saplingForLog(brokenLog.getType());
        if (sapling == null || !hasNaturalCanopy(brokenLog)) return;
        Block base = treeBase(brokenLog);
        if (!canSupportSapling(base.getRelative(0, -1, 0).getType())) return;
        boolean alreadyPending = pendingReplants.values().stream().anyMatch(pending ->
            pending.worldName().equals(base.getWorld().getName()) && Math.abs(pending.baseY() - base.getY()) <= 12
                && squareDistance(pending.baseX(), pending.baseZ(), base.getX(), base.getZ()) <= 2);
        if (alreadyPending) return;

        long now = System.currentTimeMillis();
        PendingReplant pending = new PendingReplant(base.getWorld().getName(), base.getX(), base.getY(), base.getZ(),
            sapling, now + replantDelayMillis, now + replantDelayMillis + replantLifetimeMillis);
        pendingReplants.put(pending.key(), pending);
        api.database().update("INSERT OR REPLACE INTO tree_replant_pending(world_name,base_x,base_y,base_z,sapling,earliest_at,expires_at) VALUES(?,?,?,?,?,?,?)", statement -> {
            statement.setString(1, pending.worldName()); statement.setInt(2, pending.baseX()); statement.setInt(3, pending.baseY());
            statement.setInt(4, pending.baseZ()); statement.setString(5, pending.sapling().name());
            statement.setLong(6, pending.earliestAt()); statement.setLong(7, pending.expiresAt());
        });
    }

    private void processPendingReplants() {
        if (!replantEnabled || pendingReplants.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (PendingReplant pending : List.copyOf(pendingReplants.values())) {
            if (pending.expiresAt() <= now) { removePendingReplant(pending); continue; }
            if (pending.earliestAt() > now) continue;
            World world = Bukkit.getWorld(pending.worldName());
            if (world == null) continue;
            Block origin = world.getBlockAt(pending.baseX(), pending.baseY(), pending.baseZ());
            if (world.getPlayers().stream().anyMatch(player -> player.getLocation().distanceSquared(origin.getLocation())
                <= (double) replantPlayerRadius * replantPlayerRadius)) continue;
            if (hasRemainingTrunk(origin, pending.sapling())) { removePendingReplant(pending); continue; }
            Block destination = findReplantDestination(origin);
            if (destination == null) continue;
            destination.setType(pending.sapling(), false);
            removePendingReplant(pending);
        }
    }

    private Block findReplantDestination(Block origin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble(Math.PI * 2D);
            double distance = random.nextDouble(replantMinOffset, replantMaxOffset + 1D);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            for (int offset = -3; offset <= 3; offset++) {
                Block candidate = origin.getWorld().getBlockAt(x, origin.getY() + offset, z);
                if (!candidate.getType().isAir() || !canSupportSapling(candidate.getRelative(0, -1, 0).getType())) continue;
                boolean clearance = true;
                for (int y = 1; y <= 6; y++) if (!candidate.getRelative(0, y, 0).getType().isAir()) { clearance = false; break; }
                if (clearance) return candidate;
            }
        }
        return null;
    }

    private void cancelReplantsNear(Block placed) {
        for (PendingReplant pending : List.copyOf(pendingReplants.values())) {
            if (!pending.worldName().equals(placed.getWorld().getName())) continue;
            if (Math.abs(pending.baseY() - placed.getY()) > replantRepurposeRadius) continue;
            if (squareDistance(pending.baseX(), pending.baseZ(), placed.getX(), placed.getZ())
                <= replantRepurposeRadius * replantRepurposeRadius) removePendingReplant(pending);
        }
    }

    private boolean hasNaturalCanopy(Block log) {
        for (int x = -scanRadius; x <= scanRadius; x++) for (int y = -scanRadius; y <= scanRadius; y++)
            for (int z = -scanRadius; z <= scanRadius; z++) if (isTrackedLeaf(log.getRelative(x, y, z))) return true;
        return false;
    }

    private Block treeBase(Block log) {
        Block base = log;
        while (base.getY() > base.getWorld().getMinHeight()) {
            Block below = base.getRelative(0, -1, 0);
            if (below.getType() != log.getType()) break;
            base = below;
        }
        return base;
    }

    private boolean hasRemainingTrunk(Block origin, Material sapling) {
        Material log = logForSapling(sapling);
        int radius = sapling == Material.DARK_OAK_SAPLING || sapling == Material.JUNGLE_SAPLING
            || sapling == Material.SPRUCE_SAPLING ? 1 : 0;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) for (int y = 0; y <= 16; y++)
            if (origin.getRelative(x, y, z).getType() == log) return true;
        return false;
    }

    static Material saplingForLog(Material log) {
        return switch (log) {
            case OAK_LOG -> Material.OAK_SAPLING;
            case SPRUCE_LOG -> Material.SPRUCE_SAPLING;
            case BIRCH_LOG -> Material.BIRCH_SAPLING;
            case JUNGLE_LOG -> Material.JUNGLE_SAPLING;
            case ACACIA_LOG -> Material.ACACIA_SAPLING;
            case DARK_OAK_LOG -> Material.DARK_OAK_SAPLING;
            case MANGROVE_LOG -> Material.MANGROVE_PROPAGULE;
            case CHERRY_LOG -> Material.CHERRY_SAPLING;
            case PALE_OAK_LOG -> Material.PALE_OAK_SAPLING;
            default -> null;
        };
    }

    private static Material logForSapling(Material sapling) {
        return switch (sapling) {
            case OAK_SAPLING -> Material.OAK_LOG;
            case SPRUCE_SAPLING -> Material.SPRUCE_LOG;
            case BIRCH_SAPLING -> Material.BIRCH_LOG;
            case JUNGLE_SAPLING -> Material.JUNGLE_LOG;
            case ACACIA_SAPLING -> Material.ACACIA_LOG;
            case DARK_OAK_SAPLING -> Material.DARK_OAK_LOG;
            case MANGROVE_PROPAGULE -> Material.MANGROVE_LOG;
            case CHERRY_SAPLING -> Material.CHERRY_LOG;
            case PALE_OAK_SAPLING -> Material.PALE_OAK_LOG;
            default -> Material.AIR;
        };
    }

    private static boolean canSupportSapling(Material ground) {
        return ground == Material.GRASS_BLOCK || ground == Material.DIRT || ground == Material.COARSE_DIRT
            || ground == Material.PODZOL || ground == Material.ROOTED_DIRT || ground == Material.MOSS_BLOCK
            || ground == Material.MUD || ground == Material.MUDDY_MANGROVE_ROOTS;
    }

    private static int squareDistance(int x1, int z1, int x2, int z2) {
        int x = x1 - x2, z = z1 - z2;
        return x * x + z * z;
    }

    private void removePendingReplant(PendingReplant pending) {
        pendingReplants.remove(pending.key());
        api.database().update("DELETE FROM tree_replant_pending WHERE world_name=? AND base_x=? AND base_y=? AND base_z=?", statement -> {
            statement.setString(1, pending.worldName()); statement.setInt(2, pending.baseX());
            statement.setInt(3, pending.baseY()); statement.setInt(4, pending.baseZ());
        });
    }

    private record PendingReplant(String worldName, int baseX, int baseY, int baseZ, Material sapling,
                                  long earliestAt, long expiresAt) {
        private String key() { return worldName + ':' + baseX + ':' + baseY + ':' + baseZ; }
    }

    private void addCandidate(Block block, long expiresAt) {
        decayCandidates
            .computeIfAbsent(block.getWorld().getUID(), ignored -> new HashMap<>())
            .merge(pack(block), expiresAt, Math::max);
    }

    private boolean isTrackedLeaf(Block block) {
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Leaves leaves)) {
            return false;
        }

        return !leaves.isPersistent();
    }

    private boolean isOverrideActive(World world) {
        String configured = api.worlds().getSetting(world, LeafDecayTickSpeedSetting.KEY);
        return (configured != null && !configured.equals("unset") || defaultTickSpeed > 0)
            && !"deny".equals(api.worlds().getSetting(world, "leaf-decay"));
    }

    private boolean isAllowedDecay(UUID worldId, long key) {
        Set<Long> keys = allowedDecayTicks.get(worldId);
        return keys != null && keys.contains(key);
    }

    private void markAllowedDecay(UUID worldId, long key) {
        allowedDecayTicks.computeIfAbsent(worldId, ignored -> new HashSet<>()).add(key);
    }

    private void unmarkAllowedDecay(UUID worldId, long key) {
        Set<Long> keys = allowedDecayTicks.get(worldId);
        if (keys == null) {
            return;
        }

        keys.remove(key);
        if (keys.isEmpty()) {
            allowedDecayTicks.remove(worldId);
        }
    }

    private static long pack(Block block) {
        return pack(block.getX(), block.getY(), block.getZ());
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x7FFFFFFL) | (((long) z & 0x7FFFFFFL) << 27) | ((long) y << 54);
    }

    private static Block unpack(World world, long packed) {
        int x = (int) ((packed << 37) >> 37);
        int y = (int) (packed >> 54);
        int z = (int) ((packed << 10) >> 37);
        return world.getBlockAt(x, y, z);
    }
}
