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

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * Utility class for permission checks using LuckPerms.
 */
public class PermissionUtil {
    private static Boolean isLuckPermsInstalled = null;
    private static LuckPerms luckPermsApi = null;

    /**
     * Checks if a player has a specific permission using LuckPerms.
     *
     * @param uuid       The UUID of the player.
     * @param permission The permission node to check.
     * @return True if the player has the permission, false otherwise.
     */
    public static boolean hasPermission(UUID uuid, String permission) {
        if(isLuckPermsInstalled == null) {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                isLuckPermsInstalled = true;
                luckPermsApi = LuckPermsProvider.get();
            }

            return false;
        }

        if(!isLuckPermsInstalled) {
            return false;
        }

        User user = luckPermsApi.getUserManager().getUser(uuid);
        if (user == null) return false;

        return user.getCachedData()
                .getPermissionData()
                .checkPermission(permission)
                .asBoolean();
    }
}
