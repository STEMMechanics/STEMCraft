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

package dev.stemcraft.api.database;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Functional interface for handling database result sets.
 */
@FunctionalInterface
public interface DatabaseResultSetHandler {
    /**
     * Accepts a ResultSet for processing.
     *
     * @param rs The ResultSet to process.
     * @throws SQLException If an SQL error occurs while processing the ResultSet.
     */
    void accept(ResultSet rs) throws SQLException;
}
