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
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.message.MessageService;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a mini-game arena with players, teams, and game states.
 */
public interface MiniGameArena extends MessageService, HasMeta<MiniGameArena> {
    String LOBBY_REGION_META_KEY = "lobbyRegion";
    String TEAM_SELECTION_INPUT_META_KEY = "teamSelectionInput";
    String TEAM_SELECTION_INPUTS_META_KEY = "teamSelectionInputs";
    String JOIN_COMMANDS_META_KEY = "joinCommands";
    String LEAVE_COMMANDS_META_KEY = "leaveCommands";
    String JOIN_PERMISSIONS_META_KEY = "joinPermissions";

    enum ArenaStatus {
        DISABLED,   // Arena is disabled/unavailable
        SETUP,      // Arena is being configured
        IDLE,       // No players in arena
        WAITING,    // Waiting forArenaStatus additional players, lobby phase
        STARTING,   // Countdown to game start
        PREPARATION,// Round setup/preparation phase after start countdown
        RUNNING,    // Game is in progress
        COOLDOWN,   // Post-round cooldown phase before final ending/reset
        ENDING,     // Game is ending, showing results
        RESETTING,  // Cleaning up after game
        SHUTDOWN    // Arena is shutting down (either by command, world unload, or server stop)
    }

    /**
     * Unique identifier for the arena.
     *
     * @return The arena ID.
     */
    String id();

    /**
     * Namespace for the arena.
     *
     * @return The arena namespace.
     */
    String namespace();

    /**
     * Display name of the arena.
     *
     * @return The arena name.
     */
    String getName();

    /**
     * Set the display name of the arena.
     *
     * @param name The new arena name.
     */
    MiniGameArena setName(String name);

    /**
     * World where the arena is located.
     *
     * @return The arena world.
     */
    World world();

    SCRegion getRegion();

    MiniGameArena setRegion(SCRegion region);

    /**
     * Current status of the arena (e.g., "waiting", "in-game", "ending").
     *
     * @return The arena status.
     */
    ArenaStatus getStatus();

    /**
     * Set the current status of the arena.
     *
     * @param status The new status.
     * @param countdown Update the countdown time in seconds (optional).
     */
    MiniGameArena setStatus(ArenaStatus status, int countdown);
    default MiniGameArena setStatus(ArenaStatus status) { return setStatus(status, 0); }

    /**
     * Validate the arena configuration using the registered handler.
     *
     * @return The validation result.
     */
    ArenaValidationResult validate();

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
    MiniGameArena setLobbySpawn(Location location);

    /**
     * Get the optional lobby region used by framework-owned lobby features such as floor team selection.
     *
     * @return The lobby region, or {@code null} if none is configured.
     */
    default SCRegion getLobbyRegion() {
        SCRegion region = get(LOBBY_REGION_META_KEY, SCRegion.class);
        return region == null ? null : region.copy();
    }

    /**
     * Set the optional lobby region used by framework-owned lobby features.
     *
     * @param region The lobby region, or {@code null} to clear it.
     * @return The arena instance.
     */
    default MiniGameArena setLobbyRegion(SCRegion region) {
        return set(LOBBY_REGION_META_KEY, region == null ? null : region.copy());
    }

    /**
     * Get the explicit team-selection input enabled for this arena.
     * A {@code null} value means players remain on auto-assignment unless they are assigned by framework defaults.
     *
     * @return The enabled team-selection input, or {@code null}.
     */
    default MiniGameTeamSelectionInput getTeamSelectionInput() {
        MiniGameTeamSelectionInput input = get(TEAM_SELECTION_INPUT_META_KEY, MiniGameTeamSelectionInput.class);
        if (input != null) {
            return input;
        }

        List<MiniGameTeamSelectionInput> legacy = getList(TEAM_SELECTION_INPUTS_META_KEY, MiniGameTeamSelectionInput.class, List.of());
        return legacy.isEmpty() ? null : legacy.getFirst();
    }

