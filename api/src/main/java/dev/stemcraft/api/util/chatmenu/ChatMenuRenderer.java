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

package dev.stemcraft.api.util.chatmenu;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Service for rendering chat menu components.
 */
public interface ChatMenuRenderer {

    /**
     * Renders a list of chat menu components.
     *
     * @param start The starting index for rendering.
     * @param count The number of components to render.
     * @param isPlayer Whether the rendering is for a player.
     * @return A list of rendered chat menu components.
     */
    List<Component> render(int start, int count, boolean isPlayer);
}