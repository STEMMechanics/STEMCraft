package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.database.DatabaseResultSetHandler;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.api.service.hologram.HologramTypeHandler;
import dev.stemcraft.config.ConfigFileImpl;
import dev.stemcraft.service.command.CommandBuilderImpl;
import dev.stemcraft.service.tabcompletion.TabCompleteServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ArmorStandMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentMatchers;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HologramLifecycleTest {
    private static final String TYPE = "minigame_leaderboard";

    @TempDir
    Path tempDir;

    private WorldMock world;
    private ServerMock server;
    private STEMCraft plugin;
    private HologramServiceImpl service;
    private STEMCraftAPI api;
    private final Map<Class<?>, EventHandler<?>> eventHandlers = new HashMap<>();
    private final AtomicReference<List<String>> page = new AtomicReference<>(List.of("Arena A", "Player 15"));
    private final AtomicBoolean loadChunkDuringRender = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = spy(new WorldMock());
        world.setName("hologram-lifecycle");
        server.addWorld(world);
        world.getChunkAt(0, 0);
        doAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            boolean newlyLoaded = !world.isChunkLoaded(chunkX, chunkZ);
            var chunk = world.getChunkAt(chunkX, chunkZ);
            // Paper can notify chunk loads synchronously while a board is being spawned.
            if (loadChunkDuringRender.getAndSet(false) || newlyLoaded) {
                fire(new ChunkLoadEvent(chunk, false));
            }
            ArmorStandMock stand = spy(new ArmorStandMock(server, UUID.randomUUID()));
            // MockBukkit does not implement this visibility setter yet.
            doNothing().when(stand).setVisibleByDefault(anyBoolean());
            stand.setLocation(location);
            Consumer<ArmorStand> configure = invocation.getArgument(2);
            configure.accept(stand);
            server.registerEntity(stand);
            return stand;
        }).when(world).spawn(any(Location.class), eq(ArmorStand.class),
            ArgumentMatchers.<Consumer<? super ArmorStand>>any());

        ConfigFileImpl config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "config.yml", true));
        config.set("hologram.visibility.enabled", false);
        config.set("hologram.minigame_leaderboard.cycle_delay_seconds", 0);

        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        InstanceHolder.set(api);
        plugin = mock(STEMCraft.class);
        when(plugin.getName()).thenReturn("STEMCraft");
        when(plugin.isEnabled()).thenReturn(true);
        when(api.commands().create(anyString())).thenAnswer(invocation -> new CommandBuilderImpl(api, invocation.getArgument(0)));
        var completions = new TabCompleteServiceImpl(plugin, api);
        when(api.tabComplete()).thenReturn(completions);
        completions.onEnable();
        when(api.config().load("config.yml")).thenReturn(config);
        var events = api.events();
        doAnswer(invocation -> {
            eventHandlers.put(invocation.getArgument(0), invocation.getArgument(1));
            return mock(Listener.class);
        }).when(events).register(any(), any());
        when(api.placeholders().apply(anyString())).thenAnswer(invocation -> {
            String line = invocation.getArgument(0);
            if (line.equals("broken row")) {
                throw new IllegalStateException("Rendering failed");
            }
            return line;
        });

        service = new HologramServiceImpl(plugin, api);
        service.onEnable();

        HologramTypeHandler handler = mock(HologramTypeHandler.class);
        when(handler.lines(anyString(), any(), anyInt(), any())).thenAnswer(invocation -> page.get());
        service.registerType(TYPE, handler);
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.despawnAll();
        }
        InstanceHolder.set(null);
        MockBukkit.unmock();
    }

    @Test
    void chunkLoadDuringRotationDoesNotLeaveAStaleBoard() throws Exception {
        service.create(TYPE, null, new Location(world, 1, 70, 1), List.of());
        assertEquals(2, world.getEntitiesByClass(ArmorStand.class).size());

        page.set(List.of("Arena B", "Player 5"));
        loadChunkDuringRender.set(true);
        advanceCycle();
        assertEquals(2, world.getEntitiesByClass(ArmorStand.class).size());

        page.set(List.of("Arena C", "Player 20"));
        advanceCycle();
        assertEquals(List.of("Arena C", "Player 20"), rowTexts());

        List<String> fullPage = IntStream.rangeClosed(0, 10)
            .mapToObj(rank -> rank == 0 ? "Full leaderboard" : rank + ". Player_" + rank + " " + (100 - rank))
            .toList();
        List<List<String>> differentSizedPages = List.of(
            fullPage,
            List.of("Short leaderboard", "1. Player_A 15", "2. Player_B 5"),
            List.of("Empty leaderboard", "No results"),
            List.of(),
            List.of("Title only")
        );
        for (int cycle = 0; cycle < 40; cycle++) {
            page.set(differentSizedPages.get(cycle % differentSizedPages.size()));
            loadChunkDuringRender.set(true);
            advanceCycle();
            assertEquals(page.get(), rowTexts());
        }
    }

    @Test
    void rotationDoesNotLoadAnUnattendedChunkAndReturningRendersOnlyOnce() throws Exception {
        service.create(TYPE, null, new Location(world, 1, 70, 1), List.of());
        fire(new ChunkUnloadEvent(world.getChunkAt(0, 0), true));
        assertTrue(world.unloadChunk(0, 0, true));

        page.set(List.of("Arena B", "Player 5"));
        for (int cycle = 0; cycle < 3; cycle++) {
            advanceCycle();
        }
        assertFalse(world.isChunkLoaded(0, 0));
        assertTrue(rowTexts().isEmpty());

        fire(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        fire(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        assertEquals(page.get(), rowTexts());
        page.set(List.of("Arena C", "Player 20"));
        advanceCycle();
        assertEquals(page.get(), rowTexts());
    }

    @Test
    void failedRenderRemovesPartialRowsAndTheNextCycleCanRecover() throws Exception {
        service.create(TYPE, null, new Location(world, 1, 70, 1), List.of());
        page.set(List.of("Arena B", "broken row"));

        assertThrows(IllegalStateException.class, () -> service.update(TYPE, null));
        assertTrue(rowTexts().isEmpty());

        page.set(List.of("Arena C", "Player 20"));
        advanceCycle();
        assertEquals(page.get(), rowTexts());
    }

    @Test
    void reloadLeavesUnlinkedRowsAndOtherDisplaysWhileRebuildingSavedBoards() throws Exception {
        int id = service.create(TYPE, null, new Location(world, 1, 70, 1), List.of());
        ArmorStand stale = world.spawn(new Location(world, 1, 69, 1), ArmorStand.class, stand -> {
            stand.addScoreboardTag("stemcraft:hologram");
            stand.customName(Component.text("Stale page"));
        });
        ArmorStand other = world.spawn(new Location(world, 2, 70, 2), ArmorStand.class,
            stand -> stand.customName(Component.text("Other plugin")));
        ArmorStand dynamic = world.spawn(new Location(world, 3, 70, 3), ArmorStand.class, stand -> {
            stand.addScoreboardTag("stemcraft:hologram");
            stand.addScoreboardTag("stemcraft:dynamic-hologram");
            stand.customName(Component.text("Per-player display"));
        });

        service.onReload();

        assertTrue(stale.isValid());
        assertTrue(other.isValid());
        assertTrue(dynamic.isValid());
        assertEquals(List.of("Arena A", "Player 15", "Stale page"), rowTexts());
        page.set(List.of("Arena B", "Player 5"));
        advanceCycle();
        assertEquals(List.of("Arena B", "Player 5", "Stale page"), rowTexts());

        service.delete(id);
        advanceCycle();
        assertEquals(List.of("Stale page"), rowTexts());
        assertTrue(other.isValid());
        assertTrue(dynamic.isValid());
    }

    @Test
    void deleteLeavesUnlinkedRowsAloneWhenTrackingHasBeenLost() throws Exception {
        int id = service.create(TYPE, null, new Location(world, 1, 70, 1), List.of());
        var field = HologramServiceImpl.class.getDeclaredField("entitiesById");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(service)).remove(id);

        service.delete(id);

        assertEquals(page.get(), rowTexts());
    }

    @Test
    void enablingLeavesUnlinkedRowsAndRestoresSavedBoardsInAlreadyLoadedChunks() throws Exception {
        service.onDisable();
        ArmorStand stale = world.spawn(new Location(world, 1, 69, 1), ArmorStand.class, stand -> {
            stand.addScoreboardTag("stemcraft:hologram");
            stand.customName(Component.text("Unlinked row"));
        });
        ResultSet row = mock(ResultSet.class);
        when(row.getInt("id")).thenReturn(42);
        when(row.getString("type")).thenReturn("");
        when(row.getDouble("x")).thenReturn(1D);
        when(row.getDouble("y")).thenReturn(70D);
        when(row.getDouble("z")).thenReturn(1D);
        when(row.getString("data_yaml")).thenReturn("lines:\n  - Saved title\n  - Saved rank\n");
        var database = api.database();
        doAnswer(invocation -> {
            DatabaseResultSetHandler handler = invocation.getArgument(2);
            handler.accept(row);
            return null;
        }).when(database).queryEach(eq("SELECT id, type, context, x, y, z, data_yaml FROM holograms WHERE world = ?"),
            any(), any());

        service = new HologramServiceImpl(plugin, api);
        service.onEnable();

        assertTrue(stale.isValid());
        assertEquals(List.of("Saved title", "Saved rank", "Unlinked row"), rowTexts());
        fire(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        assertEquals(List.of("Saved title", "Saved rank", "Unlinked row"), rowTexts());
    }

    @Test
    void rangeDeletionIncludesTheBoundaryAndKeepsOtherWorldsAndUnlinkedDisplays() {
        PlayerMock player = adminPlayer();
        Location near = new Location(world, 1, 70, 0);
        Location boundary = new Location(world, 4, 70, 0);
        Location outside = new Location(world, 4.25, 70, 0);
        var otherWorld = server.addSimpleWorld("other-hologram-world");
        assertFalse(otherWorld.isChunkLoaded(0, 0));
        Location otherLocation = new Location(otherWorld, 1, 70, 0);
        service.create(TYPE, null, near, List.of());
        service.create(TYPE, null, boundary, List.of());
        int outsideId = service.create(TYPE, null, outside, List.of());
        int otherId = service.create(TYPE, null, otherLocation, List.of());
        ArmorStand unlinked = world.spawn(new Location(world, 0, 70, 0), ArmorStand.class,
            stand -> stand.addScoreboardTag("stemcraft:hologram"));
        ArmorStand foreign = world.spawn(new Location(world, 0, 70, 1), ArmorStand.class,
            stand -> stand.customName(Component.text("Citizens/other plugin")));

        assertTrue(server.dispatchCommand(player, "hologram delete -range 4"));

        assertEquals(-1, service.closest(near, 0));
        assertEquals(-1, service.closest(boundary, 0));
        assertEquals(outsideId, service.closest(outside, 0));
        assertEquals(otherId, service.closest(otherLocation, 0));
        assertTrue(unlinked.isValid());
        assertTrue(foreign.isValid());
        verify(api.messages()).success(player, "HOLOGRAM_DELETE_RANGE_SUCCESS", null, "count", 2, "range", "4");
    }

    @Test
    void limitSelectsClosestBoardsRegardlessOfCreationOrderAndFlagOrder() {
        PlayerMock player = adminPlayer();
        Location far = new Location(world, 3, 70, 0);
        Location nearest = new Location(world, 1, 70, 0);
        Location second = new Location(world, 2, 70, 0);
        int farId = service.create(TYPE, null, far, List.of());
        service.create(TYPE, null, nearest, List.of());
        service.create(TYPE, null, second, List.of());

        assertTrue(server.dispatchCommand(player, "hologram delete -limit 2 -range 4"));

        assertEquals(-1, service.closest(nearest, 0));
        assertEquals(-1, service.closest(second, 0));
        assertEquals(farId, service.closest(far, 0));
    }

    @Test
    void equalDistancesUseBoardIdsAsATieBreakAndDecimalRangesWork() {
        PlayerMock player = adminPlayer();
        Location first = new Location(world, 1.25, 70, 0);
        Location second = new Location(world, 0, 70, 1.25);
        service.create(TYPE, null, first, List.of());
        int secondId = service.create(TYPE, null, second, List.of());

        assertTrue(server.dispatchCommand(player, "hologram delete -range 1.25 -limit 1"));

        assertEquals(-1, service.closest(first, 0));
        assertEquals(secondId, service.closest(second, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "delete", "delete -range", "delete -range 4 -limit", "delete -limit 1",
        "delete -range 0", "delete -range -4", "delete -range NaN", "delete -range Infinity",
        "delete -range abc", "delete -range 4 -limit 0", "delete -range 4 -limit -1",
        "delete -range 4 -limit 1.5", "delete -range 4 -limit 2147483648",
        "delete -range 4 -limit abc", "delete -range 4 -unknown 1",
        "delete -range 4 -range 8", "delete -range 4 -limit 1 -limit 2", "delete 1 -range 4"
    })
    void invalidDeletionArgumentsDoNotDeleteAnyBoards(String args) {
        PlayerMock player = adminPlayer();
        Location location = new Location(world, 1, 70, 0);
        int id = service.create(TYPE, null, location, List.of());

        assertTrue(server.dispatchCommand(player, "hologram " + args));

        assertEquals(id, service.closest(location, 0));
    }

    @Test
    void rangeDeletionRequiresAPlayerAndExistingHologramPermission() {
        PlayerMock player = adminPlayer();
        Location location = new Location(world, 1, 70, 0);
        int id = service.create(TYPE, null, location, List.of());

        assertTrue(server.dispatchCommand(server.getConsoleSender(), "hologram delete -range 4"));
        player.setOp(false);
        assertTrue(server.dispatchCommand(player, "hologram delete -range 4"));

        assertEquals(id, service.closest(location, 0));
    }

    @Test
    void deletingByIdStillWorksFromConsole() {
        Location location = new Location(world, 1, 70, 0);
        int id = service.create(TYPE, null, location, List.of());

        assertTrue(server.dispatchCommand(server.getConsoleSender(), "hologram delete " + id));

        assertEquals(-1, service.closest(location, 0));
        assertTrue(rowTexts().isEmpty());
    }

    @Test
    void noNearbyBoardsReportsTheRangeWithoutDeletingAnything() {
        PlayerMock player = adminPlayer();
        Location location = new Location(world, 5, 70, 0);
        int id = service.create(TYPE, null, location, List.of());

        assertTrue(server.dispatchCommand(player, "hologram delete -range 4"));

        assertEquals(id, service.closest(location, 0));
        verify(api.messages()).error(player, "HOLOGRAM_CLOSEST_NONE", null, "range", "4");
    }

    @Test
    void deletionCompletesBoardIdsAndOnlyUnusedRangeFlags() {
        PlayerMock player = adminPlayer();
        int id = service.create(TYPE, null, new Location(world, 1, 70, 0), List.of());
        var command = server.getCommandMap().getCommand("hologram");

        var root = command.tabComplete(player, "hologram", new String[]{"delete", ""});
        assertTrue(root.contains(String.valueOf(id)));
        assertTrue(command.tabComplete(player, "hologram", new String[]{"delete", "-"})
            .containsAll(List.of("-range", "-limit")));
        assertEquals(List.of("-limit"), command.tabComplete(player, "hologram", new String[]{"delete", "-range", "4", "-"}));
        assertEquals(List.of("-range"), command.tabComplete(player, "hologram", new String[]{"delete", "-limit", "1", "-"}));
        assertTrue(command.tabComplete(player, "hologram", new String[]{"delete", "-range", "4", "-limit", "1", "-"}).isEmpty());
    }

    private PlayerMock adminPlayer() {
        PlayerMock player = server.addPlayer("HologramAdmin");
        player.setOp(true);
        player.teleport(new Location(world, 0, 70, 0));
        return player;
    }

    private void advanceCycle() throws Exception {
        var method = HologramServiceImpl.class.getDeclaredMethod("advanceMinigameLeaderboard");
        method.setAccessible(true);
        method.invoke(service);
    }

    private List<String> rowTexts() {
        return world.getEntitiesByClass(ArmorStand.class).stream()
            .filter(stand -> stand.getScoreboardTags().contains("stemcraft:hologram")
                && !stand.getScoreboardTags().contains("stemcraft:dynamic-hologram"))
            .sorted(Comparator.comparingDouble((ArmorStand stand) -> stand.getLocation().getY()).reversed())
            .map(stand -> PlainTextComponentSerializer.plainText().serialize(stand.customName()))
            .toList();
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> void fire(T event) {
        ((EventHandler<T>) eventHandlers.get(event.getClass())).handle(event);
    }
}
