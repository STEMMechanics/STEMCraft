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

package dev.stemcraft.api.service.database;

import dev.stemcraft.api.database.DatabaseResultSetHandler;
import dev.stemcraft.api.database.DatabaseResultSetMapper;
import dev.stemcraft.api.database.DatabaseStatementBinder;

/**
 * Service for managing database interactions.
 */
public interface DatabaseService {

    /**
     * Closes the database connection and releases any resources.
     */
    void close();

    /**
     * Executes an update statement (INSERT, UPDATE, DELETE).
     *
     * @param sql The SQL statement to execute.
     * @param binder The binder to bind parameters to the statement.
     * @return The number of affected rows.
     */
    int update(String sql, DatabaseStatementBinder binder);

    /**
     * Executes a query statement and maps the result set.
     *
     * @param sql The SQL statement to execute.
     * @param binder The binder to bind parameters to the statement.
     * @param handler The handler to process the result set.
     */
    void query(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler);

    /**
     * Executes a query statement and maps a single result from the result set.
     *
     * @param sql The SQL statement to execute.
     * @param binder The binder to bind parameters to the statement.
     * @param handler The handler to process each row of the result set.
     */
    void queryEach(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler);

    /**
     * Executes a query statement and maps a single result from the result set.
     *
     * @param sql The SQL statement to execute.
     * @param binder The binder to bind parameters to the statement.
     * @param handler The handler to process the single result.
     */
    void querySingle(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler);

    /**
     * Executes a query statement and maps a single result from the result set.
     *
     * @param sql    The SQL statement to execute.
     * @param binder The binder to set parameters on the prepared statement.
     * @param mapper The mapper to convert the result set row to an object of type T.
     * @param <T>    The type of the object to map to.
     * @return The mapped object of type T, or null if no result was found.
     */
    <T> T querySingleMapped(String sql, DatabaseStatementBinder binder, DatabaseResultSetMapper<T> mapper);

    /**
     * Executes a raw SQL statement.
     *
     * @param sql The SQL statement to execute.
     * @return True if the execution was successful, false otherwise.
     */
    boolean execute(String sql);

    /**
     * Gets the migration version for the given migration name.
     *
     * @param name The name of the migration.
     * @return The migration version, or -1 if not found.
     */
    int migrationVersion(String name);

    /**
     * Sets the migration version for the given migration name.
     *
     * @param name The name of the migration.
     * @param version The migration version to set.
     */
    void setMigrationVersion(String name, int version);

    /**
     * Clears the migration record for the given migration name.
     *
     * @param name The name of the migration.
     */
    void clearMigration(String name);
}