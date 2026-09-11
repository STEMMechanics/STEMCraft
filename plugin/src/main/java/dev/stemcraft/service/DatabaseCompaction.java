package dev.stemcraft.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/** Startup-only maintenance, before other services can use the shared connection. */
final class DatabaseCompaction {
    private DatabaseCompaction() {}

    static boolean compactIfNeeded(Connection connection,Path file,long minimumFreeBytes,
                                   int minimumFreePercent,Logger logger) throws SQLException,IOException {
        if (!connection.getAutoCommit()) return false;
        long pages=pragma(connection,"page_count");
        long free=pragma(connection,"freelist_count");
        long pageSize=pragma(connection,"page_size");
        long before=pages*pageSize;
        if (pages==0||free*pageSize<minimumFreeBytes||100.0*free/pages<minimumFreePercent)
            return false;

        // SQLite may need up to twice the original database size in temporary space.
        if (Files.getFileStore(file).getUsableSpace()<2*before) {
            logger.warning("SQLite compaction deferred: insufficient free disk space (need up to "
                +2*before+" bytes). It will be checked again on the next startup.");
            return false;
        }
        if (!checkpoint(connection)) {
            logger.warning("SQLite compaction deferred: another connection is holding the WAL open.");
            return false;
        }
        logger.info("Compacting SQLite during startup: "+free*pageSize+" reclaimable bytes out of "+before+". This may take a while.");
        try (var statement=connection.createStatement()) { statement.execute("VACUUM"); }
        if (!checkpoint(connection))
            logger.warning("SQLite compacted, but WAL truncation is waiting for another connection to close.");
        long after=pragma(connection,"page_count")*pageSize;
        logger.info("SQLite compaction complete: "+before+" -> "+after+" bytes (reclaimed "+(before-after)+").");
        return true;
    }

    private static boolean checkpoint(Connection connection) throws SQLException {
        try (var statement=connection.createStatement(); var result=statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")) {
            return result.next()&&result.getInt(1)==0;
        }
    }

    private static long pragma(Connection connection,String name) throws SQLException {
        try (var statement=connection.createStatement(); var result=statement.executeQuery("PRAGMA "+name)) {
            if (!result.next()) throw new SQLException("No result for PRAGMA "+name);
            return result.getLong(1);
        }
    }
}
