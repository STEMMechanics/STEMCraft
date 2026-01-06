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

package dev.stemcraft.api.service.audit;

import org.bukkit.entity.Player;

/**
 * Service for logging audit actions performed by players.
 */
public interface AuditService {

    /**
     * Logs an action performed by a player with optional placeholders for additional context.
     *
     * @param player       The player who performed the action.
     * @param action       A string describing the action.
     * @param placeholders Optional placeholders for additional context.
     */
    void log(Player player, String action, String... placeholders);
}
