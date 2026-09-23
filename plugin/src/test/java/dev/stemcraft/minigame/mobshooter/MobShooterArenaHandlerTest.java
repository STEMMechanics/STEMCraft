package dev.stemcraft.minigame.mobshooter;

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

class MobShooterArenaHandlerTest {
    private ServerMock server;
    private WorldMock world;
    private STEMCraftAPI api;
    private MiniGameArena arena;
    private MobShooterArenaSettings settings;
    private List<Player> players;
    private MobShooterMiniGame game;
    private MobShooterArenaHandler handler;
    private MiniGameArena.ArenaStatus status;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("mobshooter");
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        arena = mock(MiniGameArena.class);
        settings = new MobShooterArenaSettings();
        players = new ArrayList<>();
        when(arena.getOrCreate(eq("mobshooterSettings"), eq(MobShooterArenaSettings.class), any())).thenReturn(settings);
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
        return new SCRegion(new CuboidRegion(BlockVector3.at(-10, 40, -10), BlockVector3.at(20, 80, 20)), world);
    }

    private void game() {
        game = new MobShooterMiniGame(api);
        game.game = mock(MiniGame.class);
        handler = new MobShooterArenaHandler(api, game);
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
    void mobShooterOnlyScoresItsOwnArrows() {
        game();
        Player shooter = join();
        settings.targets.add(new Location(world, 2, 64, 2));
        settings.targetCount = 1;
        org.bukkit.World spawnWorld = spy(world);
        var target = mock(org.bukkit.entity.Chicken.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.isValid()).thenReturn(true);
        when(target.getLocation()).thenReturn(settings.targets.getFirst());
        doReturn(target).when(spawnWorld).spawn(any(Location.class), any());
        when(arena.world()).thenReturn(spawnWorld);
        when(game.game.findPlayer(shooter)).thenReturn(arena);
        handler.registerListeners();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<dev.stemcraft.api.service.event.EventHandler<org.bukkit.event.entity.EntityDamageEvent>> damage = ArgumentCaptor.forClass(dev.stemcraft.api.service.event.EventHandler.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<dev.stemcraft.api.service.event.EventHandler<org.bukkit.event.entity.EntityShootBowEvent>> shoot = ArgumentCaptor.forClass(dev.stemcraft.api.service.event.EventHandler.class);
        verify(api.events()).register(eq(org.bukkit.event.entity.EntityDamageEvent.class), damage.capture());
        verify(api.events()).register(eq(org.bukkit.event.entity.EntityShootBowEvent.class), shoot.capture());
        Runnable tick = start();
        for (int i = 0; i < 5; i++) tick.run();
        var arrow = mock(org.bukkit.entity.Arrow.class);
        when(arrow.getShooter()).thenReturn(shooter);
        var hit = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(hit.getEntity()).thenReturn(target);
        when(hit.getDamager()).thenReturn(arrow);
        damage.getValue().handle(hit);
        verify(arena.getPlayer(shooter), never()).setScore(3);
        var bow = mock(org.bukkit.event.entity.EntityShootBowEvent.class);
        when(bow.getEntity()).thenReturn(shooter);
        when(bow.getProjectile()).thenReturn(arrow);
        shoot.getValue().handle(bow);
        damage.getValue().handle(hit);
        verify(arena.getPlayer(shooter)).setScore(3);
        verify(arrow).remove();
        handler.onArenaUnload(arena);
        verify(target).remove();
    }

    @Test
    void validationRejectsMissingTargetSpawns() {
        game();
        var result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertTrue(result.getErrors().stream().anyMatch(s -> s.contains("target spawn")));
    }

    @Test
    void usesFrameworkDirectlyAndPersistsOnlyItsOwnSettings() {
        assertSame(dev.stemcraft.minigame.BaseMiniGame.class, MobShooterMiniGame.class.getSuperclass());
        game();
        var file = mock(dev.stemcraft.api.config.ConfigFile.class, RETURNS_DEEP_STUBS);
        var section = file.createSection("arenas.test", true);
        new MobShooterConfig(api, game, file).save(arena);
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
