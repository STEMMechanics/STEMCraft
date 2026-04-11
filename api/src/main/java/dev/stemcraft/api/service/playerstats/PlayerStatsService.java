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

package dev.stemcraft.api.service.playerstats;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PlayerStatsService {

    void register(@NotNull PlayerStatDefinition definition);

    void unregister(@NotNull String name);

    @Nullable PlayerStatDefinition getDefinition(@NotNull String key);

    @NotNull List<PlayerStatDefinition> getDefinitions();

    void increment(@NotNull UUID playerUuid, @Nullable String username, @NotNull String key, double amount);

    void set(@NotNull UUID playerUuid, @Nullable String username, @NotNull String key, double value);

    double total(@NotNull UUID playerUuid, @NotNull String key);

    void captureOnlinePlayers();

    @NotNull List<PlayerStatsRecord> list(@Nullable String uuidText, @Nullable String username, @Nullable String statKey, @Nullable String period);

    @NotNull List<PlayerStatsRecord> top(@NotNull String statKey, int limit, @Nullable String period);

    @NotNull Map<String, Object> buildWebhookStatsResponse(@Nullable String uuidText, @Nullable String username, @Nullable String statKey, @Nullable String period);
}
