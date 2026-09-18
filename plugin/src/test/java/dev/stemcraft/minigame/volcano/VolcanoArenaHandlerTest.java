package dev.stemcraft.minigame.volcano;

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