    /**
     * Set the explicit team-selection input enabled for this arena.
     *
     * @param input The enabled input, or {@code null} for auto-only behavior.
     * @return The arena instance.
     */
    default MiniGameArena setTeamSelectionInput(MiniGameTeamSelectionInput input) {
        set(TEAM_SELECTION_INPUT_META_KEY, input);
        set(TEAM_SELECTION_INPUTS_META_KEY, input == null ? List.of() : List.of(input));
        return this;
    }

    /**
     * Get commands that should run when a player or spectator joins this arena from outside the minigame.
     * Commands use the same prefix convention as world transition commands:
     * {@code server:} runs as console, otherwise the command runs as the player.
     *
     * @return The configured join commands.
     */
    default List<String> getJoinCommands() {
        return List.copyOf(getList(JOIN_COMMANDS_META_KEY, String.class, List.of()));
    }

    /**
     * Set commands that should run when a player or spectator joins this arena from outside the minigame.
     *
     * @param commands The commands to run.
     * @return The arena instance.
     */
    default MiniGameArena setJoinCommands(List<String> commands) {
        return set(JOIN_COMMANDS_META_KEY, commands == null ? List.of() : new ArrayList<>(commands));
    }

    /**
     * Get commands that should run when a player or spectator fully leaves this arena.
     *
     * @return The configured leave commands.
     */
    default List<String> getLeaveCommands() {
        return List.copyOf(getList(LEAVE_COMMANDS_META_KEY, String.class, List.of()));
    }

    /**
     * Set commands that should run when a player or spectator fully leaves this arena.
     *
     * @param commands The commands to run.
     * @return The arena instance.
     */
    default MiniGameArena setLeaveCommands(List<String> commands) {
        return set(LEAVE_COMMANDS_META_KEY, commands == null ? List.of() : new ArrayList<>(commands));
    }

    /**
     * Get permissions that should be granted while a player or spectator is inside this arena.
     * These permissions are attached on join and automatically removed on leave.
     *
     * @return The configured temporary permissions.
     */
    default List<String> getJoinPermissions() {
        return List.copyOf(getList(JOIN_PERMISSIONS_META_KEY, String.class, List.of()));
    }

    /**
     * Set permissions that should be granted while a player or spectator is inside this arena.
     *
     * @param permissions The permissions to attach.
     * @return The arena instance.
     */
    default MiniGameArena setJoinPermissions(List<String> permissions) {
        return set(JOIN_PERMISSIONS_META_KEY, permissions == null ? List.of() : new ArrayList<>(permissions));
    }

    /**
     * Get the spectator spawn location.
     *
     * @return The spectator spawn location, or null to fall back to the lobby.
     */
    Location getSpectatorSpawn();

    /**
     * Set the spectator spawn location.
     *
     * @param location The spectator spawn location.
     */
    MiniGameArena setSpectatorSpawn(Location location);

    /**
     * Current countdown time in seconds.
     *
     * @return The countdown time.
     */
    int getCountdown();

    /**
     * The original countdown value for the current phase, used for progress displays.
     *
     * @return The countdown maximum for the current phase, or 0 if none.
     */
    int getCountdownMax();

    /**
     * Set the countdown time in seconds.
     *
     * @param seconds The new countdown time.
     */
    MiniGameArena setCountdown(int seconds);

    /**
     * Decrement the countdown by 1 second.
     *
     * @return The updated countdown time.
     */
    default int decrementCountdown() {
        int current = getCountdown();
        if (current > 0) {
            setCountdown(current - 1);
        }
        return getCountdown();
    }

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
     * Number of spectators currently in the arena.
     *
     * @return The number of spectators.
     */
    int numSpectators();

    /**
     * List of spectators currently in the arena.
     *
     * @return The list of spectators.
     */
    List<Player> getSpectators();

