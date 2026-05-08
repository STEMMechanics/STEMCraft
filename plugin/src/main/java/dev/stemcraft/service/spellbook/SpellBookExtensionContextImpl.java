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

package dev.stemcraft.service.spellbook;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.spellbook.SpellBookExtensionContext;
import dev.stemcraft.api.service.spellbook.SpellBookService;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Default spell-book extension registration context.
 */
public final class SpellBookExtensionContextImpl implements SpellBookExtensionContext {
    private final STEMCraftAPI api;
    private final SpellBookService spellBooks;
    private final Supplier<ConfigSection> configSupplier;

    public SpellBookExtensionContextImpl(@NotNull STEMCraftAPI api,
                                         @NotNull SpellBookService spellBooks,
                                         @NotNull Supplier<ConfigSection> configSupplier) {
        this.api = api;
        this.spellBooks = spellBooks;
        this.configSupplier = configSupplier;
    }

    @Override
    public @NotNull STEMCraftAPI api() {
        return api;
    }

    @Override
    public @NotNull SpellBookService spellBooks() {
        return spellBooks;
    }

    @Override
    public @NotNull ConfigSection config() {
        return configSupplier.get();
    }
}
