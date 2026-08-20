package dev.stemcraft.minigame.minefield;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinefieldArenaHandlerTest {
    @Test
    void countAdjacentMinesCountsDiagonalNeighbors() {
        MinefieldArenaHandler handler = newHandler();

        int count = handler.countAdjacentMines(10, 10, Set.of(
            "9,9",
            "10,9",
            "11,11",
            "20,20"
        ));

        assertEquals(3, count);
    }

    @Test
    void carveGuaranteedPathConnectsEntryToExitInsideBounds() {
        MinefieldArenaHandler handler = newHandler();
        MinefieldArenaHandler.FieldBounds bounds = new MinefieldArenaHandler.FieldBounds(0, 4, 64, 0, 4);
        Set<String> playable = Set.of(
            "0,1", "1,1", "2,1", "2,2", "3,2", "4,2", "4,3"
        );
        Set<String> path = handler.carveGuaranteedPath(
            bounds,
            playable,
            new Location(null, -2.0d, 64.0d, 1.0d),
            new Location(null, 9.0d, 64.0d, 3.0d),
            new Random(7L)
        );

        assertTrue(path.contains("0,1"));
        assertTrue(path.contains("4,2"));
        assertTrue(path.stream().allMatch(playable::contains));
    }

    @Test
    void buildMineLayoutLeavesProtectedPathSafe() {
        MinefieldArenaHandler handler = newHandler();
        MinefieldArenaHandler.FieldBounds bounds = new MinefieldArenaHandler.FieldBounds(0, 5, 64, 0, 1);
        Set<String> playable = Set.of(
            "0,0", "1,0", "2,0", "3,0", "4,0", "5,0",
            "0,1", "1,1", "2,1", "3,1", "4,1", "5,1"
        );
        Set<String> protectedCells = Set.of("0,0", "1,0", "2,0", "3,0", "4,0", "5,0");

        Set<String> mines = handler.buildMineLayout(bounds, playable, 6, protectedCells, new Random(3L));

        assertTrue(mines.stream().noneMatch(protectedCells::contains));
        assertEquals(6, mines.size());
    }

    @Test
    void buildMineLayoutClampsToAvailableCells() {
        MinefieldArenaHandler handler = newHandler();
        MinefieldArenaHandler.FieldBounds bounds = new MinefieldArenaHandler.FieldBounds(0, 2, 64, 0, 0);
        Set<String> playable = Set.of("0,0", "1,0", "2,0");
        Set<String> protectedCells = Set.of("0,0");

        Set<String> mines = handler.buildMineLayout(bounds, playable, 10, protectedCells, new Random(7L));

        assertEquals(Set.of("1,0", "2,0"), mines);
    }

    @Test
    void carveGuaranteedPathReturnsEmptyWhenGapSplitsField() {
        MinefieldArenaHandler handler = newHandler();
        MinefieldArenaHandler.FieldBounds bounds = new MinefieldArenaHandler.FieldBounds(0, 3, 64, 0, 0);

        Set<String> path = handler.carveGuaranteedPath(
            bounds,
            Set.of("0,0", "1,0", "3,0"),
            new Location(null, -1.0d, 64.0d, 0.0d),
            new Location(null, 4.0d, 64.0d, 0.0d),
            new Random(1L)
        );

        assertTrue(path.isEmpty());
    }

    @Test
    void queueAutoStartIfReadyStartsWaitingArenaOnNextTick() {
        TaskService tasks = mock(TaskService.class);
        MinefieldArenaHandler handler = newHandler(tasks);
        MiniGameArena arena = mock(MiniGameArena.class);

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);
        when(arena.numPlayers()).thenReturn(1);
        when(arena.getMinPlayers()).thenReturn(1);
        when(arena.get("suppressAutoStart", Boolean.class, false)).thenReturn(false);

        handler.queueAutoStartIfReady(arena);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(tasks).runLater(eq(1L), taskCaptor.capture());

        taskCaptor.getValue().run();

        verify(arena).setStatus(MiniGameArena.ArenaStatus.STARTING, 30);
    }

    private MinefieldArenaHandler newHandler() {
        return newHandler(mock(TaskService.class));
    }

    private MinefieldArenaHandler newHandler(TaskService tasks) {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        MinefieldMiniGame game = mock(MinefieldMiniGame.class);
        MiniGame minigame = mock(MiniGame.class);

        when(api.events()).thenReturn(events);
        when(api.tasks()).thenReturn(tasks);
        when(events.register(any(), any())).thenReturn(mock(org.bukkit.event.Listener.class));
        when(game.minigame()).thenReturn(minigame);
        when(game.startCountdownSeconds(any())).thenReturn(30);

        return new MinefieldArenaHandler(api, game);
    }
}