    /**
     * List of all arena occupants, including participants and spectators.
     *
     * @return The combined occupant list.
     */
    default List<Player> getOccupants() {
        List<Player> occupants = new java.util.ArrayList<>(getPlayers());
        occupants.addAll(getSpectators());
        return occupants;
    }

    /**
     * Check if a player is spectating this arena.
     *
     * @param player The player to check.
     * @return True if the player is spectating, false otherwise.
     */
    boolean hasSpectator(Player player);

    /**
     * Add a spectator to the arena.
     *
     * @param player The player to add as spectator.
     */
    void addSpectator(Player player);

    /**
     * Remove a spectator from the arena.
     *
     * @param player The player to remove as spectator.
     */
    void removeSpectator(Player player);

    /**
     * Check if a player is currently inside this arena as either a participant or spectator.
     *
     * @param player The player to check.
     * @return True if the player is in the arena.
     */
    default boolean hasOccupant(Player player) {
        return hasPlayer(player) || hasSpectator(player);
    }

    /**
     * Remove a player from the arena regardless of whether they are a participant or spectator.
     *
     * @param player The player to remove.
     */
    default void removeOccupant(Player player) {
        if (hasPlayer(player)) {
            removePlayer(player);
        } else if (hasSpectator(player)) {
            removeSpectator(player);
        }
    }

    /**
     * Remove all participants and spectators from the arena, restoring their previous state and location.
     */
    default void removeAllOccupants() {
        for (Player player : getPlayers()) {
            removePlayer(player);
        }
        for (Player spectator : getSpectators()) {
            removeSpectator(spectator);
        }
    }

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
     * Remove a team from the arena.
     *
     * @param id The team ID to remove.
     */
    void removeTeam(String id);

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
     * Give a kit to the player.
     *
     * @param player The player to receive the kit.
     * @param id The kit ID.
     * @param clearInventory Whether to clear the inventory first.
     */
    void giveKit(Player player, String id, boolean clearInventory);
    default void giveKit(Player player, String id) { giveKit(player, id, true); }

    /**
     * Enable or disable automatic ammo replenishment for a consumable material in this arena.
     *
     * @param ammo The ammo material to toggle.
     * @param enabled True to replenish this ammo after supported shots, false to disable it.
     */
    void setUnlimitedAmmo(Material ammo, boolean enabled);
    default void setUnlimitedAmmo(Material ammo) { setUnlimitedAmmo(ammo, true); }

    /**
     * Check whether an ammo material is replenished automatically in this arena.
     *
     * @param ammo The ammo material to check.
     * @return True if the ammo is unlimited in this arena.
     */
    boolean hasUnlimitedAmmo(Material ammo);

    /**
     * Get the ammo materials configured for automatic replenishment in this arena.
     *
     * @return The configured unlimited ammo materials.
     */
    Set<Material> getUnlimitedAmmo();

    /**
     * Enable or disable automatic replenishment for a placed block material in this arena.
     *
     * @param material The block material to replenish after placement.
     * @param enabled True to replenish the material, false to disable it.
     */
    void setUnlimitedPlacement(Material material, boolean enabled);
    default void setUnlimitedPlacement(Material material) { setUnlimitedPlacement(material, true); }

    /**
     * Check whether a placed block material is replenished automatically in this arena.
     *
     * @param material The material to check.
     * @return True if the material is replenished after placement.
     */
    boolean hasUnlimitedPlacement(Material material);

    /**
     * Get the placed block materials configured for automatic replenishment in this arena.
     *
     * @return The configured unlimited placement materials.
     */
    Set<Material> getUnlimitedPlacements();

    /**
     * Start a temporary named celebration using fireworks at one or more locations.
     *
     * @param key The celebration key. Reusing the same key replaces the previous effect.
     * @param locations The celebration anchor locations.
     * @param durationSeconds The duration of the celebration in seconds.
     * @param colors The firework colors to use. If omitted, a default palette is used.
     */
    void startCelebration(String key, Collection<Location> locations, int durationSeconds, Color... colors);
    default void startCelebration(String key, Location location, int durationSeconds, Color... colors) {
        startCelebration(key, location == null ? List.of() : List.of(location), durationSeconds, colors);
    }

