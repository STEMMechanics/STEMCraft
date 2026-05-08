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

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Service for parsing and querying written books that act as spells.
 */
public interface SpellBookService {
    /**
     * Check whether spell-book behaviors are enabled in config.
     *
     * @return True when enabled.
     */
    boolean isEnabled();

    /**
     * Normalize spell text into a stable matching key.
     *
     * @param spell The raw spell text.
     * @return The normalized spell text.
     */
    @NotNull String normalizeSpell(@NotNull String spell);

    /**
     * Parse a written book into a spell-book payload.
     *
     * @param item The item to inspect.
     * @return Parsed spell-book data, or null if the item is not a spell book.
     */
    @Nullable SpellBook read(@Nullable ItemStack item);

    /**
     * Check whether the item is a valid spell book.
     *
     * @param item The item to inspect.
     * @return True when the item contains a valid spell.
     */
    boolean isSpellBook(@Nullable ItemStack item);

    /**
     * Check whether the item contains the given spell.
     *
     * @param item The item to inspect.
     * @param spell The spell to match.
     * @return True when the item matches the spell.
     */
    boolean hasSpell(@Nullable ItemStack item, @NotNull String spell);

    /**
     * Read every spell book from the given inventory.
     *
     * @param inventory The inventory to inspect.
     * @return Matched spell books.
     */
    @NotNull List<SpellBookMatch> getSpellBooks(@NotNull Inventory inventory);

    /**
     * Read spell books from the given player source.
     *
     * @param player The player to inspect.
     * @param source The lookup scope.
     * @return Matched spell books.
     */
    @NotNull List<SpellBookMatch> getSpellBooks(@NotNull Player player, @NotNull SpellBookSource source);

    /**
     * Find the first matching spell in the inventory.
     *
     * @param inventory The inventory to inspect.
     * @param spell The spell to match.
     * @return The first match, or null.
     */
    @Nullable SpellBookMatch findSpell(@NotNull Inventory inventory, @NotNull String spell);

    /**
     * Find the first matching spell in the player source.
     *
     * @param player The player to inspect.
     * @param source The lookup scope.
     * @param spell The spell to match.
     * @return The first match, or null.
     */
    @Nullable SpellBookMatch findSpell(@NotNull Player player, @NotNull SpellBookSource source, @NotNull String spell);

    /**
     * Register a spell-book behavior extension.
     *
     * @param extension The extension to register.
     */
    void registerExtension(@NotNull SpellBookExtension extension);

    /**
     * Bind or clear an owner on a spell-book item.
     *
     * @param item The book item to modify.
     * @param ownerId The owner to bind, or null to clear.
     */
    void setOwner(@NotNull ItemStack item, @Nullable UUID ownerId);

    /**
     * Get the owner bound to the spell-book item.
     *
     * @param item The book item to inspect.
     * @return The owner UUID, or null if none is bound.
     */
    @Nullable UUID getOwner(@Nullable ItemStack item);

    /**
     * Get the current owner bound to a spell in a block-backed container inventory.
     *
     * @param inventory The inventory to inspect.
     * @param spell The spell to inspect.
     * @return The owner UUID, or null if no owner is bound.
     */
    @Nullable UUID getContainerSpellOwner(@NotNull Inventory inventory, @NotNull String spell);

    /**
     * Update or clear the owner bound to a spell in a block-backed container inventory.
     *
     * @param inventory The inventory to inspect.
     * @param spell The spell to update.
     * @param ownerId The owner to bind, or null to clear.
     */
    void updateContainerSpellOwner(@NotNull Inventory inventory, @NotNull String spell, @Nullable UUID ownerId);
}
