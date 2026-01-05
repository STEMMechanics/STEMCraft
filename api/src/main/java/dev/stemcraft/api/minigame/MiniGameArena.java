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

package dev.stemcraft.api.minigame;

import dev.stemcraft.api.capability.HasMeta;
import dev.stemcraft.api.service.message.MessageService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public interface MiniGameArena extends MessageService, HasMeta {

    // Arena status constants
    String STATUS_SETUP   = "setup";    // Arena is being configured
    String STATUS_WAITING = "waiting";  // Waiting for players, lobby phase
    String STATUS_STARTING = "starting";    // Countdown to game start
    String STATUS_RUNNING = "running";  // Game is in progress
    String STATUS_ENDING  = "ending";   // Game is ending, showing results
    String STATUS_CLEANUP = "cleanup";  // Cleaning up after game
    String STATUS_DISABLED = "disabled";    // Arena is disabled/unavailable


    /**
     * Unique identifier for the arena.
     */
    String getId();

    /**
     * Namespace for the arena.
     */
    String getNamespace();

    /**
     * Display name of the arena.
     */
    String getName();

    /**
     * World where the arena is located.
     */
    World getWorld();

    /**
     * Current status of the arena (e.g., "waiting", "in-game", "ending").
     */
    String getStatus();

    /**
     * Set the current status of the arena.
     */
    void setStatus(String status, int countdown);
    default void setStatus(String status) { setStatus(status, 0); }

    /**
     * Get the lobby spawn location.
     */
    Location getLobbySpawn();

    /**
     * Set the lobby spawn location.
     */
    void setLobbySpawn(Location location);



    /**
     * Current countdown time in seconds.
     */
    int getCountdown();

    /**
     * Set the countdown time in seconds.
     */
    void setCountdown(int seconds);

    /**
     * Number of players currently in the arena.
     */
    int numPlayers();

    /**
     * List of players currently in the arena.
     */
    List<Player> getPlayers();

    /**
     * Check if a player is in the arena.
     */
    boolean hasPlayer(Player player);

    /**
     * Add a player to the arena.
     */
    void addPlayer(Player player);

    /**
     * Remove a player from the arena.
     */
    void removePlayer(Player player);

    /**
     * Get the list of teams in the arena.
     */
    List<MiniGameTeam> getTeams();

    MiniGameTeam addTeam(String id, String name, Location spawn);

    MiniGameTeam getTeam(String id);

    /**
     * Get a random team from the arena, keeping teams balanced.
     */
    String getRandomTeam();

    /**
     * Assign a player to a random team, keeping teams balanced.
     */
    void setRandomTeam(Player player);

    /**
     * Get the list of players in a specific team.
     */
    List<Player> getTeamPlayers(String team);

    /**
     * Get the team of a specific player.
     */
    MiniGameTeam getPlayerTeam(Player player);
    MiniGameTeam getPlayerTeam(MiniGamePlayer player);

    /**
     * Set the team of a specific player.
     */
    void setPlayerTeam(Player player, String team);

    /**
     * Teleport a player to a specific location.
     */
    void teleport(Player player, Location location);

    /**
     * Teleport all players in the arena to a specific location.
     */
    void teleportAll(Location location);

    /**
     * Teleport all players in a specific team to a specific location.
     */
    void teleportTeam(String team, Location location);

    void teleportAllToLobby();

    void teleportToLobby(Player player);

    void teleportToTeamSpawn(Player player);

    void teleportAllToTeamSpawns();

    int getPlayerProtectionRemaining(Player player);
    default boolean getPlayerProtection(Player player) { return getPlayerProtectionRemaining(player) != 0; }

    void setPlayerProtection(Player player, boolean protect, int duration);
    default void setPlayerProtection(Player player, boolean protect) { setPlayerProtection(player, protect, -1); }

    MiniGamePlayer getPlayer(Player player);

    void addKit(String id, String name, Material icon, Map<Material, Integer> items);
    void removeKit(String id);
    boolean hasKit(String id);
    Map<Material, Integer> getKit(String id);
    List<String> getKits();

    int getMinPlayers();
    void setMinPlayers(int minPlayers);
    int getMaxPlayers();
    void setMaxPlayers(int maxPlayers);

}
