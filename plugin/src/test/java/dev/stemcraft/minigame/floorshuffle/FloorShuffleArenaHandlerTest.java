package dev.stemcraft.minigame.floorshuffle;

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

class FloorShuffleArenaHandlerTest {
    private ServerMock server;
    private WorldMock world;
    private STEMCraftAPI api;
    private MiniGameArena arena;
    private FloorShuffleArenaSettings settings;
    private List<Player> players;
    private FloorShuffleMiniGame game;
    private FloorShuffleArenaHandler handler;
    private MiniGameArena.ArenaStatus status;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("floorshuffle");
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        arena = mock(MiniGameArena.class);
        settings = new FloorShuffleArenaSettings();
        players = new ArrayList<>();
        when(arena.getOrCreate(eq("floorshuffleSettings"), eq(FloorShuffleArenaSettings.class), any())).thenReturn(settings);
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
        game = new FloorShuffleMiniGame(api);
        game.game = mock(MiniGame.class);
        handler = new FloorShuffleArenaHandler(api, game);
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
    void floorRestoresExactBlocksOnDisableAndCancelsLoop() {
        game();
        join();
        Runnable tick = start();
        assertNotEquals(Material.STONE, world.getBlockAt(1, 63, 1).getType());
        handler.onArenaUnload(arena);
        for (int x = 0; x < 6; x++)
            for (int z = 0; z < 6; z++) assertEquals(Material.STONE, world.getBlockAt(x, 63, z).getType());
        tick.run(); // A queued callback cannot mutate a cleaned arena.
        assertEquals(Material.STONE, world.getBlockAt(1, 63, 1).getType());
        verify(api.tasks(), atLeastOnce()).cancel("minigame:floorshuffle:test");
    }

    @Test
    void validationAcceptsConfiguredFlatFloorAndRejectsUnsafeSettings() {
        game();
        var result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertFalse(result.hasErrors(), result.getErrors().toString());
        settings.countdown = 10000;
        result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertTrue(result.hasErrors());
    }

    @Test
    void validationRejectsNonFlatFloor() {
        game();
        settings.floor = region(0, 63, 0, 5, 64, 5);
        var result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertTrue(result.hasErrors());
    }

    @Test
    void usesFrameworkDirectlyAndPersistsOnlyItsOwnSettings() {
        assertSame(dev.stemcraft.minigame.BaseMiniGame.class, FloorShuffleMiniGame.class.getSuperclass());
        game();
        var file = mock(dev.stemcraft.api.config.ConfigFile.class, RETURNS_DEEP_STUBS);
        var section = file.createSection("arenas.test", true);
        new FloorShuffleConfig(api, game, file).save(arena);
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
