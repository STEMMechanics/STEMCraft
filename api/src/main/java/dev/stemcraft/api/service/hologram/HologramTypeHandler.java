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

package dev.stemcraft.api.service.hologram;

import java.util.List;

/**
 * Handler for different hologram types.
 */
public interface HologramTypeHandler {

    /**
     * Lists all holograms contexts of a specific type.
     * Can be null if your type does not support contexts.
     *
     * @param type The hologram type.
     * @return A list of contexts.
     */
    @SuppressWarnings("SameReturnValue")
    List<String> list(String type);

    /**
     * Lists all lines of a specific hologram type and context (if used by the handler).
     *
     * @param type The hologram type.
     * @param context The hologram context.
     * @param id The hologram ID.
     * @param data Additional data.
     * @return A list of lines for the hologram.
     */
    List<String> lines(String type, String context, int id, List<String> data);
}