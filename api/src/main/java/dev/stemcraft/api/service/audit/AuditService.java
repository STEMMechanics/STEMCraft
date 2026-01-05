package dev.stemcraft.api.service.audit;

import org.bukkit.entity.Player;

public interface AuditService {

    /**
     * Logs an action performed by a player with optional placeholders for additional context.
     *
     * @param player       The player who performed the action.
     * @param action       A string describing the action.
     * @param placeholders Optional placeholders for additional context.
     */
    void log(Player player, String action, String... placeholders);
}
