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
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
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

    private int scanRadius;
    private int defaultTickSpeed;
    private long candidateRetentionTicks;
    private long tickCounter;

    public LeafDecayRandomTickFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        reloadSettings();

        api.worlds().registerSettingHandler(new LeafDecayTickSpeedSetting());

        api.events().register(BlockBreakEvent.class, event -> {
            if (event.isCancelled()) {
                return;
            }
            trackFromLogRemoval(event.getBlock());
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
    }

    private void reloadSettings() {
        scanRadius = Math.max(1, getConfigSection().getInt("scan_radius", 7));
        defaultTickSpeed = Math.max(0, getConfigSection().getInt("default_tick_speed", 8));
        candidateRetentionTicks = Math.max(20L, getConfigSection().getLong("candidate_retention_ticks", 1200L));
    }

    private void processDecayTicks() {
        tickCounter++;

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
