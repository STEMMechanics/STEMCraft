package dev.stemcraft.minigame.volcano;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** One arena's vents and controlled lava flows, including every block needed for restoration. */
final class VolcanoEruption {
    private static final double GEYSER_MIN_HEIGHT = 10.0;
    private static final double GEYSER_MAX_HEIGHT = 15.0;
    private static final int GEYSER_MIN_DURATION_TICKS = 30;
    private static final int GEYSER_DURATION_VARIANCE_TICKS = 25;
    private static final List<BlockFace> DIRECTIONS = List.of(
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private enum Pattern { SPOUTS, BURST, FISSURE }

    private final World world;
    private final SCRegion arenaRegion;
    private final Random random;
    private final Map<Block, BlockData> floor = new LinkedHashMap<>();
    private final Map<Block, LavaTile> lava = new LinkedHashMap<>();
    private final Map<Block, Long> hotFloorUntil = new HashMap<>();
    private final List<Block> warnings = new ArrayList<>();
    private final List<Flow> flows = new ArrayList<>();
    private final List<Jet> jets = new ArrayList<>();
    private final List<Pattern> patterns = new ArrayList<>();
    private final int lavaLimit;
    private long nextPhase;
    private int phase;
    private Pattern pattern;
    private boolean restored;

    VolcanoEruption(World world, SCRegion arenaRegion, List<Block> blocks, Random random) {
        this.world = world;
        this.arenaRegion = arenaRegion;
        this.random = random;
        for (Block block : blocks) floor.put(block, block.getBlockData().clone());
        // Leave room to dodge even on a small platform.
        lavaLimit = Math.max(1, blocks.size() / 3);
    }

    boolean isWarning(Block block) {
        return warnings.contains(block);
    }

    boolean containsLava(Block block) {
        return lava.containsKey(block);
    }

    boolean isHotFloor(Block block) {
        return hotFloorUntil.containsKey(block);
    }

    boolean hasLavaContact(Location location) {
        if (location == null || location.getWorld() != world) return false;
        for (int offset = -1; offset <= 1; offset++) {
            if (containsLava(location.clone().add(0, offset, 0).getBlock())) return true;
        }
        return false;
    }

    boolean isGeyserHazard(Location location, long tick) {
        if (location == null || location.getWorld() != world) return false;
        for (Jet jet : jets) {
            long age = tick - jet.start;
            if (age < 0 || age >= jet.duration) continue;

            double rise = Math.min(1.0, age / 20.0) * jet.height;
            double centreX = jet.block.getX() + 0.5 + jet.direction.getModX() * rise * 0.12;
            double centreZ = jet.block.getZ() + 0.5 + jet.direction.getModZ() * rise * 0.12;
            double relativeY = location.getY() - (jet.block.getY() + 1.0);
            if (relativeY < -0.25 || relativeY > rise + 1.1) continue;

            double dx = location.getX() - centreX;
            double dz = location.getZ() - centreZ;
            if (dx * dx + dz * dz <= 0.75 * 0.75) return true;
        }
        return false;
    }

    void tick(long tick) {
        if (restored) return;
        cool(tick);
        advanceFlows(tick);
        renderJets(tick);
        if (tick >= nextPhase) {
            if (warnings.isEmpty()) warn(tick);
            else erupt(tick);
        }
        for (Block block : warnings) {
            Location vent = block.getLocation().clone().add(0.5, 1.05, 0.5);
            world.spawnParticle(Particle.SMOKE, vent, 2, 0.22, 0.04, 0.22, 0.025);
            // A count of zero gives FLAME a direction; positive Y sends it upward.
            world.spawnParticle(Particle.FLAME, vent, 0, 0, 1, 0, 0.08);
        }
    }

    private void warn(long tick) {
        List<Block> candidates = floor.keySet().stream()
                .filter(b -> b.getType().isSolid() && !hotFloorUntil.containsKey(b) && canFlood(b))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (candidates.isEmpty()) {
            nextPhase = tick + 10;
            return;
        }
        Collections.shuffle(candidates, random);
        if (patterns.isEmpty()) {
            patterns.addAll(List.of(Pattern.values()));
            Collections.shuffle(patterns, random);
            if (patterns.getFirst() == pattern) Collections.rotate(patterns, 1);
        }
        pattern = patterns.removeFirst();
        phase++;
        Block centre = candidates.getFirst();
        switch (pattern) {
            case SPOUTS -> warnings.addAll(candidates.subList(0, Math.min(candidates.size(), Math.min(5, 1 + phase / 6))));
            case BURST -> {
                warnings.add(centre);
                for (BlockFace direction : shuffledDirections()) {
                    Block neighbour = centre.getRelative(direction);
                    if (candidates.contains(neighbour)) warnings.add(neighbour);
                    if (warnings.size() >= Math.min(5, 3 + phase / 9)) break;
                }
            }
            case FISSURE -> {
                BlockFace direction = randomDirection();
                for (int offset : new int[]{0, 1, -1, 2, -2, 3}) {
                    Block neighbour = centre.getRelative(direction.getModX() * offset, 0, direction.getModZ() * offset);
                    if (candidates.contains(neighbour)) warnings.add(neighbour);
                    if (warnings.size() >= Math.min(6, 3 + phase / 8)) break;
                }
            }
        }
        nextPhase = tick + Math.max(24, 74 - phase * 2 + random.nextInt(23) - 11);
        world.playSound(centre.getLocation(), Sound.BLOCK_LAVA_AMBIENT, 0.8f, 0.6f + random.nextFloat() * 0.4f);
    }

    private void erupt(long tick) {
        for (Block block : warnings) {
            if (!block.getType().isSolid()) continue;
            block.setType(Material.MAGMA_BLOCK, false);
            hotFloorUntil.put(block, tick + 100 + random.nextInt(61));
            BlockFace direction = randomDirection();
            double height = GEYSER_MIN_HEIGHT + random.nextDouble() * (GEYSER_MAX_HEIGHT - GEYSER_MIN_HEIGHT);
            jets.add(new Jet(block, direction, tick,
                    GEYSER_MIN_DURATION_TICKS + random.nextInt(GEYSER_DURATION_VARIANCE_TICKS + 1), height));
            if (flood(block, 0, tick)) flows.add(new Flow(block, direction, 3 + random.nextInt(4), tick + 6));
            world.spawnParticle(Particle.LAVA, block.getLocation().clone().add(0.5, 1.2, 0.5), 8, 0.25, 0.1, 0.25, 0.1);
            openCrater(block);
        }
        if (!warnings.isEmpty()) {
            world.playSound(warnings.getFirst().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f + random.nextFloat() * 0.5f);
        }
        warnings.clear();
        nextPhase = tick + 6 + random.nextInt(15);
    }

    private void renderJets(long tick) {
        jets.removeIf(jet -> tick - jet.start >= jet.duration);
        for (Jet jet : jets) {
            Location base = jet.block.getLocation().clone().add(0.5, 1.1, 0.5);
            double rise = Math.min(1, (tick - jet.start) / 20.0) * jet.height;
            Location tip = base.clone().add(jet.direction.getModX() * rise * 0.12, rise,
                    jet.direction.getModZ() * rise * 0.12);
            for (double y = 1.0; y < rise; y += 1.0) {
                Location column = base.clone().add(jet.direction.getModX() * y * 0.12, y,
                        jet.direction.getModZ() * y * 0.12);
                world.spawnParticle(Particle.FLAME, column, 2, 0.12, 0.18, 0.12, 0.015);
                if (tick % 4 == 0) world.spawnParticle(Particle.LAVA, column, 1, 0.1, 0.1, 0.1, 0.02);
            }
            world.spawnParticle(Particle.FLAME, tip, 6, 0.18, 0.12, 0.18, 0.025);
            world.spawnParticle(Particle.FLAME, base, 0, jet.direction.getModX() * 0.18, 1,
                    jet.direction.getModZ() * 0.18, 0.3);
            world.spawnParticle(Particle.SMOKE, tip, 2, 0.25, 0.15, 0.25, 0.04);
            if (tick % 6 == 0) world.spawnParticle(Particle.LAVA, tip, 2, 0.15, 0.1, 0.15, 0.05);
        }
    }

    private void advanceFlows(long tick) {
        var iterator = flows.iterator();
        while (iterator.hasNext()) {
            Flow flow = iterator.next();
            if (tick < flow.nextStep) continue;
            List<BlockFace> directions = shuffledDirections();
            directions.remove(flow.direction);
            directions.addFirst(flow.direction);
            boolean advanced = false;
            for (BlockFace direction : directions) {
                Block next = flow.block.getRelative(direction);
                if (flow.trail.contains(next) || warnings.contains(next) || !canFlood(next)) continue;
                if (!flood(next, Math.min(7, flow.level + 1), tick)) continue;
                flow.block = next;
                flow.direction = direction;
                flow.trail.add(next);
                flow.level++;
                flow.steps--;
                flow.nextStep = tick + 6;
                advanced = true;
                break;
            }
            if (!advanced || flow.steps <= 0) iterator.remove();
        }
    }

    private boolean canFlood(Block ground) {
        if (!floor.containsKey(ground) || !ground.getType().isSolid()) return false;
        Block above = ground.getRelative(BlockFace.UP);
        return arenaRegion.contains(above.getLocation()) && (above.getType().isAir() || lava.containsKey(above));
    }

    private boolean flood(Block ground, int level, long tick) {
        if (!canFlood(ground)) return false;
        Block above = ground.getRelative(BlockFace.UP);
        LavaTile previous = lava.get(above);
        if (previous == null && lava.size() >= lavaLimit) return false;
        BlockData original = previous == null ? above.getBlockData().clone() : previous.original;
        if (previous != null) {
            level = Math.min(level, ((Levelled) above.getBlockData()).getLevel());
        }
        Levelled data = (Levelled) Material.LAVA.createBlockData();
        data.setLevel(level);
        above.setBlockData(data, false);
        ground.setType(Material.MAGMA_BLOCK, false);
        hotFloorUntil.merge(ground, tick + 100 + random.nextInt(61), Math::max);
        lava.put(above, new LavaTile(original));
        return true;
    }

    private void openCrater(Block vent) {
        double chance = pattern == Pattern.BURST ? 0.65 : 0.3;
        if (random.nextDouble() >= chance
                || floor.keySet().stream().filter(b -> b.getType().isAir()).count() >= Math.max(1, floor.size() / 4)) return;
        for (BlockFace direction : shuffledDirections()) {
            Block neighbour = vent.getRelative(direction);
            if (floor.containsKey(neighbour) && neighbour.getType().isSolid()
                    && !warnings.contains(neighbour) && !hotFloorUntil.containsKey(neighbour)
                    && !lava.containsKey(neighbour.getRelative(BlockFace.UP))) {
                neighbour.setType(Material.AIR, false);
                return;
            }
        }
    }

    private void cool(long tick) {
        var hot = hotFloorUntil.entrySet().iterator();
        while (hot.hasNext()) {
            var entry = hot.next();
            if (tick < entry.getValue()) continue;
            Block block = entry.getKey();
            if (block.getType() == Material.MAGMA_BLOCK) {
                block.setType(random.nextBoolean() ? Material.BASALT : Material.BLACKSTONE, false);
            }
            hot.remove();
        }
    }

    void restore() {
        restored = true;
        lava.forEach((block, tile) -> block.setBlockData(tile.original, false));
        lava.clear();
        floor.forEach((block, data) -> block.setBlockData(data, false));
        hotFloorUntil.clear();
        warnings.clear();
        flows.clear();
        jets.clear();
    }

    private BlockFace randomDirection() {
        return DIRECTIONS.get(random.nextInt(DIRECTIONS.size()));
    }

    private List<BlockFace> shuffledDirections() {
        List<BlockFace> directions = new ArrayList<>(DIRECTIONS);
        Collections.shuffle(directions, random);
        return directions;
    }

    private record LavaTile(BlockData original) { }
    private record Jet(Block block, BlockFace direction, long start, int duration, double height) { }

    private static final class Flow {
        Block block;
        BlockFace direction;
        final List<Block> trail = new ArrayList<>();
        int steps;
        int level;
        long nextStep;

        Flow(Block block, BlockFace direction, int steps, long nextStep) {
            this.block = block;
            this.direction = direction;
            this.steps = steps;
            this.nextStep = nextStep;
            trail.add(block);
        }
    }
}
