package dev.stemcraft.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseCompactionTest {
    @TempDir Path directory;

    @Test void reclaimsFreedPagesAndTruncatesWalWithoutChangingRemainingData() throws Exception {
        Path file=directory.resolve("database.db");
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+file)) {
            try(var statement=connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("CREATE TABLE retained(id INTEGER PRIMARY KEY,payload TEXT)");
                statement.execute("INSERT INTO retained VALUES(42,'keep me')");
                statement.execute("CREATE TABLE discarded(payload BLOB)");
                statement.execute("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<2048) INSERT INTO discarded SELECT zeroblob(2048) FROM n");
                statement.execute("DROP TABLE discarded");
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
            long before=Files.size(file);
            var logger=Logger.getAnonymousLogger();
            assertFalse(DatabaseCompaction.compactIfNeeded(connection,file,Long.MAX_VALUE,20,logger));
            assertEquals(before,Files.size(file));
            assertTrue(DatabaseCompaction.compactIfNeeded(connection,file,1024,20,logger));
            assertTrue(Files.size(file)<before/2);
            Path wal=Path.of(file+"-wal");
            assertTrue(!Files.exists(wal)||Files.size(wal)==0);
            try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT id,payload FROM retained")) {
                assertTrue(result.next());assertEquals(42,result.getInt(1));assertEquals("keep me",result.getString(2));
                assertFalse(result.next());
            }
            assertFalse(DatabaseCompaction.compactIfNeeded(connection,file,1024,20,logger));
        }
    }
}
