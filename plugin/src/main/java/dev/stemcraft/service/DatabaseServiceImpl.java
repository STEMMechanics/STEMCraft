/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.service;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.database.DatabaseResultSetHandler;
import dev.stemcraft.api.database.DatabaseResultSetMapper;
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
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api    The STEMCraft API instance.
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
            api.messages().error("SQLite JDBC driver not found. Add org.xerial:sqlite-jdbc to your plugin.", e);
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
            api.messages().error("Failed to open SQLite database connection.", e);
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
     *
     * @param sql    The SQL statement to execute.
     * @param binder The binder to set parameters on the prepared statement.
     * @return The number of affected rows.
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
                api.messages().error("Failed to execute update: " + sql, e);
            }
        } else {
            api.messages().error("Cannot execute update; database connection is null.");
        }

        return 0;
    }

    /**
     * Executes a query statement and maps the result set.
     *
     * @param sql     The SQL statement to execute.
     * @param binder  The binder to set parameters on the prepared statement.
     * @param handler The handler to process the result set.
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
            api.messages().error("Failed to execute query: " + sql, e);
        }
    }

    /**
     * Executes a query statement and maps the result set.
     *
     * @param sql     The SQL statement to execute.
     * @param binder  The binder to set parameters on the prepared statement.
     * @param handler The handler to process each row of the result set.
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
            api.messages().error("Failed to execute query: " + sql, e);
        }
    }

    /**
     * Executes a query statement and maps a single result from the result set.
     *
     * @param sql     The SQL statement to execute.
     * @param binder  The binder to set parameters on the prepared statement.
     * @param handler The handler to process the single row of the result set.
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
            api.messages().error("Failed to execute query: " + sql, e);
        }
    }

    /**
     * Executes a query statement and maps a single result from the result set.
     *
     * @param sql    The SQL statement to execute.
     * @param binder The binder to set parameters on the prepared statement.
     * @param mapper The mapper to convert the result set row to an object of type T.
     * @param <T>    The type of the object to map to.
     * @return The mapped object of type T, or null if no result was found.
     */
    public <T> T querySingleMapped(
            String sql,
            DatabaseStatementBinder binder,
            DatabaseResultSetMapper<T> mapper
    ) {
        if (connection == null) {
            throw new IllegalStateException("Database connection is not initialized.");
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            if (binder != null) {
                binder.bind(ps);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper != null ? mapper.map(rs) : null;
                }
                return null;
            }

        } catch (SQLException e) {
            api.messages().error("Failed to execute query: " + sql, e);
            return null;
        }
    }

    /**
     * Executes a raw SQL statement.
     *
     * @param sql The SQL statement to execute.
     */
    public boolean execute(String sql) {
        try (Statement st = connection.createStatement()) {
            return st.execute(sql);
        } catch (SQLException e) {
            api.messages().error("Failed to execute SQL: " + sql, e);
        }

        return false;
    }

    /**
     * Gets the migration version for the given migration name.
     *
     * @param name The name of the migration.
     * @return The migration version, or 0 if not found.
     */
    public int migrationVersion(String name) {
        return querySingleMapped(
                "SELECT version FROM migrations WHERE name = ?;",
                ps -> ps.setString(1, name),
                rs -> rs.getInt(1)
        );
    }

    /**
     * Sets the migration version for the given migration name.
     *
     * @param name    The name of the migration.
     * @param version The migration version to set.
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
     *
     * @param name The name of the migration.
     */
    public void clearMigration(String name) {
        update("DELETE FROM migrations WHERE name = ?;", ps -> ps.setString(1, name));
    }
}