    /**
     * Stop a named active celebration for this arena.
     *
     * @param key The celebration key.
     */
    void stopCelebration(String key);

    /**
     * Stop all active celebrations for this arena.
     */
    void stopAllCelebrations();

    /**
     * Start a temporary winner celebration using fireworks at one or more locations.
     *
     * @param locations The celebration anchor locations.
     * @param durationSeconds The duration of the celebration in seconds.
     * @param colors The firework colors to use. If omitted, a default palette is used.
     */
    default void startWinnerCelebration(Collection<Location> locations, int durationSeconds, Color... colors) {
        startCelebration("winner", locations, durationSeconds, colors);
    }
    default void startWinnerCelebration(Location location, int durationSeconds, Color... colors) {
        startWinnerCelebration(location == null ? List.of() : List.of(location), durationSeconds, colors);
    }

    /**
     * Stop any active winner celebration for this arena.
     */
    default void stopWinnerCelebration() {
        stopCelebration("winner");
    }

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
    MiniGameArena setMinPlayers(int minPlayers);

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
    MiniGameArena setMaxPlayers(int maxPlayers);

    /**
     * Show a title and subtitle to the arena players.
     *
     * @param title The title text.
     * @param subtitle The subtitle text.
     * @param fadeIn The duration to fade in.
     * @param stay The duration to stay.
     * @param fadeOut The duration to fade out.
     */
    void showTitle(String title, String subtitle, Duration fadeIn, Duration stay, Duration fadeOut);

