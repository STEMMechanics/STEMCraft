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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Service for recording and retrieving player punishments.
 */
public interface PunishmentService {

    /**
     * Generic entry point for other plugins (e.g. Naughty feature).
     *
     * @param playerUuid The UUID of the player being punished.
     * @param actor The player issuing the punishment.
     * @param duration The duration of the punishment.
     * @param type The type of punishment (e.g., "ban", "mute").
     * @param alerted Whether alerts should be sent for this punishment.
     * @param reason The reason for the punishment.
     */
    void record(@NotNull UUID playerUuid, @Nullable Player actor, @Nullable Duration duration, @NotNull String type, boolean alerted, @NotNull String reason);

    /**
     * Latest first, 1-based page index.
     *
     * @param targetUuid The UUID of the player whose punishments to list.
     * @param type The type of punishment to filter by.
     * @param page The page number (1-based).
     * @param pageSize The number of records per page.
     * @return A list of punishment records.
     */
    @NotNull List<PunishmentRecord> list(@Nullable UUID targetUuid, @Nullable String type, int page, int pageSize);

    /**
     * Register a callback for alerting when a punishment of the given type is issued.
     *
     * @param type The type of punishment to listen for.
     * @param callback The callback to invoke when a punishment of the specified type is issued.
     */
    void registerAlert(@NotNull String type, @NotNull PunishmentAlertCallback callback);
}
