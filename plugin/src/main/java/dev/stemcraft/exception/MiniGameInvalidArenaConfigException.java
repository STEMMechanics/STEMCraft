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

import java.io.Serial;

/**
 * Core runtime exception used throughout the STEMCraft plugin.
 *
 * This exception automatically logs itself to the plugin logger when
 * instantiated, ensuring all thrown exceptions are consistently reported.
 */
@SuppressWarnings("unused")
public class MiniGameInvalidArenaConfigException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 0x9FAB4CDE57AE29ABL;

    /**
     * Creates a new {@link MiniGameInvalidArenaConfigException} with no message or cause.
     *
     * The exception is immediately logged using the plugin logger.
     */
    public MiniGameInvalidArenaConfigException() {
        STEMCraft.getPlugin().messages().error(this.getMessage(), this);
    }

    /**
     * Creates a new {@link MiniGameInvalidArenaConfigException} wrapping another throwable.
     *
     * The original throwable is set as the cause and logged automatically.
     *
     * @param t the underlying cause of this exception.
     */
    public MiniGameInvalidArenaConfigException(Throwable t) {
        super(t);
        STEMCraft.getPlugin().messages().error(this.getMessage(), this);
    }

    /**
     * Creates a new {@link MiniGameInvalidArenaConfigException} with a custom message.
     *
     * The message is logged immediately when the exception is created.
     *
     * @param message a human-readable description of the error.
     */
    public MiniGameInvalidArenaConfigException(String message) {
        super(message);
        STEMCraft.getPlugin().messages().error(message);
    }

    /**
     * Creates a new {@link MiniGameInvalidArenaConfigException} with a custom message and cause.
     *
     * Both the message and the underlying cause are logged immediately.
     *
     * @param message a human-readable description of the error.
     * @param t the underlying cause of this exception.
     */
    public MiniGameInvalidArenaConfigException(String message, Throwable t) {
        super(message, t);
        STEMCraft.getPlugin().messages().error(message, t);
    }
}