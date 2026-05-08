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

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import org.jetbrains.annotations.NotNull;

/**
 * Context exposed while registering spell-book extensions.
 */
public interface SpellBookExtensionContext {
    /**
     * Get the STEMCraft API.
     *
     * @return The API instance.
     */
    @NotNull STEMCraftAPI api();

    /**
     * Get the spell-book service.
     *
     * @return The spell-book service.
     */
    @NotNull SpellBookService spellBooks();

    /**
     * Get the config section for this extension under {@code spell-books.extensions.<id>}.
     *
     * @return The extension config section.
     */
    @NotNull ConfigSection config();
}
