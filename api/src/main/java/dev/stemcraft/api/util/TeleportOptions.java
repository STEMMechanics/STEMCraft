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

package dev.stemcraft.api.util;

/**
 * Options that control how STEMCraft should treat a plugin-owned teleport.
 *
 * @param updateBackLocation Whether /back should record the pre-teleport location.
 * @param updateWorldLastLocation Whether world last-location tracking should update.
 * @param logToConsole Whether TeleportUtils should emit a console log line.
 * @param grantDamageProtection Whether TeleportUtils should grant teleport protection.
 */
public record TeleportOptions(
    boolean updateBackLocation,
    boolean updateWorldLastLocation,
    boolean logToConsole,
    boolean grantDamageProtection
) {
    public static final TeleportOptions DEFAULT = new TeleportOptions(true, true, true, true);
    public static final TeleportOptions INTERNAL = new TeleportOptions(false, false, false, false);
}
