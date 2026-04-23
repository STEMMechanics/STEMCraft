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
 * <p>
 * This exception automatically logs itself to the plugin logger when
 * instantiated, ensuring all thrown exceptions are consistently reported.
 */
@SuppressWarnings("unused")
public class ResourcePackGeneratorException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 0x9AABC852FE2198L;

    /**
     * Creates a new {@link ResourcePackGeneratorException} with no message or cause.
     * <p>
     * The exception is immediately logged using the plugin logger.
     */
    public ResourcePackGeneratorException() {
        super();
    }

    /**
     * Creates a new {@link ResourcePackGeneratorException} wrapping another throwable.
     * <p>
     * The original throwable is set as the cause and logged automatically.
     *
     * @param t the underlying cause of this exception.
     */
    public ResourcePackGeneratorException(Throwable t) {
        super(t);
    }

    /**
     * Creates a new {@link ResourcePackGeneratorException} with a custom message.
     * <p>
     * The message is logged immediately when the exception is created.
     *
     * @param message a human-readable description of the error.
     */
    public ResourcePackGeneratorException(String message) {
        super(message);
    }

    /**
     * Creates a new {@link ResourcePackGeneratorException} with a custom message and cause.
     * <p>
     * Both the message and the underlying cause are logged immediately.
     *
     * @param message a human-readable description of the error.
     * @param t the underlying cause of this exception.
     */
    public ResourcePackGeneratorException(String message, Throwable t) {
        super(message, t);
    }


    /**
     * Logs the exception to the plugin logger.
     */
    public void log() {
        if (getCause() != null) {
            STEMCraft.getPlugin().messages().error(getMessage(), getCause());
        } else {
            STEMCraft.getPlugin().messages().error(getMessage());
        }
    }
}