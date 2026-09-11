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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the PlayerService for logging player actions.
 */
public class PlayerServiceImpl extends BaseService implements PlayerService {
    private static final Pattern PLAIN_PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern DECORATED_PLAYER_NAME_PATTERN = Pattern.compile("^[^A-Za-z0-9_]+([A-Za-z0-9_]{1,16})$");
    private final List<Player> hiddenPlayers = new ArrayList<>();

    /**
     * Constructor for PlayerServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public PlayerServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Hides a player from all other players.
     *
     * @param player The player to hide.
     */
    @Override
    public void hide(@NotNull Player player) {
        if(hiddenPlayers.contains(player)) return;

        hiddenPlayers.add(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.hidePlayer(plugin, player);
        }
    }

    /**
     * Shows a hidden player to all other players.
     *
     * @param player The player to show.
     */
    @Override
    public void show(@NotNull Player player) {
        if(!hiddenPlayers.contains(player)) return;

        hiddenPlayers.remove(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            other.showPlayer(plugin, player);
        }
    }

    @Override
    public @Nullable ResolvedPlayer resolveIdentity(@Nullable String input) {
        for (String candidate : lookupCandidates(input)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(candidate)) {
                    return ResolvedPlayer.of(online);
                }
            }

            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(candidate);
            ResolvedPlayer cachedResolved = cached == null ? null : ResolvedPlayer.of(cached);
            if (cachedResolved != null) {
                return cachedResolved;
            }

            for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                if (offline.getName() == null || !offline.getName().equalsIgnoreCase(candidate)) {
                    continue;
                }

                ResolvedPlayer resolved = ResolvedPlayer.of(offline);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        return null;
    }

    @Override
    public @Nullable ResolvedPlayer resolveIdentityByUuid(@NotNull UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return ResolvedPlayer.of(online);
        }
        return ResolvedPlayer.of(Bukkit.getOfflinePlayer(uuid));
    }

    @Override
    public boolean isWhitelisted(@Nullable UUID uuid, @Nullable String username, @Nullable String platform) {
        return PlayerUtil.isWhitelistedVanilla(uuid, username);
    }

    private @NotNull List<String> lookupCandidates(@Nullable String input) {
        if (input == null) {
            return List.of();
        }

        String name = input.trim();
        if (name.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(name);

        Matcher decoratedMatcher = DECORATED_PLAYER_NAME_PATTERN.matcher(name);
        if (decoratedMatcher.matches()) {
            candidates.add(decoratedMatcher.group(1));
        }

        if (PLAIN_PLAYER_NAME_PATTERN.matcher(name).matches()) {
            candidates.add("*" + name);
        }

        return List.copyOf(candidates);
    }
}
