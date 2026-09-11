package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.service.DatabaseServiceImpl;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamedRegionStorageTest {
    Connection connection;
    DatabaseServiceImpl database;
    MessageService messages;
    NamedRegionStorage storage;

    @BeforeEach void setup() throws Exception {
        connection=DriverManager.getConnection("jdbc:sqlite::memory:");
        var api=mock(STEMCraftAPI.class);
        messages=mock(MessageService.class);
        when(api.messages()).thenReturn(messages);
        database=new DatabaseServiceImpl(mock(STEMCraft.class),api);
        var field=DatabaseServiceImpl.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(database,connection);
        database.execute("CREATE TABLE migrations(name TEXT PRIMARY KEY,version INTEGER NOT NULL)");
        database.setMigrationVersion("named-regions",5);
        database.execute("CREATE TABLE named_areas(id TEXT PRIMARY KEY,kind TEXT NOT NULL)");
        database.execute("CREATE TABLE named_region_cells(world_name TEXT NOT NULL,cell_x INTEGER NOT NULL,cell_z INTEGER NOT NULL,region_id TEXT NOT NULL,PRIMARY KEY(world_name,cell_x,cell_z))");
        database.execute("CREATE INDEX idx_named_region_cells_region_id ON named_region_cells(region_id)");
        storage=new NamedRegionStorage(database,Logger.getAnonymousLogger());
    }

    @AfterEach void cleanup() throws Exception { connection.close(); }

    @Test void migrationPreservesEverySampleIncludingNegativeCoordinatesAndWorlds() {
        Map<NamedRegionStorage.Cell,String> expected=new HashMap<>();
        for(String world:new String[]{"survival","nether"}) {
            for(int x=-8;x<8;x++) for(int z=-8;z<8;z++) {
                var cell=new NamedRegionStorage.Cell(world,x,z);
                String id=(x+z)%2==0?"region-a":"region-b";
                expected.put(cell,id);
                database.update("INSERT INTO named_region_cells VALUES(?,?,?,?)",ps->{
                    ps.setString(1,world);ps.setInt(2,cell.cellX());ps.setInt(3,cell.cellZ());ps.setString(4,id);
                });
            }
        }
        storage.initialise();
        assertEquals(6,database.migrationVersion("named-regions"));
        assertEquals(32,scalar("SELECT COUNT(*) FROM named_region_chunks"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='named_region_cells'"));
        Map<NamedRegionStorage.Cell,String> actual=new HashMap<>();
        storage.forEachCell(actual::put);
        assertEquals(expected,actual);
        expected.forEach((cell,id)->assertEquals(id,storage.cell(cell.world(),cell.cellX(),cell.cellZ())));
        assertTrue(storage.completeChunk("survival",-1,-1));
        storage.initialise(); // Reload must not need/recreate the dropped legacy table.
        actual.clear();storage.forEachCell(actual::put);assertEquals(expected,actual);
        verifyNoInteractions(messages);
    }

    @Test void migrationRollsBackIfVersionCannotBeSaved() {
        database.execute("INSERT INTO named_region_cells VALUES('survival',-1,-1,'region-a')");
        database.execute("CREATE TRIGGER reject_version BEFORE UPDATE ON migrations BEGIN SELECT RAISE(ABORT,'test failure'); END");
        assertThrows(IllegalStateException.class,storage::initialise);
        assertEquals(5,database.migrationVersion("named-regions"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM named_region_cells"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM named_region_chunks"));
    }

    @Test void longMergeChainsSurviveReloadAndPartialChunksStayPartial() {
        storage.initialise();
        storage.setCell("survival",-1,-1,"region-0",true);
        for(int i=0;i<160;i++) storage.alias("region-"+i,"region-"+(i+1));
        assertTrue(database.execute("VACUUM"));
        storage.initialise();
        assertEquals("region-160",storage.cell("survival",-1,-1));
        assertFalse(storage.completeChunk("survival",-1,-1));
        assertNull(storage.cell("survival",-2,-1));
        verifyNoInteractions(messages);
    }

    @Test void failedAliasWriteDoesNotRedirectCachedCells() {
        storage.initialise();
        storage.setCell("survival",0,0,"a",true);
        database.execute("CREATE TRIGGER reject_alias BEFORE INSERT ON named_region_merges BEGIN SELECT RAISE(ABORT,'test failure'); END");
        assertThrows(IllegalStateException.class,()->storage.alias("a","b"));
        assertEquals("a",storage.cell("survival",0,0));
    }

    @Test void unchangedChunksAreNotRewrittenAndFailedFlushesCanBeRetried() {
        storage.initialise();
        storage.setCell("survival",0,0,"a",true);
        database.execute("CREATE TRIGGER reject_chunk BEFORE UPDATE ON named_region_chunks BEGIN SELECT RAISE(ABORT,'test failure'); END");
        storage.flushChunk("survival",0,0);
        verifyNoInteractions(messages);
        storage.setCell("survival",1,0,"b",false);
        assertThrows(IllegalStateException.class,()->storage.flushChunk("survival",0,0));
        database.execute("DROP TRIGGER reject_chunk");
        storage.flushChunk("survival",0,0);
        storage.initialise();
        assertEquals("a",storage.cell("survival",0,0));
        assertEquals("b",storage.cell("survival",1,0));
    }

    private int scalar(String sql) {
        return database.querySingleMapped(sql,null,rs->rs.getInt(1),-1);
    }
}
