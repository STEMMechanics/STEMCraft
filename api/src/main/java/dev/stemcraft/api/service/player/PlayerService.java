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

package dev.stemcraft.api.service.player;

import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Service for managing player-related functionalities.
 */
public interface PlayerService {
    
    /**
     * Hide a player from all other players.
     *
     * @param player The player to hide.
     */
    void hide(@NotNull Player player);

    /**
     * Show a player to all other players.
     *
     * @param player The player to show.
     */
    void show(@NotNull Player player);

    /**
     * Check whether a player is effectively whitelisted according to the
     * currently active whitelist authority.
     *
     * @param uuid The player UUID.
     * @param username The player username.
     * @param platform The player platform, such as java or bedrock.
     * @return True if the player is allowed by the active whitelist policy.
     */
    boolean isWhitelisted(@Nullable UUID uuid, @Nullable String username, @Nullable String platform);

    /**
     * Check whether a player is effectively whitelisted according to the
     * currently active whitelist authority.
     *
     * @param uuid The player UUID.
     * @param username The player username.
     * @return True if the player is allowed by the active whitelist policy.
     */
    default boolean isWhitelisted(@Nullable UUID uuid, @Nullable String username) {
        String platform = uuid != null && PlayerUtil.isBedrock(uuid) ? "bedrock" : "java";
        return isWhitelisted(uuid, username, platform);
    }

    /**
     * Check whether a player is effectively whitelisted according to the
     * currently active whitelist authority.
     *
     * @param player The player to test.
     * @return True if the player is allowed by the active whitelist policy.
     */
    default boolean isWhitelisted(@NotNull Player player) {
        String platform = PlayerUtil.isBedrock(player) ? "bedrock" : "java";
        return isWhitelisted(player.getUniqueId(), player.getName(), platform);
    }
}
