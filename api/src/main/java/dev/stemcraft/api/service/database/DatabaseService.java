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
import dev.stemcraft.api.database.DatabaseStatementBinder;

public interface DatabaseService {

    /**
     * Closes the database connection and releases any resources.
     */
    void close();

    /**
     * Executes an update statement (INSERT, UPDATE, DELETE).
     */
    int update(String sql, DatabaseStatementBinder binder);

    /**
     * Executes a query statement and maps the result set.
     */
    void query(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler);

    /**
     * Executes a query statement and maps a single result from the result set.
     */
    void queryEach(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler);

    /**
     * Executes a query statement and maps a single result from the result set.
     */
    void querySingle(String sql, DatabaseStatementBinder binder, DatabaseResultSetHandler handler);

    /**
     * Executes a raw SQL statement.
     */
    boolean execute(String sql);

    /**
     * Gets the migration version for the given migration name.
     */
    int migrationVersion(String name);

    /**
     * Sets the migration version for the given migration name.
     */
    void setMigrationVersion(String name, int version);

    /**
     * Clears the migration record for the given migration name.
     */
    void clearMigration(String name);
}