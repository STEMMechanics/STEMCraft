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

/**
 * Represents a mini-game arena with players, teams, and game states.
 */
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
     *
     * @return The arena ID.
     */
    String getId();

    /**
     * Namespace for the arena.
     *
     * @return The arena namespace.
     */
    String getNamespace();

    /**
     * Display name of the arena.
     *
     * @return The arena name.
     */
    String getName();

    /**
     * World where the arena is located.
     *
     * @return The arena world.
     */
    World getWorld();

    /**
     * Current status of the arena (e.g., "waiting", "in-game", "ending").
     *
     * @return The arena status.
     */
    String getStatus();

    /**
     * Set the current status of the arena.
     *
     * @param status The new status.
     * @param countdown Update the countdown time in seconds (optional).
     */
    void setStatus(String status, int countdown);
    default void setStatus(String status) { setStatus(status, 0); }

    /**
     * Get the lobby spawn location.
     *
     * @return The lobby spawn location.
     */
    Location getLobbySpawn();

    /**
     * Set the lobby spawn location.
     *
     * @param location The new lobby spawn location.
     */
    void setLobbySpawn(Location location);

    /**
     * Current countdown time in seconds.
     *
     * @return The countdown time.
     */
    int getCountdown();

    /**
     * Set the countdown time in seconds.
     *
     * @param seconds The new countdown time.
     */
    void setCountdown(int seconds);

    /**
     * Number of players currently in the arena related to the minigame.
     *
     * @return The number of players.
     */
    int numPlayers();

    /**
     * List of players currently in the arena.
     *
     * @return The list of players.
     */
    List<Player> getPlayers();

    /**
     * Check if a player is in the arena.
     *
     * @param player The player to check.
     * @return True if the player is in the arena, false otherwise.
     */
    boolean hasPlayer(Player player);

    /**
     * Add a player to the arena and teleport them to the lobby spawn
     * or random team spawn if teams are enabled and the game has started.
     *
     * @param player The player to add.
     */
    void addPlayer(Player player);

    /**
     * Remove a player from the arena and teleport them to their previous location.
     *
     * @param player The player to remove.
     */
    void removePlayer(Player player);

    /**
     * Get the list of teams in the arena.
     *
     * @return The list of teams.
     */
    List<MiniGameTeam> getTeams();

    /**
     * Add a new team to the arena.
     *
     * @param id The team ID.
     * @param name The team name.
     * @param spawn The team spawn location.
     * @return The created MiniGameTeam.
     */
    MiniGameTeam addTeam(String id, String name, Location spawn);

    /**
     * Get a team in the arena.
     *
     * @param id The team ID to retrieve.
     * @return The MiniGameTeam with the specified ID, or null if not found.
     */
    MiniGameTeam getTeam(String id);

    /**
     * Get a random team from the arena, keeping teams balanced.
     *
     * @return The ID of the randomly selected team taking balance into account.
     */
    String getRandomTeam();

    /**
     * Assign a player to a random team, keeping teams balanced.
     *
     * @param player The player to assign to a random team taking balance into account.
     */
    void setRandomTeam(Player player);

    /**
     * Get the list of players in a specific team.
     *
     * @param team The team ID.
     * @return The list of players in the specified team.
     */
    List<Player> getTeamPlayers(String team);

    /**
     * Get the team of a specific player.
     *
     * @param player The player whose team to retrieve.
     * @return The MiniGameTeam the player belongs to.
     */
    MiniGameTeam getPlayerTeam(Player player);
    MiniGameTeam getPlayerTeam(MiniGamePlayer player);

    /**
     * Set the team of a specific player.
     *
     * @param player The player whose team to set.
     * @param team The team ID to assign the player to.
     */
    void setPlayerTeam(Player player, String team);

    /**
     * Teleport a player to a specific location.
     *
     * @param player The player to teleport.
     * @param location The location to teleport the player to.
     */
    void teleport(Player player, Location location);

    /**
     * Teleport all players in the arena to a specific location.
     *
     * @param location The location to teleport all players to.
     */
    void teleportAll(Location location);

    /**
     * Teleport all players in a specific team to a specific location.
     *
     * @param team The team ID whose players to teleport.
     * @param location The location to teleport the team players to.
     */
    void teleportTeam(String team, Location location);

    /**
     * Teleport all players in the arena to the lobby spawn.
     */
    void teleportAllToLobby();

    /**
     * Teleport a player to the lobby spawn.
     *
     * @param player The player to teleport to the lobby.
     */
    void teleportToLobby(Player player);

    /**
     * Teleport a player to their team's spawn location.
     *
     * @param player The player to teleport to their team spawn.
     */
    void teleportToTeamSpawn(Player player);

    /**
     * Teleport all players in the arena to their respective team spawn locations.
     */
    void teleportAllToTeamSpawns();

    /**
     * Get the remaining protection time for a player in seconds.
     *
     * @param player The player whose protection time to retrieve.
     * @return The remaining protection time in seconds.
     */
    int getPlayerProtectionRemaining(Player player);
    default boolean getPlayerProtection(Player player) { return getPlayerProtectionRemaining(player) != 0; }

    /**
     * Set or remove protection for a player for a specified duration in seconds.
     *
     * @param player The player whose protection to set.
     * @param protect True to set protection, false to remove it.
     * @param duration The duration of the protection in seconds. Use -1 for indefinite protection.
     */
    void setPlayerProtection(Player player, boolean protect, int duration);
    default void setPlayerProtection(Player player, boolean protect) { setPlayerProtection(player, protect, -1); }

    /**
     * Get the MiniGamePlayer wrapper for a specific player.
     *
     * @param player The player whose MiniGamePlayer to retrieve.
     * @return The MiniGamePlayer associated with the specified player.
     */
    MiniGamePlayer getPlayer(Player player);

    /**
     * Add a kit to the arena.
     *
     * @param id The kit ID.
     * @param name The kit name.
     * @param icon The kit icon material.
     * @param items The kit items as a map of Material to quantity.
     */
    void addKit(String id, String name, Material icon, Map<Material, Integer> items);

    /**
     * Remove a kit from the arena.
     *
     * @param id The kit ID to remove.
     */
    void removeKit(String id);

    /**
     * Check if a kit exists in the arena.
     *
     * @param id The kit ID to check.
     * @return True if the kit exists, false otherwise.
     */
    boolean hasKit(String id);

    /**
     * Get the items of a specific kit.
     *
     * @param id The kit ID to retrieve.
     * @return A map of Material to quantity representing the kit items.
     */
    Map<Material, Integer> getKit(String id);

    /**
     * Get the list of all kit IDs in the arena.
     *
     * @return A list of kit IDs.
     */
    List<String> getKits();

    /**
     * Get the minimum number of players required to start the game.
     *
     * @return The minimum number of players.
     */
    int getMinPlayers();

    /**
     * Set the minimum number of players required to start the game.
     *
     * @param minPlayers The minimum number of players.
     */
    void setMinPlayers(int minPlayers);

    /**
     * Get the maximum number of players allowed in the arena.
     *
     * @return The maximum number of players.
     */
    int getMaxPlayers();

    /**
     * Set the maximum number of players allowed in the arena.
     *
     * @param maxPlayers The maximum number of players.
     */
    void setMaxPlayers(int maxPlayers);

}