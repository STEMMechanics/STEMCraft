package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.comet.CometService;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CometFeature extends BaseFeature implements CometService {
    private static final int COMET_BLOCK_COUNT = 96;
    private static final List<Vector> SPHERE_OFFSETS = createSphereOffsets();

    private final Set<BlockDisplay> active = new LinkedHashSet<>();
    private double startY;
    private double landingAngleDegrees;
    private int flightTicks;
    private double trailLength;
    private int impactExplosions;
    private double crashScarLength;
    private int crashScarRadius;
    private float explosionPower;
    private boolean fire;
    private double warningRadius;
    private double heatRadius;
    private double lethalHeatRadius;
    private double heatDamage;
    private int geodeRadius;
    private int magmaDebris;

    public CometFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        reloadSettings();
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
    }

    @Override
    public void onDisable() {
        for (BlockDisplay display : new ArrayList<>(active)) {
            if (display.isValid()) display.remove();
        }
        active.clear();
    }

    private void reloadSettings() {
        ConfigSection config = getConfigSection();
        startY = config.getDouble("start-y", 290.0d);
        landingAngleDegrees = Math.clamp(config.getDouble("landing-angle-degrees", 10.0d), 5.0d, 20.0d);
        flightTicks = Math.max(200, config.getInt("flight-ticks", 400));
        trailLength = Math.max(8.0d, config.getDouble("trail-length", 40.0d));
        impactExplosions = Math.max(1, config.getInt("impact-explosions", 10));
        crashScarLength = Math.clamp(config.getDouble("crash-scar-length", 80.0d), 50.0d, 100.0d);
        crashScarRadius = Math.clamp(config.getInt("crash-scar-radius", 6), 3, 12);
        explosionPower = (float) Math.max(0.0d, config.getDouble("explosion-power", 10.0d));
        fire = config.getBoolean("fire", true);
        warningRadius = Math.max(16.0d, config.getDouble("warning-radius", 200.0d));
        heatRadius = Math.max(1.0d, config.getDouble("heat-radius", 16.0d));
        lethalHeatRadius = Math.clamp(config.getDouble("lethal-heat-radius", 5.0d), 0.0d, heatRadius);
        heatDamage = Math.max(0.0d, config.getDouble("heat-damage", 8.0d));
        geodeRadius = Math.max(3, config.getInt("geode-radius", 8));
        magmaDebris = Math.max(0, config.getInt("magma-debris", 60));
    }

    @Override
    public void launch(Location impact) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0d);
        launch(impact, new Vector(Math.cos(angle), 0.0d, Math.sin(angle)));
    }

    @Override
    public void launch(Location impact, Vector direction) {
        if (impact.getWorld() == null) throw new IllegalArgumentException("Impact location must have a world");
        Vector horizontal = direction.clone().setY(0.0d);
        if (horizontal.lengthSquared() < 0.001d) {
            throw new IllegalArgumentException("Comet direction must have a non-zero horizontal component");
        }
        horizontal.normalize();
        Location target = impact.clone();
        if (!STEMCraft.getPlugin().getServer().isPrimaryThread()) {
            api.tasks().runLater(0L, () -> launch(target, horizontal));
            return;
        }

        World world = target.getWorld();
        double safeStartY = Math.min(startY, world.getMaxHeight() - 8.0d);
        List<Location> crashPath = createCrashPath(world, target, horizontal);
        Vector approach = crashPath.get(crashPath.size() - 1).toVector()
            .subtract(crashPath.get(0).toVector()).normalize();
        if (approach.getY() >= -0.01d) {
            approach = horizontal.clone().setY(-Math.tan(Math.toRadians(landingAngleDegrees))).normalize();
        }
        double approachLength = (safeStartY - target.getY()) / -approach.getY();
        Location start = target.clone().subtract(approach.clone().multiply(approachLength));
        start.setY(safeStartY);
        launchComet(start, target);
    }

    private void launchComet(Location start, Location impact) {
        World world = impact.getWorld();
        Vector horizontalDirection = impact.toVector().subtract(start.toVector()).setY(0.0d).normalize();
        Set<Chunk> crashChunks = loadCrashChunks(world, impact, horizontalDirection);
        Set<Chunk> flightChunks = new LinkedHashSet<>();
        updateFlightChunks(world, start, impact.toVector().subtract(start.toVector()).normalize(), flightChunks, crashChunks);
        UUID cometId = UUID.randomUUID();
        String taskId = taskId(cometId);
        List<BlockDisplay> displays = new ArrayList<>(COMET_BLOCK_COUNT);
        for (Vector offset : SPHERE_OFFSETS) {
            BlockDisplay display = world.spawn(start.clone().add(offset), BlockDisplay.class, entity -> {
                entity.setBlock(Material.MAGMA_BLOCK.createBlockData());
                entity.setGlowing(true);
                entity.setGlowColorOverride(Color.ORANGE);
                entity.setViewRange(12.0f);
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setTeleportDuration(2);
                entity.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f), new AxisAngle4f(),
                    new Vector3f(1.0f), new AxisAngle4f()));
            });
            displays.add(display);
            active.add(display);
        }
        warnNearby(impact);

        int[] age = {0};
        Vector travelDirection = impact.toVector().subtract(start.toVector()).normalize();
        api.tasks().repeating(taskId, 1L, 1L, () -> {
            if (displays.stream().noneMatch(BlockDisplay::isValid)) {
                finish(taskId, displays);
                return;
            }
            age[0]++;
            double elapsed = Math.min(1.0d, (double) age[0] / flightTicks);
            double progress = Math.pow(elapsed, 1.35d);
            Location centre = interpolateLinear(start, impact, progress);
            for (int i = 0; i < displays.size(); i++) {
                BlockDisplay display = displays.get(i);
                if (display.isValid()) display.teleport(centre.clone().add(SPHERE_OFFSETS.get(i)));
            }
            spawnFireTrail(world, centre, travelDirection);
            if (age[0] % 5 == 0) applyCometHeat(world, centre);
            if (age[0] % 10 == 0) {
                updateFlightChunks(world, centre, travelDirection, flightChunks, crashChunks);
            }
            if (age[0] >= flightTicks) {
                flightChunks.forEach(chunk -> chunk.removePluginChunkTicket(STEMCraft.getPlugin()));
                flightChunks.clear();
                beginImpact(taskId, displays, start, impact, crashChunks);
            }
        });
        api.tasks().runLater(flightTicks + impactExplosions * 3L + 100L,
            () -> {
                flightChunks.forEach(chunk -> chunk.removePluginChunkTicket(STEMCraft.getPlugin()));
                crashChunks.forEach(chunk -> chunk.removePluginChunkTicket(STEMCraft.getPlugin()));
            });
    }

    private void spawnFireTrail(World world, Location centre, Vector travelDirection) {
        Vector backwards = travelDirection.clone().multiply(-1.0d);
        for (int segment = 0; segment < 8; segment++) {
            double distance = trailLength * segment / 7.0d;
            Location tail = centre.clone().add(backwards.clone().multiply(distance));
            double spread = 2.4d * (1.0d - segment / 10.0d);
            world.spawnParticle(Particle.FLAME, tail, 16, spread, spread, spread, 0.10d, null, true);
            world.spawnParticle(Particle.LARGE_SMOKE, tail, 8, spread, spread, spread, 0.05d, null, true);
            if (segment < 5) world.spawnParticle(Particle.LAVA, tail, 3, spread, spread, spread, 0.0d, null, true);
        }
    }

    private Set<Chunk> loadCrashChunks(World world, Location impact, Vector crashDirection) {
        Set<Chunk> chunks = new LinkedHashSet<>();
        Location crashEnd = impact.clone().add(crashDirection.clone().multiply(crashScarLength));
        collectChunks(world, impact, crashEnd, chunks);
        chunks.forEach(chunk -> chunk.addPluginChunkTicket(STEMCraft.getPlugin()));
        return chunks;
    }

    private void updateFlightChunks(World world, Location centre, Vector direction, Set<Chunk> held,
                                    Set<Chunk> crashChunks) {
        Set<Chunk> required = new LinkedHashSet<>();
        collectChunks(world, centre, centre.clone().add(direction.clone().multiply(64.0d)), required);
        required.removeAll(crashChunks);
        for (Chunk chunk : new ArrayList<>(held)) {
            if (required.contains(chunk)) continue;
            chunk.removePluginChunkTicket(STEMCraft.getPlugin());
            held.remove(chunk);
        }
        for (Chunk chunk : required) {
            if (held.add(chunk)) chunk.addPluginChunkTicket(STEMCraft.getPlugin());
        }
    }

    private void collectChunks(World world, Location from, Location to, Set<Chunk> chunks) {
        double length = from.distance(to);
        int samples = Math.max(1, (int) Math.ceil(length / 8.0d));
        int chunkRadius = Math.max(1, (int) Math.ceil((heatRadius + 4.0d) / 16.0d));
        for (int sample = 0; sample <= samples; sample++) {
            Location point = interpolateLinear(from, to, (double) sample / samples);
            int centreX = point.getBlockX() >> 4;
            int centreZ = point.getBlockZ() >> 4;
            for (int x = -chunkRadius; x <= chunkRadius; x++) {
                for (int z = -chunkRadius; z <= chunkRadius; z++) {
                    chunks.add(world.getChunkAt(centreX + x, centreZ + z));
                }
            }
        }
    }

    private void applyCometHeat(World world, Location centre) {
        double lethalSquared = lethalHeatRadius * lethalHeatRadius;
        for (LivingEntity entity : world.getNearbyLivingEntities(centre, heatRadius)) {
            if (entity.isDead() || !entity.isValid()) continue;
            if (entity instanceof Player player && player.getGameMode() != GameMode.SURVIVAL) continue;
            double distanceSquared = entity.getLocation().distanceSquared(centre);
            if (distanceSquared <= lethalSquared) {
                entity.setHealth(0.0d);
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            double scaledDamage = heatDamage * (1.0d - distance / heatRadius);
            if (scaledDamage > 0.0d) entity.damage(scaledDamage);
            entity.setFireTicks(Math.max(entity.getFireTicks(), 60));
        }
    }

    private void warnNearby(Location impact) {
        double radiusSquared = warningRadius * warningRadius;
        for (Player player : impact.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(impact) > radiusSquared) continue;
            player.sendRichMessage("<red><bold>COMET INCOMING</bold></red><newline><yellow>Impact nearby—take cover!</yellow>");
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.9f, 0.6f);
        }
    }

    private void beginImpact(String taskId, List<BlockDisplay> displays, Location start, Location impact,
                             Set<Chunk> crashChunks) {
        finish(taskId, displays);
        World world = impact.getWorld();
        Vector crashDirection = impact.toVector().subtract(start.toVector()).setY(0.0d).normalize();
        List<Location> crashPath = createCrashPath(world, impact, crashDirection);
        boolean waterImpact = crashPath.stream().anyMatch(location -> hasNearbyWater(location, 7));
        for (int i = 0; i < impactExplosions; i++) {
            int index = i;
            api.tasks().runLater(i * 3L, () -> {
                Location blast = crashPath.get(index);
                if (blast.getY() <= world.getMinHeight() + 2) return;
                Location previous = index == 0 ? impact : crashPath.get(index - 1);
                boolean blastWater = hasNearbyWater(blast, 7);
                carveCrashSegment(world, previous, blast);
                if (blastWater) {
                    vaporizeWaterAndCarve(world, blast, Math.max(5, Math.round(explosionPower)));
                }
                world.spawnParticle(Particle.EXPLOSION_EMITTER, blast, 2, 1.5d, 1.0d, 1.5d, 0.0d);
                world.playSound(blast, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f + index * 0.04f);
                world.createExplosion(blast, explosionPower, fire, true);
            });
        }
        long finishDelay = impactExplosions * 3L + 5L;
        api.tasks().runLater(finishDelay, () -> {
            Location finalBlast = crashPath.get(crashPath.size() - 1);
            Vector terminalDirection = crashPath.size() > 1
                ? finalBlast.toVector().subtract(crashPath.get(crashPath.size() - 2).toVector()).normalize()
                : crashDirection;
            Location terminal = finalBlast.clone().add(terminalDirection.clone()
                .multiply(explosionPower + geodeRadius * 0.5d));
            if (!waterImpact) scatterMagma(world, impact);
            createPartialGeode(world, terminal, terminalDirection);
            if (waterImpact) wakeNearbyWater(world, impact, 28);
            crashChunks.forEach(chunk -> chunk.removePluginChunkTicket(STEMCraft.getPlugin()));
            crashChunks.clear();
        });
    }

    private List<Location> createCrashPath(World world, Location impact, Vector direction) {
        List<Location> path = new ArrayList<>(impactExplosions);
        for (int index = 0; index < impactExplosions; index++) {
            double progress = impactExplosions == 1 ? 1.0d : (double) index / (impactExplosions - 1);
            double distance = crashScarLength * progress;
            Location horizontal = impact.clone().add(direction.clone().multiply(distance));
            Block surface = world.getHighestBlockAt(horizontal, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            double depth = 1.0d + Math.tan(Math.toRadians(landingAngleDegrees)) * distance;
            path.add(new Location(world, horizontal.getX(), surface.getY() + 0.5d - depth, horizontal.getZ()));
        }
        return path;
    }

    private void carveCrashSegment(World world, Location from, Location to) {
        double length = from.distance(to);
        int samples = Math.max(1, (int) Math.ceil(length / 1.5d));
        for (int sample = 0; sample <= samples; sample++) {
            Location centre = interpolateLinear(from, to, (double) sample / samples);
            carveSphere(world, centre, crashScarRadius);
        }
    }

    private Location interpolateLinear(Location from, Location to, double progress) {
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(progress));
    }

    private void carveSphere(World world, Location centre, int radius) {
        int radiusSquared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radiusSquared) continue;
                    Block block = world.getBlockAt(
                        centre.getBlockX() + x, centre.getBlockY() + y, centre.getBlockZ() + z);
                    if (block.getType() != Material.BEDROCK) block.setType(Material.AIR, false);
                }
            }
        }
    }

    private boolean hasNearbyWater(Location location, int radius) {
        World world = location.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -3; y <= 3; y++) {
                    if (world.getBlockAt(location.getBlockX() + x, location.getBlockY() + y,
                        location.getBlockZ() + z).getType() == Material.WATER) return true;
                }
            }
        }
        return false;
    }

    private void vaporizeWaterAndCarve(World world, Location centre, int radius) {
        int radiusSquared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radiusSquared) continue;
                    Block block = world.getBlockAt(
                        centre.getBlockX() + x, centre.getBlockY() + y, centre.getBlockZ() + z);
                    if (block.getType() != Material.BEDROCK) block.setType(Material.AIR, false);
                }
            }
        }
        world.spawnParticle(Particle.CLOUD, centre, 180, radius, radius * 0.6d, radius, 0.12d, null, true);
        world.spawnParticle(Particle.LARGE_SMOKE, centre, 90, radius, radius * 0.6d, radius, 0.08d, null, true);
        wakeNearbyWater(world, centre, radius + 2);
    }

    private void wakeNearbyWater(World world, Location centre, int radius) {
        int minX = centre.getBlockX() - radius;
        int maxX = centre.getBlockX() + radius;
        int minY = Math.max(world.getMinHeight(), centre.getBlockY() - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, centre.getBlockY() + radius);
        int minZ = centre.getBlockZ() - radius;
        int maxZ = centre.getBlockZ() + radius;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.WATER) {
                        block.setBlockData(block.getBlockData().clone(), true);
                    }
                }
            }
        }
    }

    private void scatterMagma(World world, Location impact) {
        for (int i = 0; i < magmaDebris; i++) {
            int x = impact.getBlockX() + ThreadLocalRandom.current().nextInt(-18, 19);
            int z = impact.getBlockZ() + ThreadLocalRandom.current().nextInt(-18, 19);
            Block ground = world.getHighestBlockAt(x, z);
            if (isStableMagmaSurface(ground)) {
                ground.setType(Material.MAGMA_BLOCK, true);
            }
        }
    }

    private boolean isStableMagmaSurface(Block block) {
        Material material = block.getType();
        if (material.isAir() || block.isLiquid() || !material.isSolid()) return false;
        if (Tag.LEAVES.isTagged(material) || Tag.LOGS.isTagged(material) || Tag.PLANKS.isTagged(material)) return false;

        // Debris belongs in the terrain, never on player builds, vegetation, or other temporary surfaces.
        String name = material.name();
        return material == Material.GRASS_BLOCK
            || material == Material.MYCELIUM
            || material == Material.PODZOL
            || material == Material.GRAVEL
            || material == Material.CLAY
            || name.contains("DIRT")
            || name.endsWith("SAND")
            || name.endsWith("SANDSTONE")
            || name.endsWith("STONE")
            || name.endsWith("DEEPSLATE")
            || name.endsWith("TERRACOTTA")
            || name.equals("TUFF")
            || name.equals("CALCITE")
            || name.equals("NETHERRACK")
            || name.equals("SOUL_SOIL")
            || name.equals("SOUL_SAND");
    }

    private void createPartialGeode(World world, Location terminal, Vector incoming) {
        int cx = terminal.getBlockX();
        int cy = Math.max(world.getMinHeight() + geodeRadius + 1, terminal.getBlockY());
        int cz = terminal.getBlockZ();
        Vector opening = incoming.clone().multiply(-1).normalize();
        for (int x = -geodeRadius; x <= geodeRadius; x++) {
            for (int y = -geodeRadius; y <= geodeRadius; y++) {
                for (int z = -geodeRadius; z <= geodeRadius; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance > geodeRadius) continue;
                    Vector local = new Vector(x, y, z);
                    double openingDepth = local.dot(opening);
                    double openingRadius = Math.max(2.0d, openingDepth * 0.85d);
                    boolean openingCone = openingDepth > 0.0d
                        && local.clone().crossProduct(opening).lengthSquared() < openingRadius * openingRadius;
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (openingCone || distance < geodeRadius - 2.2d) {
                        block.setType(Material.AIR, false);
                    } else if (distance > geodeRadius - 0.8d) {
                        block.setType(Material.SMOOTH_BASALT, false);
                    } else if (distance > geodeRadius - 1.5d) {
                        block.setType(Material.CALCITE, false);
                    } else {
                        block.setType(ThreadLocalRandom.current().nextDouble() < 0.18d
                            ? Material.BUDDING_AMETHYST : Material.AMETHYST_BLOCK, false);
                    }
                }
            }
        }
    }

    private void finish(String taskId, List<BlockDisplay> displays) {
        api.tasks().cancel(taskId);
        for (BlockDisplay display : displays) {
            active.remove(display);
            if (display.isValid()) display.remove();
        }
    }

    private String taskId(UUID id) {
        return "comet:" + id;
    }

    static List<Vector> createSphereOffsets() {
        List<Vector> candidates = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (x * x + y * y + z * z <= 9) candidates.add(new Vector(x, y, z));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Vector::lengthSquared));
        return List.copyOf(candidates.subList(0, COMET_BLOCK_COUNT));
    }
}
