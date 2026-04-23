package dev.stemcraft.service.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiniGameArenaSharedTest {
    @Test
    void startingCountdownTitleUsesSharedArenaFormatting() {
        MiniGameArena arena = mock(MiniGameArena.class, CALLS_REAL_METHODS);

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

    private MiniGameArenaImpl createArena(World world) {
        MiniGameServiceImpl service = mock(MiniGameServiceImpl.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);

        return new MiniGameArenaImpl(service, api, "bedwars", "test", world);
    }
}
