package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.service.DatabaseServiceImpl;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeleportWorldGroupTest {
    ServerMock server;
    STEMCraftAPI api;
    TeleportUtils feature;
    Connection connection;
    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        var db = new DatabaseServiceImpl(mock(STEMCraft.class), api);
        var field = DatabaseServiceImpl.class.getDeclaredField("connection");
        field.setAccessible(true); field.set(db, connection);
        when(api.database()).thenReturn(db);
        feature = new TeleportUtils(api);
        invoke("ensureBackLocationStorage", new Class<?>[]{});
    }
    @AfterEach void cleanup() throws Exception {
        connection.close();
        MockBukkit.unmock();
    }
    Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        var method = TeleportUtils.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(feature, args);
    }
    void save(UUID player, Location location) throws Exception {
        invoke("setWorldLastLocation", new Class<?>[]{UUID.class, Location.class}, player, location);
    }
    Location resolve(UUID player) throws Exception {
        return (Location) invoke("getLastLocationInWorldSet", new Class<?>[]{UUID.class, String.class}, player, "survival");
    }
    @Test void allDimensionsParticipateAndHubDoesNotReplaceReturnLocationAfterReload() throws Exception {
        UUID player = UUID.randomUUID();
        var hub = server.addSimpleWorld("hub");
        for (String suffix : new String[]{"", "_nether", "_the_end", "_deep", "_faraway", "_underhalls"}) {
            var world = server.addSimpleWorld("survival" + suffix);
            var last = new Location(world, 14.5, 65, -20.5, 35, 12);
            save(player, last);
            save(player, hub.getSpawnLocation());
            assertEquals(last, resolve(player));
        }
        var expected = resolve(player);
        feature = new TeleportUtils(api);
        invoke("loadWorldLastLocationsFromStorage", new Class<?>[]{});
        assertEquals(expected, resolve(player));
    }
    @Test void sameMillisecondArrivalWinsAndNamesHaveAnUnderscoreBoundary() throws Exception {
        UUID player = UUID.randomUUID();
        var overworld = server.addSimpleWorld("survival");
        var nether = server.addSimpleWorld("survival_nether");
        for (int i = 0; i < 20; i++) {
            save(player, overworld.getSpawnLocation());
            save(player, nether.getSpawnLocation());
            assertEquals(nether, resolve(player).getWorld());
        }
        assertTrue(TeleportUtils.inWorldSet("survival_underhalls_v2", "survival"));
        assertFalse(TeleportUtils.inWorldSet("survival2_nether", "survival"));
        assertFalse(TeleportUtils.inWorldSet("bridge_survival", "survival"));
    }
    @Test void groupCommandDoesNotTeleportPlayersAlreadyInACustomDimension() throws Exception {
        var world = server.addSimpleWorld("survival_deep");
        var player = server.addPlayer();
        player.teleport(new Location(world, 30, 65, 40));
        var builder = mock(dev.stemcraft.api.command.CommandBuilder.class, RETURNS_SELF);
        when(api.commands().create("tpworldlast")).thenReturn(builder);
        try (var pluginAccess = mockStatic(STEMCraft.class)) {
            pluginAccess.when(STEMCraft::getPlugin).thenReturn(mock(STEMCraft.class));
            feature.onEnable();
            var executor = org.mockito.ArgumentCaptor.forClass(dev.stemcraft.api.command.CommandExecutor.class);
            verify(builder).executor(executor.capture());
            var ctx = mock(dev.stemcraft.api.command.CommandContext.class);
            when(ctx.getArg(0)).thenReturn("survival");
            when(ctx.getPlayer(eq(1), any())).thenReturn(player);
            executor.getValue().execute(api, null, ctx);
            verify(ctx).returnInfo("You are already in this world group.");
            assertEquals(new Location(world, 30, 65, 40), player.getLocation());
        }
    }

    @Test void staleSavedWorldCannotCreateAnOrdinaryOverworld() throws Exception {
        UUID player = UUID.randomUUID();
        var world = server.addSimpleWorld("survival_removed");
        save(player, world.getSpawnLocation());
        server.unloadWorld(world, false);
        assertNull(resolve(player));
        verify(api.worlds(), never()).loadWorld("survival_removed");
    }
}
