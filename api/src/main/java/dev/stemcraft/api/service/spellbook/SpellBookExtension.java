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

package dev.stemcraft.api.service.spellbook;

import org.jetbrains.annotations.NotNull;

/**
 * Extension point for adding spell-book behaviors.
 */
public interface SpellBookExtension {
    /**
     * Stable id for this extension registration.
     *
     * @return Extension id.
     */
    @NotNull String id();

    /**
     * Register this spell-book behavior.
     *
     * @param context Registration context.
     */
    void register(@NotNull SpellBookExtensionContext context);
}
