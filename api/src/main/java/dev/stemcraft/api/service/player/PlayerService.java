package dev.stemcraft.api.service.player;

import java.util.UUID;

import org.bukkit.entity.Player;

public interface PlayerService {

    /**
     * Log an action against the player. If player is null it is logged as a server action.
     */
    void log(Player player, String action, String... placeholders);

    /**
     * Hide a player from all other players.
     */
    void hide(Player player);

    /**
     * Show a player to all other players.
     */
    void show(Player player);

    /**
     * Get office players name
     */
    String getOfflineName(UUID uuid);
}
