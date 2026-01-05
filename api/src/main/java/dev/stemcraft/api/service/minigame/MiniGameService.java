package dev.stemcraft.api.service.minigame;

import dev.stemcraft.api.capability.HasMeta;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameHudProvider;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public interface MiniGameService extends HasMeta {

    /**
     * Register a new mini-game arena.
     *
     * @param namespace The namespace for the arena (usually the mini-game name)
     * @param id The unique ID for the arena
     * @param name The display name for the arena
     * @param world The world where the arena is located
     * @return The registered MiniGameArena instance
     */
    MiniGameArena addArena(String namespace, String id, String name, World world);

    /**
     * Remove an existing mini-game arena.
     *
     * @param namespace The namespace for the arena
     * @param id The unique ID of the arena to remove
     */
    void removeArena(String namespace, String id);

    /**
     * Check if a mini-game arena exists.
     *
     * @param namespace The namespace for the arena
     * @param id The unique ID of the arena
     * @return True if the arena exists, false otherwise
     */
    boolean hasArena(String namespace, String id);

    /**
     * Get a list of all arena IDs for a specific mini-game namespace.
     *
     * @param namespace The namespace for the mini-game
     * @return A list of arena IDs
     */
    List<MiniGameArena> getArenas(String namespace);

    /**
     * Add a player to a mini-game arena.
     * @param player
     * @param namespace
     * @param arenaId
     */
    void addPlayer(Player player, String namespace, String arenaId);

    /**
     * Add a player to a mini-game arena.
     * @param player
     * @param arena
     */
    void addPlayer(Player player, MiniGameArena arena);

    /**
     * Remove a player from their current mini-game arena.
     * @param player
     */
    void removePlayer(Player player);

    /**
     * Get the namespace of the mini-game arena a player is currently in.
     *
     * @param player The player to check
     * @return The namespace of the mini-game arena, or null if not in any arena
     */
    String getPlayerArenaNamespace(Player player);

    /**
     * Get the mini-game arena a player is currently in.
     *
     * @param namespace The namespace for the mini-game
     * @param player The player to check
     * @return The MiniGameArena instance the player is in, or null if not in any arena
     */
    MiniGameArena getPlayerArena(String namespace, Player player);

    /**
     * Register a HUD provider for a specific mini-game status.
     *
     * @param namespace The namespace for the mini-game
     * @param status The status for which the HUD is provided
     * @param bossBarLines Lines to display in the boss bar
     * @param scoreboardLines Lines to display in the scoreboard
     * @param dataType The expected data type of the arena
     * @param provider The HUD provider implementation
     */
    void registerHud(String namespace, String status, List<String> bossBarLines, List<String> scoreboardLines, MiniGameHudProvider provider);
}
