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

package dev.stemcraft.api.service.gatekeeper;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface GatekeeperService {

    boolean isBlacklisted(UUID uuid);
    default boolean isBlacklisted(Player player) { return isBlacklisted(player.getUniqueId()); }
    void blacklist(UUID uuid, boolean value);

    boolean isWhitelisted(UUID uuid);
    default boolean isWhitelisted(Player player) { return isWhitelisted(player.getUniqueId()); }
    void whitelist(UUID uuid, boolean value);
    default void whitelist(Player player, boolean value) { whitelist(player.getUniqueId(), value); }
}
