package dev.stemcraft.api.minigame;

import org.bukkit.World;
import org.bukkit.entity.Player;
import dev.stemcraft.api.config.ConfigSectionView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface MiniGame {

    /**
     * Gets the arena handler for this mini-game.
     *
     * @return The MiniGameArenaHandler instance.
     */
    MiniGameArenaHandler handler();

    /**
     * Whether arenas in this mini-game disable hunger by default when no arena override is set.
     *
     * @return {@code true} if hunger is disabled by default.
     */
    boolean disablesHungerByDefault();

    /**
     * Sets whether arenas in this mini-game disable hunger by default.
     *
     * @param disableHungerByDefault {@code true} to disable hunger by default.
     * @return The MiniGame instance for method chaining.
     */
    MiniGame setDisableHungerByDefault(boolean disableHungerByDefault);

    /**
     * Registers HUD lines for a specific arena status.
     *
     * @param status        The arena status for which to register the HUD.
     * @param simpleLines   The list of simple HUD lines.
     * @param detailedLines The list of detailed HUD lines.
     * @return The MiniGame instance for method chaining.
     */
    default MiniGame registerHud(MiniGameArena.ArenaStatus status, List<String> simpleLines,  List<String> detailedLines) {
        return registerHud(status, simpleLines, detailedLines, 1, "PURPLE");
    }

    /**
     * Registers HUD lines for a specific arena status with a bossbar line hold duration.
     *
     * @param status The arena status for which to register the HUD.
     * @param simpleLines The list of bossbar HUD lines.
     * @param detailedLines The list of scoreboard HUD lines.
     * @param bossBarLineHoldUpdates Number of HUD update cycles to keep each bossbar line visible.
     * @return The MiniGame instance for method chaining.
     */
    default MiniGame registerHud(MiniGameArena.ArenaStatus status,
                                 List<String> simpleLines,
                                 List<String> detailedLines,
                                 int bossBarLineHoldUpdates) {
        return registerHud(status, simpleLines, detailedLines, bossBarLineHoldUpdates, "PURPLE");
    }

    /**
     * Registers HUD lines for a specific arena status with bossbar cycling and colour.
     *
     * @param status The arena status for which to register the HUD.
     * @param simpleLines The list of bossbar HUD lines.
     * @param detailedLines The list of scoreboard HUD lines.
     * @param bossBarLineHoldUpdates Number of HUD update cycles to keep each bossbar line visible.
     * @param bossBarColor The bossbar colour or placeholder-driven colour token.
     * @return The MiniGame instance for method chaining.
     */
    MiniGame registerHud(MiniGameArena.ArenaStatus status,
                         List<String> simpleLines,
                         List<String> detailedLines,
                         int bossBarLineHoldUpdates,
                         String bossBarColor);

    /**
     * Registers a placeholder provider for arenas, teams, or players.
     *
     * @param key      The placeholder key.
     * @param provider The provider function for the placeholder.
     * @return The MiniGame instance for method chaining.
     */
    MiniGame registerArenaPlaceholder(String key, MiniGamePlaceholderProvider provider);

    /**
     * Registers a team or player placeholder.
     *
     * @param key    The placeholder key.
     * @param provider The provider function for the placeholder.
     * @return The MiniGame instance for method chaining.
     */
    MiniGame registerTeamPlaceholder(String key, MiniGamePlaceholderProvider provider);

    /**
     * Registers a player placeholder.
     *
     * @param key    The placeholder key.
     * @param provider The provider function for the placeholder.
     * @return The MiniGame instance for method chaining.
     */
    MiniGame registerPlayerPlaceholder(String key, MiniGamePlaceholderProvider provider);

    /**
     * Configure the framework-managed team selection policy for this minigame.
     *
     * @param policy The team selection policy, or {@code null} to disable framework-managed team selection.
     * @return The MiniGame instance for method chaining.
     */
    MiniGame setTeamSelectionPolicy(@Nullable MiniGameTeamSelectionPolicy policy);

    /**
     * Get the framework-managed team selection policy for this minigame.
     *
     * @return The configured team selection policy, or {@code null} if none is configured.
     */
    @Nullable MiniGameTeamSelectionPolicy getTeamSelectionPolicy();

    /** Configures optional framework-managed winner gifts. */
    default MiniGame configureRewards(@Nullable ConfigSectionView rewards) { return this; }

    /** Sends one configured random gift to each supplied winner through mail. */
    default void rewardWinners(Collection<UUID> winnerUuids) { }

    /** Sends configured winner gifts with arena details available to mail placeholders. */
    default void rewardWinners(@Nullable MiniGameArena arena, Collection<UUID> winnerUuids) {
        rewardWinners(winnerUuids);
    }

    /**
     * Gets the list of all arenas in this mini-game.
     *
     * @return A list of MiniGameArena instances.
     */
    List<MiniGameArena> arenas();

    /**
     * Gets a specific arena by its ID.
     *
     * @param arenaId The ID of the arena.
     * @return The MiniGameArena instance.
     */
    MiniGameArena arena(String arenaId);

    /**
     * Creates a new arena with the specified ID in the given world.
     *
     * @param arenaId The ID of the arena.
     * @param world   The world where the arena will be created.
     * @return The created MiniGameArena instance.
     */
    MiniGameArena createArena(String arenaId, World world);

    /**
     * Removes an arena by its ID.
     *
     * @param arenaId The ID of the arena to remove.
     */
    void removeArena(String arenaId);

    /**
     * Finds the arena a player is currently in.
     *
     * @param player The player to search for.
     * @return The MiniGameArena instance the player is in, or null if not found.
     */
    MiniGameArena findPlayer(Player player);
}
