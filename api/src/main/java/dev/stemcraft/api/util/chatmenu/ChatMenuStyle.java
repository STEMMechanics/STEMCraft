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

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Semantic styles for clickable chat menus.
 *
 * <p>Menu code should use these roles instead of embedding raw Adventure colors. This keeps
 * the visual language consistent and makes palette changes a single-file change.</p>
 */
public enum ChatMenuStyle {
    /** Menu title and separator text. */
    TITLE(NamedTextColor.AQUA),
    /** Key/label text in a {@code Key: value} line. */
    KEY(NamedTextColor.GOLD),
    /** Normal readable value text. */
    VALUE(NamedTextColor.WHITE),
    /** Secondary or explanatory text. */
    MUTED(NamedTextColor.GRAY),
    /** Accent value text such as a command name or link. */
    ACCENT(NamedTextColor.AQUA),
    /** Informational/view action. */
    ACTION_VIEW(NamedTextColor.AQUA),
    /** Edit action. */
    ACTION_EDIT(NamedTextColor.AQUA),
    /** Create action. */
    ACTION_CREATE(NamedTextColor.GREEN),
    /** Add/run/positive action. */
    ACTION_ADD(NamedTextColor.GREEN),
    /** Destructive delete/remove action. */
    ACTION_DELETE(NamedTextColor.RED),
    /** Warning, pause, reload, or review action. */
    ACTION_WARNING(NamedTextColor.YELLOW),
    /** Back and other navigation actions. */
    ACTION_NAVIGATION(NamedTextColor.GOLD),
    /** Disabled or unavailable content/action. */
    DISABLED(NamedTextColor.DARK_GRAY);

    private final NamedTextColor color;

    ChatMenuStyle(NamedTextColor color) {
        this.color = color;
    }

    public NamedTextColor color() {
        return color;
    }
}
