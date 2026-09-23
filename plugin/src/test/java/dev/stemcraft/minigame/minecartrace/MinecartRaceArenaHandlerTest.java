package dev.stemcraft.minigame.minecartrace;

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

class MinecartRaceArenaHandlerTest {
    private ServerMock server;
    private WorldMock world;
    private STEMCraftAPI api;
    private MiniGameArena arena;
    private MinecartRaceArenaSettings settings;
    private List<Player> players;
    private MinecartRaceMiniGame game;
    private MinecartRaceArenaHandler handler;
    private MiniGameArena.ArenaStatus status;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("minecartrace");
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        arena = mock(MiniGameArena.class);
        settings = new MinecartRaceArenaSettings();
        players = new ArrayList<>();
        when(arena.getOrCreate(eq("minecartraceSettings"), eq(MinecartRaceArenaSettings.class), any())).thenReturn(settings);
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
        game = new MinecartRaceMiniGame(api);
        game.game = mock(MiniGame.class);
        handler = new MinecartRaceArenaHandler(api, game);
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
    void countdownJoinReturnsAssignedSpawnInsteadOfLobby() {
        game();
        Player first = join();
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        assertEquals(settings.spawns.getFirst(), handler.onPlayerJoinArena(arena, first));
        settings.spawns.add(new Location(world, 3, 64, 3));
        Player second = join();
        assertEquals(settings.spawns.get(1), handler.onPlayerJoinArena(arena, second));
    }

    @Test
    void minecartRaceRequiresEveryGateBeforeFinishing() {
        raceGates();
    }

    private void raceGates() {
        game();
        settings.laps = 1;
        settings.checkpoints.add(region(5, 63, 0, 5, 67, 3));
        settings.checkpoints.add(region(10, 63, 0, 10, 67, 3));
        Player rider = mock(Player.class, RETURNS_DEEP_STUBS);
        UUID id = UUID.randomUUID();
        when(rider.getUniqueId()).thenReturn(id);
        when(rider.getName()).thenReturn("Racer");
        Location[] position = {settings.spawns.getFirst()};
        when(rider.getLocation()).thenAnswer(_ -> position[0].clone());
        players.add(rider);
        org.bukkit.entity.Entity mount = mock(org.bukkit.entity.Minecart.class, RETURNS_DEEP_STUBS);
        when(mount.getLocation()).thenAnswer(_ -> position[0].clone());
        when(mount.isValid()).thenReturn(true);
        when(mount.getVelocity()).thenReturn(new org.bukkit.util.Vector(0.2, 0, 0));
        when(rider.getVehicle()).thenReturn(mount);
        org.bukkit.World spawnWorld = spy(world);
        when(arena.world()).thenReturn(spawnWorld);
        doReturn(mount).when(spawnWorld).spawn(any(Location.class), any());
        Runnable tick = start();
        // Reach the finish via a detour that misses the required first gate.
        position[0] = new Location(world, 1, 64, 8);
        tick.run();
        position[0] = new Location(world, 10, 64, 8);
        tick.run();
        position[0] = new Location(world, 10, 64, 1);
        tick.run();
        assertEquals(RUNNING, status);
        assertEquals("Gates: 0/2", handler.progress(arena, rider));
        position[0] = new Location(world, 5, 64, 1);
        tick.run();
        assertEquals("Gates: 1/2", handler.progress(arena, rider));
        position[0] = new Location(world, 10, 64, 1);
        tick.run();
        assertEquals(ENDING, status);
        verify(arena.getPlayer(rider)).setScore(2);
        verify(mount).remove();
    }

    @Test
    void usesFrameworkDirectlyAndPersistsOnlyItsOwnSettings() {
        assertSame(dev.stemcraft.minigame.BaseMiniGame.class, MinecartRaceMiniGame.class.getSuperclass());
        game();
        var file = mock(dev.stemcraft.api.config.ConfigFile.class, RETURNS_DEEP_STUBS);
        var section = file.createSection("arenas.test", true);
        new MinecartRaceConfig(api, game, file).save(arena);
        verify(section).set("world", world.getName());
        verify(section).set(eq("spawns"), any());
        verify(section).set("round-seconds", 180);
        verify(section).set(eq("laps"), any());
        verify(section, never()).set(eq("floor"), any());
        verify(section, never()).set(eq("kill-target"), any());
        verify(section, never()).set(eq("reload-ticks"), any());
        verify(section, never()).set(eq("target-count"), any());
        verify(section, never()).set(eq("targets"), any());
        verify(file).save();
    }
}
