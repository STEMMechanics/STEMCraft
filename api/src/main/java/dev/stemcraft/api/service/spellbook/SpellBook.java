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

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Parsed spell-book payload from a written book item.
 *
 * @param spell The spell text as written in the book.
 * @param normalizedSpell The normalized spell text used for matching.
 * @param ownerId Optional owner bound to the book for placed-container spells.
 */
public record SpellBook(
    @NotNull String spell,
    @NotNull String normalizedSpell,
    @Nullable UUID ownerId
) { }
