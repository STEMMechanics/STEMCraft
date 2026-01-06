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

package dev.stemcraft.api.service.punishment;

import org.bukkit.entity.Player;

/**
 * Functional interface for handling punishment alerts.
 */
@FunctionalInterface
public interface PunishmentAlertCallback {

    /**
     * Handles a punishment alert.
     *
     * @param type   The type of punishment alert.
     * @param player The player associated with the punishment.
     * @param record The punishment record details.
     * @return true if the alert was handled successfully, false otherwise.
     */
    boolean run(String type, Player player, PunishmentRecord record);
}
