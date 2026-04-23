package dev.stemcraft.minigame.tntrun;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.service.event.EventService;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.Listener;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TntRunArenaHandlerTest {
    @Test
    void resolveVoidYUsesArenaMinimumWhenConfiguredMatchesWorldMinHeight() {
        TntRunArenaHandler handler = newHandler();

        assertEquals(58, handler.resolveVoidY(new Location(mock(World.class), 0.0d, 64.0d, 0.0d), -64, -64));
    }

    @Test
    void resolveVoidYRespectsExplicitConfiguredValue() {
        TntRunArenaHandler handler = newHandler();

        assertEquals(42, handler.resolveVoidY(new Location(mock(World.class), 0.0d, 64.0d, 0.0d), 42, -64));
    }

    @Test
    void shouldEliminateRunnerWhenBelowVoidY() {
        TntRunArenaHandler handler = newHandler();

        assertTrue(handler.shouldEliminateRunner(new Location(mock(World.class), 5.0d, 49.0d, 5.0d), location -> true, 50));
    }

    @Test
    void shouldEliminateRunnerWhenLeavingArenaBounds() {
        TntRunArenaHandler handler = newHandler();
        Predicate<Location> insideArena = location ->
            location.getBlockX() >= 0 && location.getBlockX() <= 10
                && location.getBlockZ() >= 0 && location.getBlockZ() <= 10;

        assertFalse(handler.shouldEliminateRunner(new Location(mock(World.class), 5.0d, 65.0d, 5.0d), insideArena, 50));
        assertTrue(handler.shouldEliminateRunner(new Location(mock(World.class), 11.0d, 65.0d, 5.0d), insideArena, 50));
    }

    @Test
    void decayTrackerScansActivePlayersWhileRunning() {
        TaskService tasks = mock(TaskService.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        boolean[] scanned = {false};
        TntRunArenaHandler handler = newHandler(tasks, scanned);

        when(arena.id()).thenReturn("test");
        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.RUNNING);

        handler.startDecayTracker(arena);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(tasks).repeating(anyString(), eq(0L), eq(2L), taskCaptor.capture());

        taskCaptor.getValue().run();

        assertTrue(scanned[0]);
    }

    @Test
    void stationarySupportRequiresGracePeriodBeforeDecay() {
        TntRunArenaHandler handler = newHandler();
        MiniGameArena arena = mock(MiniGameArena.class);

        when(arena.getOrCreate(eq("occupiedSupportAges"), eq(Map.class), any())).thenReturn(new java.util.LinkedHashMap<String, Long>());

        Set<String> occupied = new LinkedHashSet<>(Set.of("0,64,0"));

        assertTrue(handler.updateOccupiedSupportAges(arena, occupied).isEmpty());
        assertTrue(handler.updateOccupiedSupportAges(arena, occupied).isEmpty());
        assertTrue(handler.updateOccupiedSupportAges(arena, occupied).isEmpty());
        assertEquals(Set.of("0,64,0"), handler.updateOccupiedSupportAges(arena, occupied));
    }

    @Test
    void queueAutoStartIfReadyStartsWaitingArenaOnNextTick() {
        TaskService tasks = mock(TaskService.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        TntRunArenaHandler handler = newHandler(tasks, null);

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);
        when(arena.numPlayers()).thenReturn(2);
        when(arena.getMinPlayers()).thenReturn(2);
        when(arena.get("suppressAutoStart", Boolean.class, false)).thenReturn(false);

        handler.queueAutoStartIfReady(arena);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(tasks).runLater(eq(1L), taskCaptor.capture());

        taskCaptor.getValue().run();

        verify(arena).setStatus(MiniGameArena.ArenaStatus.STARTING, 30);
    }

    @Test
    void queueAutoStartIfReadyDoesNothingWhenBelowMinimumPlayers() {
        TaskService tasks = mock(TaskService.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        TntRunArenaHandler handler = newHandler(tasks, null);

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);
        when(arena.numPlayers()).thenReturn(1);
        when(arena.getMinPlayers()).thenReturn(2);
        when(arena.get("suppressAutoStart", Boolean.class, false)).thenReturn(false);

        handler.queueAutoStartIfReady(arena);

        verify(tasks, never()).runLater(eq(1L), any());
    }

    @Test
    void resetArenaAfterRoundReturnsAllOccupants() {
        MiniGameArena arena = mock(MiniGameArena.class);
        TntRunArenaHandler handler = newHandler();

        when(arena.getOrCreate(eq("pendingDecays"), eq(Set.class), any())).thenReturn(new LinkedHashSet<String>());
        when(arena.getOrCreate(eq("occupiedSupportAges"), eq(Map.class), any())).thenReturn(new java.util.LinkedHashMap<String, Long>());
        when(arena.getOrCreate(eq("arenaSnapshot"), eq(Map.class), any())).thenReturn(new java.util.LinkedHashMap<String, Object>());

        handler.resetArenaAfterRound(arena);

        verify(arena).stopWinnerCelebration();
        verify(arena).removeAllOccupants();
    }

    @Test
    void startingCapturesCurrentArenaStateWithoutResettingOldSnapshotFirst() {
        MiniGameArena arena = mock(MiniGameArena.class);
        boolean[] resetCalled = {false};
        boolean[] captureCalled = {false};
        TntRunArenaHandler handler = newHandlerWithLifecycleHooks(resetCalled, captureCalled);

        when(arena.getOrCreate(eq("pendingDecays"), eq(Set.class), any())).thenReturn(new LinkedHashSet<String>());
        when(arena.getOrCreate(eq("occupiedSupportAges"), eq(Map.class), any())).thenReturn(new java.util.LinkedHashMap<String, Long>());
        when(arena.getOccupants()).thenReturn(List.of());

        handler.onArenaStatusChanged(arena, MiniGameArena.ArenaStatus.WAITING, MiniGameArena.ArenaStatus.STARTING);

        assertFalse(resetCalled[0]);
        assertTrue(captureCalled[0]);
    }

    @Test
    void idleUnloadDoesNotRestoreOldSnapshot() {
        MiniGameArena arena = mock(MiniGameArena.class);
        boolean[] resetCalled = {false};
        boolean[] captureCalled = {false};
        TntRunArenaHandler handler = newHandlerWithLifecycleHooks(resetCalled, captureCalled);

        when(arena.getStatus()).thenReturn(MiniGameArena.ArenaStatus.WAITING);
        when(arena.getOrCreate(eq("pendingDecays"), eq(Set.class), any())).thenReturn(new LinkedHashSet<String>());
        when(arena.getOrCreate(eq("occupiedSupportAges"), eq(Map.class), any())).thenReturn(new java.util.LinkedHashMap<String, Long>());

        handler.onArenaUnload(arena);

        assertFalse(resetCalled[0]);
        assertFalse(captureCalled[0]);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void snapshotAndResetRestoreExactArenaBlocksIncludingAirAndLava() {
        MiniGameArena arena = mock(MiniGameArena.class);
        World world = mock(World.class);
        TntRunArenaHandler handler = newHandlerWithBlockData(Map.of(
            "minecraft:lava[level=0]", mock(BlockData.class),
            "minecraft:air", mock(BlockData.class)
        ));
        Map snapshot = new LinkedHashMap<>();

        Block capturedLava = mock(Block.class);
        Block capturedAir = mock(Block.class);
        Block restoreLava = mock(Block.class);
        Block restoreAir = mock(Block.class);
        BlockData lavaData = handler.createBlockData("minecraft:lava[level=0]");
        BlockData airData = handler.createBlockData("minecraft:air");

        when(arena.world()).thenReturn(world);
        when(arena.getOrCreate(eq("arenaSnapshot"), eq(Map.class), any())).thenReturn(snapshot);

        when(capturedLava.getType()).thenReturn(Material.LAVA);
        when(capturedLava.getBlockData()).thenReturn(lavaData);
        when(capturedLava.getX()).thenReturn(0);
        when(capturedLava.getY()).thenReturn(64);
        when(capturedLava.getZ()).thenReturn(0);
        when(lavaData.getAsString()).thenReturn("minecraft:lava[level=0]");

        when(capturedAir.getType()).thenReturn(Material.AIR);
        when(capturedAir.getBlockData()).thenReturn(airData);
        when(capturedAir.getX()).thenReturn(1);
        when(capturedAir.getY()).thenReturn(64);
        when(capturedAir.getZ()).thenReturn(0);
        when(airData.getAsString()).thenReturn("minecraft:air");

        when(world.getBlockAt(0, 64, 0)).thenReturn(restoreLava);
        when(world.getBlockAt(1, 64, 0)).thenReturn(restoreAir);
        handler.snapshotBlock(snapshot, capturedLava);
        handler.snapshotBlock(snapshot, capturedAir);

        assertEquals(2, snapshot.size());
        handler.resetArenaBlocks(arena);

        verify(restoreLava).setType(Material.LAVA, false);
        verify(restoreLava).setBlockData(lavaData, false);
        verify(restoreAir).setType(Material.AIR, false);
        verify(restoreAir).setBlockData(airData, false);
    }

    @Test
    void allowsLavaDamageDuringLiveRound() {
        TntRunArenaHandler handler = newHandler();
        EntityDamageEvent event = mock(EntityDamageEvent.class);

        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.LAVA);
        when(event.getFinalDamage()).thenReturn(4.0d);

        assertTrue(handler.allowsLiveDamage(event));
    }

    @Test
    void fallDamageIsNotTreatedAsLiveHazardDamage() {
        TntRunArenaHandler handler = newHandler();
        EntityDamageEvent event = mock(EntityDamageEvent.class);

        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);

        assertFalse(handler.allowsLiveDamage(event));
    }

    private TntRunArenaHandler newHandler() {
        return newHandler(mock(TaskService.class), null);
    }

    private TntRunArenaHandler newHandlerWithLifecycleHooks(boolean[] resetCalled, boolean[] captureCalled) {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        TaskService tasks = mock(TaskService.class);
        TntRunMiniGame game = mock(TntRunMiniGame.class);
        MiniGame minigame = mock(MiniGame.class);

        when(api.events()).thenReturn(events);
        when(api.tasks()).thenReturn(tasks);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(game.minigame()).thenReturn(minigame);
        when(game.startCountdownSeconds(any())).thenReturn(30);
        when(game.joinOrder(any())).thenReturn(new ArrayList<>());
        when(game.startingGrid(any())).thenReturn(new ArrayList<>());
        when(game.assignedSpawnSlots(any())).thenReturn(new LinkedHashMap<>());

        return new TntRunArenaHandler(api, game) {
            @Override
            void captureArenaSnapshot(@NonNull MiniGameArena arena) {
                captureCalled[0] = true;
            }

            @Override
            void resetArenaBlocks(@NonNull MiniGameArena arena) {
                resetCalled[0] = true;
            }
        };
    }

    private TntRunArenaHandler newHandlerWithBlockData(Map<String, BlockData> blockDataByString) {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        TaskService tasks = mock(TaskService.class);
        TntRunMiniGame game = mock(TntRunMiniGame.class);
        MiniGame minigame = mock(MiniGame.class);

        when(api.events()).thenReturn(events);
        when(api.tasks()).thenReturn(tasks);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(game.minigame()).thenReturn(minigame);
        when(game.startCountdownSeconds(any())).thenReturn(30);

        return new TntRunArenaHandler(api, game) {
            @Override
            org.bukkit.block.data.BlockData createBlockData(@NonNull String blockData) {
                return blockDataByString.get(blockData);
            }
        };
    }

    private TntRunArenaHandler newHandler(TaskService tasks, boolean[] scanned) {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        EventService events = mock(EventService.class);
        TntRunMiniGame game = mock(TntRunMiniGame.class);
        MiniGame minigame = mock(MiniGame.class);

        when(api.events()).thenReturn(events);
        when(api.tasks()).thenReturn(tasks);
        when(events.register(any(), any())).thenReturn(mock(Listener.class));
        when(game.minigame()).thenReturn(minigame);
        when(game.startCountdownSeconds(any())).thenReturn(30);

        return new TntRunArenaHandler(api, game) {
            @Override
            void scheduleDecayUnderActivePlayers(@NonNull MiniGameArena arena) {
                if (scanned != null) {
                    scanned[0] = true;
                }
            }
        };
    }
}
