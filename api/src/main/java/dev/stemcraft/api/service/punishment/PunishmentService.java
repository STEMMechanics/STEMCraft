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

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface PunishmentService {

    /**
     * Generic entry point for other plugins (e.g. Naughty feature).
     */
    void record(UUID playerUuid, Player actor, Duration duration, String type, boolean alerted, String reason);

    /**
     * Latest first, 1-based page index.
     */
    List<PunishmentRecord> list(UUID targetUuid, String type, int page, int pageSize);

    /**
     * Register a callback for alerting when a punishment of the given type is issued.
     */
    void registerAlert(String type, PunishmentAlertCallback callback);
}