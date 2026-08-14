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

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** A validated snapshot of values submitted by a dialog. */
public record DialogResponse(@NotNull String reference,
                             @NotNull Player player,
                             @NotNull Map<String, String> values) {
    public DialogResponse {
        values = Map.copyOf(values);
    }

    public @NotNull String text(@NotNull String key) {
        return values.getOrDefault(key, "");
    }
}
