package dev.stemcraft.minigame.volcano;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.*;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.*;

class VolcanoArenaHandlerTest {
    private ServerMock server;
    private WorldMock world;
    private STEMCraftAPI api;
    private MiniGameArena arena;
    private VolcanoArenaSettings settings;
    private List<Player> players;
    private VolcanoMiniGame game;
    private VolcanoArenaHandler handler;
    private MiniGameArena.ArenaStatus status;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("volcano");
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        arena = mock(MiniGameArena.class);
        settings = new VolcanoArenaSettings();
        players = new ArrayList<>();
        when(arena.getOrCreate(eq("volcanoSettings"), eq(VolcanoArenaSettings.class), any())).thenReturn(settings);
        when(arena.id()).thenReturn("test");
        when(arena.world()).thenReturn(world);
        when(arena.getRegion()).thenReturn(region(-10, 40, -10, 20, 80, 20));
        when(arena.getLobbySpawn()).thenReturn(new Location(world, 0, 65, 0));
        when(arena.getSpectatorSpawn()).thenReturn(new Location(world, 0, 70, 0));
        when(arena.getMinPlayers()).thenReturn(1);
        when(arena.getMaxPlayers()).thenReturn(1);
        when(arena.getPlayers()).thenAnswer(_ -> new ArrayList<>(players));
        when(arena.getOccupants()).thenAnswer(_ -> new ArrayList<>(players));
        when(arena.numPlayers()).thenAnswer(_ -> players.size());
        when(arena.hasPlayer(any())).thenAnswer(i -> players.contains(i.getArgument(0, Player.class)));
        when(arena.getPlayer(any())).thenReturn(mock(MiniGamePlayer.class));
        when(arena.getStatus()).thenAnswer(_ -> status);
        when(arena.setStatus(any(), anyInt())).thenAnswer(i -> {
            var before = status;
            status = i.getArgument(0);
            handler.onArenaStatusChanged(arena, before, status);
            return arena;
        });
        doAnswer(i -> {
            players.remove(i.getArgument(0, Player.class));
            return null;
        }).when(arena).addSpectator(any());
        settings.floor = region(0, 63, 0, 5, 63, 5);
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) world.getBlockAt(x, 63, z).setType(Material.STONE);
        settings.spawns.add(new Location(world, 1.5, 64, 1.5));
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    private SCRegion region(int x, int y, int z, int xx, int yy, int zz) {
        return new SCRegion(new CuboidRegion(BlockVector3.at(x, y, z), BlockVector3.at(xx, yy, zz)), world);
    }

    private void game() {
        game = new VolcanoMiniGame(api);
        game.game = mock(MiniGame.class);
        handler = new VolcanoArenaHandler(api, game);
    }

    private Player join() {
        Player p = spy(server.addPlayer());
        // MockBukkit does not implement damage(double, DamageSource).
        doNothing().when(p).damage(anyDouble(), any(DamageSource.class));
        players.add(p);
        when(arena.getPlayer(p)).thenReturn(mock(MiniGamePlayer.class));
        return p;
    }

    private Runnable start() {
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        status = RUNNING;
        handler.onArenaStatusChanged(arena, STARTING, RUNNING);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(api.tasks()).repeating(anyString(), eq(2L), callback.capture());
        return callback.getValue();
    }

    @Test
    void countdownKeepsInitialAndLateJoiningPlayersInLobby() {
        game();
        Player first = join();
        first.teleport(arena.getLobbySpawn());
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        assertEquals(arena.getLobbySpawn(), first.getLocation());
        assertEquals(arena.getLobbySpawn(), handler.onPlayerJoinArena(arena, first));
        settings.spawns.add(new Location(world, 3, 64, 3));
        Player second = join();
        second.teleport(handler.onPlayerJoinArena(arena, second));
        assertEquals(arena.getLobbySpawn(), second.getLocation());
        handler.onArenaCountdownEnd(arena);
        assertEquals(PREPARATION, status);
        assertEquals(settings.spawns.getFirst(), first.getLocation());
        assertEquals(settings.spawns.get(1), second.getLocation());
        verify(arena).setStatus(PREPARATION, 15);
        verify(api.tasks(), never()).repeating(anyString(), eq(2L), any(Runnable.class));
        assertEquals("Get ready!", handler.objective(arena));
        assertEquals("00:00", handler.survivalTime(arena, first));
        Location readyPosition = new Location(world, 2.5, 64, 2.5, 120, 20);
        first.teleport(readyPosition);
        settings.spawns.add(new Location(world, 4.5, 64, 4.5));
        Player third = join();
        third.teleport(handler.onPlayerJoinArena(arena, third));
        assertEquals(settings.spawns.get(2), third.getLocation());
        handler.onArenaCountdownEnd(arena);
        assertEquals(RUNNING, status);
        assertEquals(readyPosition, first.getLocation());
        verify(arena).setStatus(RUNNING, settings.roundSeconds);
    }

    @Test
    void validationAllowsNoSpawnsButRequiresCapacityWhenAnyAreConfigured() {
        game();
        when(arena.getMaxPlayers()).thenReturn(2);
        settings.spawns.clear();
        var result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertFalse(result.hasErrors(), result.getErrors().toString());
        settings.spawns.add(new Location(world, 1.5, 64, 1.5));
        result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertTrue(result.getErrors().contains("Add at least maxplayers starting spawns."));
        settings.spawns.add(new Location(world, 3.5, 64, 3.5));
        result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertFalse(result.hasErrors(), result.getErrors().toString());
    }

    @Test
    void randomSpawnsPlaceStartingAndCountdownPlayersOneBlockAboveFloor() {
        game();
        settings.spawns.clear();
        Player first = join();
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        assertEquals(arena.getLobbySpawn(), handler.onPlayerJoinArena(arena, first));
        Player second = join();
        assertEquals(arena.getLobbySpawn(), handler.onPlayerJoinArena(arena, second));
        handler.onArenaCountdownEnd(arena);
        assertAboveFloor(first.getLocation());
        assertAboveFloor(second.getLocation());
        assertTrue(settings.spawns.isEmpty());
    }

    @Test
    void hazardDamageOnlyEliminatesWhenLethalAndIsDisabledInLobby() {
        game();
        Player p = join();
        start();
        for (var type : List.of(DamageType.HOT_FLOOR, DamageType.IN_FIRE, DamageType.ON_FIRE, DamageType.LAVA)) {
            var event = mock(org.bukkit.event.entity.EntityDamageEvent.class);
            when(event.getEntity()).thenReturn(p);
            when(event.getDamageSource()).thenReturn(DamageSource.builder(type).build());
            when(event.getFinalDamage()).thenReturn(1.0);
            assertEquals(MiniGameArenaHandler.HandlerEventResult.ALLOW, handler.onEntityDamage(arena, event));
            assertTrue(players.contains(p));
            status = STARTING;
            assertEquals(MiniGameArenaHandler.HandlerEventResult.DENY, handler.onEntityDamage(arena, event));
            status = RUNNING;
        }
        p.setHealth(1.0);
        var lethal = mock(org.bukkit.event.entity.EntityDamageEvent.class);
        when(lethal.getEntity()).thenReturn(p);
        when(lethal.getDamageSource()).thenReturn(DamageSource.builder(DamageType.HOT_FLOOR).build());
        when(lethal.getFinalDamage()).thenReturn(1.0);
        assertEquals(MiniGameArenaHandler.HandlerEventResult.DENY, handler.onEntityDamage(arena, lethal));
        verify(arena).addSpectator(p);
        verify(arena.getPlayer(p)).setDeaths(1);
        assertEquals(ENDING, status);
    }

    @Test
    void touchingImpactMagmaDoesNotImmediatelyEliminatePlayer() {
        game();
        Player p = join();
        Runnable tick = start();
        Block magma = null;
        for (int i = 0; i < 60 && magma == null; i++) {
            tick.run();
            for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) {
                if (world.getBlockAt(x, 63, z).getType() == Material.MAGMA_BLOCK) magma = world.getBlockAt(x, 63, z);
            }
        }
        assertNotNull(magma);
        p.teleport(magma.getLocation().clone().add(0.5, 1, 0.5));
        tick.run();
        assertTrue(players.contains(p));
        assertEquals(RUNNING, status);
    }

    @Test
    void flameMarkersBurnOncePerSecond() throws Exception {
        game();
        Player p = join();
        Runnable tick = start();
        round().eruption = mock(VolcanoEruption.class);
        when(round().eruption.isWarning(world.getBlockAt(1, 63, 1))).thenReturn(true);
        tick.run();
        verify(p).damage(eq(1.0), argThat((DamageSource source) -> source.getDamageType() == DamageType.IN_FIRE));
        for (int i = 0; i < 9; i++) tick.run();
        verify(p, times(1)).damage(eq(1.0), any(DamageSource.class));
        tick.run();
        verify(p, times(2)).damage(eq(1.0), any(DamageSource.class));
    }

    @SuppressWarnings("unchecked")
    private VolcanoArenaHandler.Round round() throws Exception {
        var field = VolcanoArenaHandler.class.getDeclaredField("rounds");
        field.setAccessible(true);
        return ((Map<MiniGameArena, VolcanoArenaHandler.Round>) field.get(handler)).get(arena);
    }

    @Test
    void survivalTimeStopsForEliminatedPlayersWhileOthersContinue() {
        game();
        Player first = join();
        Player second = join();
        Player third = join();
        settings.spawns.add(new Location(world, 3.5, 64, 3.5));
        settings.spawns.add(new Location(world, 4.5, 64, 4.5));
        Runnable tick = start();
        for (int i = 0; i < 10; i++) tick.run();
        assertEquals("00:01", handler.survivalTime(arena, first));
        first.teleport(new Location(world, 1, 60, 1));
        tick.run();
        for (int i = 0; i < 10; i++) tick.run();
        assertEquals(RUNNING, status);
        assertEquals("00:01", handler.survivalTime(arena, first));
        assertEquals("00:02", handler.survivalTime(arena, second));
        assertEquals("00:02", handler.survivalTime(arena, third));
        assertTrue(handler.progress(arena, second).contains("Survived: 00:02"));
        assertFalse(handler.objective(arena).contains("Eruption"));
        verify(arena.getPlayer(second)).setScore(2);
    }

    @Test
    void managedLavaCannotFlowOrChangeLevelsAndIsRestoredOnStop() {
        game();
        join();
        Runnable tick = start();
        Block lava = null;
        for (int i = 0; i < 60 && lava == null; i++) {
            tick.run();
            for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) {
                if (world.getBlockAt(x, 64, z).getType() == Material.LAVA) lava = world.getBlockAt(x, 64, z);
            }
        }
        assertNotNull(lava);
        var outgoing = new BlockFromToEvent(lava, BlockFace.DOWN);
        handler.onFluidFlow(outgoing);
        assertTrue(outgoing.isCancelled());
        var incoming = new BlockFromToEvent(lava.getRelative(BlockFace.UP), lava);
        handler.onFluidFlow(incoming);
        assertTrue(incoming.isCancelled());
        var levels = new FluidLevelChangeEvent(lava, Material.AIR.createBlockData());
        handler.onFluidLevelChange(levels);
        assertTrue(levels.isCancelled());
        var forming = new BlockFormEvent(lava, lava.getState());
        handler.onLavaForm(forming);
        assertTrue(forming.isCancelled());
        var ignition = new BlockIgniteEvent(lava.getRelative(BlockFace.UP), BlockIgniteEvent.IgniteCause.LAVA, lava);
        handler.onLavaIgnite(ignition);
        assertTrue(ignition.isCancelled());
        var unrelated = new BlockFromToEvent(world.getBlockAt(15, 64, 15), BlockFace.DOWN);
        handler.onFluidFlow(unrelated);
        assertFalse(unrelated.isCancelled());
        var naturalIgnition = new BlockIgniteEvent(world.getBlockAt(15, 64, 15),
                BlockIgniteEvent.IgniteCause.LAVA, world.getBlockAt(15, 63, 15));
        handler.onLavaIgnite(naturalIgnition);
        assertFalse(naturalIgnition.isCancelled());
        handler.registerListeners();
        verify(api.events()).register(eq(BlockFromToEvent.class), any(), eq(EventPriority.HIGHEST), eq(true));
        verify(api.events()).register(eq(FluidLevelChangeEvent.class), any(), eq(EventPriority.HIGHEST), eq(true));
        verify(api.events()).register(eq(BlockFormEvent.class), any(), eq(EventPriority.HIGHEST), eq(true));
        verify(api.events()).register(eq(BlockIgniteEvent.class), any(), eq(EventPriority.HIGHEST), eq(true));
        arena.setStatus(DISABLED, 0);
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) {
            assertEquals(Material.STONE, world.getBlockAt(x, 63, z).getType());
            assertEquals(Material.AIR, world.getBlockAt(x, 64, z).getType());
        }
        var afterStop = new BlockFromToEvent(lava, BlockFace.DOWN);
        handler.onFluidFlow(afterStop);
        assertFalse(afterStop.isCancelled());
        for (int i = 0; i < 60; i++) tick.run();
        assertEquals(Material.AIR, lava.getType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"shutdown", "unload", "normal-end"})
    void restoresActiveLavaOnShutdownUnloadAndNormalCompletion(String end) {
        game();
        Player player = join();
        Runnable tick = start();
        for (int i = 0; i < 60; i++) tick.run();
        assertTrue(java.util.stream.IntStream.range(0, 36)
                .anyMatch(i -> world.getBlockAt(i / 6, 64, i % 6).getType() == Material.LAVA));
        player.setFireTicks(80);
        switch (end) {
            case "shutdown" -> arena.setStatus(SHUTDOWN, 0);
            case "unload" -> handler.onArenaUnload(arena);
            case "normal-end" -> {
                handler.onArenaCountdownEnd(arena);
                assertEquals(ENDING, status);
                handler.onArenaCountdownEnd(arena);
                assertEquals(WAITING, status);
            }
            default -> fail("Unknown ending");
        }
        assertEquals(0, player.getFireTicks());
        for (int i = 0; i < 60; i++) tick.run();
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) {
            assertEquals(Material.STONE, world.getBlockAt(x, 63, z).getType());
            assertEquals(Material.AIR, world.getBlockAt(x, 64, z).getType());
        }
    }

    @Test
    void soloJoinWaitsForFullCountdown() {
        game();
        Player p = join();
        status = WAITING;
        p.teleport(handler.onPlayerJoinArena(arena, p));
        ArgumentCaptor<Runnable> countdown = ArgumentCaptor.forClass(Runnable.class);
        verify(api.tasks()).runLater(eq(1L), countdown.capture());
        countdown.getValue().run();
        assertEquals(STARTING, status);
        verify(arena).setCountdown(30);
        assertEquals(arena.getLobbySpawn(), p.getLocation());
        handler.onArenaCountdownEnd(arena);
        assertEquals(PREPARATION, status);
        assertEquals(settings.spawns.getFirst(), p.getLocation());
        handler.onArenaCountdownEnd(arena);
        assertEquals(RUNNING, status);
        assertEquals(settings.spawns.getFirst(), p.getLocation());
    }

    @Test
    void zeroStartDelaySkipsPreparation() {
        game();
        join();
        settings.startDelaySeconds = 0;
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        handler.onArenaCountdownEnd(arena);
        assertEquals(RUNNING, status);
        verify(arena, never()).setStatus(eq(PREPARATION), anyInt());
    }

    @Test
    void leavingDuringPreparationCancelsTheRoundWithoutEruptions() {
        game();
        Player player = join();
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        handler.onArenaCountdownEnd(arena);
        assertEquals(PREPARATION, status);
        players.remove(player);
        handler.onPlayerLeaveArena(arena, player);
        assertEquals(WAITING, status);
        handler.onArenaCountdownEnd(arena);
        assertEquals(WAITING, status);
        verify(api.tasks(), never()).repeating(anyString(), eq(2L), any(Runnable.class));
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) {
            assertEquals(Material.STONE, world.getBlockAt(x, 63, z).getType());
            assertEquals(Material.AIR, world.getBlockAt(x, 64, z).getType());
        }
    }

    private void assertAboveFloor(Location spawn) {
        assertEquals(world, spawn.getWorld());
        assertEquals(64, spawn.getY());
        assertEquals(spawn.getBlockX() + 0.5, spawn.getX());
        assertEquals(spawn.getBlockZ() + 0.5, spawn.getZ());
        assertTrue(settings.floor.contains(spawn.clone().subtract(0, 1, 0)));
    }

    @Test
    void soloSurvivalDoesNotEndOnFirstTickButEndsWhenTesterFalls() {
        game();
        Player p = join();
        Runnable tick = start();
        tick.run();
        assertEquals(RUNNING, status);
        p.teleport(new Location(world, 1, 60, 1));
        tick.run();
        assertEquals(ENDING, status);
        verify(game.game, never()).rewardWinners(any(), any());
    }

    @Test
    void twoPlayerSurvivalAwardsRemainingPlayer() {
        game();
        Player first = join();
        Player second = join();
        settings.spawns.add(new Location(world, 3.5, 64, 3.5));
        Runnable tick = start();
        first.teleport(new Location(world, 1, 60, 1));
        tick.run();
        assertEquals(ENDING, status);
        verify(game.game).rewardWinners(arena, List.of(second.getUniqueId()));
    }

    @Test
    void simultaneousFallsDoNotAwardAnAlreadyFallingSurvivor() {
        game();
        Player first = join();
        Player second = join();
        settings.spawns.add(new Location(world, 3.5, 64, 3.5));
        Runnable tick = start();
        first.teleport(new Location(world, 1, 60, 1));
        second.teleport(new Location(world, 3, 60, 3));
        tick.run();
        assertEquals(ENDING, status);
        verify(game.game, never()).rewardWinners(any(), any());
    }

    @Test
    void usesFrameworkDirectlyAndPersistsOnlyItsOwnSettings() {
        assertSame(dev.stemcraft.minigame.BaseMiniGame.class, VolcanoMiniGame.class.getSuperclass());
        game();
        var file = mock(dev.stemcraft.api.config.ConfigFile.class, RETURNS_DEEP_STUBS);
        var section = file.createSection("arenas.test", true);
        new VolcanoConfig(api, game, file).save(arena);
        verify(section).set("world", world.getName());
        verify(section).set(eq("spawns"), any());
        verify(section).set("round-seconds", 180);
        verify(section).set(eq("floor"), any());
        verify(section, never()).set(eq("kill-target"), any());
        verify(section, never()).set(eq("laps"), any());
        verify(section, never()).set(eq("reload-ticks"), any());
        verify(section, never()).set(eq("target-count"), any());
        verify(section, never()).set(eq("targets"), any());
        verify(file).save();
    }
}
