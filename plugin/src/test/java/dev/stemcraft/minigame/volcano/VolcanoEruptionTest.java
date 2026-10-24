package dev.stemcraft.minigame.volcano;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VolcanoEruptionTest {
    private WorldMock world;
    private SCRegion arena;
    private List<Block> floor;

    @BeforeEach
    void setup() {
        world = MockBukkit.mock().addSimpleWorld("volcano");
        arena = new SCRegion(new CuboidRegion(BlockVector3.at(-1, 60, -1), BlockVector3.at(6, 80, 6)), world);
        floor = new ArrayList<>();
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) {
            Block block = world.getBlockAt(x, 63, z);
            block.setType(Material.STONE);
            floor.add(block);
        }
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    private VolcanoEruption eruption() {
        return new VolcanoEruption(world, arena, floor, new Random(42));
    }

    @Test
    void lavaSpreadsCoolsAndVariesWithoutConsumingTheEntirePlatform() {
        Block obstruction = world.getBlockAt(0, 64, 0);
        obstruction.setType(Material.GLASS);
        VolcanoEruption eruption = eruption();
        Set<Set<Block>> warningPatterns = new HashSet<>();
        Set<String> jetDirections = new HashSet<>();
        boolean flowingLavaSeen = false;
        boolean cooledRockSeen = false;
        for (long tick = 2; tick <= 1000; tick += 2) {
            world.clearSpawnedParticles();
            eruption.tick(tick);
            Set<Block> warning = new HashSet<>();
            int lavaCount = 0;
            int holes = 0;
            for (Block ground : floor) {
                if (eruption.isWarning(ground)) warning.add(ground);
                if (ground.getType().isAir()) holes++;
                if (ground.getType() == Material.BASALT || ground.getType() == Material.BLACKSTONE) cooledRockSeen = true;
                Block above = ground.getRelative(0, 1, 0);
                if (above.getType() != Material.LAVA) continue;
                lavaCount++;
                assertTrue(eruption.containsLava(above));
                assertTrue(ground.getType().isSolid(), "Lava must stay above solid floor");
                assertTrue(arena.contains(above.getLocation()));
                int level = ((Levelled) above.getBlockData()).getLevel();
                assertTrue(level >= 0 && level <= 7, "No falling lava columns");
                if (level > 0) flowingLavaSeen = true;
            }
            if (!warning.isEmpty()) warningPatterns.add(Set.copyOf(warning));
            assertTrue(lavaCount <= 12, "At most a third of the floor may contain lava");
            assertTrue(holes <= 9, "Crater holes are capped to leave escape routes");
            assertEquals(Material.GLASS, obstruction.getType());
            world.getSpawnedParticles().stream()
                    .filter(p -> p.particle() == Particle.FLAME && p.count() == 0 && p.extra() > 0.1)
                    .forEach(p -> jetDirections.add(Math.signum(p.offsetX()) + "," + Math.signum(p.offsetZ())));
        }
        assertTrue(flowingLavaSeen, "Flows must have shallower lava after their source");
        assertTrue(cooledRockSeen, "Hot floor should cool into volcanic rock");
        assertTrue(warningPatterns.size() >= 6, "Eruptions need varied positions and footprints");
        assertTrue(warningPatterns.stream().anyMatch(p -> p.size() == 1));
        assertTrue(warningPatterns.stream().anyMatch(p -> p.size() >= 3));
        assertTrue(jetDirections.size() >= 3, "Jets cannot all lean east");
        for (int x = -1; x <= 6; x++) for (int z = -1; z <= 6; z++) {
            if (x >= 0 && x <= 5 && z >= 0 && z <= 5) continue;
            assertEquals(Material.AIR, world.getBlockAt(x, 64, z).getType());
        }
    }

    @Test
    void flameJetsRiseFromTheFloorInsteadOfDescendingOntoIt() {
        VolcanoEruption eruption = eruption();
        long tick = 2;
        while (tick <= 120 && floor.stream().noneMatch(b -> b.getType() == Material.MAGMA_BLOCK)) {
            eruption.tick(tick);
            tick += 2;
        }
        assertTrue(tick <= 120, "First eruption must occur after its warning");
        double firstHeight = 0;
        double previousHeight = 0;
        for (int i = 0; i < 10; i++, tick += 2) {
            world.clearSpawnedParticles();
            eruption.tick(tick);
            double height = world.getSpawnedParticles().stream()
                    .filter(p -> p.particle() == Particle.FLAME && p.count() == 6)
                    .mapToDouble(p -> p.y()).max().orElseThrow();
            if (i == 0) firstHeight = height;
            assertTrue(height >= previousHeight, "Spurt height should rise with elapsed time");
            previousHeight = height;
            assertTrue(world.getSpawnedParticles().stream()
                    .filter(p -> p.particle() == Particle.FLAME && p.count() == 0)
                    .allMatch(p -> p.offsetY() > 0));
        }
        assertTrue(previousHeight > firstHeight + 1.5);
    }

    @Test
    void restoresOriginalFloorDataAndAirAfterActiveLavaThenIgnoresStaleTicks() {
        Map<Block, String> original = new LinkedHashMap<>();
        for (Block ground : floor) {
            ground.setBlockData(Material.BASALT.createBlockData("[axis=x]"));
            Block above = ground.getRelative(0, 1, 0);
            above.setType(Material.CAVE_AIR);
            original.put(ground, ground.getBlockData().getAsString());
            original.put(above, above.getBlockData().getAsString());
        }
        VolcanoEruption eruption = eruption();
        for (long tick = 2; tick <= 120; tick += 2) eruption.tick(tick);
        List<Block> activeLava = original.keySet().stream().filter(b -> b.getType() == Material.LAVA).toList();
        assertFalse(activeLava.isEmpty());
        eruption.restore();
        eruption.tick(1000);
        original.forEach((block, data) -> assertEquals(data, block.getBlockData().getAsString()));
        activeLava.forEach(block -> assertFalse(eruption.containsLava(block)));
    }
}
