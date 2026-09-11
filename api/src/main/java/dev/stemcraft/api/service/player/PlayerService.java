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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Service for managing player-related functionalities.
 */
public interface PlayerService {
    /**
     * Resolved player identity data.
     *
     * @param uuid The player's UUID.
     * @param name The canonical player name known by the server.
     * @param platform The resolved platform, such as java or bedrock.
     */
    record ResolvedPlayer(
        @NotNull UUID uuid,
        @NotNull String name,
        @NotNull String platform
    ) {
        public ResolvedPlayer {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(platform, "platform");
        }

        public static @NotNull ResolvedPlayer of(@NotNull Player player) {
            return new ResolvedPlayer(
                player.getUniqueId(),
                player.getName(),
                PlayerUtil.isBedrock(player) ? "bedrock" : "java"
            );
        }

        public static @Nullable ResolvedPlayer of(@NotNull OfflinePlayer player) {
            if (player.getName() == null) {
                return null;
            }

            return new ResolvedPlayer(
                player.getUniqueId(),
                player.getName(),
                PlayerUtil.isBedrock(player.getUniqueId()) ? "bedrock" : "java"
            );
        }
    }
    
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
     * Resolve a player identity from user-supplied input.
     *
     * Supports exact Bukkit/Floodgate names first, then common java/bedrock
     * alias forms such as {@code name} and {@code *name}.
     *
     * @param input The player name or alias entered by a user.
     * @return The resolved player identity, or null if no player matches.
     */
    @Nullable ResolvedPlayer resolveIdentity(@Nullable String input);

    /** Resolve the current known identity for a player UUID. */
    default @Nullable ResolvedPlayer resolveIdentityByUuid(@NotNull UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        return online == null ? ResolvedPlayer.of(Bukkit.getOfflinePlayer(uuid)) : ResolvedPlayer.of(online);
    }

    /**
     * Check whether a player is allowed by the server's whitelist rules.
     *
     * @param uuid The player UUID.
     * @param username The player username.
     * @param platform The player platform, such as java or bedrock.
     * @return True if the player is allowed by the server whitelist.
     */
    boolean isWhitelisted(@Nullable UUID uuid, @Nullable String username, @Nullable String platform);

    /**
     * Check whether a player is allowed by the server's whitelist rules.
     *
     * @param uuid The player UUID.
     * @param username The player username.
     * @return True if the player is allowed by the server whitelist.
     */
    default boolean isWhitelisted(@Nullable UUID uuid, @Nullable String username) {
        String platform = PlayerUtil.isBedrock(uuid) ? "bedrock" : "java";
        return isWhitelisted(uuid, username, platform);
    }

    /**
     * Check whether a player is allowed by the server's whitelist rules.
     *
     * @param player The player to test.
     * @return True if the player is allowed by the server whitelist.
     */
    default boolean isWhitelisted(@NotNull Player player) {
        String platform = PlayerUtil.isBedrock(player) ? "bedrock" : "java";
        return isWhitelisted(player.getUniqueId(), player.getName(), platform);
    }
}
