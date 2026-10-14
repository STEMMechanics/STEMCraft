package dev.stemcraft.minigame.floorshuffle;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.*;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.time.Duration;

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
    void soloJoinStaysInLobbyUntilCountdownEnds() {
        game();
        Player first = join();
        status = WAITING;
        first.teleport(handler.onPlayerJoinArena(arena, first));
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(api.tasks()).runLater(eq(1L), callback.capture());
        callback.getValue().run();
        assertEquals(STARTING, status);
        verify(arena).setCountdown(30);
        assertEquals(arena.getLobbySpawn(), first.getLocation());

        settings.spawns.add(new Location(world, 3, 64, 3));
        Player second = join();
        second.teleport(handler.onPlayerJoinArena(arena, second));
        assertEquals(arena.getLobbySpawn(), second.getLocation());
        handler.onArenaCountdownEnd(arena);
        assertEquals(PREPARATION, status);
        assertEquals(settings.spawns.getFirst(), first.getLocation());
        assertEquals(settings.spawns.get(1), second.getLocation());
        verify(arena).setStatus(PREPARATION, 15);
        verify(api.tasks(), never()).repeating(eq("minigame:floorshuffle:test"), eq(2L), any(Runnable.class));
        assertEquals("Get ready!", handler.objective(arena));
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
    void unconfiguredSpawnsPlaceAllPlayersAboveFloorAfterLobbyCountdown() {
        game();
        settings.spawns.clear();
        Player first = join();
        first.teleport(arena.getLobbySpawn());
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        assertEquals(arena.getLobbySpawn(), first.getLocation());
        Player second = join();
        second.teleport(handler.onPlayerJoinArena(arena, second));
        assertEquals(arena.getLobbySpawn(), second.getLocation());

        handler.onArenaCountdownEnd(arena);
        assertAboveFloor(first.getLocation());
        assertAboveFloor(second.getLocation());
        assertTrue(settings.spawns.isEmpty());
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
    void leavingDuringPreparationCancelsTheRoundAndRestoresTheFloor() {
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
        verify(api.tasks(), never()).repeating(eq("minigame:floorshuffle:test"), eq(2L), any(Runnable.class));
    }

    @Test
    void reusedRandomFloorTilesDoNotShiftSpawnPositions() {
        game();
        settings.spawns.clear();
        settings.floor = region(0, 63, 0, 5, 63, 0);
        when(arena.getMaxPlayers()).thenReturn(7);
        // Seven players on six tiles guarantees that a random tile is reused.
        for (int i = 0; i < 7; i++) join();

        start();

        for (Player player : players) assertAboveFloor(player.getLocation());
        for (int x = 0; x < 6; x++) {
            assertEquals(new Location(world, x, 63, 0), world.getBlockAt(x, 63, 0).getLocation());
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
    void validationAllowsNoSpawnsButRequiresFullCapacityWhenConfigured() {
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
    void fallingBelowFloorOnlyEliminatesAfterLeavingArena() {
        game();
        Player player = join();
        Runnable tick = start();
        player.teleport(new Location(world, 2, 60, 2));
        tick.run();
        verify(arena, never()).addSpectator(player);
        assertEquals(RUNNING, status);

        player.teleport(new Location(world, 2, 39, 2));
        tick.run();
        verify(arena).addSpectator(player);
        verify(arena).setStatus(ENDING, 20);
    }

    @Test
    void colourAnnouncementUsesConfiguredChatAndTwoSecondTitle() {
        game();
        game.textFormat = new FloorShuffleTextFormat(
                "<{colour-tag}>Go to {colour}</{colour-tag}>",
                "<{colour-tag}>{colour} NOW</{colour-tag}>", "Stay safe", "Target: {colour}");
        Player player = join();
        start();
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(arena).showTitle(title.capture(), eq("Stay safe"), eq(Duration.ZERO), eq(Duration.ofSeconds(2)), eq(Duration.ZERO));
        Material colour = List.of(Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE,
                        Material.LIME_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE).stream()
                .filter(m -> title.getValue().equals(FloorShuffleTextFormat.render(game.textFormat.title(), m)))
                .findFirst().orElseThrow();
        verify(api.messages()).info(player, FloorShuffleTextFormat.render(game.textFormat.chat(), colour));
        assertEquals(FloorShuffleTextFormat.render(game.textFormat.objective(), colour), handler.objective(arena));
    }

    @Test
    void unsafeTilesFlashTwiceBeforeDisappearingWhileSafeTilesStaySolid() {
        game();
        join();
        Runnable tick = start();
        Map<Block, Material> tiles = floorMaterials();
        Material safe = tiles.values().stream()
                .filter(m -> handler.objective(arena).contains(m.name().replace("_CONCRETE", "")))
                .findFirst().orElseThrow();
        for (int i = 0; i < 45; i++) tick.run(); // Tick 90, before the first flash.
        assertEquals(tiles, floorMaterials());
        tick.run(); // Tick 92: first flash.
        assertFlashingTiles(tiles, safe);
        for (int i = 0; i < 3; i++) tick.run(); // Tick 98: original colours.
        assertEquals(tiles, floorMaterials());
        for (int i = 0; i < 3; i++) tick.run(); // Tick 104: second flash.
        assertFlashingTiles(tiles, safe);
        for (int i = 0; i < 3; i++) tick.run(); // Tick 110: original colours.
        assertEquals(tiles, floorMaterials());
        for (int i = 0; i < 3; i++) tick.run(); // Tick 116: disappear.
        tiles.forEach((block, material) -> assertEquals(material == safe ? safe : Material.AIR, block.getType()));
        for (int i = 0; i < 25; i++) tick.run(); // Next challenge restores all tiles.
        assertTrue(floorMaterials().values().stream().allMatch(m -> m.isSolid() && m != Material.WHITE_CONCRETE));
    }

    private void assertFlashingTiles(Map<Block, Material> tiles, Material safe) {
        tiles.forEach((block, material) -> assertEquals(material == safe ? safe : Material.WHITE_CONCRETE, block.getType()));
    }

    private Map<Block, Material> floorMaterials() {
        Map<Block, Material> materials = new LinkedHashMap<>();
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) {
            Block block = world.getBlockAt(x, 63, z);
            materials.put(block, block.getType());
        }
        return materials;
    }

    @Test
    void lobbyShuffleUsesConfiguredIntervalAndCannotOverwriteRunningOrStoppedFloor() {
        game();
        settings.lobbyShuffleSeconds = 7;
        status = WAITING;
        handler.onArenaStatusChanged(arena, DISABLED, WAITING);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(api.tasks()).repeating(eq("minigame:floorshuffle:test:lobby"), eq(140L), callback.capture());
        assertTrue(floorMaterials().values().stream().noneMatch(m -> m == Material.STONE));
        world.getBlockAt(0, 63, 0).setType(Material.STONE);
        callback.getValue().run();
        assertNotEquals(Material.STONE, world.getBlockAt(0, 63, 0).getType());

        join();
        Runnable gameTick = start();
        Map<Block, Material> runningTiles = floorMaterials();
        callback.getValue().run(); // Stale waiting callback must not change the running floor.
        assertEquals(runningTiles, floorMaterials());
        for (int i = 0; i < 46; i++) gameTick.run(); // Stop during the first warning flash.
        handler.onArenaUnload(arena);
        callback.getValue().run();
        gameTick.run();
        assertTrue(floorMaterials().values().stream().allMatch(m -> m == Material.STONE));
        verify(api.tasks(), atLeastOnce()).cancel("minigame:floorshuffle:test:lobby");
    }

    @Test
    void countdownLobbyAnimationRestoresFloorOnDisable() {
        game();
        status = STARTING;
        handler.onArenaStatusChanged(arena, WAITING, STARTING);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(api.tasks()).repeating(eq("minigame:floorshuffle:test:lobby"), eq(60L), callback.capture());
        world.getBlockAt(0, 63, 0).setType(Material.STONE);
        callback.getValue().run();
        assertNotEquals(Material.STONE, world.getBlockAt(0, 63, 0).getType());
        status = DISABLED;
        handler.onArenaStatusChanged(arena, STARTING, DISABLED);
        callback.getValue().run();
        assertTrue(floorMaterials().values().stream().allMatch(m -> m == Material.STONE));
    }

    @Test
    void zeroLobbyIntervalDisablesAnimationAndInvalidIntervalsFailValidation() {
        game();
        settings.lobbyShuffleSeconds = 0;
        status = WAITING;
        handler.onArenaStatusChanged(arena, DISABLED, WAITING);
        verify(api.tasks(), never()).repeating(anyString(), anyLong(), any());
        assertTrue(floorMaterials().values().stream().allMatch(m -> m == Material.STONE));
        var result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertFalse(result.hasErrors());
        settings.lobbyShuffleSeconds = -1;
        result = ArenaValidationResult.success();
        handler.validate(arena, result);
        assertTrue(result.hasErrors());
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
        verify(section).set("lobby-shuffle-seconds", 3);
        verify(section).set(eq("floor"), any());
        verify(section, never()).set(eq("kill-target"), any());
        verify(section, never()).set(eq("laps"), any());
        verify(section, never()).set(eq("reload-ticks"), any());
        verify(section, never()).set(eq("target-count"), any());
        verify(section, never()).set(eq("targets"), any());
        verify(file).save();
    }
}
