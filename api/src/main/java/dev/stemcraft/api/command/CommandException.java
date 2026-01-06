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

package dev.stemcraft.api.command;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.PlaceholderUtil;
import lombok.Getter;

/**
 * Exception thrown to indicate a command error
 */
public class CommandException extends RuntimeException {
    /**
     * The messages to send to the command sender
     */
    @Getter
    private final String message;

    /**
     * Constructor
     */
    public CommandException() {
        super("");
        this.message = "";
    }

    /**
     * Create a new command exception with messages for the command sender.
     *
     * @param message The message key.
     * @param placeholders The placeholders to apply.
     */
    public CommandException(String message, Object... placeholders) {
        super("");
        this.message = PlaceholderUtil.apply(
                STEMCraftAPI.api().locales().resolve(message),
                placeholders
        );
    }
}