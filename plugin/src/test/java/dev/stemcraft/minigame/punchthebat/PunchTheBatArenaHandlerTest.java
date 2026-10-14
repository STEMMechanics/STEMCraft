package dev.stemcraft.minigame.punchthebat;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.*;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus.*;

class PunchTheBatArenaHandlerTest {
    private ServerMock server;
    private WorldMock world;
    private STEMCraftAPI api;
    private MiniGameArena arena;
    private PunchTheBatArenaSettings settings;
    private List<Player> players;
    private PunchTheBatMiniGame game;
    private PunchTheBatArenaHandler handler;
    private MiniGameArena.ArenaStatus status;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("punchthebat");
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        arena = mock(MiniGameArena.class);
        settings = new PunchTheBatArenaSettings();
        players = new ArrayList<>();
        when(arena.getOrCreate(eq("punchthebatSettings"), eq(PunchTheBatArenaSettings.class), any())).thenReturn(settings);
        when(arena.id()).thenReturn("test");
        when(arena.world()).thenReturn(world);
        when(arena.getRegion()).thenReturn(region());
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
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) world.getBlockAt(x, 63, z).setType(Material.STONE);
        settings.spawns.add(new Location(world, 1.5, 64, 1.5));
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    private SCRegion region() {
        return new SCRegion(new CuboidRegion(BlockVector3.at(-1, 63, -1), BlockVector3.at(6, 67, 6)), world);
    }

    private void game() {
        game = new PunchTheBatMiniGame(api);
        game.game = mock(MiniGame.class);
        handler = new PunchTheBatArenaHandler(api, game);
    }

    private Player join() {
        Player p = server.addPlayer();
        players.add(p);
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
        assertEquals(RUNNING, status);
        assertEquals(settings.spawns.getFirst(), first.getLocation());
        assertEquals(settings.spawns.get(1), second.getLocation());
    }

    @Test
    void batHitsScoreOnceAndRejectOutsiders() {
        game();
        Player player = join();
        Player outsider = server.addPlayer();
        settings.targets.add(new Location(world, 2, 65, 2));
        settings.targetCount = 1;
        org.bukkit.World spawnWorld = spy(world);
        var bat = mock(org.bukkit.entity.Bat.class);
        when(bat.getUniqueId()).thenReturn(UUID.randomUUID());
        when(bat.isValid()).thenReturn(true);
        when(bat.getLocation()).thenReturn(settings.targets.getFirst());
        when(bat.isGlowing()).thenReturn(true);
        doReturn(bat).when(spawnWorld).spawn(any(Location.class), eq(org.bukkit.entity.Bat.class));
        when(arena.world()).thenReturn(spawnWorld);
        handler.registerListeners();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<dev.stemcraft.api.service.event.EventHandler<org.bukkit.event.entity.EntityDamageEvent>> listener = ArgumentCaptor.forClass(dev.stemcraft.api.service.event.EventHandler.class);
        verify(api.events()).register(eq(org.bukkit.event.entity.EntityDamageEvent.class), listener.capture());
        Runnable tick = start();
        for (int i = 0; i < 5; i++) tick.run();
        var hit = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(hit.getEntity()).thenReturn(bat);
        when(hit.getDamager()).thenReturn(outsider);
        listener.getValue().handle(hit);
        verify(arena.getPlayer(player), never()).setScore(5);
        when(hit.getDamager()).thenReturn(player);
        listener.getValue().handle(hit);
        listener.getValue().handle(hit);
        verify(arena.getPlayer(player), times(1)).setScore(5);
        verify(bat, times(1)).remove();
        verify(hit, atLeastOnce()).setCancelled(true);
    }

    @Test
    void emptyListsAllowRandomSpawnsButConfiguredPlayersStillRequireCapacity() {
        game();
        settings.spawns.clear();
        when(arena.getMaxPlayers()).thenReturn(2);
        var result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertFalse(result.hasErrors(), result.getErrors().toString());
        settings.spawns.add(new Location(world, 1.5, 64, 1.5));
        result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertTrue(result.hasErrors());
        settings.spawns.add(new Location(world, 2.5, 64, 2.5));
        result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertFalse(result.hasErrors(), result.getErrors().toString());
    }

    @Test
    void randomSpawnsRequireSafeGroundAndHeadroom() {
        game();
        settings.spawns.clear();
        when(arena.getRegion()).thenReturn(new SCRegion(
                new CuboidRegion(BlockVector3.at(0, 64, 0), BlockVector3.at(0, 65, 0)), world));
        world.getBlockAt(0, 63, 0).setType(Material.MAGMA_BLOCK);
        var result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertTrue(result.hasErrors());
        world.getBlockAt(0, 63, 0).setType(Material.STONE);
        world.getBlockAt(0, 65, 0).setType(Material.STONE);
        result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertTrue(result.hasErrors());
        world.getBlockAt(0, 65, 0).setType(Material.AIR);
        result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertFalse(result.hasErrors(), result.getErrors().toString());
    }

    @Test
    void randomPlayersWaitInLobbyThenUseDistinctSafePositions() {
        game();
        settings.spawns.clear();
        Player first = join();
        first.teleport(arena.getLobbySpawn());
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        Player second = join();
        second.teleport(handler.onPlayerJoinArena(arena, second));
        assertEquals(arena.getLobbySpawn(), first.getLocation());
        assertEquals(arena.getLobbySpawn(), second.getLocation());
        handler.onArenaCountdownEnd(arena);
        for (Player p : players) {
            assertEquals(64, p.getLocation().getY());
            assertTrue(arena.getRegion().contains(p.getLocation()));
            assertEquals(Material.STONE, p.getLocation().subtract(0, 1, 0).getBlock().getType());
        }
        assertNotEquals(first.getLocation(), second.getLocation());
        assertTrue(settings.spawns.isEmpty());
    }

    @Test
    void emptyTargetListSpawnsAndReplacesBatsAtReachableLocations() {
        game();
        join();
        settings.targetCount = 2;
        org.bukkit.World spawnWorld = spy(world);
        List<org.bukkit.entity.Bat> bats = new ArrayList<>();
        doAnswer(invocation -> {
            Location at = invocation.getArgument(0);
            assertEquals(64.5, at.getY());
            assertTrue(arena.getRegion().contains(at));
            var bat = mock(org.bukkit.entity.Bat.class);
            when(bat.getUniqueId()).thenReturn(UUID.randomUUID());
            when(bat.isValid()).thenReturn(true);
            when(bat.getLocation()).thenReturn(at);
            bats.add(bat);
            return bat;
        }).when(spawnWorld).spawn(any(Location.class), eq(org.bukkit.entity.Bat.class));
        Runnable tick = start();
        when(arena.world()).thenReturn(spawnWorld);
        for (int i = 0; i < 5; i++) tick.run();
        assertEquals(2, bats.size());
        when(bats.getFirst().isValid()).thenReturn(false);
        for (int i = 0; i < 5; i++) tick.run();
        assertEquals(3, bats.size());
        assertTrue(settings.targets.isEmpty());
    }

    @Test
    void usesFrameworkDirectlyAndPersistsOnlyItsOwnSettings() {
        assertSame(dev.stemcraft.minigame.BaseMiniGame.class, PunchTheBatMiniGame.class.getSuperclass());
        game();
        var file = mock(dev.stemcraft.api.config.ConfigFile.class, RETURNS_DEEP_STUBS);
        var section = file.createSection("arenas.test", true);
        new PunchTheBatConfig(api, game, file).save(arena);
        verify(section).set("world", world.getName());
        verify(section).set(eq("spawns"), any());
        verify(section).set("round-seconds", 180);
        verify(section).set(eq("targets"), any());
        verify(section).set(eq("target-count"), any());
        verify(section, never()).set(eq("floor"), any());
        verify(section, never()).set(eq("kill-target"), any());
        verify(section, never()).set(eq("laps"), any());
        verify(section, never()).set(eq("reload-ticks"), any());
        verify(file).save();
    }
}
