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

package dev.stemcraft.exception;

import dev.stemcraft.STEMCraft;

/**
 * Represents our core exception.
 */
public class STEMCraftException extends RuntimeException {

    /**
     * Create a new exception.
     */
    public STEMCraftException() {
        STEMCraft.getPlugin().error(this.getMessage(), this);
    }

    /**
     * Create a new exception.
     * @param t
     */
    public STEMCraftException(Throwable t) {
        super(t);
        STEMCraft.getPlugin().error(this.getMessage(), this);
    }

    /**
     * Create a new exception.
     * @param message
     */
    public STEMCraftException(String message) {
        super(message);
        STEMCraft.getPlugin().error(message);
    }

    /**
     * Create a new exception.
     * @param message
     * @param t
     */
    public STEMCraftException(String message, Throwable t) {
        super(message, t);
        STEMCraft.getPlugin().error(message, t);
    }
}
