package dev.stemcraft.feature.underhalls;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.service.DatabaseServiceImpl;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import static dev.stemcraft.feature.underhalls.UnderhallsStore.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnderhallsStoreTest {
    Connection connection;
    DatabaseServiceImpl database;
    UnderhallsStore store;
    @BeforeEach void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        database = new DatabaseServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS));
        var field = DatabaseServiceImpl.class.getDeclaredField("connection");
        field.setAccessible(true); field.set(database, connection);
        store = new UnderhallsStore(database); store.load();
    }
    @AfterEach void cleanup() throws Exception { connection.close(); }
    @Test void provenanceTombstonesAndPinnedExitsSurviveReload() {
        Pos origin = new Pos(UUID.randomUUID(), -100,65,50);
        Pos placed = origin.offset(0,1,1);
        Pos room = new Pos(UUID.randomUUID(), -60,65,69);
        Entrance entrance = new Entrance(origin, room.world(), -1,0,false);
        store.placed(placed); store.save(entrance);
        assertEquals(origin,store.pinExit(room,origin));
        store.save(entrance.retire());
        store = new UnderhallsStore(database); store.load();
        assertTrue(store.isPlaced(placed));
        assertTrue(store.entrance(origin).retired());
        assertThrows(IllegalStateException.class, () -> store.save(entrance));
        assertEquals(origin,store.pinExit(room,origin.offset(100,0,0)));
        store.removed(placed);
        store.load();
        assertFalse(store.isPlaced(placed));
        assertEquals(origin,store.exit(room));
        assertFalse(store.isPlaced(new Pos(UUID.randomUUID(),placed.x(),placed.y(),placed.z())));
    }
    @Test void failedWritesDoNotChangeCachedOwnershipOrEntrances() {
        Pos pos = new Pos(UUID.randomUUID(),0,65,0);
        database.execute("CREATE TRIGGER fail_write BEFORE INSERT ON underhalls_state BEGIN SELECT RAISE(ABORT,'failure'); END");
        assertThrows(IllegalStateException.class, () -> store.placed(pos));
        assertFalse(store.isPlaced(pos));
        assertThrows(IllegalStateException.class, () -> store.save(new Entrance(pos,UUID.randomUUID(),0,0,false)));
        assertNull(store.entrance(pos));
    }
}