    default void showTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        showTitle(title, subtitle, Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut));
    }

    default void showTitle(String title, String subtitle) {
        showTitle(title, subtitle, Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(1000));
    }

    /**
     * Show a standard start countdown title to the arena players.
     *
     * @param secondsRemaining The number of seconds remaining before the round starts.
     * @param subtitle The subtitle text to show beneath the countdown.
     */
    default void showStartingCountdownTitle(int secondsRemaining, String subtitle) {
        if (secondsRemaining <= 0) {
            return;
        }

        showTitle(
            "<gradient:#fde047:#f97316><bold>" + secondsRemaining + "</bold></gradient>",
            subtitle,
            0,
            1000,
            200
        );
    }

    /**
     * Show a standard start countdown title to the arena players.
     *
     * @param secondsRemaining The number of seconds remaining before the round starts.
     */
    default void showStartingCountdownTitle(int secondsRemaining) {
        showStartingCountdownTitle(secondsRemaining, "<gold>Game starts in</gold>");
    }

    /**
     * Pull one arena occupant toward a target location at a fixed movement speed.
     *
     * @param player The player to pull.
     * @param target The destination location.
     * @param blocksPerSecond Straight-line movement speed in blocks per second.
     */
    default void pullPlayer(Player player, Location target, double blocksPerSecond) {
        pullPlayer(player, target, blocksPerSecond, null);
    }

    /**
     * Pull one arena occupant toward a target location at a fixed movement speed.
     *
     * @param player The player to pull.
     * @param target The destination location.
     * @param blocksPerSecond Straight-line movement speed in blocks per second.
     * @param onComplete Optional callback invoked after the pull completes.
     */
    default void pullPlayer(Player player, Location target, double blocksPerSecond, Runnable onComplete) {
        if (player == null || target == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        pullPlayers(Map.of(player, target), blocksPerSecond, onComplete);
    }

    /**
     * Pull arena occupants toward explicit target locations at a fixed movement speed.
     *
     * Movement is applied in straight-line steps each tick until every target is reached.
     *
     * @param targets Destination locations keyed by player.
     * @param blocksPerSecond Straight-line movement speed in blocks per second.
     */
    default void pullPlayers(Map<Player, Location> targets, double blocksPerSecond) {
        pullPlayers(targets, blocksPerSecond, null);
    }

    /**
     * Pull arena occupants toward explicit target locations at a fixed movement speed.
     *
     * Movement is applied in straight-line steps each tick until every target is reached.
     * Any existing arena player pull is replaced.
     *
     * @param targets Destination locations keyed by player.
     * @param blocksPerSecond Straight-line movement speed in blocks per second.
     * @param onComplete Optional callback invoked after all pulls complete.
     */
    void pullPlayers(Map<Player, Location> targets, double blocksPerSecond, Runnable onComplete);

    /**
     * Cancel any active arena-managed player pulls.
     */
    void cancelPlayerPulls();

    /**
     * Check whether the player is currently being pulled by the arena framework.
     *
     * @param player The player to check.
     * @return {@code true} if the player is currently being pulled.
     */
    boolean isPlayerBeingPulled(Player player);

    /**
     * Reset the title to the default state for all players.
     */
    void resetTitle();

    /**
     * Track a supply-drop item so the arena can render and clean up a shared visual marker for it.
     *
     * @param item The dropped item entity.
     * @param markerLocation The anchor location to use for the drop signal.
     */
    void trackSupplyDrop(Item item, Location markerLocation);

    /**
     * Spawn a shared supply-drop crate that descends onto the target location and turns into a chest on landing.
     *
     * @param item The loot to place into the landed chest.
     * @param landingLocation The target landing location above the accepted support block.
     */
    void spawnSupplyDropCrate(ItemStack item, Location landingLocation);

    /**
     * Spawn a shared supply-drop crate that descends onto the target location and turns into a chest on landing.
     *
     * @param items The loot stacks to place into the landed chest.
     * @param landingLocation The target landing location above the accepted support block.
     */
    void spawnSupplyDropCrate(Collection<ItemStack> items, Location landingLocation);

    /**
     * Clear all active supply-drop markers in this arena.
     */
    void clearAllSupplyDrops();

    /**
     * Count currently active framework-managed supply drops in this arena.
     *
     * This includes both in-flight crate visuals and landed crates that have not yet been removed.
     *
     * @return The current active supply-drop count.
     */
    int countActiveSupplyDrops();

    /**
     * Find a valid supply-drop spawn location inside this arena's configured arena region.
     *
     * The search uses random columns inside {@code arenaRegion}, chooses the highest non-air block
     * in that column, and accepts it only when the block material is allowed, the surrounding
     * support blocks are solid, and there is enough passable space above it for the drop.
     *
     * @param allowedSurfaceMaterials Allowed surface materials from the minigame config.
     * @param attempts Number of random columns to try before giving up.
     * @return The drop spawn location, or {@code null} if none was found.
     */
    Location findRandomSupplyDropLocation(List<Material> allowedSurfaceMaterials, int attempts);

    /**
     * Check if the arena is currently active (waiting, countdown, or running).
     *
     * @return True if the arena is active, false otherwise.
     */
    default boolean isActive() {
        ArenaStatus status = getStatus();
        return status == ArenaStatus.WAITING
            || status == ArenaStatus.STARTING
            || status == ArenaStatus.PREPARATION
            || status == ArenaStatus.RUNNING
            || status == ArenaStatus.COOLDOWN;
    }

    /**
     * Check if players can join the pending/current game in the arena.
     * If the arena is in WAITING or STARTING status, players can join
     * and are placed into the game.
     * If the arena is in any other status, players cannot join and
     * will be placed into the lobby.
     *
     * @return True if players can join, false otherwise.
     */
    default boolean isJoinable() {
        ArenaStatus status = getStatus();
        return status == ArenaStatus.WAITING || status == ArenaStatus.STARTING;
    }

    /**
     * Check if the arena is in configuration mode (SETUP or DISABLED).
     *
     * @return True if the arena is in configuration mode, false otherwise.
     */
    default boolean isInConfigMode() {
        return getStatus() == ArenaStatus.SETUP || getStatus() == ArenaStatus.DISABLED;
    }
}
