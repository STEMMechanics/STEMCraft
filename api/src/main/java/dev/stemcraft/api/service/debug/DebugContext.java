/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.api.service.debug;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Context supplied to a debug test. */
public interface DebugContext {
    /** The sender who invoked {@code /debug}. */
    @NotNull CommandSender sender();

    /** The player being inspected, or {@code null} when no player was selected. */
    @Nullable Player player();

    /** Send an informational line to the command sender. */
    void info(@NotNull String message);

    /** Send a successful result line to the command sender. */
    void success(@NotNull String message);

    /** Send a warning line to the command sender. */
    void warn(@NotNull String message);

    /** Send an error line to the command sender. */
    void error(@NotNull String message);
}
