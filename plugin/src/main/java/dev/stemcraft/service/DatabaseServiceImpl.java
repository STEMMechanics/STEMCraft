package dev.stemcraft.service;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.database.DatabaseResultSetHandler;
import dev.stemcraft.api.database.DatabaseStatementBinder;
import dev.stemcraft.api.service.database.DatabaseService;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * SQLite manager with integer schema version + ordered migrations.
 *
 * Usage:
 *   SQLiteManager db = new SQLiteManager(plugin, "data.db")
 *       .migration(1, """
 *           CREATE TABLE IF NOT EXISTS players (
 *             uuid TEXT PRIMARY KEY,
 *             name TEXT NOT NULL,
 *             created_at INTEGER NOT NULL
 *           );
 *       """)
 *       .migration(2, """
 *           ALTER TABLE players ADD COLUMN last_seen INTEGER NOT NULL DEFAULT 0;
 *       """);
 *
 *   db.init(); // opens + applies migrations
 *   try (Connection c = db.connection()) { ... }
 *   db.close();
 */
public class DatabaseServiceImpl extends BaseService implements DatabaseService, AutoCloseable {
    private static final String DATABASE_FILENAME = "database.db";
    private static final String META_TABLE = "schema_meta";
    private static final String META_KEY_VERSION = "schema_version";

    private Connection connection;
    private final TreeMap<Integer, List<String>> migrations = new TreeMap<>();

    /**
     * Creates a new SQLiteManager instance.
     */
    public DatabaseServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Called when the service is enabled.
     */
    public void onEnable() {
        if (connection != null) return;

        // Ensure driver is loaded (Paper usually has it, but this keeps it explicit)
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.error("SQLite JDBC driver not found. Add org.xerial:sqlite-jdbc to your plugin.", e);
            return;
        }

        File database = new File(plugin.getDataFolder(), DATABASE_FILENAME);
        String url = "jdbc:sqlite:" + database.getAbsolutePath();

        try {
            connection = DriverManager.getConnection(url);

            execute("PRAGMA foreign_keys=ON;");
            execute("PRAGMA journal_mode=WAL;");
            execute("PRAGMA synchronous=NORMAL;");
            execute("PRAGMA busy_timeout=5000;");

            execute("CREATE TABLE IF NOT EXISTS migrations ( name TEXT PRIMARY KEY, version INTEGER NOT NULL );");

            if(migrationVersion("stemcraft") == 0) {
                // create players login table
                execute("""
                    CREATE TABLE IF NOT EXISTS player_logins (
                      uuid TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      first_login INTEGER NOT NULL,
                      last_login INTEGER NOT NULL,
                      login_count INTEGER NOT NULL
                    );
                """);

                // insert a row with random data
                UUID uuid = UUID.randomUUID();
                String name = "test-name";
                long now = System.currentTimeMillis();

                String sql = "INSERT INTO player_logins (uuid, name, first_login, last_login, login_count) " +
                        "VALUES (?, ?, ?, ?, ?);";
                update(sql, ps -> {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setLong(3, now);
                    ps.setLong(4, now);
                    ps.setInt(5, 1);
                });

                setMigrationVersion("stemcraft", 1);
            }
        } catch (SQLException e) {
            plugin.error("Failed to open SQLite database connection.", e);
            connection = null;
        }
    }

    /**
     * Called when the service is disabled.
     */
    @Override
    public void onDisable() {
        close();
    }

    /**
     * Returns the active database connection.
     */
    @Override
    public void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    /**
     * Executes an update statement (INSERT, UPDATE, DELETE).
     */
    @Override
    public int update(String sql, DatabaseStatementBinder binder) {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (binder != null) {
                    binder.bind(ps);
                }

                return ps.executeUpdate();
            } catch (SQLException e) {
                plugin.error("Failed to execute update: " + sql, e);
            }
        } else {
            plugin.error("Cannot execute update; database connection is null.");
        }

        return 0;
    }

    /**
     * Executes a query statement and maps the result set.
     */
    public void query(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler) {
        if (connection == null) {
            throw new IllegalStateException("Database connection is not initialized.");
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            if (binder != null) {
                binder.bind(ps);
            }

            try (ResultSet rs = ps.executeQuery()) {
                handler.accept(rs);
            }

        } catch (SQLException e) {
            plugin.error("Failed to execute query: " + sql, e);
        }
    }

    /**
     * Executes a query statement and maps the result set.
     */
    public void queryEach(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler) {
        if (connection == null) {
            throw new IllegalStateException("Database connection is not initialized.");
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            if (binder != null) {
                binder.bind(ps);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    handler.accept(rs);
                }
            }

        } catch (SQLException e) {
            plugin.error("Failed to execute query: " + sql, e);
        }
    }

    /**
     * Executes a query statement and maps a single result from the result set.
     */
    public void querySingle(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler) {
        if (connection == null) {
            throw new IllegalStateException("Database connection is not initialized.");
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            if (binder != null) {
                binder.bind(ps);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if( rs.next() ) {
                    handler.accept(rs);
                }
            }

        } catch (SQLException e) {
            plugin.error("Failed to execute query: " + sql, e);
        }
    }

    /**
     * Executes a raw SQL statement.
     */
    public boolean execute(String sql) {
        try (Statement st = connection.createStatement()) {
            return st.execute(sql);
        } catch (SQLException e) {
            plugin.error("Failed to execute SQL: " + sql, e);
        }

        return false;
    }

    /**
     * Gets the migration version for the given migration name.
     */
    public int migrationVersion(String name) {
        return querySingle(
                "SELECT version FROM migrations WHERE name = ?;",
                ps -> ps.setString(1, name),
                rs -> rs.getInt(1),
                0
        );
    }

    /**
     * Sets the migration version for the given migration name.
     */
    public void setMigrationVersion(String name, int version) {
        String sql = "INSERT INTO migrations (name, version) VALUES (?, ?) " +
                        "ON CONFLICT(name) DO UPDATE SET version = excluded.version;";

        update(sql, ps -> {
            ps.setString(1, name);
            ps.setInt(2, version);
        });
    }

    /**
     * Clears the migration record for the given migration name.
     */
    public void clearMigration(String name) {
        update("DELETE FROM migrations WHERE name = ?;", ps -> {
            ps.setString(1, name);
        });
    }
}