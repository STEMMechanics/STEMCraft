package dev.stemcraft.service.minigame;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiniGameArenaSharedTest {
    @Test
    void startingCountdownTitleUsesSharedArenaFormatting() {
        MiniGameServiceImpl service = mock(MiniGameServiceImpl.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        World world = mock(World.class);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
        MiniGameArena arena = spy(new MiniGameArenaImpl(service, api, "test", "countdown", world));

        arena.showStartingCountdownTitle(4, "<gold>Race starts in</gold>");

        verify(arena).showTitle(
            eq("<gradient:#fde047:#f97316><bold>4</bold></gradient>"),
            eq("<gold>Race starts in</gold>"),
            eq(Duration.ZERO),
            eq(Duration.ofSeconds(1)),
            eq(Duration.ofMillis(200))
        );
    }

    @Test
    void supplyDropMarkersPersistUntilLastTrackedDropIsCleared() {
        MiniGameServiceImpl service = mock(MiniGameServiceImpl.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        TaskService tasks = mock(TaskService.class);
        World world = mock(World.class);
        Item firstDrop = mock(Item.class);
        Item secondDrop = mock(Item.class);

        UUID firstDropId = UUID.randomUUID();
        UUID secondDropId = UUID.randomUUID();
        String firstTaskToken = firstDropId.toString().replace('-', '_');
        String secondTaskToken = secondDropId.toString().replace('-', '_');
        Location spawn = new Location(world, 0.0d, 64.0d, 0.0d);
        Location markerLocation = new Location(world, 10.5d, 65.15d, 10.5d);
        Location firstDropLocation = new Location(world, 10.5d, 66.35d, 10.5d);
        Location secondDropLocation = new Location(world, 10.5d, 66.35d, 10.5d);

        when(api.tasks()).thenReturn(tasks);
        when(world.getSpawnLocation()).thenReturn(spawn);
        when(firstDrop.getUniqueId()).thenReturn(firstDropId);
        when(firstDrop.getLocation()).thenReturn(firstDropLocation);
        when(secondDrop.getUniqueId()).thenReturn(secondDropId);
        when(secondDrop.getLocation()).thenReturn(secondDropLocation);

        MiniGameArenaImpl arena = new MiniGameArenaImpl(service, api, "bedwars", "test", world);

        arena.trackSupplyDrop(firstDrop, markerLocation);
        arena.trackSupplyDrop(secondDrop, markerLocation);
        arena.clearSupplyDrop(firstDropId);

        arena.clearSupplyDrop(secondDropId);

        assertAll(
            () -> verify(tasks, times(2)).repeating(contains(firstTaskToken), anyLong(), anyLong(), any(Runnable.class)),
            () -> verify(tasks, times(2)).repeating(contains(secondTaskToken), anyLong(), anyLong(), any(Runnable.class)),
            () -> verify(tasks, times(4)).cancel(contains(firstTaskToken)),
            () -> verify(tasks, times(4)).cancel(contains(secondTaskToken))
        );
    }

    @Test
    void trySpawnSupplyDropCrateRejectsAnInvalidFinalChestLocation() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("shared-drop-invalid-crate-test");
            for (int y = 70; y <= 76; y++) {
                world.getBlockAt(0, y, 0).setType(Material.AIR);
            }

            MiniGameArenaImpl arena = createArena(world);

            assertFalse(arena.trySpawnSupplyDropCrate(
                new ItemStack(Material.DIAMOND),
                new Location(world, 0.5d, 74.15d, 0.5d)
            ));
            assertEquals(0, arena.countActiveSupplyDrops());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void trySpawnSupplyDropCrateCancelsWhenSupportChangesBeforeLanding() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("shared-drop-revalidation-test");
            createSupportedSurface(world, 0, 73, 0);

            STEMCraftAPI api = mock(STEMCraftAPI.class);
            TaskService tasks = mock(TaskService.class);
            when(api.tasks()).thenReturn(tasks);
            MiniGameArenaImpl arena = new MiniGameArenaImpl(
                mock(MiniGameServiceImpl.class),
                api,
                "bedwars",
                "test",
                world
            );

            Location landingLocation = new Location(world, 0.5d, 74.15d, 0.5d);
            assertTrue(arena.trySpawnSupplyDropCrate(new ItemStack(Material.DIAMOND), landingLocation));

            ArgumentCaptor<Runnable> descentTask = ArgumentCaptor.forClass(Runnable.class);
            verify(tasks).repeating(any(), eq(0L), eq(1L), descentTask.capture());
            for (int tick = 0; tick < 415; tick++) {
                descentTask.getValue().run();
            }

            world.getBlockAt(0, 73, 0).setType(Material.AIR);
            descentTask.getValue().run();

            assertEquals(0, arena.countActiveSupplyDrops());
            assertEquals(Material.AIR, world.getBlockAt(0, 74, 0).getType());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void findRandomSupplyDropLocationUsesHighestAllowedConfiguredSurface() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("shared-bedwars-drop-test");
            world.getBlockAt(0, 64, 0).setType(Material.YELLOW_BED);
            world.getBlockAt(0, 73, 0).setType(Material.GRASS_BLOCK);
            world.getBlockAt(1, 73, 0).setType(Material.STONE);
            world.getBlockAt(-1, 73, 0).setType(Material.STONE);
            world.getBlockAt(0, 73, 1).setType(Material.STONE);
            world.getBlockAt(0, 73, -1).setType(Material.STONE);
            world.getBlockAt(1, 73, 1).setType(Material.STONE);
            world.getBlockAt(1, 73, -1).setType(Material.STONE);
            world.getBlockAt(-1, 73, 1).setType(Material.STONE);
            world.getBlockAt(-1, 73, -1).setType(Material.STONE);
            world.getBlockAt(0, 74, 0).setType(Material.AIR);
            world.getBlockAt(0, 75, 0).setType(Material.AIR);

            MiniGameArenaImpl arena = createArena(world);
            Location dropLocation = arena.findRandomSupplyDropLocation(
                new Location(world, 0.0d, 70.0d, 0.0d),
                new Location(world, 0.0d, 75.0d, 0.0d),
                location -> true,
                List.of(Material.GRASS_BLOCK),
                1
            );

            assertNotNull(dropLocation);
            assertAll(
                () -> assertEquals(world, dropLocation.getWorld()),
                () -> assertEquals(0.5d, dropLocation.getX(), 0.0001d),
                () -> assertEquals(74.15d, dropLocation.getY(), 0.0001d),
                () -> assertEquals(0.5d, dropLocation.getZ(), 0.0001d)
            );
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void findRandomSupplyDropLocationRejectsColumnWhenHighestBlockIsNotAllowed() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("shared-drop-disallowed-test");
            world.getBlockAt(0, 72, 0).setType(Material.GRASS_BLOCK);
            world.getBlockAt(0, 73, 0).setType(Material.STONE);
            world.getBlockAt(0, 74, 0).setType(Material.AIR);
            world.getBlockAt(0, 75, 0).setType(Material.AIR);

            MiniGameArenaImpl arena = createArena(world);

            assertNull(arena.findRandomSupplyDropLocation(
                new Location(world, 0.0d, 70.0d, 0.0d),
                new Location(world, 0.0d, 75.0d, 0.0d),
                location -> true,
                List.of(Material.GRASS_BLOCK),
                1
            ));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void findRandomSupplyDropLocationRejectsColumnWhenSurroundingBlocksAreNotSolid() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("shared-drop-support-test");
            world.getBlockAt(0, 73, 0).setType(Material.GRASS_BLOCK);
            world.getBlockAt(1, 73, 0).setType(Material.STONE);
            world.getBlockAt(-1, 73, 0).setType(Material.STONE);
            world.getBlockAt(0, 73, 1).setType(Material.STONE);
            world.getBlockAt(0, 73, -1).setType(Material.AIR);
            world.getBlockAt(1, 73, 1).setType(Material.STONE);
            world.getBlockAt(1, 73, -1).setType(Material.STONE);
            world.getBlockAt(-1, 73, 1).setType(Material.STONE);
            world.getBlockAt(-1, 73, -1).setType(Material.STONE);
            world.getBlockAt(0, 74, 0).setType(Material.AIR);
            world.getBlockAt(0, 75, 0).setType(Material.AIR);

            MiniGameArenaImpl arena = createArena(world);

            assertNull(arena.findRandomSupplyDropLocation(
                new Location(world, 0.0d, 70.0d, 0.0d),
                new Location(world, 0.0d, 75.0d, 0.0d),
                location -> true,
                List.of(Material.GRASS_BLOCK),
                1
            ));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void findRandomSupplyDropLocationFallsBackToCoreRegionAndExcludesLobbyRegion() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("shared-drop-region-fallback-test");
            createSupportedSurface(world, 0, 73, 0);

            MiniGameArenaImpl arena = createArena(world);
            arena.setRegion(region(world, 0, 70, 0, 0, 75, 0));

            assertNotNull(arena.findRandomSupplyDropLocation(
                List.of(Material.GRASS_BLOCK),
                1
            ));

            arena.setLobbyRegion(region(world, 0, 73, 0, 0, 74, 0));

            assertNull(arena.findRandomSupplyDropLocation(
                List.of(Material.GRASS_BLOCK),
                1
            ));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void findRandomSupplyDropLocationAppliesAdditionalRegionsAndSuitabilityPredicate() {
        ServerMock server = MockBukkit.mock();
        try {
            WorldMock world = server.addSimpleWorld("shared-drop-filter-test");
            createSupportedSurface(world, 0, 73, 0);

            MiniGameArenaImpl arena = createArena(world);
            arena.setRegion(region(world, 0, 70, 0, 0, 75, 0));
            SCRegion excludedRegion = region(world, 0, 73, 0, 0, 74, 0);

            assertNull(arena.findRandomSupplyDropLocation(
                List.of(Material.GRASS_BLOCK),
                1,
                excludedRegion
            ));

            Location filteredLocation = arena.findRandomSupplyDropLocation(
                List.of(Material.GRASS_BLOCK),
                1,
                (location, currentArena) -> currentArena == arena && location.getBlockY() == 74
            );
            assertNotNull(filteredLocation);
        } finally {
            MockBukkit.unmock();
        }
    }

    private static void createSupportedSurface(WorldMock world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.GRASS_BLOCK);
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                world.getBlockAt(x + offsetX, y, z + offsetZ).setType(Material.STONE);
            }
        }
        world.getBlockAt(x, y + 1, z).setType(Material.AIR);
        world.getBlockAt(x, y + 2, z).setType(Material.AIR);
    }

    private static SCRegion region(WorldMock world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new SCRegion(
            new CuboidRegion(BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ)),
            world
        );
    }

    private MiniGameArenaImpl createArena(World world) {
        MiniGameServiceImpl service = mock(MiniGameServiceImpl.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);

        return new MiniGameArenaImpl(service, api, "bedwars", "test", world);
    }
}
