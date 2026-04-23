package dev.stemcraft.service.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        Block block = mock(Block.class);
        BlockData blockData = mock(BlockData.class);
        Item firstDrop = mock(Item.class);
        Item secondDrop = mock(Item.class);

        UUID worldId = UUID.randomUUID();
        UUID firstDropId = UUID.randomUUID();
        UUID secondDropId = UUID.randomUUID();
        String firstTaskToken = firstDropId.toString().replace('-', '_');
        String secondTaskToken = secondDropId.toString().replace('-', '_');
        Location spawn = new Location(world, 0.0d, 64.0d, 0.0d);
        Location markerLocation = new Location(world, 10.0d, 65.0d, 10.0d);
        Location firstDropLocation = new Location(world, 10.5d, 66.35d, 10.5d);
        Location secondDropLocation = new Location(world, 10.5d, 66.35d, 10.5d);

        when(api.tasks()).thenReturn(tasks);
        when(world.getSpawnLocation()).thenReturn(spawn);
        when(world.getUID()).thenReturn(worldId);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(block.getLocation()).thenReturn(markerLocation);
        when(block.getType()).thenReturn(Material.STONE);
        when(block.getBlockData()).thenReturn(blockData);
        when(blockData.clone()).thenReturn(blockData);
        when(firstDrop.getUniqueId()).thenReturn(firstDropId);
        when(firstDrop.getLocation()).thenReturn(firstDropLocation);
        when(secondDrop.getUniqueId()).thenReturn(secondDropId);
        when(secondDrop.getLocation()).thenReturn(secondDropLocation);

        MiniGameArenaImpl arena = new MiniGameArenaImpl(service, api, "bedwars", "test", world);

        arena.trackSupplyDrop(firstDrop, markerLocation);
        arena.trackSupplyDrop(secondDrop, markerLocation);
        arena.clearSupplyDrop(firstDropId);

        verify(block, times(1)).setType(Material.BEACON, false);
        verify(block, never()).setType(Material.STONE, false);

        arena.clearSupplyDrop(secondDropId);

        assertAll(
            () -> verify(block).setType(Material.STONE, false),
            () -> verify(block).setBlockData(eq(blockData), eq(false)),
            () -> verify(tasks).repeating(contains(firstTaskToken), eq(0L), eq(20L), any(Runnable.class)),
            () -> verify(tasks).repeating(contains(secondTaskToken), eq(0L), eq(20L), any(Runnable.class)),
            () -> verify(tasks, times(2)).cancel(contains(firstTaskToken)),
            () -> verify(tasks, times(2)).cancel(contains(secondTaskToken))
        );
    }
}
