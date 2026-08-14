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
 */

package dev.stemcraft.api.service.dialog;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** Fluent builder for a cross-platform input dialog. */
public interface DialogBuilder {
    DialogBuilder title(Component title);

    DialogBuilder body(Component body);

    DialogBuilder textInput(String key, Component label, String initialValue, int maxLength);

    DialogBuilder multilineTextInput(String key, Component label, String initialValue, int maxLength, int lines);

    DialogBuilder submit(Component label, Consumer<DialogResponse> callback);

    DialogBuilder cancel(Component label, Runnable callback);

    /**
     * Builds and opens the dialog for a player.
     *
     * @return false if the appropriate client UI could not be opened
     */
    boolean open(Player player);
}
